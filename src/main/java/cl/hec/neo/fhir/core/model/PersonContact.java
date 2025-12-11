package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PersonContact Entity
 * Contactos de emergencia y familiares de pacientes
 */
@Entity
@Table(name = "person_contact", schema = "public", indexes = {
    @Index(name = "idx_person_contact_person", columnList = "person_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    @ToString.Exclude
    private Person person;

    @Column(name = "relationship_code", length = 50)
    private String relationshipCode; // 'emergency', 'family', 'guardian', etc.

    @Column(name = "relationship_text")
    private String relationshipText;

    @Column(name = "name_text", length = 500)
    private String nameText;

    @Column(name = "name_family")
    private String nameFamily;

    @Column(name = "name_given", columnDefinition = "text[]")
    @Type(StringArrayType.class)
    private String[] nameGiven;

    @Column(name = "telecom_system", length = 20)
    private String telecomSystem;

    @Column(name = "telecom_value")
    private String telecomValue;

    @Column(name = "address_text", length = 500)
    private String addressText;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "organization")
    private String organization;

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
