package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ehr Entity - Electronic Health Record
 * Representa el EHR (Expediente Clínico Electrónico) de un paciente
 * Cada persona puede tener un EHR por tenant
 */
@Entity
@Table(name = "ehr", schema = "public",
    indexes = {
        @Index(name = "idx_ehr_person", columnList = "person_id"),
        @Index(name = "idx_ehr_tenant", columnList = "tenant_id"),
        @Index(name = "idx_ehr_ehr_id", columnList = "ehr_id", unique = true)
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ehr {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    @ToString.Exclude
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    @Column(name = "ehr_id", nullable = false, unique = true, length = 100)
    private String ehrId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private EhrStatus status = EhrStatus.ACTIVE;

    @Column(name = "time_created", nullable = false)
    @Builder.Default
    private LocalDateTime timeCreated = LocalDateTime.now();

    @Column(name = "system_id", length = 100)
    private String systemId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Estado del EHR
     */
    public enum EhrStatus {
        ACTIVE,      // EHR activo
        INACTIVE,    // EHR inactivo (paciente dado de baja)
        MERGED,      // EHR fusionado con otro (duplicado)
        ARCHIVED     // EHR archivado
    }
}
