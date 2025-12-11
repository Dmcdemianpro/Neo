package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.EpisodeResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para EpisodeResource (Recursos vinculados a episodios)
 */
@Repository
public interface EpisodeResourceRepository extends JpaRepository<EpisodeResource, UUID> {

    /**
     * Buscar recursos por episodio
     */
    List<EpisodeResource> findByEpisodeIdOrderByLinkedAtDesc(UUID episodeId);

    /**
     * Buscar por tipo de recurso y ID
     */
    Optional<EpisodeResource> findByResourceTypeAndResourceId(String resourceType, String resourceId);

    /**
     * Buscar recursos de un tipo específico en un episodio
     */
    List<EpisodeResource> findByEpisodeIdAndResourceTypeOrderByLinkedAtDesc(UUID episodeId, String resourceType);

    /**
     * Buscar episodio que contiene un recurso FHIR específico
     */
    @Query("SELECT er FROM EpisodeResource er WHERE er.resourceType = :resourceType AND er.resourceId = :resourceId")
    Optional<EpisodeResource> findEpisodeForResource(@Param("resourceType") String resourceType, @Param("resourceId") String resourceId);

    /**
     * Contar recursos vinculados a un episodio
     */
    long countByEpisodeId(UUID episodeId);
}
