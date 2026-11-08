package com.iitsm.backend.domain.intelligence;

import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Incident;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Intelligent feature: incident correlation (common root cause).</b>
 *
 * <p>When several open incidents arrive close together on services that all depend on one service, that service
 * is the likely root cause: if Payment and Email both fail and both depend on Customer Database, check the
 * database first. This uses the service dependency graph ({@link ItService#getDependsOn()}).
 *
 * <p>Algorithm: every incident "touches" its own service and everything that service depends on, directly or
 * through a chain. A service touched by at least {@value #MIN_INCIDENTS} incidents is a candidate root. Candidates
 * are taken largest group first; a candidate whose incidents are all already explained by an earlier one is
 * skipped, and among equally large groups the most fundamental service (the one others depend on) wins.
 */
public final class IncidentCorrelator {

    public static final int MIN_INCIDENTS = 3;

    public record Correlation(ItService rootService, List<Incident> incidents, Set<String> affectedServices,
                              String explanation) {
    }

    /** @param openIncidents recent open incidents (the caller chooses the time window) */
    public List<Correlation> correlate(Collection<Incident> openIncidents) {
        Map<ItService, List<Incident>> byRoot = new LinkedHashMap<>();
        for (Incident incident : openIncidents) {
            for (ItService touched : selfAndDependencies(incident.getService())) {
                byRoot.computeIfAbsent(touched, s -> new ArrayList<>()).add(incident);
            }
        }

        List<Map.Entry<ItService, List<Incident>>> candidates = byRoot.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= MIN_INCIDENTS)
                .filter(entry -> isPlausibleRoot(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt((Map.Entry<ItService, List<Incident>> e) -> e.getValue().size())
                        .reversed()
                        .thenComparing(e -> -dependentsAmong(e.getKey(), byRoot.keySet())))
                .toList();

        List<Correlation> result = new ArrayList<>();
        Set<Incident> explained = new HashSet<>();
        for (Map.Entry<ItService, List<Incident>> candidate : candidates) {
            if (explained.containsAll(candidate.getValue())) {
                continue;
            }
            explained.addAll(candidate.getValue());
            result.add(describe(candidate.getKey(), candidate.getValue()));
        }
        return result;
    }

    private static Correlation describe(ItService root, List<Incident> incidents) {
        Set<String> services = new LinkedHashSet<>();
        incidents.stream().map(i -> i.getService().getName()).sorted().forEach(services::add);
        String explanation;
        if (services.size() == 1 && services.contains(root.getName())) {
            explanation = incidents.size() + " open incidents on " + root.getName()
                    + ": possibly one underlying outage. Consider handling them as one major incident.";
        } else {
            explanation = incidents.size() + " open incidents on " + String.join(", ", services)
                    + (services.contains(root.getName()) ? ", which involve " : ", which all depend on ")
                    + root.getName() + ". Check " + root.getName() + " first: it is the likely common cause.";
        }
        List<Incident> sorted = incidents.stream().sorted(Comparator.comparing(Incident::getCreatedAt)).toList();
        return new Correlation(root, sorted, services, explanation);
    }

    /**
     * Blaming a dependency needs evidence from more than one service: three incidents that are all on Payment point
     * at Payment itself, not at the database Payment happens to use.
     */
    private static boolean isPlausibleRoot(ItService root, List<Incident> incidents) {
        Set<ItService> services = new HashSet<>();
        incidents.forEach(incident -> services.add(incident.getService()));
        return services.size() > 1 || services.contains(root);
    }

    /** The service itself plus everything it depends on, directly or through a chain. */
    static Set<ItService> selfAndDependencies(ItService service) {
        Set<ItService> result = new LinkedHashSet<>();
        Deque<ItService> toVisit = new ArrayDeque<>();
        toVisit.push(service);
        while (!toVisit.isEmpty()) {
            ItService current = toVisit.pop();
            if (result.add(current)) {
                toVisit.addAll(current.getDependsOn());
            }
        }
        return result;
    }

    /** How many of the given services depend on {@code service}: a higher number means more fundamental. */
    private static long dependentsAmong(ItService service, Collection<ItService> services) {
        return services.stream().filter(other -> !other.equals(service) && other.dependsOnTransitively(service)).count();
    }
}
