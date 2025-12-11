package cl.hec.neo.fhir.core.service;

import cl.hec.neo.fhir.core.model.MasterCatalog;
import cl.hec.neo.fhir.core.model.Tenant;
import cl.hec.neo.fhir.core.repository.MasterCatalogRepository;
import cl.hec.neo.fhir.core.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para gestión de catálogos maestros (LOINC, SNOMED-CT, ICD-10, etc.)
 *
 * Responsabilidades:
 * - CRUD de códigos en catálogos maestros
 * - Búsqueda y validación de códigos
 * - Gestión de jerarquías de códigos
 * - Mapeo entre sistemas de codificación
 * - Versionamiento de códigos
 * - Catálogos globales vs tenant-specific
 */
@Service
@Transactional
public class NeoMasterCatalogService {

    private static final Logger log = LoggerFactory.getLogger(NeoMasterCatalogService.class);

    private final MasterCatalogRepository catalogRepository;
    private final TenantRepository tenantRepository;
    private final NeoAuditService auditService;

    public NeoMasterCatalogService(
            MasterCatalogRepository catalogRepository,
            TenantRepository tenantRepository,
            NeoAuditService auditService) {
        this.catalogRepository = catalogRepository;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
    }

    /**
     * Crear un nuevo código en el catálogo
     */
    public MasterCatalog createCode(MasterCatalog catalog, String createdBy) {
        log.info("Creating catalog code: system={}, code={}, tenant={}",
            catalog.getSystem(), catalog.getCode(), catalog.getTenant() != null ? catalog.getTenant().getId() : "GLOBAL");

        // Validar que no exista ya el código
        UUID tenantId = catalog.getTenant() != null ? catalog.getTenant().getId() : null;

        if (tenantId != null) {
            Optional<MasterCatalog> existing = catalogRepository.findBySystemAndCodeAndTenantId(
                catalog.getSystem(), catalog.getCode(), tenantId);
            if (existing.isPresent()) {
                throw new IllegalArgumentException(
                    "Code already exists: " + catalog.getSystem() + "|" + catalog.getCode());
            }
        } else {
            Optional<MasterCatalog> existing = catalogRepository.findBySystemAndCodeAndTenantIdIsNull(
                catalog.getSystem(), catalog.getCode());
            if (existing.isPresent()) {
                throw new IllegalArgumentException(
                    "Global code already exists: " + catalog.getSystem() + "|" + catalog.getCode());
            }
        }

        // Validar código padre si existe
        if (catalog.getParentCode() != null) {
            validateParentCode(catalog.getParentCode(), catalog.getSystem());
        }

        // Timestamps
        catalog.setCreatedAt(LocalDateTime.now());
        catalog.setUpdatedAt(LocalDateTime.now());
        catalog.setCreatedBy(createdBy);
        catalog.setUpdatedBy(createdBy);

        MasterCatalog saved = catalogRepository.save(catalog);

        // Auditoría
        auditService.logCatalogCreated(saved, createdBy);

        log.info("Catalog code created successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Actualizar un código existente
     */
    public MasterCatalog updateCode(UUID id, MasterCatalog updates, String updatedBy) {
        log.info("Updating catalog code: id={}", id);

        MasterCatalog existing = catalogRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Catalog code not found: " + id));

        // Actualizar campos editables
        existing.setDisplay(updates.getDisplay());
        existing.setDefinition(updates.getDefinition());
        existing.setActive(updates.getActive());
        existing.setParentCode(updates.getParentCode());
        existing.setPropertiesJson(updates.getPropertiesJson());
        existing.setMappingsJson(updates.getMappingsJson());
        existing.setVersion(updates.getVersion());
        existing.setEffectiveFrom(updates.getEffectiveFrom());
        existing.setEffectiveTo(updates.getEffectiveTo());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy(updatedBy);

        // Validar código padre si cambió
        if (updates.getParentCode() != null) {
            validateParentCode(updates.getParentCode(), existing.getSystem());
        }

        MasterCatalog saved = catalogRepository.save(existing);

        // Auditoría
        auditService.logCatalogUpdated(saved, updatedBy);

        log.info("Catalog code updated successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Desactivar un código (soft delete)
     */
    public void deactivateCode(UUID id, String deactivatedBy) {
        log.info("Deactivating catalog code: id={}", id);

        MasterCatalog catalog = catalogRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Catalog code not found: " + id));

        catalog.setActive(false);
        catalog.setUpdatedAt(LocalDateTime.now());
        catalog.setUpdatedBy(deactivatedBy);

        catalogRepository.save(catalog);

        // Auditoría
        auditService.logCatalogDeactivated(catalog, deactivatedBy);

        log.info("Catalog code deactivated successfully: id={}", id);
    }

    /**
     * Buscar código por sistema y código
     */
    @Transactional(readOnly = true)
    public Optional<MasterCatalog> findBySystemAndCode(String system, String code, UUID tenantId) {
        log.debug("Finding catalog code: system={}, code={}, tenant={}", system, code, tenantId);

        // Primero buscar en catálogo específico del tenant
        if (tenantId != null) {
            Optional<MasterCatalog> tenantCatalog = catalogRepository.findBySystemAndCodeAndTenantId(
                system, code, tenantId);
            if (tenantCatalog.isPresent()) {
                return tenantCatalog;
            }
        }

        // Si no se encuentra, buscar en catálogo global
        return catalogRepository.findBySystemAndCodeAndTenantIdIsNull(system, code);
    }

    /**
     * Validar que un código existe y está activo
     */
    @Transactional(readOnly = true)
    public boolean validateCode(String system, String code, UUID tenantId) {
        Optional<MasterCatalog> catalog = findBySystemAndCode(system, code, tenantId);
        return catalog.isPresent() && catalog.get().getActive() && isEffective(catalog.get());
    }

    /**
     * Buscar códigos por tipo de catálogo
     */
    @Transactional(readOnly = true)
    public List<MasterCatalog> findByCatalogType(MasterCatalog.CatalogType type, UUID tenantId) {
        log.debug("Finding catalog codes by type: type={}, tenant={}", type, tenantId);

        if (tenantId != null) {
            return catalogRepository.findByCatalogTypeAndTenantIdOrderByDisplayAsc(type, tenantId);
        } else {
            return catalogRepository.findByCatalogTypeAndTenantIdIsNullOrderByDisplayAsc(type);
        }
    }

    /**
     * Buscar códigos activos por tipo
     */
    @Transactional(readOnly = true)
    public List<MasterCatalog> findActiveByCatalogType(MasterCatalog.CatalogType type) {
        log.debug("Finding active catalog codes by type: type={}", type);
        return catalogRepository.findByCatalogTypeAndActiveTrueOrderByDisplayAsc(type);
    }

    /**
     * Búsqueda por texto en display
     */
    @Transactional(readOnly = true)
    public List<MasterCatalog> searchByDisplay(
            MasterCatalog.CatalogType type,
            String searchText,
            UUID tenantId) {
        log.debug("Searching catalog codes by display: type={}, text={}, tenant={}",
            type, searchText, tenantId);
        return catalogRepository.searchByDisplay(type, tenantId, searchText);
    }

    /**
     * Buscar códigos por sistema
     */
    @Transactional(readOnly = true)
    public List<MasterCatalog> findBySystem(String system, UUID tenantId) {
        log.debug("Finding catalog codes by system: system={}, tenant={}", system, tenantId);

        if (tenantId != null) {
            return catalogRepository.findBySystemAndTenantIdOrderByCodeAsc(system, tenantId);
        } else {
            return catalogRepository.findBySystemAndTenantIdIsNullOrderByCodeAsc(system);
        }
    }

    /**
     * Buscar códigos hijos (jerarquía)
     */
    @Transactional(readOnly = true)
    public List<MasterCatalog> findChildren(String parentCode, String system) {
        log.debug("Finding child codes: parent={}, system={}", parentCode, system);
        return catalogRepository.findByParentCodeAndSystemOrderByCodeAsc(parentCode, system);
    }

    /**
     * Contar códigos por tipo
     */
    @Transactional(readOnly = true)
    public long countByType(MasterCatalog.CatalogType type, UUID tenantId) {
        if (tenantId != null) {
            return catalogRepository.countByCatalogTypeAndActiveTrueAndTenantId(type, tenantId);
        } else {
            return catalogRepository.countByCatalogTypeAndActiveTrueAndTenantIdIsNull(type);
        }
    }

    /**
     * Importar códigos masivamente desde una fuente externa (LOINC, SNOMED, etc.)
     */
    public int importCodes(List<MasterCatalog> codes, String importedBy) {
        log.info("Importing {} catalog codes", codes.size());

        int imported = 0;
        int skipped = 0;

        for (MasterCatalog code : codes) {
            try {
                // Verificar si ya existe
                UUID tenantId = code.getTenant() != null ? code.getTenant().getId() : null;
                Optional<MasterCatalog> existing;

                if (tenantId != null) {
                    existing = catalogRepository.findBySystemAndCodeAndTenantId(
                        code.getSystem(), code.getCode(), tenantId);
                } else {
                    existing = catalogRepository.findBySystemAndCodeAndTenantIdIsNull(
                        code.getSystem(), code.getCode());
                }

                if (existing.isEmpty()) {
                    code.setCreatedAt(LocalDateTime.now());
                    code.setUpdatedAt(LocalDateTime.now());
                    code.setCreatedBy(importedBy);
                    code.setUpdatedBy(importedBy);
                    catalogRepository.save(code);
                    imported++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("Error importing code: system={}, code={}, error={}",
                    code.getSystem(), code.getCode(), e.getMessage());
            }
        }

        log.info("Import completed: imported={}, skipped={}", imported, skipped);
        return imported;
    }

    /**
     * Crear mapeo entre códigos de diferentes sistemas
     */
    public void createMapping(UUID sourceId, String targetSystem, String targetCode, String mappedBy) {
        log.info("Creating code mapping: sourceId={}, target={}|{}", sourceId, targetSystem, targetCode);

        MasterCatalog source = catalogRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source code not found: " + sourceId));

        // Agregar mapeo al JSON de mappings
        String mapping = String.format("{\"system\": \"%s\", \"code\": \"%s\"}", targetSystem, targetCode);

        // TODO: Actualizar mappingsJson agregando el nuevo mapeo
        source.setUpdatedAt(LocalDateTime.now());
        source.setUpdatedBy(mappedBy);
        catalogRepository.save(source);

        log.info("Code mapping created successfully");
    }

    // ==================== Métodos privados ====================

    /**
     * Validar que existe el código padre
     */
    private void validateParentCode(String parentCode, String system) {
        List<MasterCatalog> parents = catalogRepository.findByParentCodeAndSystemOrderByCodeAsc(parentCode, system);
        if (parents.isEmpty()) {
            log.warn("Parent code not found: parent={}, system={}", parentCode, system);
            // No lanzar excepción, solo advertir
        }
    }

    /**
     * Verificar si un código está vigente según fechas de efectividad
     */
    private boolean isEffective(MasterCatalog catalog) {
        LocalDateTime now = LocalDateTime.now();

        if (catalog.getEffectiveFrom() != null && now.isBefore(catalog.getEffectiveFrom())) {
            return false;
        }

        if (catalog.getEffectiveTo() != null && now.isAfter(catalog.getEffectiveTo())) {
            return false;
        }

        return true;
    }
}
