package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ClinicalDocument Entity - Documentos Clínicos con Versionado
 * Representa documentos clínicos estructurados (epicrisis, informes operatorios, consentimientos, etc.)
 * Basado en FHIR Composition con versionado completo
 */
@Entity
@Table(name = "clinical_document", schema = "public",
    indexes = {
        @Index(name = "idx_clinical_doc_ehr", columnList = "ehr_id"),
        @Index(name = "idx_clinical_doc_type", columnList = "document_type"),
        @Index(name = "idx_clinical_doc_status", columnList = "status"),
        @Index(name = "idx_clinical_doc_author", columnList = "author_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ehr_id", nullable = false)
    @ToString.Exclude
    private Ehr ehr;

    @Column(name = "fhir_composition_id", nullable = false, unique = true, length = 100)
    private String fhirCompositionId; // Reference to FHIR Composition

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private Integer currentVersion = 1;

    @Column(name = "title", length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidentiality", length = 20)
    private Confidentiality confidentiality;

    @Column(name = "author_id", length = 100)
    private String authorId; // Practitioner ID

    @Column(name = "encounter_id", length = 100)
    private String encounterId; // Related Encounter

    @Column(name = "date_created", nullable = false)
    private LocalDateTime dateCreated;

    @Column(name = "date_attested")
    private LocalDateTime dateAttested;

    @Column(name = "custodian")
    private String custodian;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClinicalDocumentVersion> versions = new ArrayList<>();

    /**
     * Tipo de documento clínico
     */
    public enum DocumentType {
        DISCHARGE,          // Epicrisis / Alta
        OPERATIVE,          // Informe operatorio
        PROGRESS,           // Nota de evolución
        CONSULTATION,       // Informe de consulta
        DIAGNOSTIC,         // Informe diagnóstico
        CONSENT,            // Consentimiento informado
        REFERRAL,           // Derivación
        CERTIFICATE,        // Certificado médico
        PRESCRIPTION,       // Receta
        LAB_REPORT,         // Informe de laboratorio
        IMAGING_REPORT,     // Informe de imagen
        PROCEDURE_NOTE,     // Nota de procedimiento
        ADMISSION,          // Nota de ingreso
        TRANSFER,           // Nota de traslado
        OTHER               // Otro
    }

    /**
     * Estado del documento
     */
    public enum DocumentStatus {
        PRELIMINARY,        // Preliminar (borrador)
        FINAL,              // Final
        AMENDED,            // Enmendado
        CORRECTED,          // Corregido
        APPENDED,           // Añadido
        CANCELLED,          // Cancelado
        ENTERED_IN_ERROR    // Error de ingreso
    }

    /**
     * Nivel de confidencialidad
     */
    public enum Confidentiality {
        N,  // Normal - acceso normal
        R,  // Restricted - acceso restringido
        V   // Very restricted - muy restringido
    }
}
