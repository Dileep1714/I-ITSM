package com.iitsm.backend.service;

import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaStatus;

import java.time.Instant;
import java.util.Set;

/**
 * Filters for the incident search. Every field is optional; the filters that are given are combined with AND,
 * and multi-value filters (status, priority, SLA) match any of their values.
 *
 * <p>Bound from query parameters, e.g.
 * {@code /api/incidents?scope=ALL&status=NEW,ASSIGNED&priority=P1&q=vpn&createdFrom=2026-11-01T00:00:00Z}.
 *
 * @param q           text contained in the incident number, title or description (case-insensitive)
 * @param status      lifecycle states to include
 * @param priority    priority levels to include (incidents without a priority yet never match)
 * @param sla         SLA states to include, as of the last SLA monitor check (see {@code IncidentSpecifications})
 * @param serviceId   affected service
 * @param categoryId  category
 * @param assigneeId  assigned technician
 * @param unassigned  {@code true}: only incidents nobody is assigned to
 * @param createdFrom created at or after this instant
 * @param createdTo   created before this instant
 * @param scope       {@code MINE} (default): what I reported, or what is assigned to me as a technician;
 *                    {@code ALL}: every incident I am allowed to see (employees always see only their own)
 */
public record IncidentSearchCriteria(String q,
                                     Set<IncidentStatus> status,
                                     Set<PriorityLevel> priority,
                                     Set<SlaStatus> sla,
                                     Long serviceId,
                                     Long categoryId,
                                     Long assigneeId,
                                     Boolean unassigned,
                                     Instant createdFrom,
                                     Instant createdTo,
                                     Scope scope) {

    public static final int MAX_QUERY_LENGTH = 200;

    public enum Scope {
        MINE,
        ALL
    }

    public IncidentSearchCriteria {
        q = q == null || q.isBlank() ? null : q.strip();
        status = status == null ? Set.of() : Set.copyOf(status);
        priority = priority == null ? Set.of() : Set.copyOf(priority);
        sla = sla == null ? Set.of() : Set.copyOf(sla);
        scope = scope == null ? Scope.MINE : scope;
    }

    /**
     * Checks combinations of filters. Kept out of the constructor because Spring creates this record while binding
     * request parameters, and would wrap an exception thrown there instead of letting it reach the error handler.
     */
    public void validate() {
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw new InvalidInputException("Search text must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw new InvalidInputException("createdFrom must be before createdTo");
        }
        if (Boolean.TRUE.equals(unassigned) && assigneeId != null) {
            throw new InvalidInputException("Use either assigneeId or unassigned=true, not both");
        }
    }
}
