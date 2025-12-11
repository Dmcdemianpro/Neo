package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.MasterCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para MasterCatalog
 */
@Repository
public interface MasterCatalogRepository extends JpaRepository<MasterCatalog, UUID> {

    /**
     * Buscar por sistema y código (tenant específico)
     */
    Optional<MasterCatalog> findBySystemAndCodeAndTenantId(String system, String code, UUID tenantId);

    /**
     * Buscar por sistema y código (catálogo global)
     */
    Optional<MasterCatalog> findBySystemAndCodeAndTenantIdIsNull(String system, String code);

    /**
     * Buscar por tipo de catálogo (tenant específico)
     */
    List<MasterCatalog> findByCatalogTypeAndTenantIdOrderByDisplayAsc(
        MasterCatalog.CatalogType catalogType, UUID tenantId);

    /**
     * Buscar por tipo de catálogo (global)
     */
    List<MasterCatalog> findByCatalogTypeAndTenantIdIsNullOrderByDisplayAsc(
        MasterCatalog.CatalogType catalogType);

    /**
     * Buscar por sistema (tenant específico)
     */
    List<MasterCatalog> findBySystemAndTenantIdOrderByCodeAsc(String system, UUID tenantId);

    /**
     * Buscar por sistema (global)
     */
    List<MasterCatalog> findBySystemAndTenantIdIsNullOrderByCodeAsc(String system);

    /**
     * Buscar códigos activos por tipo
     */
    List<MasterCatalog> findByCatalogTypeAndActiveTrueOrderByDisplayAsc(
        MasterCatalog.CatalogType catalogType);

    /**
     * Búsqueda por display (texto parcial)
     */
    @Query("SELECT mc FROM MasterCatalog mc WHERE " +
           "mc.catalogType = :catalogType AND " +
           "(mc.tenant.id = :tenantId OR mc.tenant IS NULL) AND " +
           "mc.active = true AND " +
           "LOWER(mc.display) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
           "ORDER BY mc.display")
    List<MasterCatalog> searchByDisplay(@Param("catalogType") MasterCatalog.CatalogType catalogType,
                                        @Param("tenantId") UUID tenantId,
                                        @Param("searchText") String searchText);

    /**
     * Buscar por código padre (para jerarquías)
     */
    List<MasterCatalog> findByParentCodeAndSystemOrderByCodeAsc(String parentCode, String system);

    /**
     * Contar códigos por tipo de catálogo
     */
    long countByCatalogTypeAndActiveTrueAndTenantId(
        MasterCatalog.CatalogType catalogType, UUID tenantId);

    /**
     * Contar códigos globales por tipo de catálogo
     */
    long countByCatalogTypeAndActiveTrueAndTenantIdIsNull(
        MasterCatalog.CatalogType catalogType);
}
