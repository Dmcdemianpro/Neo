package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository para Tenant (Multi-tenancy)
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Buscar tenant por código
     */
    Optional<Tenant> findByCode(String code);

    /**
     * Verificar si existe un tenant con el código dado
     */
    boolean existsByCode(String code);

    /**
     * Buscar tenant por código y status
     */
    Optional<Tenant> findByCodeAndStatus(String code, Tenant.TenantStatus status);
}
