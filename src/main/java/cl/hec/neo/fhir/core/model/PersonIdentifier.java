package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PersonIdentifier Entity
 * Identificadores de pacientes (RUN, Pasaporte, FONASA, IDs locales)
 */
@Entity
@Table(name = "person_identifier", schema = "public",
    uniqueConstraints = @UniqueConstraint(columnNames = {"system", "value", "person_id"}),
    indexes = {
        @Index(name = "idx_person_id_person", columnList = "person_id"),
        @Index(name = "idx_person_id_system_value", columnList = "system, value"),
        @Index(name = "idx_person_id_value", columnList = "value"),
        @Index(name = "idx_person_id_type", columnList = "type_code")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    @ToString.Exclude
    private Person person;

    @Column(name = "system", nullable = false)
    private String system;

    @Column(name = "value", nullable = false, length = 100)
    private String value;

    @Column(name = "use", length = 20)
    @Enumerated(EnumType.STRING)
    private IdentifierUse use;

    @Column(name = "type_code", length = 50)
    private String typeCode;

    @Column(name = "type_text", length = 100)
    private String typeText;

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @Column(name = "assigner")
    private String assigner;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum IdentifierUse {
        OFFICIAL,
        TEMP,
        SECONDARY,
        OLD
    }
}
