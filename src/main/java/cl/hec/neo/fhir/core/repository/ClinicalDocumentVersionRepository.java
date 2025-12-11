package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.ClinicalDocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para ClinicalDocumentVersion (Versiones de Documentos)
 */
@Repository
public interface ClinicalDocumentVersionRepository extends JpaRepository<ClinicalDocumentVersion, UUID> {

    /**
     * Buscar versiones por documento
     */
    List<ClinicalDocumentVersion> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    /**
     * Buscar versión específica de un documento
     */
    Optional<ClinicalDocumentVersion> findByDocumentIdAndVersionNumber(UUID documentId, Integer versionNumber);

    /**
     * Obtener última versión de un documento
     */
    @Query("SELECT cdv FROM ClinicalDocumentVersion cdv WHERE cdv.document.id = :documentId ORDER BY cdv.versionNumber DESC LIMIT 1")
    Optional<ClinicalDocumentVersion> findLatestVersion(@Param("documentId") UUID documentId);

    /**
     * Contar versiones de un documento
     */
    long countByDocumentId(UUID documentId);

    /**
     * Buscar versiones creadas por un usuario
     */
    List<ClinicalDocumentVersion> findByVersionedByOrderByVersionedAtDesc(String versionedBy);
}
