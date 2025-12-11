package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tenant Entity - Multi-tenancy support
 * Representa una organización/hospital independiente en la plataforma NEO
 */
@Entity
@Table(name = "tenant", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", length = 50)
    @Enumerated(EnumType.STRING)
    private TenantType type;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "config_json", columnDefinition = "jsonb")
    private String configJson;

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

    public enum TenantType {
        HOSPITAL,
        CLINIC,
        LAB,
        PHARMACY,
        OTHER
    }

    public enum TenantStatus {
        ACTIVE,
        SUSPENDED,
        INACTIVE
    }
}
