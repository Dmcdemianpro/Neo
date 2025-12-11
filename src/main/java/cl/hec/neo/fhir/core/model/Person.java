package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Person Entity - Master Patient Index (MPI)
 * Entidad central de pacientes con soporte para deduplicación y multi-tenancy
 */
@Entity
@Table(name = "person", schema = "public", indexes = {
    @Index(name = "idx_person_tenant", columnList = "tenant_id"),
    @Index(name = "idx_person_birth_date", columnList = "birth_date"),
    @Index(name = "idx_person_active", columnList = "active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE person SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gender", length = 20)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "deceased_boolean")
    @Builder.Default
    private Boolean deceasedBoolean = false;

    @Column(name = "deceased_datetime")
    private LocalDateTime deceasedDatetime;

    @Column(name = "marital_status", length = 50)
    private String maritalStatus;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    private Person mergedInto;

    @Column(name = "match_score", precision = 3, scale = 2)
    private BigDecimal matchScore;

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

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Relationships
    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PersonIdentifier> identifiers = new ArrayList<>();

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PersonName> names = new ArrayList<>();

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PersonTelecom> telecoms = new ArrayList<>();

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PersonAddress> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PersonContact> contacts = new ArrayList<>();

    public enum Gender {
        MALE,
        FEMALE,
        OTHER,
        UNKNOWN
    }

    // Helper methods
    public void addIdentifier(PersonIdentifier identifier) {
        identifiers.add(identifier);
        identifier.setPerson(this);
    }

    public void addName(PersonName name) {
        names.add(name);
        name.setPerson(this);
    }

    public void addTelecom(PersonTelecom telecom) {
        telecoms.add(telecom);
        telecom.setPerson(this);
    }

    public void addAddress(PersonAddress address) {
        addresses.add(address);
        address.setPerson(this);
    }

    public void addContact(PersonContact contact) {
        contacts.add(contact);
        contact.setPerson(this);
    }
}
