package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.AuditEvent;
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
 * Repository para AuditEvent (Auditoría)
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Buscar eventos de auditoría por tenant (paginado)
     */
    Page<AuditEvent> findByTenantIdOrderByRecordedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * Buscar eventos por usuario
     */
    Page<AuditEvent> findByUserIdOrderByRecordedAtDesc(String userId, Pageable pageable);

    /**
     * Buscar eventos por paciente
     */
    Page<AuditEvent> findByPatientIdOrderByRecordedAtDesc(UUID patientId, Pageable pageable);

    /**
     * Buscar eventos por tipo de acción
     */
    Page<AuditEvent> findByActionOrderByRecordedAtDesc(AuditEvent.AuditAction action, Pageable pageable);

    /**
     * Buscar eventos por tipo de entidad
     */
    Page<AuditEvent> findByEntityTypeOrderByRecordedAtDesc(String entityType, Pageable pageable);

    /**
     * Buscar eventos de una entidad específica
     */
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByRecordedAtDesc(String entityType, String entityId);

    /**
     * Buscar eventos por correlation ID (para tracing)
     */
    List<AuditEvent> findByCorrelationIdOrderByRecordedAtAsc(String correlationId);

    /**
     * Buscar eventos en rango de fechas
     */
    @Query("SELECT ae FROM AuditEvent ae WHERE ae.tenant.id = :tenantId AND ae.recordedAt BETWEEN :startDate AND :endDate ORDER BY ae.recordedAt DESC")
    Page<AuditEvent> findByTenantIdAndDateRange(@Param("tenantId") UUID tenantId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, Pageable pageable);

    /**
     * Buscar eventos fallidos
     */
    @Query("SELECT ae FROM AuditEvent ae WHERE ae.tenant.id = :tenantId AND ae.outcome = 'FAILURE' ORDER BY ae.recordedAt DESC")
    Page<AuditEvent> findFailuresByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * Contar eventos por acción y tenant
     */
    @Query("SELECT COUNT(ae) FROM AuditEvent ae WHERE ae.tenant.id = :tenantId AND ae.action = :action AND ae.recordedAt BETWEEN :startDate AND :endDate")
    long countByTenantIdAndActionAndDateRange(@Param("tenantId") UUID tenantId, @Param("action") AuditEvent.AuditAction action, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
