package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.Prescription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para Prescription (Recetas Electrónicas)
 */
@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    /**
     * Buscar por FHIR MedicationRequest ID
     */
    Optional<Prescription> findByFhirMedicationRequestId(String fhirMedicationRequestId);

    /**
     * Buscar por número de prescripción
     */
    Optional<Prescription> findByPrescriptionNumber(String prescriptionNumber);

    /**
     * Buscar prescripciones por paciente
     */
    Page<Prescription> findByPatientIdOrderByAuthoredOnDesc(UUID patientId, Pageable pageable);

    /**
     * Buscar prescripciones por EHR
     */
    List<Prescription> findByEhrIdOrderByAuthoredOnDesc(UUID ehrId);

    /**
     * Buscar prescripciones por episodio
     */
    List<Prescription> findByEpisodeIdOrderByAuthoredOnDesc(UUID episodeId);

    /**
     * Buscar prescripciones por prescriptor
     */
    Page<Prescription> findByPrescriberIdOrderByAuthoredOnDesc(String prescriberId, Pageable pageable);

    /**
     * Buscar prescripciones activas por paciente
     */
    @Query("SELECT p FROM Prescription p WHERE p.patient.id = :patientId AND p.status = 'ACTIVE' ORDER BY p.authoredOn DESC")
    List<Prescription> findActiveByPatientId(@Param("patientId") UUID patientId);

    /**
     * Buscar prescripciones pendientes de envío a CENABAST
     */
    @Query("SELECT p FROM Prescription p WHERE p.tenant.id = :tenantId AND p.cenabastStatus = 'PENDING' ORDER BY p.authoredOn ASC")
    List<Prescription> findPendingCenabastByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Buscar prescripciones con errores CENABAST
     */
    @Query("SELECT p FROM Prescription p WHERE p.tenant.id = :tenantId AND p.cenabastStatus = 'ERROR' ORDER BY p.cenabastSentAt DESC")
    List<Prescription> findCenabastErrorsByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Buscar prescripciones que necesitan re-dispensación
     */
    @Query("SELECT p FROM Prescription p WHERE p.patient.id = :patientId AND p.status = 'ACTIVE' AND p.refillsAllowed > p.dispenseCount ORDER BY p.authoredOn DESC")
    List<Prescription> findRefillableByPatientId(@Param("patientId") UUID patientId);

    /**
     * Buscar prescripciones vigentes (no vencidas)
     */
    @Query("SELECT p FROM Prescription p WHERE p.patient.id = :patientId AND p.status = 'ACTIVE' AND (p.validityPeriodEnd IS NULL OR p.validityPeriodEnd > :currentDate) ORDER BY p.authoredOn DESC")
    List<Prescription> findValidByPatientId(@Param("patientId") UUID patientId, @Param("currentDate") LocalDateTime currentDate);
}
