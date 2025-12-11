package cl.hec.neo.fhir.core.model;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ClinicalDocumentVersion Entity
 * Versionado completo de documentos clínicos
 * Almacena snapshots completos de cada versión con hash para integridad
 */
@Entity
@Table(name = "clinical_document_version", schema = "public",
    indexes = {
        @Index(name = "idx_clinical_doc_ver_document", columnList = "document_id"),
        @Index(name = "idx_clinical_doc_ver_number", columnList = "version_number"),
        @Index(name = "idx_clinical_doc_ver_date", columnList = "versioned_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_document_version", columnNames = {"document_id", "version_number"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicalDocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @ToString.Exclude
    private ClinicalDocument document;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String snapshotJson; // Full FHIR Composition JSON

    @Column(name = "snapshot_hash", length = 64)
    private String snapshotHash; // SHA-256 hash for integrity

    @Column(name = "change_reason", columnDefinition = "text")
    private String changeReason;

    @Column(name = "changed_sections", columnDefinition = "text[]")
    @Type(StringArrayType.class)
    private String[] changedSections; // Which sections changed

    @Column(name = "versioned_at", nullable = false)
    @Builder.Default
    private LocalDateTime versionedAt = LocalDateTime.now();

    @Column(name = "versioned_by", nullable = false, length = 100)
    private String versionedBy;
}
