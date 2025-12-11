package cl.hec.neo.fhir.core.service;

import cl.hec.neo.fhir.core.model.ClinicalDocument;
import cl.hec.neo.fhir.core.model.ClinicalDocumentVersion;
import cl.hec.neo.fhir.core.model.Ehr;
import cl.hec.neo.fhir.core.repository.ClinicalDocumentRepository;
import cl.hec.neo.fhir.core.repository.ClinicalDocumentVersionRepository;
import cl.hec.neo.fhir.core.repository.EhrRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para gestión de documentos clínicos con versionado
 *
 * Responsabilidades:
 * - CRUD de documentos clínicos (epicrisis, informes, consentimientos, etc.)
 * - Versionado completo con snapshots FHIR
 * - Integridad de datos con hashing SHA-256
 * - Gestión de estados del documento (preliminary, final, amended, etc.)
 * - Atestación de documentos
 * - Control de confidencialidad
 * - Auditoría completa de cambios
 */
@Service
@Transactional
public class NeoClinicalDocumentService {

    private static final Logger log = LoggerFactory.getLogger(NeoClinicalDocumentService.class);

    private final ClinicalDocumentRepository documentRepository;
    private final ClinicalDocumentVersionRepository versionRepository;
    private final EhrRepository ehrRepository;
    private final NeoAuditService auditService;

    public NeoClinicalDocumentService(
            ClinicalDocumentRepository documentRepository,
            ClinicalDocumentVersionRepository versionRepository,
            EhrRepository ehrRepository,
            NeoAuditService auditService) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.ehrRepository = ehrRepository;
        this.auditService = auditService;
    }

    /**
     * Crear un nuevo documento clínico con su primera versión
     */
    public ClinicalDocument createDocument(ClinicalDocument document, String fhirJson, String createdBy) {
        log.info("Creating clinical document: type={}, ehrId={}, compositionId={}",
            document.getDocumentType(), document.getEhr().getId(), document.getFhirCompositionId());

        // Validar que existe el EHR
        Ehr ehr = ehrRepository.findById(document.getEhr().getId())
            .orElseThrow(() -> new IllegalArgumentException("EHR not found: " + document.getEhr().getId()));

        document.setEhr(ehr);

        // Validar que no exista otro documento con el mismo FHIR ID
        Optional<ClinicalDocument> existing = documentRepository.findByFhirCompositionId(document.getFhirCompositionId());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Document with FHIR ID already exists: " + document.getFhirCompositionId());
        }

        // Timestamps
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        if (document.getDateCreated() == null) {
            document.setDateCreated(LocalDateTime.now());
        }

        // Estado inicial si no está definido
        if (document.getStatus() == null) {
            document.setStatus(ClinicalDocument.DocumentStatus.PRELIMINARY);
        }

        // Versión inicial
        document.setCurrentVersion(1);

        ClinicalDocument saved = documentRepository.save(document);

        // Crear primera versión
        createVersion(saved, fhirJson, "Initial version", null, createdBy);

        // Auditoría
        auditService.auditCreate(
            ehr.getPerson().getTenant(),
            "ClinicalDocument",
            saved.getId().toString(),
            createdBy,
            saved
        );

        log.info("Clinical document created successfully: id={}, compositionId={}",
            saved.getId(), saved.getFhirCompositionId());
        return saved;
    }

    /**
     * Actualizar un documento existente creando una nueva versión
     */
    public ClinicalDocument updateDocument(UUID id, String newFhirJson, String changeReason,
                                          String[] changedSections, String updatedBy) {
        log.info("Updating clinical document: id={}", id);

        ClinicalDocument document = documentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Clinical document not found: " + id));

        // Backup del estado anterior para auditoría
        ClinicalDocument beforeState = clone(document);

        // Incrementar versión
        int newVersion = document.getCurrentVersion() + 1;
        document.setCurrentVersion(newVersion);
        document.setUpdatedAt(LocalDateTime.now());

        // Crear nueva versión con snapshot
        ClinicalDocumentVersion version = createVersion(document, newFhirJson, changeReason,
            changedSections, updatedBy);

        ClinicalDocument saved = documentRepository.save(document);

        // Auditoría
        auditService.auditUpdate(
            document.getEhr().getPerson().getTenant(),
            "ClinicalDocument",
            saved.getId().toString(),
            updatedBy,
            beforeState,
            saved
        );

        log.info("Clinical document updated successfully: id={}, newVersion={}",
            saved.getId(), newVersion);
        return saved;
    }

    /**
     * Cambiar el estado de un documento
     */
    public ClinicalDocument changeStatus(UUID id, ClinicalDocument.DocumentStatus newStatus,
                                        String reason, String changedBy) {
        log.info("Changing document status: id={}, newStatus={}", id, newStatus);

        ClinicalDocument document = documentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Clinical document not found: " + id));

        ClinicalDocument.DocumentStatus oldStatus = document.getStatus();
        document.setStatus(newStatus);
        document.setUpdatedAt(LocalDateTime.now());

        ClinicalDocument saved = documentRepository.save(document);

        // Auditoría del cambio de estado
        auditService.audit(
            NeoAuditService.AuditEventBuilder.create()
                .tenant(document.getEhr().getPerson().getTenant())
                .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.UPDATE)
                .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
                .entityType("ClinicalDocument")
                .entityId(id.toString())
                .userId(changedBy)
                .detailsJson(String.format("{\"statusChange\":{\"from\":\"%s\",\"to\":\"%s\",\"reason\":\"%s\"}}",
                    oldStatus, newStatus, reason != null ? reason : ""))
        );

        log.info("Document status changed: id={}, {} -> {}", id, oldStatus, newStatus);
        return saved;
    }

    /**
     * Atestar un documento (marcar como atestado/firmado)
     */
    public ClinicalDocument attestDocument(UUID id, String attestedBy) {
        log.info("Attesting document: id={}, attestedBy={}", id, attestedBy);

        ClinicalDocument document = documentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Clinical document not found: " + id));

        document.setDateAttested(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());

        // Cambiar a estado FINAL si estaba en PRELIMINARY
        if (document.getStatus() == ClinicalDocument.DocumentStatus.PRELIMINARY) {
            document.setStatus(ClinicalDocument.DocumentStatus.FINAL);
        }

        ClinicalDocument saved = documentRepository.save(document);

        // Auditoría
        auditService.audit(
            NeoAuditService.AuditEventBuilder.create()
                .tenant(document.getEhr().getPerson().getTenant())
                .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.UPDATE)
                .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
                .entityType("ClinicalDocument")
                .entityId(id.toString())
                .userId(attestedBy)
                .detailsJson("{\"action\":\"attested\"}")
        );

        log.info("Document attested successfully: id={}", id);
        return saved;
    }

    /**
     * Cancelar un documento
     */
    public ClinicalDocument cancelDocument(UUID id, String reason, String cancelledBy) {
        log.info("Cancelling document: id={}, reason={}", id, reason);

        ClinicalDocument document = documentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Clinical document not found: " + id));

        document.setStatus(ClinicalDocument.DocumentStatus.CANCELLED);
        document.setUpdatedAt(LocalDateTime.now());

        ClinicalDocument saved = documentRepository.save(document);

        // Auditoría con razón de cancelación
        auditService.audit(
            NeoAuditService.AuditEventBuilder.create()
                .tenant(document.getEhr().getPerson().getTenant())
                .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.UPDATE)
                .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
                .entityType("ClinicalDocument")
                .entityId(id.toString())
                .userId(cancelledBy)
                .detailsJson(String.format("{\"action\":\"cancelled\",\"reason\":\"%s\"}", reason))
        );

        log.info("Document cancelled successfully: id={}", id);
        return saved;
    }

    /**
     * Buscar documento por ID
     */
    @Transactional(readOnly = true)
    public Optional<ClinicalDocument> findById(UUID id) {
        log.debug("Finding document by id: {}", id);
        return documentRepository.findById(id);
    }

    /**
     * Buscar documento por FHIR Composition ID
     */
    @Transactional(readOnly = true)
    public Optional<ClinicalDocument> findByFhirCompositionId(String fhirCompositionId) {
        log.debug("Finding document by FHIR ID: {}", fhirCompositionId);
        return documentRepository.findByFhirCompositionId(fhirCompositionId);
    }

    /**
     * Listar todos los documentos de un paciente (por EHR)
     */
    @Transactional(readOnly = true)
    public List<ClinicalDocument> findByEhr(UUID ehrId) {
        log.debug("Finding documents by EHR: {}", ehrId);
        return documentRepository.findByEhrIdOrderByDateCreatedDesc(ehrId);
    }

    /**
     * Listar documentos por tipo
     */
    @Transactional(readOnly = true)
    public List<ClinicalDocument> findByEhrAndType(UUID ehrId, ClinicalDocument.DocumentType type) {
        log.debug("Finding documents by EHR and type: ehrId={}, type={}", ehrId, type);
        return documentRepository.findByEhrIdAndDocumentTypeOrderByDateCreatedDesc(ehrId, type);
    }

    /**
     * Listar documentos finales de un paciente
     */
    @Transactional(readOnly = true)
    public List<ClinicalDocument> findFinalDocuments(UUID ehrId) {
        log.debug("Finding final documents by EHR: {}", ehrId);
        return documentRepository.findFinalDocumentsByEhrId(ehrId);
    }

    /**
     * Listar documentos por autor
     */
    @Transactional(readOnly = true)
    public List<ClinicalDocument> findByAuthor(String authorId) {
        log.debug("Finding documents by author: {}", authorId);
        return documentRepository.findByAuthorIdOrderByDateCreatedDesc(authorId);
    }

    /**
     * Listar documentos por nivel de confidencialidad
     */
    @Transactional(readOnly = true)
    public List<ClinicalDocument> findByConfidentiality(UUID ehrId, ClinicalDocument.Confidentiality confidentiality) {
        log.debug("Finding documents by confidentiality: ehrId={}, level={}", ehrId, confidentiality);
        return documentRepository.findByEhrIdAndConfidentiality(ehrId, confidentiality);
    }

    // ==================== Version Management ====================

    /**
     * Obtener todas las versiones de un documento
     */
    @Transactional(readOnly = true)
    public List<ClinicalDocumentVersion> getVersionHistory(UUID documentId) {
        log.debug("Getting version history for document: {}", documentId);
        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    }

    /**
     * Obtener una versión específica
     */
    @Transactional(readOnly = true)
    public Optional<ClinicalDocumentVersion> getVersion(UUID documentId, Integer versionNumber) {
        log.debug("Getting version: documentId={}, version={}", documentId, versionNumber);
        return versionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber);
    }

    /**
     * Obtener la versión actual (más reciente)
     */
    @Transactional(readOnly = true)
    public Optional<ClinicalDocumentVersion> getCurrentVersion(UUID documentId) {
        log.debug("Getting current version for document: {}", documentId);
        return versionRepository.findLatestVersion(documentId);
    }

    /**
     * Validar integridad de una versión usando su hash
     */
    @Transactional(readOnly = true)
    public boolean validateVersionIntegrity(UUID versionId) {
        Optional<ClinicalDocumentVersion> versionOpt = versionRepository.findById(versionId);
        if (versionOpt.isEmpty()) {
            return false;
        }

        ClinicalDocumentVersion version = versionOpt.get();
        String computedHash = computeHash(version.getSnapshotJson());
        boolean isValid = computedHash.equals(version.getSnapshotHash());

        if (!isValid) {
            log.warn("Version integrity check FAILED: versionId={}, stored={}, computed={}",
                versionId, version.getSnapshotHash(), computedHash);
        }

        return isValid;
    }

    /**
     * Contar versiones de un documento
     */
    @Transactional(readOnly = true)
    public long countVersions(UUID documentId) {
        return versionRepository.countByDocumentId(documentId);
    }

    // ==================== Helper Methods ====================

    /**
     * Crear una nueva versión de un documento
     */
    private ClinicalDocumentVersion createVersion(ClinicalDocument document, String fhirJson,
                                                  String changeReason, String[] changedSections,
                                                  String versionedBy) {
        ClinicalDocumentVersion version = new ClinicalDocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(document.getCurrentVersion());
        version.setSnapshotJson(fhirJson);
        version.setSnapshotHash(computeHash(fhirJson));
        version.setChangeReason(changeReason);
        version.setChangedSections(changedSections);
        version.setVersionedAt(LocalDateTime.now());
        version.setVersionedBy(versionedBy);

        ClinicalDocumentVersion saved = versionRepository.save(version);

        log.debug("Created version {} for document {}", saved.getVersionNumber(), document.getId());
        return saved;
    }

    /**
     * Calcular hash SHA-256 de un JSON
     */
    private String computeHash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            // Convertir bytes a hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error computing hash: {}", e.getMessage());
            throw new RuntimeException("Failed to compute SHA-256 hash", e);
        }
    }

    private ClinicalDocument clone(ClinicalDocument source) {
        ClinicalDocument clone = new ClinicalDocument();
        clone.setId(source.getId());
        clone.setEhr(source.getEhr());
        clone.setFhirCompositionId(source.getFhirCompositionId());
        clone.setDocumentType(source.getDocumentType());
        clone.setStatus(source.getStatus());
        clone.setCurrentVersion(source.getCurrentVersion());
        clone.setTitle(source.getTitle());
        clone.setConfidentiality(source.getConfidentiality());
        clone.setAuthorId(source.getAuthorId());
        clone.setEncounterId(source.getEncounterId());
        clone.setDateCreated(source.getDateCreated());
        clone.setDateAttested(source.getDateAttested());
        clone.setCustodian(source.getCustodian());
        clone.setMetadata(source.getMetadata());
        clone.setCreatedAt(source.getCreatedAt());
        clone.setUpdatedAt(source.getUpdatedAt());
        return clone;
    }

    // ==================== DTOs ====================

    @lombok.Data
    @lombok.Builder
    public static class DocumentStats {
        private UUID documentId;
        private String title;
        private ClinicalDocument.DocumentType type;
        private ClinicalDocument.DocumentStatus status;
        private Integer currentVersion;
        private Long totalVersions;
        private LocalDateTime dateCreated;
        private LocalDateTime dateAttested;
        private String authorId;
        private Boolean integrityValid;
    }

    /**
     * Obtener estadísticas de un documento
     */
    @Transactional(readOnly = true)
    public DocumentStats getDocumentStats(UUID documentId) {
        ClinicalDocument document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        long versionCount = versionRepository.countByDocumentId(documentId);

        // Validar integridad de la versión actual
        Optional<ClinicalDocumentVersion> currentVersion = versionRepository.findLatestVersion(documentId);
        Boolean integrityValid = currentVersion.map(v -> validateVersionIntegrity(v.getId())).orElse(null);

        return DocumentStats.builder()
            .documentId(document.getId())
            .title(document.getTitle())
            .type(document.getDocumentType())
            .status(document.getStatus())
            .currentVersion(document.getCurrentVersion())
            .totalVersions(versionCount)
            .dateCreated(document.getDateCreated())
            .dateAttested(document.getDateAttested())
            .authorId(document.getAuthorId())
            .integrityValid(integrityValid)
            .build();
    }
}
