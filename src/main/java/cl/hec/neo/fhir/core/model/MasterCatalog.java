package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MasterCatalog Entity - Catálogos maestros de códigos
 * Almacena sistemas de codificación como LOINC, SNOMED-CT, ICD-10, etc.
 * Soporte multi-tenant con catálogos globales y específicos por tenant
 */
@Entity
@Table(name = "master_catalog", schema = "public",
    indexes = {
        @Index(name = "idx_master_catalog_system", columnList = "system"),
        @Index(name = "idx_master_catalog_code", columnList = "code"),
        @Index(name = "idx_master_catalog_type", columnList = "catalog_type"),
        @Index(name = "idx_master_catalog_tenant", columnList = "tenant_id"),
        @Index(name = "idx_master_catalog_active", columnList = "active")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_catalog_system_code_tenant",
            columnNames = {"system", "code", "tenant_id"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Tenant (null = catálogo global)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    /**
     * Tipo de catálogo
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "catalog_type", nullable = false, length = 50)
    private CatalogType catalogType;

    /**
     * Sistema de codificación (URI del sistema)
     * Ejemplos: "http://loinc.org", "http://snomed.info/sct", "http://hl7.org/fhir/sid/icd-10"
     */
    @Column(name = "system", nullable = false, length = 255)
    private String system;

    /**
     * Código dentro del sistema
     */
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    /**
     * Display name - nombre para mostrar
     */
    @Column(name = "display", nullable = false, length = 500)
    private String display;

    /**
     * Definición completa del código
     */
    @Column(name = "definition", columnDefinition = "text")
    private String definition;

    /**
     * Indica si el código está activo y puede ser usado
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Código padre (para jerarquías)
     */
    @Column(name = "parent_code", length = 100)
    private String parentCode;

    /**
     * Propiedades adicionales en formato JSON
     * Ejemplo: {"unit": "mg/dL", "normalRange": {"min": 70, "max": 100}}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties_json", columnDefinition = "jsonb")
    private String propertiesJson;

    /**
     * Mappings a otros sistemas de codificación
     * Ejemplo: [{"system": "http://hl7.org/fhir/sid/icd-10", "code": "E11.9", "equivalence": "equivalent"}]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mappings_json", columnDefinition = "jsonb")
    private String mappingsJson;

    /**
     * Versión del catálogo
     */
    @Column(name = "version", length = 50)
    private String version;

    /**
     * Fecha de vigencia desde
     */
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    /**
     * Fecha de vigencia hasta
     */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Tipos de catálogos soportados
     */
    public enum CatalogType {
        MEDICATION,        // Medicamentos (CENABAST, ATC)
        DIAGNOSIS,         // Diagnósticos (ICD-10, CIE-10)
        PROCEDURE,         // Procedimientos (CPT, FONASA)
        LABORATORY,        // Laboratorio (LOINC)
        OBSERVATION,       // Observaciones clínicas
        ALLERGY,          // Alergias
        IMMUNIZATION,     // Inmunizaciones
        DEVICE,           // Dispositivos médicos
        ORGANIZATION,     // Organizaciones de salud
        SPECIALTY,        // Especialidades médicas
        LOCATION,         // Ubicaciones/servicios
        GENDER,           // Género
        MARITAL_STATUS,   // Estado civil
        ETHNICITY,        // Etnia
        LANGUAGE,         // Idiomas
        COUNTRY,          // Países
        UNIT_OF_MEASURE,  // Unidades de medida (UCUM)
        OTHER             // Otros catálogos
    }

    /**
     * Helper para verificar si el código está vigente
     */
    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();
        boolean afterFrom = (effectiveFrom == null || !now.isBefore(effectiveFrom));
        boolean beforeTo = (effectiveTo == null || now.isBefore(effectiveTo));
        return active && afterFrom && beforeTo;
    }
}
