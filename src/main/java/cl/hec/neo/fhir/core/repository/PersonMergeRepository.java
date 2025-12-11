package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.PersonMerge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository para PersonMerge (Historial de fusiones MPI)
 */
@Repository
public interface PersonMergeRepository extends JpaRepository<PersonMerge, UUID> {

    /**
     * Buscar merges por persona origen
     */
    List<PersonMerge> findBySourcePersonIdOrderByMergedAtDesc(UUID sourcePersonId);

    /**
     * Buscar merges por persona destino
     */
    List<PersonMerge> findByTargetPersonIdOrderByMergedAtDesc(UUID targetPersonId);

    /**
     * Buscar merges activos por persona origen
     */
    List<PersonMerge> findBySourcePersonIdAndStatusOrderByMergedAtDesc(
        UUID sourcePersonId, PersonMerge.MergeStatus status);

    /**
     * Buscar merges activos por persona destino
     */
    List<PersonMerge> findByTargetPersonIdAndStatusOrderByMergedAtDesc(
        UUID targetPersonId, PersonMerge.MergeStatus status);

    /**
     * Buscar merges por tenant
     */
    Page<PersonMerge> findByTenantIdOrderByMergedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * Buscar merges por tipo de matching
     */
    List<PersonMerge> findByTenantIdAndMatchTypeOrderByMergedAtDesc(
        UUID tenantId, PersonMerge.MatchType matchType);

    /**
     * Buscar merges automáticos
     */
    List<PersonMerge> findByTenantIdAndIsAutomaticTrueOrderByMergedAtDesc(UUID tenantId);

    /**
     * Buscar merges por usuario
     */
    List<PersonMerge> findByMergedByOrderByMergedAtDesc(String mergedBy);

    /**
     * Buscar merges en un rango de fechas
     */
    @Query("SELECT pm FROM PersonMerge pm WHERE pm.tenant.id = :tenantId AND pm.mergedAt BETWEEN :startDate AND :endDate ORDER BY pm.mergedAt DESC")
    List<PersonMerge> findByTenantIdAndDateRange(
        @Param("tenantId") UUID tenantId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Buscar merges revertidos
     */
    List<PersonMerge> findByTenantIdAndStatusOrderByReversedAtDesc(
        UUID tenantId, PersonMerge.MergeStatus status);

    /**
     * Contar merges activos por tenant
     */
    long countByTenantIdAndStatus(UUID tenantId, PersonMerge.MergeStatus status);

    /**
     * Contar merges automáticos activos
     */
    long countByTenantIdAndIsAutomaticTrueAndStatus(
        UUID tenantId, PersonMerge.MergeStatus status);

    /**
     * Buscar por correlation ID (para tracing)
     */
    List<PersonMerge> findByCorrelationIdOrderByMergedAtAsc(String correlationId);

    /**
     * Verificar si existe un merge activo entre dos personas
     */
    @Query("SELECT COUNT(pm) > 0 FROM PersonMerge pm WHERE " +
           "(pm.sourcePerson.id = :personId1 AND pm.targetPerson.id = :personId2) OR " +
           "(pm.sourcePerson.id = :personId2 AND pm.targetPerson.id = :personId1) AND " +
           "pm.status = 'ACTIVE'")
    boolean existsActiveMergeBetween(@Param("personId1") UUID personId1,
                                     @Param("personId2") UUID personId2);
}
