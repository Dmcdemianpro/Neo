package cl.hec.neo.fhir.core.service;

import cl.hec.neo.fhir.core.model.Ehr;
import cl.hec.neo.fhir.core.model.Episode;
import cl.hec.neo.fhir.core.model.EpisodeResource;
import cl.hec.neo.fhir.core.repository.EhrRepository;
import cl.hec.neo.fhir.core.repository.EpisodeRepository;
import cl.hec.neo.fhir.core.repository.EpisodeResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para gestión de episodios clínicos
 *
 * Responsabilidades:
 * - CRUD de episodios de atención (emergencia, hospitalización, ambulatorio, crónico)
 * - Vinculación de recursos FHIR a episodios
 * - Gestión del ciclo de vida del episodio (planificado, activo, finalizado, cancelado)
 * - Tracking de diagnósticos y razones de atención
 * - Gestión de derivaciones y organizaciones responsables
 * - Auditoría de cambios de estado
 */
@Service
@Transactional
public class NeoEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(NeoEpisodeService.class);

    private final EpisodeRepository episodeRepository;
    private final EpisodeResourceRepository episodeResourceRepository;
    private final EhrRepository ehrRepository;
    private final NeoAuditService auditService;

    public NeoEpisodeService(
            EpisodeRepository episodeRepository,
            EpisodeResourceRepository episodeResourceRepository,
            EhrRepository ehrRepository,
            NeoAuditService auditService) {
        this.episodeRepository = episodeRepository;
        this.episodeResourceRepository = episodeResourceRepository;
        this.ehrRepository = ehrRepository;
        this.auditService = auditService;
    }

    /**
     * Crear un nuevo episodio clínico
     */
    public Episode createEpisode(Episode episode, String createdBy) {
        log.info("Creating episode: type={}, ehrId={}, episodeNumber={}",
            episode.getType(), episode.getEhr().getId(), episode.getEpisodeNumber());

        // Validar que existe el EHR
        Ehr ehr = ehrRepository.findById(episode.getEhr().getId())
            .orElseThrow(() -> new IllegalArgumentException("EHR not found: " + episode.getEhr().getId()));

        episode.setEhr(ehr);

        // Validar que no exista otro episodio con el mismo número
        Optional<Episode> existing = episodeRepository.findByEpisodeNumber(episode.getEpisodeNumber());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Episode number already exists: " + episode.getEpisodeNumber());
        }

        // Timestamps
        episode.setCreatedAt(LocalDateTime.now());
        episode.setUpdatedAt(LocalDateTime.now());
        episode.setCreatedBy(createdBy);
        episode.setUpdatedBy(createdBy);

        // Si no tiene fecha de inicio, usar fecha actual
        if (episode.getPeriodStart() == null) {
            episode.setPeriodStart(LocalDateTime.now());
        }

        // Estado inicial si no está definido
        if (episode.getStatus() == null) {
            episode.setStatus(Episode.EpisodeStatus.PLANNED);
        }

        Episode saved = episodeRepository.save(episode);

        // Auditoría
        auditService.auditCreate(
            ehr.getPerson().getTenant(),
            "Episode",
            saved.getId().toString(),
            createdBy,
            saved
        );

        log.info("Episode created successfully: id={}, number={}", saved.getId(), saved.getEpisodeNumber());
        return saved;
    }

    /**
     * Actualizar un episodio existente
     */
    public Episode updateEpisode(UUID id, Episode updates, String updatedBy) {
        log.info("Updating episode: id={}", id);

        Episode existing = episodeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + id));

        // Backup del estado anterior para auditoría
        Episode beforeState = clone(existing);

        // Actualizar campos editables
        existing.setType(updates.getType());
        existing.setDiagnosisPrimary(updates.getDiagnosisPrimary());
        existing.setDiagnosisPrimaryText(updates.getDiagnosisPrimaryText());
        existing.setDiagnosisSecondary(updates.getDiagnosisSecondary());
        existing.setCareManagerId(updates.getCareManagerId());
        existing.setManagingOrganizationId(updates.getManagingOrganizationId());
        existing.setReasonCode(updates.getReasonCode());
        existing.setReasonText(updates.getReasonText());
        existing.setReferralSource(updates.getReferralSource());
        existing.setPriority(updates.getPriority());
        existing.setMetadata(updates.getMetadata());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy(updatedBy);

        // Si cambió el período de inicio/fin
        if (updates.getPeriodStart() != null) {
            existing.setPeriodStart(updates.getPeriodStart());
        }
        if (updates.getPeriodEnd() != null) {
            existing.setPeriodEnd(updates.getPeriodEnd());
        }

        Episode saved = episodeRepository.save(existing);

        // Auditoría
        auditService.auditUpdate(
            existing.getEhr().getPerson().getTenant(),
            "Episode",
            saved.getId().toString(),
            updatedBy,
            beforeState,
            saved
        );

        log.info("Episode updated successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Cambiar el estado de un episodio
     */
    public Episode changeStatus(UUID id, Episode.EpisodeStatus newStatus, String changedBy) {
        log.info("Changing episode status: id={}, newStatus={}", id, newStatus);

        Episode episode = episodeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + id));

        Episode.EpisodeStatus oldStatus = episode.getStatus();
        episode.setStatus(newStatus);
        episode.setUpdatedAt(LocalDateTime.now());
        episode.setUpdatedBy(changedBy);

        // Registrar cambio en historial de estados (actualizar JSON)
        // TODO: Implementar lógica para agregar al statusHistory JSON

        Episode saved = episodeRepository.save(episode);

        // Auditoría del cambio de estado
        auditService.audit(
            NeoAuditService.AuditEventBuilder.create()
                .tenant(episode.getEhr().getPerson().getTenant())
                .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.UPDATE)
                .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
                .entityType("Episode")
                .entityId(id.toString())
                .userId(changedBy)
                .detailsJson(String.format("{\"statusChange\":{\"from\":\"%s\",\"to\":\"%s\"}}",
                    oldStatus, newStatus))
        );

        log.info("Episode status changed: id={}, {} -> {}", id, oldStatus, newStatus);
        return saved;
    }

    /**
     * Finalizar un episodio (cambiar a FINISHED)
     */
    public Episode finishEpisode(UUID id, String finishedBy) {
        log.info("Finishing episode: id={}", id);

        Episode episode = episodeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + id));

        episode.setStatus(Episode.EpisodeStatus.FINISHED);
        episode.setPeriodEnd(LocalDateTime.now());
        episode.setUpdatedAt(LocalDateTime.now());
        episode.setUpdatedBy(finishedBy);

        Episode saved = episodeRepository.save(episode);

        // Auditoría
        auditService.audit(
            NeoAuditService.AuditEventBuilder.create()
                .tenant(episode.getEhr().getPerson().getTenant())
                .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.UPDATE)
                .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
                .entityType("Episode")
                .entityId(id.toString())
                .userId(finishedBy)
                .detailsJson("{\"action\":\"finished\"}")
        );

        log.info("Episode finished successfully: id={}", id);
        return saved;
    }

    /**
     * Cancelar un episodio (cambiar a CANCELLED)
     */
    public Episode cancelEpisode(UUID id, String reason, String cancelledBy) {
        log.info("Cancelling episode: id={}, reason={}", id, reason);

        Episode episode = episodeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + id));

        episode.setStatus(Episode.EpisodeStatus.CANCELLED);
        episode.setUpdatedAt(LocalDateTime.now());
        episode.setUpdatedBy(cancelledBy);

        Episode saved = episodeRepository.save(episode);

        // Auditoría con razón de cancelación
        auditService.audit(
            NeoAuditService.AuditEventBuilder.create()
                .tenant(episode.getEhr().getPerson().getTenant())
                .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.UPDATE)
                .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
                .entityType("Episode")
                .entityId(id.toString())
                .userId(cancelledBy)
                .detailsJson(String.format("{\"action\":\"cancelled\",\"reason\":\"%s\"}", reason))
        );

        log.info("Episode cancelled successfully: id={}", id);
        return saved;
    }

    /**
     * Buscar episodio por ID
     */
    @Transactional(readOnly = true)
    public Optional<Episode> findById(UUID id) {
        log.debug("Finding episode by id: {}", id);
        return episodeRepository.findById(id);
    }

    /**
     * Buscar episodio por número
     */
    @Transactional(readOnly = true)
    public Optional<Episode> findByEpisodeNumber(String episodeNumber) {
        log.debug("Finding episode by number: {}", episodeNumber);
        return episodeRepository.findByEpisodeNumber(episodeNumber);
    }

    /**
     * Listar todos los episodios de un paciente (por EHR)
     */
    @Transactional(readOnly = true)
    public List<Episode> findByEhr(UUID ehrId) {
        log.debug("Finding episodes by EHR: {}", ehrId);
        return episodeRepository.findByEhrIdOrderByPeriodStartDesc(ehrId);
    }

    /**
     * Listar episodios activos de un paciente
     */
    @Transactional(readOnly = true)
    public List<Episode> findActiveByEhr(UUID ehrId) {
        log.debug("Finding active episodes by EHR: {}", ehrId);
        return episodeRepository.findActiveByEhrId(ehrId);
    }

    /**
     * Listar episodios por tipo
     */
    @Transactional(readOnly = true)
    public List<Episode> findByEhrAndType(UUID ehrId, Episode.EpisodeType type) {
        log.debug("Finding episodes by EHR and type: ehrId={}, type={}", ehrId, type);
        return episodeRepository.findByEhrIdAndType(ehrId, type);
    }

    /**
     * Listar episodios por estado
     */
    @Transactional(readOnly = true)
    public List<Episode> findByEhrAndStatus(UUID ehrId, Episode.EpisodeStatus status) {
        log.debug("Finding episodes by EHR and status: ehrId={}, status={}", ehrId, status);
        return episodeRepository.findByEhrIdAndStatusOrderByPeriodStartDesc(ehrId, status);
    }

    /**
     * Listar episodios en un rango de fechas
     */
    @Transactional(readOnly = true)
    public List<Episode> findByDateRange(UUID ehrId, LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Finding episodes by date range: ehrId={}, start={}, end={}", ehrId, startDate, endDate);
        return episodeRepository.findByEhrIdAndDateRange(ehrId, startDate, endDate);
    }

    /**
     * Listar episodios por cuidador
     */
    @Transactional(readOnly = true)
    public List<Episode> findByCareManager(UUID careManagerId) {
        log.debug("Finding episodes by care manager: {}", careManagerId);
        return episodeRepository.findByCareManagerIdOrderByPeriodStartDesc(careManagerId);
    }

    // ==================== Episode Resources ====================

    /**
     * Vincular un recurso FHIR a un episodio
     */
    public EpisodeResource linkResource(UUID episodeId, String resourceType, String resourceId, String linkedBy) {
        log.info("Linking resource to episode: episodeId={}, type={}, resourceId={}",
            episodeId, resourceType, resourceId);

        Episode episode = episodeRepository.findById(episodeId)
            .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + episodeId));

        // Validar que el recurso no esté ya vinculado a este episodio
        Optional<EpisodeResource> existing = episodeResourceRepository.findByEpisodeIdAndResourceTypeAndResourceId(
            episodeId, resourceType, resourceId);

        if (existing.isPresent()) {
            log.warn("Resource already linked to episode: episodeId={}, resourceType={}, resourceId={}",
                episodeId, resourceType, resourceId);
            return existing.get();
        }

        // Crear vinculación
        EpisodeResource episodeResource = new EpisodeResource();
        episodeResource.setEpisode(episode);
        episodeResource.setResourceType(resourceType);
        episodeResource.setResourceId(resourceId);
        episodeResource.setLinkedAt(LocalDateTime.now());
        episodeResource.setLinkedBy(linkedBy);

        EpisodeResource saved = episodeResourceRepository.save(episodeResource);

        // Auditoría
        auditService.audit(
            NeoAuditService.AuditEventBuilder.create()
                .tenant(episode.getEhr().getPerson().getTenant())
                .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.CREATE)
                .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
                .entityType("EpisodeResource")
                .entityId(saved.getId().toString())
                .userId(linkedBy)
                .detailsJson(String.format("{\"episodeId\":\"%s\",\"resourceType\":\"%s\",\"resourceId\":\"%s\"}",
                    episodeId, resourceType, resourceId))
        );

        log.info("Resource linked successfully: episodeResourceId={}", saved.getId());
        return saved;
    }

    /**
     * Desvincular un recurso de un episodio
     */
    public void unlinkResource(UUID episodeResourceId, String unlinkedBy) {
        log.info("Unlinking resource from episode: episodeResourceId={}", episodeResourceId);

        EpisodeResource episodeResource = episodeResourceRepository.findById(episodeResourceId)
            .orElseThrow(() -> new IllegalArgumentException("Episode resource link not found: " + episodeResourceId));

        // Auditoría antes de eliminar
        auditService.audit(
            NeoAuditService.AuditEventBuilder.create()
                .tenant(episodeResource.getEpisode().getEhr().getPerson().getTenant())
                .action(cl.hec.neo.fhir.core.model.AuditEvent.AuditAction.DELETE)
                .outcome(cl.hec.neo.fhir.core.model.AuditEvent.AuditOutcome.SUCCESS)
                .entityType("EpisodeResource")
                .entityId(episodeResourceId.toString())
                .userId(unlinkedBy)
                .detailsJson("{\"action\":\"unlinked\"}")
        );

        episodeResourceRepository.delete(episodeResource);

        log.info("Resource unlinked successfully: episodeResourceId={}", episodeResourceId);
    }

    /**
     * Listar recursos vinculados a un episodio
     */
    @Transactional(readOnly = true)
    public List<EpisodeResource> findResourcesByEpisode(UUID episodeId) {
        log.debug("Finding resources by episode: {}", episodeId);
        return episodeResourceRepository.findByEpisodeIdOrderByLinkedAtDesc(episodeId);
    }

    /**
     * Listar recursos de un tipo específico en un episodio
     */
    @Transactional(readOnly = true)
    public List<EpisodeResource> findResourcesByEpisodeAndType(UUID episodeId, String resourceType) {
        log.debug("Finding resources by episode and type: episodeId={}, type={}", episodeId, resourceType);
        return episodeResourceRepository.findByEpisodeIdAndResourceTypeOrderByLinkedAtDesc(episodeId, resourceType);
    }

    /**
     * Buscar el episodio que contiene un recurso FHIR específico
     */
    @Transactional(readOnly = true)
    public Optional<Episode> findEpisodeForResource(String resourceType, String resourceId) {
        log.debug("Finding episode for resource: type={}, id={}", resourceType, resourceId);
        return episodeResourceRepository.findEpisodeForResource(resourceType, resourceId)
            .map(EpisodeResource::getEpisode);
    }

    /**
     * Contar recursos vinculados a un episodio
     */
    @Transactional(readOnly = true)
    public long countResourcesByEpisode(UUID episodeId) {
        return episodeResourceRepository.countByEpisodeId(episodeId);
    }

    // ==================== Helper Methods ====================

    private Episode clone(Episode source) {
        Episode clone = new Episode();
        clone.setId(source.getId());
        clone.setEhr(source.getEhr());
        clone.setEpisodeNumber(source.getEpisodeNumber());
        clone.setType(source.getType());
        clone.setStatus(source.getStatus());
        clone.setStatusHistory(source.getStatusHistory());
        clone.setPeriodStart(source.getPeriodStart());
        clone.setPeriodEnd(source.getPeriodEnd());
        clone.setDiagnosisPrimary(source.getDiagnosisPrimary());
        clone.setDiagnosisPrimaryText(source.getDiagnosisPrimaryText());
        clone.setDiagnosisSecondary(source.getDiagnosisSecondary());
        clone.setCareManagerId(source.getCareManagerId());
        clone.setManagingOrganizationId(source.getManagingOrganizationId());
        clone.setReasonCode(source.getReasonCode());
        clone.setReasonText(source.getReasonText());
        clone.setReferralSource(source.getReferralSource());
        clone.setPriority(source.getPriority());
        clone.setMetadata(source.getMetadata());
        clone.setCreatedAt(source.getCreatedAt());
        clone.setUpdatedAt(source.getUpdatedAt());
        clone.setCreatedBy(source.getCreatedBy());
        clone.setUpdatedBy(source.getUpdatedBy());
        return clone;
    }
}
