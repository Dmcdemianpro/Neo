package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.Ehr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para EHR (Electronic Health Record)
 */
@Repository
public interface EhrRepository extends JpaRepository<Ehr, UUID> {

    /**
     * Buscar por EHR ID único
     */
    Optional<Ehr> findByEhrId(String ehrId);

    /**
     * Buscar EHR por persona
     */
    List<Ehr> findByPersonId(UUID personId);

    /**
     * Buscar EHR por persona y tenant
     */
    Optional<Ehr> findByPersonIdAndTenantId(UUID personId, UUID tenantId);

    /**
     * Buscar EHRs activos por tenant
     */
    @Query("SELECT e FROM Ehr e WHERE e.tenant.id = :tenantId AND e.status = 'ACTIVE'")
    List<Ehr> findActiveByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Verificar si existe EHR para persona y tenant
     */
    boolean existsByPersonIdAndTenantId(UUID personId, UUID tenantId);
}
