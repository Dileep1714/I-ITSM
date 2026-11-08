package com.iitsm.backend.domain.intelligence;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Incident;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * <b>Intelligent feature: duplicate and related incident detection.</b>
 *
 * <p>Score = TF-IDF cosine similarity of title + description, plus a bonus when the services match
 * ({@value #SAME_SERVICE_BONUS}) or are linked in the service dependency graph ({@value #RELATED_SERVICE_BONUS}),
 * capped at 1.0. Then:
 * <ul>
 *   <li>score ≥ {@value #DUPLICATE_THRESHOLD} and the other incident is still open → <b>likely duplicate</b>
 *       (support staff can link and cancel it in one click);</li>
 *   <li>score ≥ {@value #RELATED_THRESHOLD} → <b>similar</b>; if that incident was resolved, its resolution is
 *       offered as a suggested fix ("this worked last time").</li>
 * </ul>
 */
public final class RelatedIncidentFinder {

    static final double SAME_SERVICE_BONUS = 0.10;
    static final double RELATED_SERVICE_BONUS = 0.05;
    public static final double DUPLICATE_THRESHOLD = 0.55;
    public static final double RELATED_THRESHOLD = 0.25;
    static final int MAX_RESULTS = 8;

    public enum Relation {
        LIKELY_DUPLICATE,
        SIMILAR
    }

    /** What is being compared: an existing incident, or a draft that has not been saved yet. */
    public record Probe(Long excludeId, String title, String description, ItService service) {

        public Probe {
            Validate.requireNonNull(title, "Title");
            description = description == null ? "" : description;
        }

        public static Probe of(Incident incident) {
            return new Probe(incident.getId(), incident.getTitle(), incident.getDescription(), incident.getService());
        }

        String text() {
            return title + "\n" + description;
        }
    }

    public record Match(Incident incident, double score, Relation relation, List<String> reasons) {
    }

    public List<Match> find(Probe probe, Collection<Incident> candidates) {
        List<Incident> others = candidates.stream()
                .filter(candidate -> probe.excludeId() == null || !Objects.equals(candidate.getId(), probe.excludeId()))
                .toList();
        List<Match> matches = new ArrayList<>();
        for (TextSimilarity.Scored<Incident> scored : TextSimilarity.rank(probe.text(), others, RelatedIncidentFinder::text)) {
            Incident other = scored.document();
            List<String> reasons = new ArrayList<>();
            reasons.add(Math.round(scored.score() * 100) + "% text match"
                    + (scored.sharedTerms().isEmpty() ? "" : " (" + String.join(", ", scored.sharedTerms()) + ")"));
            double score = scored.score();
            if (probe.service() != null && probe.service().equals(other.getService())) {
                score += SAME_SERVICE_BONUS;
                reasons.add("Same service: " + other.getService().getName());
            } else if (probe.service() != null && servicesLinked(probe.service(), other.getService())) {
                score += RELATED_SERVICE_BONUS;
                reasons.add("Linked services: " + probe.service().getName() + " ↔ " + other.getService().getName());
            }
            score = Math.min(1.0, score);
            if (score < RELATED_THRESHOLD) {
                continue;
            }
            Relation relation = score >= DUPLICATE_THRESHOLD && other.isOpen()
                    ? Relation.LIKELY_DUPLICATE : Relation.SIMILAR;
            matches.add(new Match(other, Math.round(score * 100) / 100.0, relation, reasons));
        }
        matches.sort(Comparator.comparing(Match::relation).thenComparing(Comparator.comparingDouble(Match::score).reversed()));
        return matches.stream().limit(MAX_RESULTS).toList();
    }

    private static String text(Incident incident) {
        return incident.getTitle() + "\n" + incident.getDescription();
    }

    /** One depends on the other (directly or through a chain), or both depend on a common service. */
    static boolean servicesLinked(ItService a, ItService b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.dependsOnTransitively(b) || b.dependsOnTransitively(a)) {
            return true;
        }
        return a.getDependsOn().stream().anyMatch(dependency -> b.getDependsOn().contains(dependency));
    }
}
