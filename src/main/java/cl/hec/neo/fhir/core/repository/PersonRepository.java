package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para Person (Master Patient Index)
 */
@Repository
public interface PersonRepository extends JpaRepository<Person, UUID> {

    /**
     * Buscar personas por tenant (no eliminadas)
     */
    @Query("SELECT p FROM Person p WHERE p.tenant.id = :tenantId AND p.deletedAt IS NULL")
    List<Person> findByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Buscar personas activas por tenant
     */
    @Query("SELECT p FROM Person p WHERE p.tenant.id = :tenantId AND p.active = true AND p.deletedAt IS NULL")
    List<Person> findActiveByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Buscar por RUN (a través de PersonIdentifier)
     */
    @Query("SELECT DISTINCT p FROM Person p JOIN p.identifiers i WHERE i.system = 'http://regcivil.cl/run' AND i.value = :run AND p.deletedAt IS NULL")
    List<Person> findByRun(@Param("run") String run);

    /**
     * Buscar candidatos para matching (por nombre y fecha de nacimiento aproximada)
     */
    @Query("SELECT p FROM Person p WHERE p.tenant.id = :tenantId AND p.deletedAt IS NULL AND p.birthDate BETWEEN :birthDateFrom AND :birthDateTo")
    List<Person> findMatchCandidates(@Param("tenantId") UUID tenantId, @Param("birthDateFrom") java.time.LocalDate birthDateFrom, @Param("birthDateTo") java.time.LocalDate birthDateTo);

    /**
     * Buscar personas que fueron fusionadas en otra
     */
    @Query("SELECT p FROM Person p WHERE p.mergedInto.id = :mergedIntoId")
    List<Person> findMergedInto(@Param("mergedIntoId") UUID mergedIntoId);
}
