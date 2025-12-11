package cl.hec.neo.fhir.core.service;

import cl.hec.neo.fhir.core.model.StoredQuery;
import cl.hec.neo.fhir.core.model.Tenant;
import cl.hec.neo.fhir.core.repository.StoredQueryRepository;
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
 * Servicio de negocio para gestión de queries FHIR almacenadas
 *
 * Responsabilidades:
 * - CRUD de queries predefinidas
 * - Ejecución y tracking de uso
 * - Compartir queries entre usuarios (públicas vs privadas)
 * - Analítica de uso
 * - Parametrización de queries
 * - Favoritos por usuario
 */
@Service
@Transactional
public class NeoStoredQueryService {

    private static final Logger log = LoggerFactory.getLogger(NeoStoredQueryService.class);

    private final StoredQueryRepository queryRepository;
    private final NeoAuditService auditService;

    public NeoStoredQueryService(
            StoredQueryRepository queryRepository,
            NeoAuditService auditService) {
        this.queryRepository = queryRepository;
        this.auditService = auditService;
    }

    /**
     * Crear una nueva query almacenada
     */
    public StoredQuery createQuery(StoredQuery query, String createdBy) {
        log.info("Creating stored query: name={}, tenant={}, resource={}",
            query.getName(), query.getTenant().getId(), query.getResourceType());

        // Validar que no exista ya una query con el mismo nombre en el tenant
        Optional<StoredQuery> existing = queryRepository.findByTenantIdAndName(
            query.getTenant().getId(), query.getName());

        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                "Query with name '" + query.getName() + "' already exists for this tenant");
        }

        // Timestamps
        query.setCreatedAt(LocalDateTime.now());
        query.setUpdatedAt(LocalDateTime.now());
        query.setCreatedBy(createdBy);
        query.setUpdatedBy(createdBy);
        query.setUsageCount(0L);

        StoredQuery saved = queryRepository.save(query);

        // Auditoría
        auditService.auditCreate(saved.getTenant(), "StoredQuery", saved.getId().toString(), createdBy, saved);

        log.info("Stored query created successfully: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Actualizar una query existente
     */
    public StoredQuery updateQuery(UUID id, StoredQuery updates, String updatedBy) {
        log.info("Updating stored query: id={}", id);

        StoredQuery existing = queryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Stored query not found: " + id));

        // Backup del estado anterior para auditoría
        StoredQuery beforeState = clone(existing);

        // Actualizar campos editables
        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        existing.setResourceType(updates.getResourceType());
        existing.setQueryString(updates.getQueryString());
        existing.setParametersJson(updates.getParametersJson());
        existing.setIsPublic(updates.getIsPublic());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy(updatedBy);

        // Si cambió el nombre, validar que no exista otra con ese nombre
        if (!existing.getName().equals(beforeState.getName())) {
            Optional<StoredQuery> duplicate = queryRepository.findByTenantIdAndName(
                existing.getTenant().getId(), existing.getName());
            if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                throw new IllegalArgumentException(
                    "Query with name '" + existing.getName() + "' already exists for this tenant");
            }
        }

        StoredQuery saved = queryRepository.save(existing);

        // Auditoría
        auditService.auditUpdate(saved.getTenant(), "StoredQuery", saved.getId().toString(),
            updatedBy, beforeState, saved);

        log.info("Stored query updated successfully: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Eliminar una query almacenada
     */
    public void deleteQuery(UUID id, String deletedBy) {
        log.info("Deleting stored query: id={}", id);

        StoredQuery query = queryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Stored query not found: " + id));

        // Auditoría antes de eliminar
        auditService.auditDelete(query.getTenant(), "StoredQuery", query.getId().toString(), deletedBy, query);

        queryRepository.delete(query);

        log.info("Stored query deleted successfully: id={}, name={}", id, query.getName());
    }

    /**
     * Buscar query por ID
     */
    @Transactional(readOnly = true)
    public Optional<StoredQuery> findById(UUID id) {
        log.debug("Finding stored query by id: {}", id);
        return queryRepository.findById(id);
    }

    /**
     * Buscar query por nombre y tenant
     */
    @Transactional(readOnly = true)
    public Optional<StoredQuery> findByName(UUID tenantId, String name) {
        log.debug("Finding stored query by name: tenant={}, name={}", tenantId, name);
        return queryRepository.findByTenantIdAndName(tenantId, name);
    }

    /**
     * Listar todas las queries de un tenant
     */
    @Transactional(readOnly = true)
    public Page<StoredQuery> findByTenant(UUID tenantId, Pageable pageable) {
        log.debug("Finding stored queries by tenant: {}", tenantId);
        return queryRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    /**
     * Listar queries por tipo de recurso
     */
    @Transactional(readOnly = true)
    public List<StoredQuery> findByResourceType(UUID tenantId, String resourceType) {
        log.debug("Finding stored queries by resource type: tenant={}, type={}",
            tenantId, resourceType);
        return queryRepository.findByTenantIdAndResourceTypeOrderByNameAsc(tenantId, resourceType);
    }

    /**
     * Listar queries públicas de un tenant
     */
    @Transactional(readOnly = true)
    public List<StoredQuery> findPublicQueries(UUID tenantId) {
        log.debug("Finding public queries for tenant: {}", tenantId);
        return queryRepository.findByTenantIdAndIsPublicTrueOrderByNameAsc(tenantId);
    }

    /**
     * Listar queries creadas por un usuario
     */
    @Transactional(readOnly = true)
    public List<StoredQuery> findByCreator(UUID tenantId, String createdBy) {
        log.debug("Finding queries created by: tenant={}, creator={}", tenantId, createdBy);
        return queryRepository.findByTenantIdAndCreatedByOrderByCreatedAtDesc(tenantId, createdBy);
    }

    /**
     * Buscar queries por nombre (búsqueda parcial)
     */
    @Transactional(readOnly = true)
    public List<StoredQuery> searchByName(UUID tenantId, String searchText) {
        log.debug("Searching queries by name: tenant={}, text={}", tenantId, searchText);
        return queryRepository.searchByName(tenantId, searchText);
    }

    /**
     * Listar queries más usadas
     */
    @Transactional(readOnly = true)
    public List<StoredQuery> findMostUsed(UUID tenantId, int limit) {
        log.debug("Finding most used queries: tenant={}, limit={}", tenantId, limit);
        return queryRepository.findTopByTenantIdOrderByUsageCountDesc(tenantId,
            org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /**
     * Listar queries usadas recientemente
     */
    @Transactional(readOnly = true)
    public List<StoredQuery> findRecentlyUsed(UUID tenantId, int limit) {
        log.debug("Finding recently used queries: tenant={}, limit={}", tenantId, limit);
        return queryRepository.findTopByTenantIdOrderByLastUsedAtDesc(tenantId,
            org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /**
     * Registrar uso de una query (incrementar contador)
     */
    public void recordUsage(UUID id, String usedBy) {
        log.debug("Recording usage for query: id={}, user={}", id, usedBy);

        StoredQuery query = queryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Stored query not found: " + id));

        query.setUsageCount(query.getUsageCount() + 1);
        query.setLastUsedAt(LocalDateTime.now());

        queryRepository.save(query);

        log.debug("Usage recorded: query={}, newCount={}", query.getName(), query.getUsageCount());
    }

    /**
     * Obtener estadísticas de uso de una query
     */
    @Transactional(readOnly = true)
    public QueryStats getQueryStats(UUID id) {
        StoredQuery query = queryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Stored query not found: " + id));

        return QueryStats.builder()
            .queryId(query.getId())
            .queryName(query.getName())
            .usageCount(query.getUsageCount())
            .lastUsedAt(query.getLastUsedAt())
            .createdAt(query.getCreatedAt())
            .createdBy(query.getCreatedBy())
            .isPublic(query.getIsPublic())
            .build();
    }

    /**
     * Obtener estadísticas globales de queries por tenant
     */
    @Transactional(readOnly = true)
    public TenantQueryStats getTenantStats(UUID tenantId) {
        long totalQueries = queryRepository.countByTenantId(tenantId);
        long publicQueries = queryRepository.countByTenantIdAndIsPublicTrue(tenantId);
        long privateQueries = totalQueries - publicQueries;
        long totalUsages = queryRepository.sumUsageCountByTenantId(tenantId);

        return TenantQueryStats.builder()
            .tenantId(tenantId)
            .totalQueries(totalQueries)
            .publicQueries(publicQueries)
            .privateQueries(privateQueries)
            .totalUsages(totalUsages)
            .build();
    }

    /**
     * Duplicar una query (para reutilización con modificaciones)
     */
    public StoredQuery duplicateQuery(UUID sourceId, String newName, String duplicatedBy) {
        log.info("Duplicating query: source={}, newName={}", sourceId, newName);

        StoredQuery source = queryRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source query not found: " + sourceId));

        // Validar que no exista ya una query con el nuevo nombre
        Optional<StoredQuery> existing = queryRepository.findByTenantIdAndName(
            source.getTenant().getId(), newName);
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                "Query with name '" + newName + "' already exists for this tenant");
        }

        // Crear copia
        StoredQuery duplicate = new StoredQuery();
        duplicate.setTenant(source.getTenant());
        duplicate.setName(newName);
        duplicate.setDescription("Copy of: " + source.getDescription());
        duplicate.setResourceType(source.getResourceType());
        duplicate.setQueryString(source.getQueryString());
        duplicate.setParametersJson(source.getParametersJson());
        duplicate.setIsPublic(false); // Las copias son privadas por defecto
        duplicate.setUsageCount(0L);
        duplicate.setCreatedAt(LocalDateTime.now());
        duplicate.setUpdatedAt(LocalDateTime.now());
        duplicate.setCreatedBy(duplicatedBy);
        duplicate.setUpdatedBy(duplicatedBy);

        StoredQuery saved = queryRepository.save(duplicate);

        // Auditoría
        auditService.auditCreate(saved.getTenant(), "StoredQuery", saved.getId().toString(),
            duplicatedBy, saved);

        log.info("Query duplicated successfully: source={}, new={}", sourceId, saved.getId());
        return saved;
    }

    // ==================== Helper Methods ====================

    private StoredQuery clone(StoredQuery source) {
        StoredQuery clone = new StoredQuery();
        clone.setId(source.getId());
        clone.setTenant(source.getTenant());
        clone.setName(source.getName());
        clone.setDescription(source.getDescription());
        clone.setResourceType(source.getResourceType());
        clone.setQueryString(source.getQueryString());
        clone.setParametersJson(source.getParametersJson());
        clone.setIsPublic(source.getIsPublic());
        clone.setUsageCount(source.getUsageCount());
        clone.setLastUsedAt(source.getLastUsedAt());
        clone.setCreatedAt(source.getCreatedAt());
        clone.setUpdatedAt(source.getUpdatedAt());
        clone.setCreatedBy(source.getCreatedBy());
        clone.setUpdatedBy(source.getUpdatedBy());
        return clone;
    }

    // ==================== DTOs ====================

    @lombok.Data
    @lombok.Builder
    public static class QueryStats {
        private UUID queryId;
        private String queryName;
        private Long usageCount;
        private LocalDateTime lastUsedAt;
        private LocalDateTime createdAt;
        private String createdBy;
        private Boolean isPublic;
    }

    @lombok.Data
    @lombok.Builder
    public static class TenantQueryStats {
        private UUID tenantId;
        private Long totalQueries;
        private Long publicQueries;
        private Long privateQueries;
        private Long totalUsages;
    }
}
