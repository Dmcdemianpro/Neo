package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditEvent Entity - Auditoría Exhaustiva
 * Cumple con requisitos de compliance (Ley 19.628 Chile)
 * Registra todas las operaciones sobre datos clínicos y personales
 */
@Entity
@Table(name = "audit_event", schema = "public",
    indexes = {
        @Index(name = "idx_audit_tenant", columnList = "tenant_id"),
        @Index(name = "idx_audit_recorded", columnList = "recorded_at"),
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_audit_patient", columnList = "patient_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_correlation", columnList = "correlation_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 10)
    private AuditOutcome outcome;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType; // 'Patient', 'Observation', 'MedicationRequest', etc.

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "entity_version", length = 50)
    private String entityVersion;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_role", length = 100)
    private String userRole;

    @Column(name = "source_ip", length = 45) // IPv6 compatible
    private String sourceIp;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "request_uri", columnDefinition = "text")
    private String requestUri;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose_of_use", length = 100)
    private PurposeOfUse purposeOfUse;

    @Column(name = "patient_id")
    private UUID patientId; // If action involves a patient

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId; // For tracing across services

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "jsonb")
    private String detailsJson; // Flexible additional details

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", columnDefinition = "jsonb")
    private String beforeSnapshot; // State before change

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot", columnDefinition = "jsonb")
    private String afterSnapshot; // State after change

    /**
     * Tipo de acción auditada
     */
    public enum AuditAction {
        CREATE,     // Crear recurso
        READ,       // Leer recurso
        UPDATE,     // Actualizar recurso
        DELETE,     // Eliminar recurso
        SEARCH,     // Búsqueda
        EXECUTE,    // Ejecutar operación
        LOGIN,      // Inicio de sesión
        LOGOUT,     // Cierre de sesión
        EXPORT,     // Exportación de datos
        PRINT,      // Impresión
        MERGE       // Fusión de registros
    }

    /**
     * Resultado de la acción
     */
    public enum AuditOutcome {
        SUCCESS,    // Exitoso
        FAILURE     // Fallido
    }

    /**
     * Propósito de uso de los datos (HIPAA compliance)
     */
    public enum PurposeOfUse {
        TREATMENT,      // Tratamiento médico
        PAYMENT,        // Facturación/pago
        OPERATIONS,     // Operaciones administrativas
        RESEARCH,       // Investigación
        PUBLIC_HEALTH,  // Salud pública
        JUDICIAL,       // Orden judicial
        LAW_ENFORCEMENT,// Aplicación de ley
        DISCLOSURE      // Divulgación autorizada
    }
}
