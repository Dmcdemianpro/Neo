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
 * PersonMerge Entity - Historial de fusiones de pacientes en el MPI
 * Registra cada operación de merge para auditoría y posible reversión
 * Crítico para compliance y trazabilidad en Master Patient Index
 */
@Entity
@Table(name = "person_merge", schema = "public",
    indexes = {
        @Index(name = "idx_person_merge_source", columnList = "source_person_id"),
        @Index(name = "idx_person_merge_target", columnList = "target_person_id"),
        @Index(name = "idx_person_merge_tenant", columnList = "tenant_id"),
        @Index(name = "idx_person_merge_status", columnList = "status"),
        @Index(name = "idx_person_merge_date", columnList = "merged_at")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonMerge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Persona origen (la que fue fusionada/merged)
     * Esta persona quedó marcada como inactiva y apunta a target
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_person_id", nullable = false)
    private Person sourcePerson;

    /**
     * Persona destino (la que permanece activa)
     * Esta persona absorbe los identificadores y datos del source
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_person_id", nullable = false)
    private Person targetPerson;

    /**
     * Score de matching que justificó el merge
     * Valor entre 0.0 y 1.0
     */
    @Column(name = "match_score", precision = 5, scale = 4)
    private BigDecimal matchScore;

    /**
     * Tipo de matching que llevó al merge
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", length = 20)
    private MatchType matchType;

    /**
     * Estado del merge
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MergeStatus status = MergeStatus.ACTIVE;

    /**
     * Fecha y hora del merge
     */
    @CreationTimestamp
    @Column(name = "merged_at", nullable = false, updatable = false)
    private LocalDateTime mergedAt;

    /**
     * Usuario que ejecutó el merge
     */
    @Column(name = "merged_by", nullable = false, length = 100)
    private String mergedBy;

    /**
     * Rol del usuario que ejecutó el merge
     */
    @Column(name = "merged_by_role", length = 50)
    private String mergedByRole;

    /**
     * Motivo del merge
     */
    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    /**
     * Si fue merge manual o automático
     */
    @Column(name = "is_automatic", nullable = false)
    @Builder.Default
    private Boolean isAutomatic = false;

    /**
     * Snapshot del estado del source person antes del merge (JSON completo)
     * Necesario para reversión
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_snapshot_json", columnDefinition = "jsonb")
    private String sourceSnapshotJson;

    /**
     * Snapshot del estado del target person antes del merge (JSON completo)
     * Necesario para reversión
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_snapshot_json", columnDefinition = "jsonb")
    private String targetSnapshotJson;

    /**
     * Detalles adicionales del merge (campos que se consolidaron, conflictos resueltos, etc.)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "merge_details_json", columnDefinition = "jsonb")
    private String mergeDetailsJson;

    /**
     * Fecha y hora de reversión (si aplica)
     */
    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    /**
     * Usuario que revirtió el merge (si aplica)
     */
    @Column(name = "reversed_by", length = 100)
    private String reversedBy;

    /**
     * Motivo de la reversión (si aplica)
     */
    @Column(name = "reversal_reason", columnDefinition = "text")
    private String reversalReason;

    /**
     * Correlation ID para tracing distribuido
     */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /**
     * Tipos de matching
     */
    public enum MatchType {
        EXACT,      // Matching exacto (>= 95%)
        PROBABLE,   // Matching probable (>= 80%)
        POSSIBLE,   // Matching posible (>= 60%)
        MANUAL      // Decisión manual del usuario
    }

    /**
     * Estados del merge
     */
    public enum MergeStatus {
        ACTIVE,     // Merge activo y vigente
        REVERSED,   // Merge revertido
        SUPERSEDED  // Supersedido por otro merge
    }

    /**
     * Helper para verificar si el merge puede ser revertido
     */
    public boolean canBeReversed() {
        return status == MergeStatus.ACTIVE && reversedAt == null;
    }

    /**
     * Helper para marcar el merge como revertido
     */
    public void reverse(String reversedBy, String reason) {
        if (!canBeReversed()) {
            throw new IllegalStateException("This merge cannot be reversed");
        }
        this.status = MergeStatus.REVERSED;
        this.reversedAt = LocalDateTime.now();
        this.reversedBy = reversedBy;
        this.reversalReason = reason;
    }
}
