package com.iitsm.backend.repository;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.sla.SlaStatus;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.service.IncidentSearchCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the incident search query from {@link IncidentSearchCriteria} with JPA Specifications: each filter is one
 * small predicate, and only the filters that were given are combined (AND). New filters are added here without
 * touching the controller or repository.
 *
 * <h2>SLA status</h2>
 * The SLA status shown on an incident is calculated from the clock ({@code SlaTracker.status(now)}), which a
 * database query cannot do exactly. The search instead uses the warning and breach flags that the SLA monitor
 * records (it runs every minute, and every incident update also records breaches immediately), so an SLA filter
 * reflects the state as of the last check and may lag by up to one monitor interval.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    public static Specification<Incident> matching(IncidentSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.q() != null) {
                String pattern = "%" + escapeLike(c.q().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("number")), pattern, '\\'),
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            if (!c.status().isEmpty()) {
                predicates.add(root.get("status").in(c.status()));
            }
            if (!c.priority().isEmpty()) {
                predicates.add(root.get("priority").get("level").in(c.priority()));
            }
            if (!c.sla().isEmpty()) {
                predicates.add(cb.or(c.sla().stream().map(s -> slaStatus(root, cb, s)).toArray(Predicate[]::new)));
            }
            if (c.serviceId() != null) {
                predicates.add(cb.equal(root.get("service").get("id"), c.serviceId()));
            }
            if (c.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), c.categoryId()));
            }
            if (c.assigneeId() != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), c.assigneeId()));
            }
            if (Boolean.TRUE.equals(c.unassigned())) {
                predicates.add(cb.isNull(root.get("assignee")));
            }
            if (c.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.createdFrom()));
            }
            if (c.createdTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), c.createdTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Incident> reportedBy(Employee reporter) {
        return (root, query, cb) -> cb.equal(root.get("reporter"), reporter);
    }

    public static Specification<Incident> assignedTo(Technician technician) {
        return (root, query, cb) -> cb.equal(root.get("assignee"), technician);
    }

    /** Mirrors the order of checks in {@code SlaTracker.status(now)}, using the recorded flags. */
    private static Predicate slaStatus(Root<Incident> root, CriteriaBuilder cb, SlaStatus status) {
        Path<Object> sla = root.get("sla");
        Predicate started = cb.isNotNull(sla.get("startedAt"));
        Predicate breached = cb.or(cb.isTrue(sla.get("responseBreachRecorded")),
                cb.isTrue(sla.get("resolutionBreachRecorded")));
        Predicate notBreached = cb.not(breached);
        Predicate resolved = cb.isNotNull(sla.get("resolvedAt"));
        Predicate paused = cb.isNotNull(sla.get("pausedSince"));
        Predicate running = cb.and(started, notBreached, cb.not(resolved), cb.not(paused));
        Predicate warned = cb.or(
                cb.and(cb.isTrue(sla.get("responseWarned")), cb.isNull(sla.get("respondedAt"))),
                cb.isTrue(sla.get("resolutionWarned")));

        return switch (status) {
            case NOT_STARTED -> cb.not(started);
            case BREACHED -> cb.and(started, breached);
            case MET -> cb.and(started, notBreached, resolved);
            case PAUSED -> cb.and(started, notBreached, cb.not(resolved), paused);
            case AT_RISK -> cb.and(running, warned);
            case ON_TRACK -> cb.and(running, cb.not(warned));
        };
    }

    /** Treat the user's text literally: % and _ are wildcards in SQL LIKE. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
