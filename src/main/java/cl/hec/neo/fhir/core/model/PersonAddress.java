package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PersonAddress Entity
 * Direcciones de pacientes con soporte para geolocalización
 */
@Entity
@Table(name = "person_address", schema = "public", indexes = {
    @Index(name = "idx_person_address_person", columnList = "person_id"),
    @Index(name = "idx_person_address_city", columnList = "city"),
    @Index(name = "idx_person_address_district", columnList = "district")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    @ToString.Exclude
    private Person person;

    @Column(name = "use", length = 20)
    @Enumerated(EnumType.STRING)
    private AddressUse use;

    @Column(name = "type", length = 20)
    @Enumerated(EnumType.STRING)
    private AddressType type;

    @Column(name = "text", length = 500)
    private String text;

    @Column(name = "line", columnDefinition = "text[]")
    @Type(StringArrayType.class)
    private String[] line;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district; // comuna en Chile

    @Column(name = "state", length = 100)
    private String state; // región en Chile

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 2)
    private String country; // ISO 3166-1 alpha-2

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @Column(name = "lat", precision = 10, scale = 8)
    private BigDecimal lat; // geolocation

    @Column(name = "lon", precision = 11, scale = 8)
    private BigDecimal lon;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AddressUse {
        HOME,
        WORK,
        TEMP,
        OLD,
        BILLING
    }

    public enum AddressType {
        POSTAL,
        PHYSICAL,
        BOTH
    }
}
