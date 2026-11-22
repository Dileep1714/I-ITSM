package com.iitsm.backend.service;

import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.catalog.ServiceHealthAnalyzer;
import com.iitsm.backend.domain.catalog.ServiceHealthAnalyzer.IncidentLoad;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaStatus;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.security.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The live service map and status page: every IT service with its health, what it depends on, what it would take
 * down with it, and which of its problems come from upstream. Visible to every signed-in user (only counts are
 * shown, never incident details), so employees can see "known issues" before they report the same problem again.
 */
@Service
@Transactional(readOnly = true)
public class ServiceHealthService {

    private static final Set<IncidentStatus> OPEN = Arrays.stream(IncidentStatus.values())
            .filter(IncidentStatus::isOpen)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(IncidentStatus.class)));

    private final ItServiceRepository services;
    private final IncidentRepository incidents;
    private final CurrentUserService currentUser;
    private final Clock clock;
    private final ServiceHealthAnalyzer analyzer = new ServiceHealthAnalyzer();

    public ServiceHealthService(ItServiceRepository services, IncidentRepository incidents,
                                CurrentUserService currentUser, Clock clock) {
        this.services = services;
        this.incidents = incidents;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** One node of the map. {@code dependsOn} holds service ids, so the client can draw the edges. */
    public record ServiceHealthView(Long id, String name, String description, String criticality, String status,
                                    String health, String ownHealth, List<String> reasons, List<String> impactedBy,
                                    List<Long> dependsOn, int blastRadius, int openIncidents,
                                    PriorityLevel worstPriority, int breachedIncidents) {
    }

    public List<ServiceHealthView> healthMap() {
        currentUser.require();
        Instant now = clock.instant();
        List<ItService> all = services.findAllByOrderByName();

        Map<Long, int[]> counts = new HashMap<>();          // [open, breached]
        Map<Long, PriorityLevel> worst = new HashMap<>();
        for (Incident i : incidents.findByStatusInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(OPEN, Instant.EPOCH)) {
            Long sid = i.getService().getId();
            int[] c = counts.computeIfAbsent(sid, k -> new int[2]);
            c[0]++;
            if (i.getSla() != null && i.getSla().status(now) == SlaStatus.BREACHED) {
                c[1]++;
            }
            if (i.getPriority() != null) {
                PriorityLevel level = i.getPriority().getLevel();
                worst.merge(sid, level, (a, b) -> a.isHigherThan(b) ? a : b);
            }
        }
        Map<Long, IncidentLoad> loads = new HashMap<>();
        counts.forEach((sid, c) -> loads.put(sid, new IncidentLoad(c[0], worst.get(sid), c[1])));

        return analyzer.analyze(all, loads).stream()
                .map(h -> new ServiceHealthView(h.service().getId(), h.service().getName(),
                        h.service().getDescription(), h.service().getCriticality().name(),
                        h.service().getStatus().name(), h.health().name(), h.ownHealth().name(), h.reasons(),
                        h.impactedBy(), h.service().getDependsOn().stream().map(ItService::getId).sorted().toList(),
                        h.blastRadius(), h.load().open(), h.load().worstPriority(), h.load().breached()))
                .toList();
    }
}
