package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PrescriptionDispense Entity - Dispensaciones de Recetas
 * Registra cada dispensación de una prescripción
 * Basado en FHIR MedicationDispense
 */
@Entity
@Table(name = "prescription_dispense", schema = "public",
    indexes = {
        @Index(name = "idx_dispense_prescription", columnList = "prescription_id"),
        @Index(name = "idx_dispense_status", columnList = "status"),
        @Index(name = "idx_dispense_handed_over", columnList = "when_handed_over")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionDispense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    @ToString.Exclude
    private Prescription prescription;

    @Column(name = "fhir_medication_dispense_id", nullable = false, unique = true, length = 100)
    private String fhirMedicationDispenseId;

    @Column(name = "dispense_number", length = 100)
    private String dispenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DispenseStatus status;

    @Column(name = "quantity_dispensed", precision = 10, scale = 3)
    private BigDecimal quantityDispensed;

    @Column(name = "quantity_unit", length = 50)
    private String quantityUnit;

    @Column(name = "when_prepared")
    private LocalDateTime whenPrepared;

    @Column(name = "when_handed_over")
    private LocalDateTime whenHandedOver;

    @Column(name = "dispensed_by", length = 100)
    private String dispensedBy; // Practitioner ID

    @Column(name = "dispensed_location")
    private String dispensedLocation;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Estado de la dispensación
     */
    public enum DispenseStatus {
        PREPARATION,        // En preparación
        IN_PROGRESS,        // En proceso
        CANCELLED,          // Cancelada
        ON_HOLD,            // En espera
        COMPLETED,          // Completada
        ENTERED_IN_ERROR,   // Error de ingreso
        STOPPED,            // Detenida
        DECLINED,           // Declinada
        UNKNOWN             // Desconocido
    }
}
