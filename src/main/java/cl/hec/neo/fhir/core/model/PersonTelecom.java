package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PersonTelecom Entity
 * Contactos telefónicos y electrónicos de pacientes
 */
@Entity
@Table(name = "person_telecom", schema = "public", indexes = {
    @Index(name = "idx_person_telecom_person", columnList = "person_id"),
    @Index(name = "idx_person_telecom_value", columnList = "value")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonTelecom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    @ToString.Exclude
    private Person person;

    @Column(name = "system", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TelecomSystem system;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "use", length = 20)
    @Enumerated(EnumType.STRING)
    private TelecomUse use;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TelecomSystem {
        PHONE,
        EMAIL,
        FAX,
        URL,
        SMS
    }

    public enum TelecomUse {
        HOME,
        WORK,
        MOBILE,
        TEMP,
        OLD
    }
}
