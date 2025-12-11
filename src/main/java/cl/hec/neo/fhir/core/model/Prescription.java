package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prescription Entity - Recetas Electrónicas
 * Integrado con CENABAST para dispensación de medicamentos
 * Basado en FHIR MedicationRequest
 */
@Entity
@Table(name = "prescription", schema = "public",
    indexes = {
        @Index(name = "idx_prescription_tenant", columnList = "tenant_id"),
        @Index(name = "idx_prescription_ehr", columnList = "ehr_id"),
        @Index(name = "idx_prescription_patient", columnList = "patient_id"),
        @Index(name = "idx_prescription_episode", columnList = "episode_id"),
        @Index(name = "idx_prescription_prescriber", columnList = "prescriber_id"),
        @Index(name = "idx_prescription_number", columnList = "prescription_number"),
        @Index(name = "idx_prescription_status", columnList = "status"),
        @Index(name = "idx_prescription_cenabast", columnList = "cenabast_status")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    @Column(name = "fhir_medication_request_id", nullable = false, unique = true, length = 100)
    private String fhirMedicationRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ehr_id", nullable = false)
    @ToString.Exclude
    private Ehr ehr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    @ToString.Exclude
    private Episode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Person patient;

    @Column(name = "prescriber_id", nullable = false, length = 100)
    private String prescriberId; // Practitioner FHIR ID

    @Column(name = "prescription_number", unique = true, length = 100)
    private String prescriptionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PrescriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent", nullable = false, length = 20)
    private PrescriptionIntent intent;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private PrescriptionPriority priority;

    @Column(name = "medication_code", length = 50)
    private String medicationCode;

    @Column(name = "medication_display", columnDefinition = "text")
    private String medicationDisplay;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dosage_instruction", columnDefinition = "jsonb")
    private String dosageInstruction;

    @Column(name = "quantity_value", precision = 10, scale = 3)
    private BigDecimal quantityValue;

    @Column(name = "quantity_unit", length = 50)
    private String quantityUnit;

    @Column(name = "refills_allowed")
    @Builder.Default
    private Integer refillsAllowed = 0;

    @Column(name = "validity_period_start")
    private LocalDateTime validityPeriodStart;

    @Column(name = "validity_period_end")
    private LocalDateTime validityPeriodEnd;

    @Column(name = "authored_on", nullable = false)
    private LocalDateTime authoredOn;

    // CENABAST Integration
    @Column(name = "cenabast_sent_at")
    private LocalDateTime cenabastSentAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cenabast_response_json", columnDefinition = "jsonb")
    private String cenabastResponseJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "cenabast_status", length = 50)
    private CenabastStatus cenabastStatus;

    @Column(name = "cenabast_error_message", columnDefinition = "text")
    private String cenabastErrorMessage;

    @Column(name = "dispense_count")
    @Builder.Default
    private Integer dispenseCount = 0;

    @Column(name = "last_dispensed_at")
    private LocalDateTime lastDispensedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PrescriptionDispense> dispenses = new ArrayList<>();

    /**
     * Estado de la prescripción
     */
    public enum PrescriptionStatus {
        DRAFT,              // Borrador
        ACTIVE,             // Activa
        ON_HOLD,            // En espera
        COMPLETED,          // Completada
        CANCELLED,          // Cancelada
        STOPPED,            // Detenida
        ENTERED_IN_ERROR,   // Error de ingreso
        UNKNOWN             // Desconocido
    }

    /**
     * Intención de la prescripción
     */
    public enum PrescriptionIntent {
        PROPOSAL,       // Propuesta
        PLAN,           // Plan
        ORDER,          // Orden
        ORIGINAL_ORDER, // Orden original
        REFLEX_ORDER,   // Orden refleja
        FILLER_ORDER,   // Orden de relleno
        INSTANCE_ORDER, // Instancia de orden
        OPTION          // Opción
    }

    /**
     * Prioridad de la prescripción
     */
    public enum PrescriptionPriority {
        ROUTINE,    // Rutina
        URGENT,     // Urgente
        ASAP,       // Lo antes posible
        STAT        // Inmediato
    }

    /**
     * Estado de integración con CENABAST
     */
    public enum CenabastStatus {
        PENDING,    // Pendiente de envío
        SENT,       // Enviado a CENABAST
        ACCEPTED,   // Aceptado por CENABAST
        REJECTED,   // Rechazado por CENABAST
        DISPENSED,  // Dispensado
        ERROR       // Error en integración
    }
}
