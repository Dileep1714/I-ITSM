package com.iitsm.backend.domain.catalog;

import com.iitsm.backend.domain.incident.PriorityLevel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service dependency graph with live health: which services are in trouble, and which others are affected
 * because they depend on them.
 *
 * <p><b>Own health</b> combines the status an administrator set with what open incidents say: an open P1 means an
 * outage, an open P2 or {@value #DEGRADED_FROM_OPEN} open incidents mean degraded service. The worse of the two
 * wins, so a stale "operational" status never hides a live outage.
 *
 * <p><b>Impact propagation:</b> a service that depends, directly or transitively, on an unhealthy service is
 * IMPACTED (unless its own health is already worse), and the upstream causes are named. The <b>blast radius</b> of
 * a service is how many services depend on it, which tells administrators what fails if it fails.
 *
 * <p>Pure Java over plain values, so it is unit-testable without a database.
 */
public class ServiceHealthAnalyzer {

    /** This many open incidents on one service count as degraded service even without a P1/P2. */
    static final int DEGRADED_FROM_OPEN = 3;

    /** Health on the map, worst last (the ordinal is the severity). */
    public enum Health { OPERATIONAL, MAINTENANCE, IMPACTED, DEGRADED, OUTAGE }

    /** What is known about one service's open incidents. */
    public record IncidentLoad(int open, PriorityLevel worstPriority, int breached) {
        public static final IncidentLoad NONE = new IncidentLoad(0, null, 0);
    }

    /** One node of the analysed graph. */
    public record ServiceHealth(ItService service, Health health, Health ownHealth, List<String> reasons,
                                List<String> impactedBy, int blastRadius, IncidentLoad load) {
    }

    public List<ServiceHealth> analyze(Collection<ItService> services, Map<Long, IncidentLoad> loadByServiceId) {
        Map<ItService, Health> own = new HashMap<>();
        Map<ItService, List<String>> reasons = new HashMap<>();
        for (ItService s : services) {
            IncidentLoad load = loadByServiceId.getOrDefault(s.getId(), IncidentLoad.NONE);
            List<String> why = new ArrayList<>();
            Health h = fromStatus(s.getStatus(), why);
            Health fromIncidents = fromIncidents(load, why);
            own.put(s, worse(h, fromIncidents));
            reasons.put(s, why);
        }

        List<ServiceHealth> result = new ArrayList<>();
        for (ItService s : services) {
            // Upstream: everything this service needs, directly or transitively, that is unhealthy.
            Set<String> causes = new LinkedHashSet<>();
            for (ItService dep : transitiveDependencies(s)) {
                Health h = own.getOrDefault(dep, Health.OPERATIONAL);
                if (h == Health.DEGRADED || h == Health.OUTAGE) {
                    causes.add(dep.getName());
                }
            }
            Health ownHealth = own.get(s);
            Health effective = !causes.isEmpty() && ownHealth.ordinal() < Health.IMPACTED.ordinal()
                    ? Health.IMPACTED : ownHealth;
            List<String> why = new ArrayList<>(reasons.get(s));
            if (effective == Health.IMPACTED) {
                why.add("Depends on " + String.join(", ", causes) + ", which " + (causes.size() == 1 ? "is" : "are")
                        + " having problems");
            }
            int blastRadius = (int) services.stream().filter(other -> other != s && other.dependsOnTransitively(s)).count();
            result.add(new ServiceHealth(s, effective, ownHealth, why, List.copyOf(causes), blastRadius,
                    loadByServiceId.getOrDefault(s.getId(), IncidentLoad.NONE)));
        }
        result.sort(Comparator.comparing((ServiceHealth h) -> h.health().ordinal()).reversed()
                .thenComparing(h -> h.service().getName()));
        return result;
    }

    private static Health fromStatus(ServiceStatus status, List<String> why) {
        if (status == null) {
            return Health.OPERATIONAL;
        }
        return switch (status) {
            case OPERATIONAL -> Health.OPERATIONAL;
            case MAINTENANCE -> {
                why.add("Scheduled maintenance");
                yield Health.MAINTENANCE;
            }
            case DEGRADED, PARTIAL_OUTAGE -> {
                why.add("Marked " + (status == ServiceStatus.DEGRADED ? "degraded" : "partial outage") + " by IT");
                yield Health.DEGRADED;
            }
            case MAJOR_OUTAGE -> {
                why.add("Marked major outage by IT");
                yield Health.OUTAGE;
            }
        };
    }

    private static Health fromIncidents(IncidentLoad load, List<String> why) {
        if (load.open() == 0) {
            return Health.OPERATIONAL;
        }
        String count = load.open() + " open " + (load.open() == 1 ? "incident" : "incidents");
        if (load.worstPriority() == PriorityLevel.P1) {
            why.add(count + ", including a P1");
            return Health.OUTAGE;
        }
        if (load.worstPriority() == PriorityLevel.P2 || load.open() >= DEGRADED_FROM_OPEN) {
            why.add(count + (load.worstPriority() == PriorityLevel.P2 ? ", including a P2" : ""));
            return Health.DEGRADED;
        }
        why.add(count);
        return Health.OPERATIONAL;
    }

    private static Health worse(Health a, Health b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static Set<ItService> transitiveDependencies(ItService start) {
        Set<ItService> seen = new HashSet<>();
        List<ItService> stack = new ArrayList<>(start.getDependsOn());
        while (!stack.isEmpty()) {
            ItService next = stack.removeLast();
            if (seen.add(next)) {
                stack.addAll(next.getDependsOn());
            }
        }
        return seen;
    }
}
