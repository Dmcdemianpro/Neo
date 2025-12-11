package cl.hec.neo.fhir.core.service;

import cl.hec.neo.fhir.core.model.Person;
import cl.hec.neo.fhir.core.model.PersonIdentifier;
import cl.hec.neo.fhir.core.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NeoPatientService - Master Patient Index (MPI) Service
 *
 * Funcionalidades:
 * - Búsqueda y matching de pacientes
 * - Deduplicación automática
 * - Gestión de identificadores múltiples
 * - Fusión de registros duplicados
 * - Scoring de matches
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NeoPatientService {

    private final PersonRepository personRepository;

    // Thresholds para matching
    private static final BigDecimal EXACT_MATCH_THRESHOLD = new BigDecimal("0.95");
    private static final BigDecimal PROBABLE_MATCH_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal POSSIBLE_MATCH_THRESHOLD = new BigDecimal("0.60");

    /**
     * Buscar paciente por ID
     */
    @Transactional(readOnly = true)
    public Optional<Person> findById(UUID id) {
        return personRepository.findById(id);
    }

    /**
     * Buscar paciente por RUN (Chile)
     */
    @Transactional(readOnly = true)
    public List<Person> findByRun(String run) {
        log.debug("Searching for patient with RUN: {}", run);
        return personRepository.findByRun(normalizeRun(run));
    }

    /**
     * Buscar candidatos para matching
     * Retorna personas que podrían ser la misma basándose en criterios de similitud
     */
    @Transactional(readOnly = true)
    public List<PersonMatchResult> findMatchCandidates(Person person) {
        log.debug("Finding match candidates for person: {}", person.getId());

        List<PersonMatchResult> results = new ArrayList<>();

        // 1. Buscar por identificadores exactos (RUN, Passport, etc.)
        List<Person> exactMatches = findByExactIdentifiers(person);
        for (Person match : exactMatches) {
            if (!match.getId().equals(person.getId())) {
                results.add(new PersonMatchResult(match, new BigDecimal("1.00"), MatchType.EXACT));
            }
        }

        // 2. Buscar por nombre y fecha de nacimiento aproximada
        if (person.getBirthDate() != null && person.getTenant() != null) {
            LocalDate birthDateFrom = person.getBirthDate().minus(1, ChronoUnit.YEARS);
            LocalDate birthDateTo = person.getBirthDate().plus(1, ChronoUnit.YEARS);

            List<Person> candidates = personRepository.findMatchCandidates(
                person.getTenant().getId(),
                birthDateFrom,
                birthDateTo
            );

            for (Person candidate : candidates) {
                if (!candidate.getId().equals(person.getId()) &&
                    results.stream().noneMatch(r -> r.getPerson().getId().equals(candidate.getId()))) {

                    BigDecimal score = calculateMatchScore(person, candidate);
                    MatchType matchType = determineMatchType(score);

                    if (score.compareTo(POSSIBLE_MATCH_THRESHOLD) >= 0) {
                        results.add(new PersonMatchResult(candidate, score, matchType));
                    }
                }
            }
        }

        // Ordenar por score descendente
        results.sort((a, b) -> b.getScore().compareTo(a.getScore()));

        log.info("Found {} match candidates for person {}", results.size(), person.getId());
        return results;
    }

    /**
     * Crear o actualizar paciente
     */
    @Transactional
    public Person save(Person person) {
        log.info("Saving person: {}", person.getId());

        // Validar datos obligatorios
        validatePerson(person);

        // Normalizar identificadores
        if (person.getIdentifiers() != null) {
            person.getIdentifiers().forEach(this::normalizeIdentifier);
        }

        Person saved = personRepository.save(person);
        log.info("Person saved successfully: {}", saved.getId());
        return saved;
    }

    /**
     * Fusionar dos registros de pacientes (merge)
     * El registro 'source' será marcado como fusionado en 'target'
     */
    @Transactional
    public Person mergePerson(UUID sourceId, UUID targetId, String mergedBy) {
        log.info("Merging person {} into {}", sourceId, targetId);

        Person source = personRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source person not found: " + sourceId));

        Person target = personRepository.findById(targetId)
            .orElseThrow(() -> new IllegalArgumentException("Target person not found: " + targetId));

        // Validar que no sean el mismo
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot merge person with itself");
        }

        // Validar que source no esté ya fusionado
        if (source.getMergedInto() != null) {
            throw new IllegalStateException("Source person is already merged");
        }

        // Marcar source como fusionado
        source.setMergedInto(target);
        source.setActive(false);
        source.setMatchScore(new BigDecimal("1.00"));

        // Guardar ambos
        personRepository.save(source);
        Person mergedTarget = personRepository.save(target);

        log.info("Person {} merged into {} successfully", sourceId, targetId);
        return mergedTarget;
    }

    /**
     * Buscar todos los pacientes fusionados en un paciente target
     */
    @Transactional(readOnly = true)
    public List<Person> findMergedPersons(UUID targetId) {
        return personRepository.findMergedInto(targetId);
    }

    /**
     * Eliminar paciente (soft delete)
     */
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting person: {}", id);
        Person person = personRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Person not found: " + id));

        personRepository.delete(person); // Soft delete por @SQLDelete
        log.info("Person deleted successfully: {}", id);
    }

    // ========== Métodos privados de apoyo ==========

    private List<Person> findByExactIdentifiers(Person person) {
        List<Person> matches = new ArrayList<>();

        if (person.getIdentifiers() != null) {
            for (PersonIdentifier identifier : person.getIdentifiers()) {
                if ("http://regcivil.cl/run".equals(identifier.getSystem())) {
                    matches.addAll(personRepository.findByRun(identifier.getValue()));
                }
            }
        }

        return matches.stream().distinct().collect(Collectors.toList());
    }

    private BigDecimal calculateMatchScore(Person p1, Person p2) {
        double score = 0.0;
        int factors = 0;

        // Score por identificadores (peso: 40%)
        double identifierScore = calculateIdentifierScore(p1, p2);
        if (identifierScore > 0) {
            score += identifierScore * 0.4;
            factors++;
        }

        // Score por fecha de nacimiento (peso: 20%)
        if (p1.getBirthDate() != null && p2.getBirthDate() != null) {
            if (p1.getBirthDate().equals(p2.getBirthDate())) {
                score += 0.2;
            } else {
                long daysDiff = Math.abs(ChronoUnit.DAYS.between(p1.getBirthDate(), p2.getBirthDate()));
                if (daysDiff <= 365) {
                    score += 0.2 * (1 - (daysDiff / 365.0));
                }
            }
            factors++;
        }

        // Score por género (peso: 10%)
        if (p1.getGender() != null && p2.getGender() != null) {
            if (p1.getGender().equals(p2.getGender())) {
                score += 0.1;
            }
            factors++;
        }

        // Score por nombres (peso: 30%)
        double nameScore = calculateNameScore(p1, p2);
        if (nameScore > 0) {
            score += nameScore * 0.3;
            factors++;
        }

        // Normalizar score
        if (factors == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(score).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private double calculateIdentifierScore(Person p1, Person p2) {
        Set<String> ids1 = p1.getIdentifiers().stream()
            .map(i -> i.getSystem() + "|" + i.getValue())
            .collect(Collectors.toSet());

        Set<String> ids2 = p2.getIdentifiers().stream()
            .map(i -> i.getSystem() + "|" + i.getValue())
            .collect(Collectors.toSet());

        Set<String> intersection = new HashSet<>(ids1);
        intersection.retainAll(ids2);

        if (intersection.isEmpty()) {
            return 0.0;
        }

        return 1.0; // Match exacto en identificadores
    }

    private double calculateNameScore(Person p1, Person p2) {
        // Simplificado - en producción usar algoritmo Jaro-Winkler o Levenshtein
        // Aquí solo comparamos strings directamente

        // TODO: Implementar algoritmo sofisticado de matching de nombres
        // Por ahora, retornamos score básico
        return 0.0;
    }

    private MatchType determineMatchType(BigDecimal score) {
        if (score.compareTo(EXACT_MATCH_THRESHOLD) >= 0) {
            return MatchType.EXACT;
        } else if (score.compareTo(PROBABLE_MATCH_THRESHOLD) >= 0) {
            return MatchType.PROBABLE;
        } else if (score.compareTo(POSSIBLE_MATCH_THRESHOLD) >= 0) {
            return MatchType.POSSIBLE;
        } else {
            return MatchType.NO_MATCH;
        }
    }

    private void validatePerson(Person person) {
        if (person.getTenant() == null) {
            throw new IllegalArgumentException("Tenant is required");
        }

        if (person.getIdentifiers() == null || person.getIdentifiers().isEmpty()) {
            throw new IllegalArgumentException("At least one identifier is required");
        }
    }

    private void normalizeIdentifier(PersonIdentifier identifier) {
        if ("http://regcivil.cl/run".equals(identifier.getSystem())) {
            identifier.setValue(normalizeRun(identifier.getValue()));
        }
    }

    private String normalizeRun(String run) {
        if (run == null) return null;
        // Remover puntos y guiones: 12.345.678-9 -> 123456789
        return run.replaceAll("[.\\-]", "").toUpperCase();
    }

    // ========== Clases de resultado ==========

    public static class PersonMatchResult {
        private final Person person;
        private final BigDecimal score;
        private final MatchType matchType;

        public PersonMatchResult(Person person, BigDecimal score, MatchType matchType) {
            this.person = person;
            this.score = score;
            this.matchType = matchType;
        }

        public Person getPerson() { return person; }
        public BigDecimal getScore() { return score; }
        public MatchType getMatchType() { return matchType; }
    }

    public enum MatchType {
        EXACT,      // >= 95%
        PROBABLE,   // >= 80%
        POSSIBLE,   // >= 60%
        NO_MATCH    // < 60%
    }
}
