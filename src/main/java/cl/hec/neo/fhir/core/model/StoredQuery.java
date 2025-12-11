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
 * StoredQuery Entity - Almacena queries FHIR predefinidas
 * Permite reutilizar búsquedas complejas y parámetros predefinidos
 */
@Entity
@Table(name = "stored_query", schema = "public",
    indexes = {
        @Index(name = "idx_stored_query_tenant", columnList = "tenant_id"),
        @Index(name = "idx_stored_query_name", columnList = "name"),
        @Index(name = "idx_stored_query_resource_type", columnList = "resource_type")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    /**
     * Query FHIR completo (e.g., "Patient?name=Juan&birthdate=gt2000-01-01")
     */
    @Column(name = "query_string", nullable = false, columnDefinition = "text")
    private String queryString;

    /**
     * Parámetros del query en formato JSON
     * Ejemplo: {"name": "string", "birthdate": "date", "active": "boolean"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json", columnDefinition = "jsonb")
    private String parametersJson;

    /**
     * Indica si el query es público (disponible para todos los usuarios del tenant)
     */
    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    /**
     * Usuario creador del query
     */
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Tags para categorización y búsqueda
     * Ejemplo: ["laboratorio", "urgencia", "pediatría"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private String tags;

    /**
     * Contador de veces que se ha ejecutado este query
     */
    @Column(name = "usage_count")
    @Builder.Default
    private Long usageCount = 0L;

    /**
     * Última fecha de ejecución
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
