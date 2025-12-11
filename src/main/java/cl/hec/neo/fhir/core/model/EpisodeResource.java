package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EpisodeResource Entity
 * Vinculación de recursos FHIR a episodios clínicos
 * Permite agrupar Encounters, Observations, MedicationRequests, etc. dentro de un episodio
 */
@Entity
@Table(name = "episode_resource", schema = "public",
    indexes = {
        @Index(name = "idx_episode_resource_episode", columnList = "episode_id"),
        @Index(name = "idx_episode_resource_type", columnList = "resource_type"),
        @Index(name = "idx_episode_resource_id", columnList = "resource_id"),
        @Index(name = "idx_episode_resource_type_id", columnList = "resource_type,resource_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpisodeResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    @ToString.Exclude
    private Episode episode;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType; // 'Encounter', 'Observation', 'MedicationRequest', etc.

    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId; // FHIR resource ID

    @Column(name = "resource_version_id", length = 50)
    private String resourceVersionId;

    @Column(name = "linked_at", nullable = false)
    @Builder.Default
    private LocalDateTime linkedAt = LocalDateTime.now();

    @Column(name = "linked_by", length = 100)
    private String linkedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
