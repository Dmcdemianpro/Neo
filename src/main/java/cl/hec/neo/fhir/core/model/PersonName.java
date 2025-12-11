package cl.hec.neo.fhir.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PersonName Entity
 * Nombres de pacientes (múltiples nombres por persona, histórico)
 */
@Entity
@Table(name = "person_name", schema = "public", indexes = {
    @Index(name = "idx_person_name_person", columnList = "person_id"),
    @Index(name = "idx_person_name_family", columnList = "family")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonName {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    @ToString.Exclude
    private Person person;

    @Column(name = "use", length = 20)
    @Enumerated(EnumType.STRING)
    private NameUse use;

    @Column(name = "text", length = 500)
    private String text;

    @Column(name = "family")
    private String family;

    @Column(name = "given", columnDefinition = "text[]")
    @Type(StringArrayType.class)
    private String[] given;

    @Column(name = "prefix", columnDefinition = "text[]")
    @Type(StringArrayType.class)
    private String[] prefix;

    @Column(name = "suffix", columnDefinition = "text[]")
    @Type(StringArrayType.class)
    private String[] suffix;

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum NameUse {
        OFFICIAL,
        USUAL,
        NICKNAME,
        MAIDEN,
        OLD
    }
}
