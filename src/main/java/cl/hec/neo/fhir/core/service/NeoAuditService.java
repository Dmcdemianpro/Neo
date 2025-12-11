package cl.hec.neo.fhir.core.service;

import cl.hec.neo.fhir.core.model.AuditEvent;
import cl.hec.neo.fhir.core.model.Tenant;
import cl.hec.neo.fhir.core.repository.AuditEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * NeoAuditService - Comprehensive Audit Service
 *
 * Funcionalidades:
 * - Auditoría exhaustiva de operaciones CRUD
 * - Compliance con Ley 19.628 (Chile)
 * - Tracking de accesos a datos de pacientes
 * - Generación de reportes de auditoría
 * - Trazabilidad completa con correlation IDs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NeoAuditService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Registrar evento de auditoría
     */
    @Transactional
    public AuditEvent audit(AuditEventBuilder builder) {
        try {
            AuditEvent event = builder.build();
            AuditEvent saved = auditEventRepository.save(event);
            log.debug("Audit event recorded: {} {} on {} by {}",
                event.getAction(), event.getEntityType(), event.getEntityId(), event.getUserId());
            return saved;
        } catch (Exception e) {
            log.error("Error recording audit event", e);
            // No lanzar excepción - la auditoría no debe bloquear operaciones
            return null;
        }
    }

    /**
     * Auditar creación de recurso
     */
    @Transactional
    public void auditCreate(Tenant tenant, String entityType, String entityId, String userId, Object afterState) {
        audit(AuditEventBuilder.create()
            .tenant(tenant)
            .action(AuditEvent.AuditAction.CREATE)
            .outcome(AuditEvent.AuditOutcome.SUCCESS)
            .entityType(entityType)
            .entityId(entityId)
            .userId(userId)
            .afterSnapshot(toJson(afterState))
        );
    }

    /**
     * Auditar lectura de recurso
     */
    @Transactional
    public void auditRead(Tenant tenant, String entityType, String entityId, String userId, UUID patientId) {
        audit(AuditEventBuilder.create()
            .tenant(tenant)
            .action(AuditEvent.AuditAction.READ)
            .outcome(AuditEvent.AuditOutcome.SUCCESS)
            .entityType(entityType)
            .entityId(entityId)
            .userId(userId)
            .patientId(patientId)
        );
    }

    /**
     * Auditar actualización de recurso
     */
    @Transactional
    public void auditUpdate(Tenant tenant, String entityType, String entityId, String userId, Object beforeState, Object afterState) {
        audit(AuditEventBuilder.create()
            .tenant(tenant)
            .action(AuditEvent.AuditAction.UPDATE)
            .outcome(AuditEvent.AuditOutcome.SUCCESS)
            .entityType(entityType)
            .entityId(entityId)
            .userId(userId)
            .beforeSnapshot(toJson(beforeState))
            .afterSnapshot(toJson(afterState))
        );
    }

    /**
     * Auditar eliminación de recurso
     */
    @Transactional
    public void auditDelete(Tenant tenant, String entityType, String entityId, String userId, Object beforeState) {
        audit(AuditEventBuilder.create()
            .tenant(tenant)
            .action(AuditEvent.AuditAction.DELETE)
            .outcome(AuditEvent.AuditOutcome.SUCCESS)
            .entityType(entityType)
            .entityId(entityId)
            .userId(userId)
            .beforeSnapshot(toJson(beforeState))
        );
    }

    /**
     * Auditar búsqueda
     */
    @Transactional
    public void auditSearch(Tenant tenant, String entityType, String userId, String searchCriteria, int resultCount) {
        audit(AuditEventBuilder.create()
            .tenant(tenant)
            .action(AuditEvent.AuditAction.SEARCH)
            .outcome(AuditEvent.AuditOutcome.SUCCESS)
            .entityType(entityType)
            .userId(userId)
            .detailsJson(String.format("{\"criteria\":\"%s\",\"resultCount\":%d}", searchCriteria, resultCount))
        );
    }

    /**
     * Auditar operación fallida
     */
    @Transactional
    public void auditFailure(Tenant tenant, AuditEvent.AuditAction action, String entityType, String entityId, String userId, String errorMessage) {
        audit(AuditEventBuilder.create()
            .tenant(tenant)
            .action(action)
            .outcome(AuditEvent.AuditOutcome.FAILURE)
            .entityType(entityType)
            .entityId(entityId)
            .userId(userId)
            .detailsJson(String.format("{\"error\":\"%s\"}", errorMessage))
        );
    }

    /**
     * Auditar inicio de sesión
     */
    @Transactional
    public void auditLogin(Tenant tenant, String userId, String userName, String sourceIp, String userAgent) {
        audit(AuditEventBuilder.create()
            .tenant(tenant)
            .action(AuditEvent.AuditAction.LOGIN)
            .outcome(AuditEvent.AuditOutcome.SUCCESS)
            .entityType("Session")
            .userId(userId)
            .userName(userName)
            .sourceIp(sourceIp)
            .userAgent(userAgent)
        );
    }

    /**
     * Auditar cierre de sesión
     */
    @Transactional
    public void auditLogout(Tenant tenant, String userId, String sessionId) {
        audit(AuditEventBuilder.create()
            .tenant(tenant)
            .action(AuditEvent.AuditAction.LOGOUT)
            .outcome(AuditEvent.AuditOutcome.SUCCESS)
            .entityType("Session")
            .userId(userId)
            .sessionId(sessionId)
        );
    }

    // ========== Consultas de Auditoría ==========

    /**
     * Buscar eventos de auditoría por tenant
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> findByTenant(UUID tenantId, Pageable pageable) {
        return auditEventRepository.findByTenantIdOrderByRecordedAtDesc(tenantId, pageable);
    }

    /**
     * Buscar eventos de auditoría por usuario
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> findByUser(String userId, Pageable pageable) {
        return auditEventRepository.findByUserIdOrderByRecordedAtDesc(userId, pageable);
    }

    /**
     * Buscar eventos de auditoría de un paciente
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> findByPatient(UUID patientId, Pageable pageable) {
        return auditEventRepository.findByPatientIdOrderByRecordedAtDesc(patientId, pageable);
    }

    /**
     * Buscar historial completo de una entidad específica
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> findEntityHistory(String entityType, String entityId) {
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByRecordedAtDesc(entityType, entityId);
    }

    /**
     * Buscar eventos por correlation ID (para tracing distribuido)
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> findByCorrelationId(String correlationId) {
        return auditEventRepository.findByCorrelationIdOrderByRecordedAtAsc(correlationId);
    }

    /**
     * Buscar eventos fallidos
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> findFailures(UUID tenantId, Pageable pageable) {
        return auditEventRepository.findFailuresByTenantId(tenantId, pageable);
    }

    /**
     * Buscar eventos en rango de fechas
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> findByDateRange(UUID tenantId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return auditEventRepository.findByTenantIdAndDateRange(tenantId, startDate, endDate, pageable);
    }

    /**
     * Contar eventos por acción
     */
    @Transactional(readOnly = true)
    public long countByAction(UUID tenantId, AuditEvent.AuditAction action, LocalDateTime startDate, LocalDateTime endDate) {
        return auditEventRepository.countByTenantIdAndActionAndDateRange(tenantId, action, startDate, endDate);
    }

    // ========== Catalog-specific Audit Methods ==========

    /**
     * Auditar creación de código en catálogo
     */
    @Transactional
    public void logCatalogCreated(cl.hec.neo.fhir.core.model.MasterCatalog catalog, String createdBy) {
        auditCreate(catalog.getTenant(), "MasterCatalog", catalog.getId().toString(), createdBy, catalog);
    }

    /**
     * Auditar actualización de código en catálogo
     */
    @Transactional
    public void logCatalogUpdated(cl.hec.neo.fhir.core.model.MasterCatalog catalog, String updatedBy) {
        auditUpdate(catalog.getTenant(), "MasterCatalog", catalog.getId().toString(), updatedBy, null, catalog);
    }

    /**
     * Auditar desactivación de código en catálogo
     */
    @Transactional
    public void logCatalogDeactivated(cl.hec.neo.fhir.core.model.MasterCatalog catalog, String deactivatedBy) {
        audit(AuditEventBuilder.create()
            .tenant(catalog.getTenant())
            .action(AuditEvent.AuditAction.UPDATE)
            .outcome(AuditEvent.AuditOutcome.SUCCESS)
            .entityType("MasterCatalog")
            .entityId(catalog.getId().toString())
            .userId(deactivatedBy)
            .detailsJson("{\"action\":\"deactivated\"}")
        );
    }

    // ========== Helper Methods ==========

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    // ========== Builder para AuditEvent ==========

    public static class AuditEventBuilder {
        private final AuditEvent event = new AuditEvent();

        public static AuditEventBuilder create() {
            return new AuditEventBuilder();
        }

        public AuditEventBuilder tenant(Tenant tenant) {
            event.setTenant(tenant);
            return this;
        }

        public AuditEventBuilder action(AuditEvent.AuditAction action) {
            event.setAction(action);
            return this;
        }

        public AuditEventBuilder outcome(AuditEvent.AuditOutcome outcome) {
            event.setOutcome(outcome);
            return this;
        }

        public AuditEventBuilder entityType(String entityType) {
            event.setEntityType(entityType);
            return this;
        }

        public AuditEventBuilder entityId(String entityId) {
            event.setEntityId(entityId);
            return this;
        }

        public AuditEventBuilder entityVersion(String entityVersion) {
            event.setEntityVersion(entityVersion);
            return this;
        }

        public AuditEventBuilder userId(String userId) {
            event.setUserId(userId);
            return this;
        }

        public AuditEventBuilder userName(String userName) {
            event.setUserName(userName);
            return this;
        }

        public AuditEventBuilder userRole(String userRole) {
            event.setUserRole(userRole);
            return this;
        }

        public AuditEventBuilder sourceIp(String sourceIp) {
            event.setSourceIp(sourceIp);
            return this;
        }

        public AuditEventBuilder userAgent(String userAgent) {
            event.setUserAgent(userAgent);
            return this;
        }

        public AuditEventBuilder requestUri(String requestUri) {
            event.setRequestUri(requestUri);
            return this;
        }

        public AuditEventBuilder httpMethod(String httpMethod) {
            event.setHttpMethod(httpMethod);
            return this;
        }

        public AuditEventBuilder httpStatusCode(Integer httpStatusCode) {
            event.setHttpStatusCode(httpStatusCode);
            return this;
        }

        public AuditEventBuilder purposeOfUse(AuditEvent.PurposeOfUse purposeOfUse) {
            event.setPurposeOfUse(purposeOfUse);
            return this;
        }

        public AuditEventBuilder patientId(UUID patientId) {
            event.setPatientId(patientId);
            return this;
        }

        public AuditEventBuilder sessionId(String sessionId) {
            event.setSessionId(sessionId);
            return this;
        }

        public AuditEventBuilder correlationId(String correlationId) {
            event.setCorrelationId(correlationId);
            return this;
        }

        public AuditEventBuilder detailsJson(String detailsJson) {
            event.setDetailsJson(detailsJson);
            return this;
        }

        public AuditEventBuilder beforeSnapshot(String beforeSnapshot) {
            event.setBeforeSnapshot(beforeSnapshot);
            return this;
        }

        public AuditEventBuilder afterSnapshot(String afterSnapshot) {
            event.setAfterSnapshot(afterSnapshot);
            return this;
        }

        public AuditEvent build() {
            // Validaciones
            if (event.getTenant() == null) {
                throw new IllegalStateException("Tenant is required");
            }
            if (event.getAction() == null) {
                throw new IllegalStateException("Action is required");
            }
            if (event.getOutcome() == null) {
                throw new IllegalStateException("Outcome is required");
            }
            if (event.getEntityType() == null) {
                throw new IllegalStateException("EntityType is required");
            }
            if (event.getUserId() == null) {
                throw new IllegalStateException("UserId is required");
            }

            return event;
        }
    }
}
