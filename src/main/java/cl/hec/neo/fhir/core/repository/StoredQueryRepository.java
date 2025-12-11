package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.StoredQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para StoredQuery
 */
@Repository
public interface StoredQueryRepository extends JpaRepository<StoredQuery, UUID> {

    /**
     * Buscar queries por tenant
     */
    List<StoredQuery> findByTenantIdOrderByNameAsc(UUID tenantId);

    /**
     * Buscar queries públicas de un tenant
     */
    List<StoredQuery> findByTenantIdAndIsPublicTrueOrderByNameAsc(UUID tenantId);

    /**
     * Buscar queries por nombre (like)
     */
    @Query("SELECT sq FROM StoredQuery sq WHERE sq.tenant.id = :tenantId AND LOWER(sq.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY sq.name")
    List<StoredQuery> findByTenantIdAndNameContaining(@Param("tenantId") UUID tenantId, @Param("name") String name);

    /**
     * Buscar queries por tipo de recurso
     */
    List<StoredQuery> findByTenantIdAndResourceTypeOrderByNameAsc(UUID tenantId, String resourceType);

    /**
     * Buscar queries por creador
     */
    List<StoredQuery> findByTenantIdAndCreatedByOrderByCreatedAtDesc(UUID tenantId, String createdBy);

    /**
     * Buscar query por nombre exacto
     */
    Optional<StoredQuery> findByTenantIdAndName(UUID tenantId, String name);

    /**
     * Incrementar contador de uso
     */
    @Modifying
    @Query("UPDATE StoredQuery sq SET sq.usageCount = sq.usageCount + 1, sq.lastUsedAt = :now WHERE sq.id = :id")
    void incrementUsageCount(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /**
     * Obtener queries más usadas
     */
    @Query("SELECT sq FROM StoredQuery sq WHERE sq.tenant.id = :tenantId ORDER BY sq.usageCount DESC")
    List<StoredQuery> findMostUsedByTenantId(@Param("tenantId") UUID tenantId,
                                              org.springframework.data.domain.Pageable pageable);

    /**
     * Buscar queries por tenant con paginación
     */
    org.springframework.data.domain.Page<StoredQuery> findByTenantIdOrderByCreatedAtDesc(
        UUID tenantId, org.springframework.data.domain.Pageable pageable);

    /**
     * Obtener queries más usadas (con nombre consistente)
     */
    @Query("SELECT sq FROM StoredQuery sq WHERE sq.tenant.id = :tenantId ORDER BY sq.usageCount DESC")
    List<StoredQuery> findTopByTenantIdOrderByUsageCountDesc(@Param("tenantId") UUID tenantId,
                                                               org.springframework.data.domain.Pageable pageable);

    /**
     * Obtener queries usadas recientemente
     */
    @Query("SELECT sq FROM StoredQuery sq WHERE sq.tenant.id = :tenantId AND sq.lastUsedAt IS NOT NULL ORDER BY sq.lastUsedAt DESC")
    List<StoredQuery> findTopByTenantIdOrderByLastUsedAtDesc(@Param("tenantId") UUID tenantId,
                                                               org.springframework.data.domain.Pageable pageable);

    /**
     * Buscar queries por nombre (alias para compatibilidad)
     */
    @Query("SELECT sq FROM StoredQuery sq WHERE sq.tenant.id = :tenantId AND LOWER(sq.name) LIKE LOWER(CONCAT('%', :searchText, '%')) ORDER BY sq.name")
    List<StoredQuery> searchByName(@Param("tenantId") UUID tenantId, @Param("searchText") String searchText);

    /**
     * Contar queries por tenant
     */
    long countByTenantId(UUID tenantId);

    /**
     * Contar queries públicas por tenant
     */
    long countByTenantIdAndIsPublicTrue(UUID tenantId);

    /**
     * Sumar total de usos por tenant
     */
    @Query("SELECT COALESCE(SUM(sq.usageCount), 0) FROM StoredQuery sq WHERE sq.tenant.id = :tenantId")
    long sumUsageCountByTenantId(@Param("tenantId") UUID tenantId);
}
