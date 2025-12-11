package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para Episode (Episodios Clínicos)
 */
@Repository
public interface EpisodeRepository extends JpaRepository<Episode, UUID> {

    /**
     * Buscar por número de episodio
     */
    Optional<Episode> findByEpisodeNumber(String episodeNumber);

    /**
     * Buscar episodios por EHR
     */
    List<Episode> findByEhrIdOrderByPeriodStartDesc(UUID ehrId);

    /**
     * Buscar episodios activos por EHR
     */
    @Query("SELECT e FROM Episode e WHERE e.ehr.id = :ehrId AND e.status = 'ACTIVE' ORDER BY e.periodStart DESC")
    List<Episode> findActiveByEhrId(@Param("ehrId") UUID ehrId);

    /**
     * Buscar episodios por tipo y EHR
     */
    @Query("SELECT e FROM Episode e WHERE e.ehr.id = :ehrId AND e.type = :type ORDER BY e.periodStart DESC")
    List<Episode> findByEhrIdAndType(@Param("ehrId") UUID ehrId, @Param("type") Episode.EpisodeType type);

    /**
     * Buscar episodios en un rango de fechas
     */
    @Query("SELECT e FROM Episode e WHERE e.ehr.id = :ehrId AND e.periodStart BETWEEN :startDate AND :endDate ORDER BY e.periodStart DESC")
    List<Episode> findByEhrIdAndDateRange(@Param("ehrId") UUID ehrId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Buscar episodios por cuidador
     */
    List<Episode> findByCareManagerIdOrderByPeriodStartDesc(UUID careManagerId);

    /**
     * Buscar episodios por EHR y estado
     */
    @Query("SELECT e FROM Episode e WHERE e.ehr.id = :ehrId AND e.status = :status ORDER BY e.periodStart DESC")
    List<Episode> findByEhrIdAndStatusOrderByPeriodStartDesc(@Param("ehrId") UUID ehrId, @Param("status") Episode.EpisodeStatus status);
}
