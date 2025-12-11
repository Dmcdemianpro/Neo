package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Episode Entity - Episodios Clínicos
 * Representa un episodio de atención (emergencia, hospitalización, ambulatorio, crónico)
 * Agrupa recursos FHIR relacionados a un período de atención
 */
@Entity
@Table(name = "episode", schema = "public",
    indexes = {
        @Index(name = "idx_episode_ehr", columnList = "ehr_id"),
        @Index(name = "idx_episode_number", columnList = "episode_number")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ehr_id", nullable = false)
    @ToString.Exclude
    private Ehr ehr;

    @Column(name = "episode_number", nullable = false, length = 50)
    private String episodeNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private EpisodeType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EpisodeStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_history", columnDefinition = "jsonb")
    private String statusHistory;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @Column(name = "diagnosis_primary", length = 20)
    private String diagnosisPrimary; // ICD-10 code

    @Column(name = "diagnosis_primary_text", columnDefinition = "text")
    private String diagnosisPrimaryText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diagnosis_secondary", columnDefinition = "jsonb")
    private String diagnosisSecondary; // JSON array of {code, text}

    @Column(name = "care_manager_id")
    private UUID careManagerId; // Reference to Practitioner

    @Column(name = "managing_organization_id")
    private UUID managingOrganizationId; // Reference to Organization

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "reason_text", columnDefinition = "text")
    private String reasonText;

    @Column(name = "referral_source")
    private String referralSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private EpisodePriority priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata; // Flexible additional data

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Tipo de episodio clínico
     */
    public enum EpisodeType {
        EMERGENCY,      // Urgencia/Emergencia
        INPATIENT,      // Hospitalización
        OUTPATIENT,     // Ambulatorio
        CHRONIC,        // Condición crónica
        HOME_CARE,      // Atención domiciliaria
        PREVENTIVE      // Preventivo
    }

    /**
     * Estado del episodio
     */
    public enum EpisodeStatus {
        PLANNED,        // Planificado
        WAITLIST,       // En lista de espera
        ACTIVE,         // Activo
        ONHOLD,         // En pausa
        FINISHED,       // Finalizado
        CANCELLED,      // Cancelado
        ENTERED_IN_ERROR // Error de ingreso
    }

    /**
     * Prioridad del episodio
     */
    public enum EpisodePriority {
        ROUTINE,        // Rutina
        URGENT,         // Urgente
        ASAP,           // Lo antes posible
        STAT            // Inmediato
    }
}
