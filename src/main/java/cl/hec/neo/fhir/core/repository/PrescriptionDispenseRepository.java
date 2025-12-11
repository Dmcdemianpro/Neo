package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.PrescriptionDispense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para PrescriptionDispense (Dispensaciones)
 */
@Repository
public interface PrescriptionDispenseRepository extends JpaRepository<PrescriptionDispense, UUID> {

    /**
     * Buscar por FHIR MedicationDispense ID
     */
    Optional<PrescriptionDispense> findByFhirMedicationDispenseId(String fhirMedicationDispenseId);

    /**
     * Buscar dispensaciones por prescripción
     */
    List<PrescriptionDispense> findByPrescriptionIdOrderByWhenHandedOverDesc(UUID prescriptionId);

    /**
     * Buscar dispensaciones completadas por prescripción
     */
    @Query("SELECT pd FROM PrescriptionDispense pd WHERE pd.prescription.id = :prescriptionId AND pd.status = 'COMPLETED' ORDER BY pd.whenHandedOver DESC")
    List<PrescriptionDispense> findCompletedByPrescriptionId(@Param("prescriptionId") UUID prescriptionId);

    /**
     * Buscar dispensaciones por farmacéutico
     */
    List<PrescriptionDispense> findByDispensedByOrderByWhenHandedOverDesc(String dispensedBy);

    /**
     * Buscar dispensaciones en un rango de fechas
     */
    @Query("SELECT pd FROM PrescriptionDispense pd WHERE pd.whenHandedOver BETWEEN :startDate AND :endDate ORDER BY pd.whenHandedOver DESC")
    List<PrescriptionDispense> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Contar dispensaciones por prescripción
     */
    long countByPrescriptionId(UUID prescriptionId);

    /**
     * Buscar última dispensación de una prescripción
     */
    @Query("SELECT pd FROM PrescriptionDispense pd WHERE pd.prescription.id = :prescriptionId ORDER BY pd.whenHandedOver DESC LIMIT 1")
    Optional<PrescriptionDispense> findLatestByPrescriptionId(@Param("prescriptionId") UUID prescriptionId);
}
