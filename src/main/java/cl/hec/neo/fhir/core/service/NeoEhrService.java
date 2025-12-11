package cl.hec.neo.fhir.core.service;

import cl.hec.neo.fhir.core.model.Ehr;
import cl.hec.neo.fhir.core.model.Episode;
import cl.hec.neo.fhir.core.model.EpisodeResource;
import cl.hec.neo.fhir.core.model.Person;
import cl.hec.neo.fhir.core.repository.EhrRepository;
import cl.hec.neo.fhir.core.repository.EpisodeRepository;
import cl.hec.neo.fhir.core.repository.EpisodeResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * NeoEhrService - Electronic Health Record Service
 *
 * Funcionalidades:
 * - Gestión de EHRs (expedientes clínicos electrónicos)
 * - Gestión de episodios clínicos
 * - Vinculación de recursos FHIR a episodios
 * - Ciclo de vida de episodios
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NeoEhrService {

    private final EhrRepository ehrRepository;
    private final EpisodeRepository episodeRepository;
    private final EpisodeResourceRepository episodeResourceRepository;

    // ========== EHR Management ==========

    /**
     * Crear EHR para un paciente
     */
    @Transactional
    public Ehr createEhr(Person person) {
        log.info("Creating EHR for person: {} in tenant: {}", person.getId(), person.getTenant().getId());

        // Verificar si ya existe EHR para este paciente y tenant
        Optional<Ehr> existing = ehrRepository.findByPersonIdAndTenantId(
            person.getId(),
            person.getTenant().getId()
        );

        if (existing.isPresent()) {
            log.warn("EHR already exists for person: {} in tenant: {}", person.getId(), person.getTenant().getId());
            return existing.get();
        }

        // Crear nuevo EHR
        Ehr ehr = Ehr.builder()
            .person(person)
            .tenant(person.getTenant())
            .ehrId(generateEhrId(person))
            .status(Ehr.EhrStatus.ACTIVE)
            .timeCreated(LocalDateTime.now())
            .systemId("NEO-FHIR-SERVER")
            .build();

        Ehr saved = ehrRepository.save(ehr);
        log.info("EHR created successfully: {}", saved.getId());
        return saved;
    }

    /**
     * Buscar EHR por ID
     */
    @Transactional(readOnly = true)
    public Optional<Ehr> findEhrById(UUID id) {
        return ehrRepository.findById(id);
    }

    /**
     * Buscar EHR por ehrId único
     */
    @Transactional(readOnly = true)
    public Optional<Ehr> findEhrByEhrId(String ehrId) {
        return ehrRepository.findByEhrId(ehrId);
    }

    /**
     * Buscar EHR de un paciente en un tenant específico
     */
    @Transactional(readOnly = true)
    public Optional<Ehr> findEhrByPersonAndTenant(UUID personId, UUID tenantId) {
        return ehrRepository.findByPersonIdAndTenantId(personId, tenantId);
    }

    /**
     * Obtener o crear EHR para un paciente
     */
    @Transactional
    public Ehr getOrCreateEhr(Person person) {
        return ehrRepository.findByPersonIdAndTenantId(person.getId(), person.getTenant().getId())
            .orElseGet(() -> createEhr(person));
    }

    /**
     * Cambiar estado del EHR
     */
    @Transactional
    public Ehr updateEhrStatus(UUID ehrId, Ehr.EhrStatus newStatus) {
        log.info("Updating EHR status: {} to {}", ehrId, newStatus);

        Ehr ehr = ehrRepository.findById(ehrId)
            .orElseThrow(() -> new IllegalArgumentException("EHR not found: " + ehrId));

        ehr.setStatus(newStatus);
        Ehr updated = ehrRepository.save(ehr);

        log.info("EHR status updated successfully");
        return updated;
    }

    // ========== Episode Management ==========

    /**
     * Crear nuevo episodio clínico
     */
    @Transactional
    public Episode createEpisode(Ehr ehr, Episode.EpisodeType type, String episodeNumber) {
        log.info("Creating episode for EHR: {}, type: {}", ehr.getId(), type);

        Episode episode = Episode.builder()
            .ehr(ehr)
            .episodeNumber(episodeNumber != null ? episodeNumber : generateEpisodeNumber(ehr))
            .type(type)
            .status(Episode.EpisodeStatus.ACTIVE)
            .periodStart(LocalDateTime.now())
            .priority(Episode.EpisodePriority.ROUTINE)
            .build();

        Episode saved = episodeRepository.save(episode);
        log.info("Episode created successfully: {}", saved.getId());
        return saved;
    }

    /**
     * Buscar episodio por ID
     */
    @Transactional(readOnly = true)
    public Optional<Episode> findEpisodeById(UUID id) {
        return episodeRepository.findById(id);
    }

    /**
     * Buscar episodio por número
     */
    @Transactional(readOnly = true)
    public Optional<Episode> findEpisodeByNumber(String episodeNumber) {
        return episodeRepository.findByEpisodeNumber(episodeNumber);
    }

    /**
     * Buscar todos los episodios de un EHR
     */
    @Transactional(readOnly = true)
    public List<Episode> findEpisodesByEhr(UUID ehrId) {
        return episodeRepository.findByEhrIdOrderByPeriodStartDesc(ehrId);
    }

    /**
     * Buscar episodios activos de un EHR
     */
    @Transactional(readOnly = true)
    public List<Episode> findActiveEpisodesByEhr(UUID ehrId) {
        return episodeRepository.findActiveByEhrId(ehrId);
    }

    /**
     * Buscar episodios por tipo
     */
    @Transactional(readOnly = true)
    public List<Episode> findEpisodesByType(UUID ehrId, Episode.EpisodeType type) {
        return episodeRepository.findByEhrIdAndType(ehrId, type);
    }

    /**
     * Cerrar episodio
     */
    @Transactional
    public Episode closeEpisode(UUID episodeId) {
        log.info("Closing episode: {}", episodeId);

        Episode episode = episodeRepository.findById(episodeId)
            .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + episodeId));

        episode.setStatus(Episode.EpisodeStatus.FINISHED);
        episode.setPeriodEnd(LocalDateTime.now());

        Episode closed = episodeRepository.save(episode);
        log.info("Episode closed successfully");
        return closed;
    }

    /**
     * Actualizar estado del episodio
     */
    @Transactional
    public Episode updateEpisodeStatus(UUID episodeId, Episode.EpisodeStatus newStatus) {
        log.info("Updating episode status: {} to {}", episodeId, newStatus);

        Episode episode = episodeRepository.findById(episodeId)
            .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + episodeId));

        Episode.EpisodeStatus oldStatus = episode.getStatus();
        episode.setStatus(newStatus);

        // Si se marca como finalizado/cancelado, establecer fecha de fin
        if (newStatus == Episode.EpisodeStatus.FINISHED || newStatus == Episode.EpisodeStatus.CANCELLED) {
            if (episode.getPeriodEnd() == null) {
                episode.setPeriodEnd(LocalDateTime.now());
            }
        }

        // TODO: Guardar cambio de estado en status_history (JSONB)

        Episode updated = episodeRepository.save(episode);
        log.info("Episode status updated from {} to {}", oldStatus, newStatus);
        return updated;
    }

    // ========== Episode Resource Management ==========

    /**
     * Vincular recurso FHIR a un episodio
     */
    @Transactional
    public EpisodeResource linkResourceToEpisode(Episode episode, String resourceType, String resourceId, String linkedBy) {
        log.info("Linking resource {} {} to episode {}", resourceType, resourceId, episode.getId());

        // Verificar si ya está vinculado
        Optional<EpisodeResource> existing = episodeResourceRepository.findByResourceTypeAndResourceId(resourceType, resourceId);
        if (existing.isPresent()) {
            log.warn("Resource already linked to episode: {}", existing.get().getEpisode().getId());
            return existing.get();
        }

        EpisodeResource episodeResource = EpisodeResource.builder()
            .episode(episode)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .linkedBy(linkedBy)
            .build();

        EpisodeResource saved = episodeResourceRepository.save(episodeResource);
        log.info("Resource linked successfully");
        return saved;
    }

    /**
     * Buscar recursos vinculados a un episodio
     */
    @Transactional(readOnly = true)
    public List<EpisodeResource> findResourcesByEpisode(UUID episodeId) {
        return episodeResourceRepository.findByEpisodeIdOrderByLinkedAtDesc(episodeId);
    }

    /**
     * Buscar recursos de un tipo específico en un episodio
     */
    @Transactional(readOnly = true)
    public List<EpisodeResource> findResourcesByEpisodeAndType(UUID episodeId, String resourceType) {
        return episodeResourceRepository.findByEpisodeIdAndResourceTypeOrderByLinkedAtDesc(episodeId, resourceType);
    }

    /**
     * Buscar a qué episodio pertenece un recurso FHIR
     */
    @Transactional(readOnly = true)
    public Optional<Episode> findEpisodeForResource(String resourceType, String resourceId) {
        return episodeResourceRepository.findEpisodeForResource(resourceType, resourceId)
            .map(EpisodeResource::getEpisode);
    }

    /**
     * Desvincular recurso de episodio
     */
    @Transactional
    public void unlinkResourceFromEpisode(String resourceType, String resourceId) {
        log.info("Unlinking resource {} {} from episode", resourceType, resourceId);

        episodeResourceRepository.findByResourceTypeAndResourceId(resourceType, resourceId)
            .ifPresent(episodeResource -> {
                episodeResourceRepository.delete(episodeResource);
                log.info("Resource unlinked successfully");
            });
    }

    // ========== Helper Methods ==========

    private String generateEhrId(Person person) {
        // Formato: EHR-{tenant_code}-{person_id_short}
        String tenantCode = person.getTenant().getCode();
        String personIdShort = person.getId().toString().substring(0, 8);
        return String.format("EHR-%s-%s", tenantCode, personIdShort);
    }

    private String generateEpisodeNumber(Ehr ehr) {
        // Formato: EP-{timestamp}-{random}
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 1000);
        return String.format("EP-%d-%03d", timestamp, random);
    }
}
