package cl.hec.neo.fhir.core.service;

import cl.hec.neo.fhir.core.model.Ehr;
import cl.hec.neo.fhir.core.model.Episode;
import cl.hec.neo.fhir.core.model.Person;
import cl.hec.neo.fhir.core.model.Prescription;
import cl.hec.neo.fhir.core.model.PrescriptionDispense;
import cl.hec.neo.fhir.core.repository.EhrRepository;
import cl.hec.neo.fhir.core.repository.EpisodeRepository;
import cl.hec.neo.fhir.core.repository.PersonRepository;
import cl.hec.neo.fhir.core.repository.PrescriptionDispenseRepository;
import cl.hec.neo.fhir.core.repository.PrescriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para gestión de prescripciones electrónicas
 *
 * Responsabilidades:
 * - CRUD de prescripciones (recetas electrónicas)
 * - Gestión del ciclo de vida de prescripciones
 * - Dispensación de medicamentos
 * - Integración con CENABAST (sistema nacional de abastecimiento)
 * - Control de refills (repeticiones)
 * - Validación de vigencia de prescripciones
 * - Auditoría completa de prescripciones y dispensaciones
 */
@Service
@Transactional
public class NeoPrescriptionService {

    private static final Logger log = LoggerFactory.getLogger(NeoPrescriptionService.class);

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionDispenseRepository dispenseRepository;
    private final EhrRepository ehrRepository;
    private final EpisodeRepository episodeRepository;
    private final PersonRepository personRepository;
    private final NeoAuditService auditService;

    public NeoPrescriptionService(
            PrescriptionRepository prescriptionRepository,
            PrescriptionDispenseRepository dispenseRepository,
            EhrRepository ehrRepository,
            EpisodeRepository episodeRepository,
            PersonRepository personRepository,
            NeoAuditService auditService) {
        this.prescriptionRepository = prescriptionRepository;
        this.dispenseRepository = dispenseRepository;
        this.ehrRepository = ehrRepository;
        this.episodeRepository = episodeRepository;
        this.personRepository = personRepository;
        this.auditService = auditService;
    }

    // ==================== Prescription CRUD ====================

    /**
     * Crear nueva prescripción
     */
    public Prescription createPrescription(Prescription prescription, String createdBy) {
        log.info("Creating prescription for patient: patientId={}, medication={}",
            prescription.getPatient().getId(), prescription.getMedicationDisplay());

        // Validar que el EHR y el paciente existan
        Ehr ehr = ehrRepository.findById(prescription.getEhr().getId())
            .orElseThrow(() -> new IllegalArgumentException("EHR not found: " + prescription.getEhr().getId()));

        Person patient = personRepository.findById(prescription.getPatient().getId())
            .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + prescription.getPatient().getId()));

        // Validar episodio si se proporciona
        if (prescription.getEpisode() != null) {
            Episode episode = episodeRepository.findById(prescription.getEpisode().getId())
                .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + prescription.getEpisode().getId()));
            prescription.setEpisode(episode);
        }

        // Establecer relaciones
        prescription.setEhr(ehr);
        prescription.setPatient(patient);
        prescription.setTenant(ehr.getPerson().getTenant());

        // Valores iniciales
        if (prescription.getStatus() == null) {
            prescription.setStatus(Prescription.PrescriptionStatus.ACTIVE);
        }
        if (prescription.getAuthoredOn() == null) {
            prescription.setAuthoredOn(LocalDateTime.now());
        }
        if (prescription.getCenabastStatus() == null) {
            prescription.setCenabastStatus(Prescription.CenabastStatus.PENDING);
        }
        if (prescription.getDispenseCount() == null) {
            prescription.setDispenseCount(0);
        }
        if (prescription.getRefillsAllowed() == null) {
            prescription.setRefillsAllowed(0);
        }

        // Generar número de prescripción si no existe
        if (prescription.getPrescriptionNumber() == null) {
            prescription.setPrescriptionNumber(generatePrescriptionNumber());
        }

        Prescription saved = prescriptionRepository.save(prescription);

        // Auditoría
        auditService.auditCreate(
            saved.getTenant(),
            "Prescription",
            saved.getId().toString(),
            createdBy,
            saved
        );

        log.info("Prescription created successfully: id={}, number={}",
            saved.getId(), saved.getPrescriptionNumber());
        return saved;
    }

    /**
     * Actualizar prescripción existente
     */
    public Prescription updatePrescription(UUID id, Prescription updates, String updatedBy) {
        log.info("Updating prescription: id={}", id);

        Prescription existing = prescriptionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + id));

        // Solo permitir actualizar si no está completada o cancelada
        if (existing.getStatus() == Prescription.PrescriptionStatus.COMPLETED ||
            existing.getStatus() == Prescription.PrescriptionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot update completed or cancelled prescription");
        }

        // Actualizar campos editables
        existing.setMedicationCode(updates.getMedicationCode());
        existing.setMedicationDisplay(updates.getMedicationDisplay());
        existing.setDosageInstruction(updates.getDosageInstruction());
        existing.setQuantityValue(updates.getQuantityValue());
        existing.setQuantityUnit(updates.getQuantityUnit());
        existing.setRefillsAllowed(updates.getRefillsAllowed());
        existing.setValidityPeriodStart(updates.getValidityPeriodStart());
        existing.setValidityPeriodEnd(updates.getValidityPeriodEnd());
        existing.setPriority(updates.getPriority());
        existing.setMetadata(updates.getMetadata());

        Prescription saved = prescriptionRepository.save(existing);

        // Auditoría
        auditService.auditUpdate(
            saved.getTenant(),
            "Prescription",
            saved.getId().toString(),
            updatedBy,
            existing,
            saved
        );

        log.info("Prescription updated successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Cambiar estado de la prescripción
     */
    public Prescription changeStatus(UUID id, Prescription.PrescriptionStatus newStatus, String changedBy) {
        log.info("Changing prescription status: id={}, newStatus={}", id, newStatus);

        Prescription prescription = prescriptionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + id));

        Prescription.PrescriptionStatus oldStatus = prescription.getStatus();
        prescription.setStatus(newStatus);

        if (newStatus == Prescription.PrescriptionStatus.COMPLETED) {
            // Verificar que se hayan dispensado todas las unidades
            long dispenseCount = dispenseRepository.countByPrescriptionId(id);
            if (dispenseCount == 0) {
                log.warn("Marking prescription as completed without any dispenses: id={}", id);
            }
        }

        Prescription saved = prescriptionRepository.save(prescription);

        // Auditoría
        String auditJson = String.format("{\"oldStatus\": \"%s\", \"newStatus\": \"%s\"}",
            oldStatus, newStatus);
        auditService.audit(NeoAuditService.AuditEventBuilder.create()
            .tenant(saved.getTenant())
            .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.UPDATE)
            .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
            .entityType("Prescription")
            .entityId(saved.getId().toString())
            .userId(changedBy)
            .detailsJson(auditJson)
        );

        log.info("Prescription status changed successfully: id={}, status={}", saved.getId(), newStatus);
        return saved;
    }

    /**
     * Cancelar prescripción
     */
    public Prescription cancelPrescription(UUID id, String reason, String cancelledBy) {
        log.info("Cancelling prescription: id={}, reason={}", id, reason);

        Prescription prescription = prescriptionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + id));

        if (prescription.getStatus() == Prescription.PrescriptionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed prescription");
        }

        prescription.setStatus(Prescription.PrescriptionStatus.CANCELLED);

        // Agregar razón al metadata
        String metadata = String.format("{\"cancellationReason\": \"%s\"}", reason);
        prescription.setMetadata(metadata);

        Prescription saved = prescriptionRepository.save(prescription);

        // Auditoría
        String auditJson = String.format("{\"reason\": \"%s\"}", reason);
        auditService.audit(NeoAuditService.AuditEventBuilder.create()
            .tenant(saved.getTenant())
            .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.DELETE)
            .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
            .entityType("Prescription")
            .entityId(saved.getId().toString())
            .userId(cancelledBy)
            .detailsJson(auditJson)
        );

        log.info("Prescription cancelled successfully: id={}", saved.getId());
        return saved;
    }

    // ==================== Dispensación ====================

    /**
     * Dispensar medicamento (crear registro de dispensación)
     */
    public PrescriptionDispense dispensePrescription(
            UUID prescriptionId,
            BigDecimal quantityDispensed,
            String quantityUnit,
            String dispensedBy,
            String dispensedLocation,
            String fhirMedicationDispenseId,
            String notes) {

        log.info("Dispensing prescription: id={}, quantity={} {}",
            prescriptionId, quantityDispensed, quantityUnit);

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + prescriptionId));

        // Validar que la prescripción esté activa
        if (prescription.getStatus() != Prescription.PrescriptionStatus.ACTIVE) {
            throw new IllegalStateException("Cannot dispense inactive prescription");
        }

        // Validar vigencia
        if (!isPrescriptionValid(prescription)) {
            throw new IllegalStateException("Prescription has expired or is not yet valid");
        }

        // Validar que no se exceda el número de refills
        if (prescription.getDispenseCount() >= prescription.getRefillsAllowed() + 1) {
            throw new IllegalStateException("No refills remaining for this prescription");
        }

        // Crear registro de dispensación
        PrescriptionDispense dispense = PrescriptionDispense.builder()
            .prescription(prescription)
            .fhirMedicationDispenseId(fhirMedicationDispenseId)
            .dispenseNumber(generateDispenseNumber())
            .status(PrescriptionDispense.DispenseStatus.COMPLETED)
            .quantityDispensed(quantityDispensed)
            .quantityUnit(quantityUnit)
            .whenPrepared(LocalDateTime.now())
            .whenHandedOver(LocalDateTime.now())
            .dispensedBy(dispensedBy)
            .dispensedLocation(dispensedLocation)
            .notes(notes)
            .build();

        PrescriptionDispense saved = dispenseRepository.save(dispense);

        // Actualizar contador de dispensaciones en la prescripción
        prescription.setDispenseCount(prescription.getDispenseCount() + 1);
        prescription.setLastDispensedAt(LocalDateTime.now());

        // Si se alcanzó el número máximo de dispensaciones, marcar como completada
        if (prescription.getDispenseCount() >= prescription.getRefillsAllowed() + 1) {
            prescription.setStatus(Prescription.PrescriptionStatus.COMPLETED);
            log.info("Prescription completed after final dispense: id={}", prescriptionId);
        }

        prescriptionRepository.save(prescription);

        // Auditoría
        String auditJson = String.format(
            "{\"dispenseId\": \"%s\", \"quantity\": %s, \"unit\": \"%s\", \"dispensedBy\": \"%s\"}",
            saved.getId(), quantityDispensed, quantityUnit, dispensedBy);
        auditService.audit(NeoAuditService.AuditEventBuilder.create()
            .tenant(prescription.getTenant())
            .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.CREATE)
            .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
            .entityType("PrescriptionDispense")
            .entityId(saved.getId().toString())
            .userId(dispensedBy)
            .detailsJson(auditJson)
        );

        log.info("Prescription dispensed successfully: dispenseId={}, prescriptionId={}",
            saved.getId(), prescriptionId);
        return saved;
    }

    /**
     * Cancelar dispensación
     */
    public PrescriptionDispense cancelDispense(UUID dispenseId, String cancelledBy) {
        log.info("Cancelling dispense: id={}", dispenseId);

        PrescriptionDispense dispense = dispenseRepository.findById(dispenseId)
            .orElseThrow(() -> new IllegalArgumentException("Dispense not found: " + dispenseId));

        if (dispense.getStatus() == PrescriptionDispense.DispenseStatus.COMPLETED) {
            // Revertir contador de dispensaciones
            Prescription prescription = dispense.getPrescription();
            prescription.setDispenseCount(Math.max(0, prescription.getDispenseCount() - 1));
            prescriptionRepository.save(prescription);
        }

        dispense.setStatus(PrescriptionDispense.DispenseStatus.CANCELLED);
        PrescriptionDispense saved = dispenseRepository.save(dispense);

        // Auditoría
        auditService.audit(NeoAuditService.AuditEventBuilder.create()
            .tenant(saved.getPrescription().getTenant())
            .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.DELETE)
            .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
            .entityType("PrescriptionDispense")
            .entityId(saved.getId().toString())
            .userId(cancelledBy)
        );

        log.info("Dispense cancelled successfully: id={}", saved.getId());
        return saved;
    }

    // ==================== CENABAST Integration ====================

    /**
     * Marcar prescripción como enviada a CENABAST
     */
    public Prescription markSentToCenabast(UUID prescriptionId, String responseJson, String sentBy) {
        log.info("Marking prescription as sent to CENABAST: id={}", prescriptionId);

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + prescriptionId));

        prescription.setCenabastStatus(Prescription.CenabastStatus.SENT);
        prescription.setCenabastSentAt(LocalDateTime.now());
        prescription.setCenabastResponseJson(responseJson);
        prescription.setCenabastErrorMessage(null);

        Prescription saved = prescriptionRepository.save(prescription);

        // Auditoría
        auditService.audit(NeoAuditService.AuditEventBuilder.create()
            .tenant(saved.getTenant())
            .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.UPDATE)
            .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
            .entityType("Prescription")
            .entityId(saved.getId().toString())
            .userId(sentBy)
            .detailsJson("{\"action\": \"sent_to_cenabast\"}")
        );

        log.info("Prescription marked as sent to CENABAST: id={}", saved.getId());
        return saved;
    }

    /**
     * Actualizar estado CENABAST de la prescripción
     */
    public Prescription updateCenabastStatus(
            UUID prescriptionId,
            Prescription.CenabastStatus newStatus,
            String responseJson,
            String errorMessage,
            String updatedBy) {

        log.info("Updating CENABAST status: id={}, newStatus={}", prescriptionId, newStatus);

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + prescriptionId));

        prescription.setCenabastStatus(newStatus);
        prescription.setCenabastResponseJson(responseJson);
        prescription.setCenabastErrorMessage(errorMessage);

        Prescription saved = prescriptionRepository.save(prescription);

        // Auditoría
        String auditJson = String.format(
            "{\"cenabastStatus\": \"%s\", \"hasError\": %b}",
            newStatus, errorMessage != null);
        auditService.audit(NeoAuditService.AuditEventBuilder.create()
            .tenant(saved.getTenant())
            .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.UPDATE)
            .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
            .entityType("Prescription")
            .entityId(saved.getId().toString())
            .userId(updatedBy)
            .detailsJson(auditJson)
        );

        log.info("CENABAST status updated: id={}, status={}", saved.getId(), newStatus);
        return saved;
    }

    /**
     * Obtener prescripciones pendientes de envío a CENABAST
     */
    @Transactional(readOnly = true)
    public List<Prescription> findPendingCenabast(UUID tenantId) {
        log.debug("Finding pending CENABAST prescriptions for tenant: {}", tenantId);
        return prescriptionRepository.findPendingCenabastByTenantId(tenantId);
    }

    /**
     * Obtener prescripciones con errores CENABAST
     */
    @Transactional(readOnly = true)
    public List<Prescription> findCenabastErrors(UUID tenantId) {
        log.debug("Finding CENABAST errors for tenant: {}", tenantId);
        return prescriptionRepository.findCenabastErrorsByTenantId(tenantId);
    }

    // ==================== Queries ====================

    /**
     * Buscar prescripción por ID
     */
    @Transactional(readOnly = true)
    public Optional<Prescription> findById(UUID id) {
        log.debug("Finding prescription by id: {}", id);
        return prescriptionRepository.findById(id);
    }

    /**
     * Buscar por FHIR MedicationRequest ID
     */
    @Transactional(readOnly = true)
    public Optional<Prescription> findByFhirId(String fhirMedicationRequestId) {
        log.debug("Finding prescription by FHIR ID: {}", fhirMedicationRequestId);
        return prescriptionRepository.findByFhirMedicationRequestId(fhirMedicationRequestId);
    }

    /**
     * Buscar por número de prescripción
     */
    @Transactional(readOnly = true)
    public Optional<Prescription> findByNumber(String prescriptionNumber) {
        log.debug("Finding prescription by number: {}", prescriptionNumber);
        return prescriptionRepository.findByPrescriptionNumber(prescriptionNumber);
    }

    /**
     * Buscar prescripciones por paciente (paginado)
     */
    @Transactional(readOnly = true)
    public Page<Prescription> findByPatient(UUID patientId, Pageable pageable) {
        log.debug("Finding prescriptions by patient: patientId={}", patientId);
        return prescriptionRepository.findByPatientIdOrderByAuthoredOnDesc(patientId, pageable);
    }

    /**
     * Buscar prescripciones por EHR
     */
    @Transactional(readOnly = true)
    public List<Prescription> findByEhr(UUID ehrId) {
        log.debug("Finding prescriptions by EHR: ehrId={}", ehrId);
        return prescriptionRepository.findByEhrIdOrderByAuthoredOnDesc(ehrId);
    }

    /**
     * Buscar prescripciones por episodio
     */
    @Transactional(readOnly = true)
    public List<Prescription> findByEpisode(UUID episodeId) {
        log.debug("Finding prescriptions by episode: episodeId={}", episodeId);
        return prescriptionRepository.findByEpisodeIdOrderByAuthoredOnDesc(episodeId);
    }

    /**
     * Buscar prescripciones por prescriptor
     */
    @Transactional(readOnly = true)
    public Page<Prescription> findByPrescriber(String prescriberId, Pageable pageable) {
        log.debug("Finding prescriptions by prescriber: prescriberId={}", prescriberId);
        return prescriptionRepository.findByPrescriberIdOrderByAuthoredOnDesc(prescriberId, pageable);
    }

    /**
     * Buscar prescripciones activas por paciente
     */
    @Transactional(readOnly = true)
    public List<Prescription> findActiveByPatient(UUID patientId) {
        log.debug("Finding active prescriptions by patient: patientId={}", patientId);
        return prescriptionRepository.findActiveByPatientId(patientId);
    }

    /**
     * Buscar prescripciones vigentes (no vencidas)
     */
    @Transactional(readOnly = true)
    public List<Prescription> findValidByPatient(UUID patientId) {
        log.debug("Finding valid prescriptions by patient: patientId={}", patientId);
        return prescriptionRepository.findValidByPatientId(patientId, LocalDateTime.now());
    }

    /**
     * Buscar prescripciones que pueden re-dispensarse (refills)
     */
    @Transactional(readOnly = true)
    public List<Prescription> findRefillable(UUID patientId) {
        log.debug("Finding refillable prescriptions by patient: patientId={}", patientId);
        return prescriptionRepository.findRefillableByPatientId(patientId);
    }

    /**
     * Buscar dispensaciones de una prescripción
     */
    @Transactional(readOnly = true)
    public List<PrescriptionDispense> findDispenses(UUID prescriptionId) {
        log.debug("Finding dispenses for prescription: prescriptionId={}", prescriptionId);
        return dispenseRepository.findByPrescriptionIdOrderByWhenHandedOverDesc(prescriptionId);
    }

    /**
     * Buscar dispensaciones completadas
     */
    @Transactional(readOnly = true)
    public List<PrescriptionDispense> findCompletedDispenses(UUID prescriptionId) {
        log.debug("Finding completed dispenses for prescription: prescriptionId={}", prescriptionId);
        return dispenseRepository.findCompletedByPrescriptionId(prescriptionId);
    }

    /**
     * Buscar última dispensación
     */
    @Transactional(readOnly = true)
    public Optional<PrescriptionDispense> findLatestDispense(UUID prescriptionId) {
        log.debug("Finding latest dispense for prescription: prescriptionId={}", prescriptionId);
        return dispenseRepository.findLatestByPrescriptionId(prescriptionId);
    }

    /**
     * Contar dispensaciones
     */
    @Transactional(readOnly = true)
    public long countDispenses(UUID prescriptionId) {
        return dispenseRepository.countByPrescriptionId(prescriptionId);
    }

    // ==================== Validación ====================

    /**
     * Verificar si una prescripción está vigente según período de validez
     */
    @Transactional(readOnly = true)
    public boolean isPrescriptionValid(Prescription prescription) {
        LocalDateTime now = LocalDateTime.now();

        if (prescription.getValidityPeriodStart() != null && now.isBefore(prescription.getValidityPeriodStart())) {
            return false;
        }

        if (prescription.getValidityPeriodEnd() != null && now.isAfter(prescription.getValidityPeriodEnd())) {
            return false;
        }

        return true;
    }

    /**
     * Verificar si una prescripción puede dispensarse
     */
    @Transactional(readOnly = true)
    public boolean canDispense(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + prescriptionId));

        // Debe estar activa
        if (prescription.getStatus() != Prescription.PrescriptionStatus.ACTIVE) {
            return false;
        }

        // Debe estar vigente
        if (!isPrescriptionValid(prescription)) {
            return false;
        }

        // Debe tener refills disponibles
        if (prescription.getDispenseCount() >= prescription.getRefillsAllowed() + 1) {
            return false;
        }

        return true;
    }

    // ==================== Utilidades ====================

    /**
     * Generar número único de prescripción
     */
    private String generatePrescriptionNumber() {
        return "RX-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Generar número único de dispensación
     */
    private String generateDispenseNumber() {
        return "DISP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Estadísticas de prescripción
     */
    public static class PrescriptionStats {
        private final long totalDispenses;
        private final long completedDispenses;
        private final long remainingRefills;
        private final boolean canRefill;

        public PrescriptionStats(long totalDispenses, long completedDispenses, long remainingRefills, boolean canRefill) {
            this.totalDispenses = totalDispenses;
            this.completedDispenses = completedDispenses;
            this.remainingRefills = remainingRefills;
            this.canRefill = canRefill;
        }

        public long getTotalDispenses() { return totalDispenses; }
        public long getCompletedDispenses() { return completedDispenses; }
        public long getRemainingRefills() { return remainingRefills; }
        public boolean isCanRefill() { return canRefill; }
    }

    /**
     * Obtener estadísticas de una prescripción
     */
    @Transactional(readOnly = true)
    public PrescriptionStats getPrescriptionStats(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + prescriptionId));

        long totalDispenses = dispenseRepository.countByPrescriptionId(prescriptionId);
        long completedDispenses = dispenseRepository.findCompletedByPrescriptionId(prescriptionId).size();
        long remainingRefills = Math.max(0, (prescription.getRefillsAllowed() + 1) - prescription.getDispenseCount());
        boolean canRefill = canDispense(prescriptionId);

        return new PrescriptionStats(totalDispenses, completedDispenses, remainingRefills, canRefill);
    }
}
