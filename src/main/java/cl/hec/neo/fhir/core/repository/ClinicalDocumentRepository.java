package cl.hec.neo.fhir.core.repository;

import cl.hec.neo.fhir.core.model.ClinicalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para ClinicalDocument (Documentos Clínicos)
 */
@Repository
public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocument, UUID> {

    /**
     * Buscar por FHIR Composition ID
     */
    Optional<ClinicalDocument> findByFhirCompositionId(String fhirCompositionId);

    /**
     * Buscar documentos por EHR
     */
    List<ClinicalDocument> findByEhrIdOrderByDateCreatedDesc(UUID ehrId);

    /**
     * Buscar documentos por tipo y EHR
     */
    List<ClinicalDocument> findByEhrIdAndDocumentTypeOrderByDateCreatedDesc(UUID ehrId, ClinicalDocument.DocumentType documentType);

    /**
     * Buscar documentos finales por EHR
     */
    @Query("SELECT cd FROM ClinicalDocument cd WHERE cd.ehr.id = :ehrId AND cd.status = 'FINAL' ORDER BY cd.dateCreated DESC")
    List<ClinicalDocument> findFinalDocumentsByEhrId(@Param("ehrId") UUID ehrId);

    /**
     * Buscar documentos por autor
     */
    List<ClinicalDocument> findByAuthorIdOrderByDateCreatedDesc(String authorId);

    /**
     * Buscar documentos por nivel de confidencialidad
     */
    @Query("SELECT cd FROM ClinicalDocument cd WHERE cd.ehr.id = :ehrId AND cd.confidentiality = :confidentiality ORDER BY cd.dateCreated DESC")
    List<ClinicalDocument> findByEhrIdAndConfidentiality(@Param("ehrId") UUID ehrId, @Param("confidentiality") ClinicalDocument.Confidentiality confidentiality);
}
