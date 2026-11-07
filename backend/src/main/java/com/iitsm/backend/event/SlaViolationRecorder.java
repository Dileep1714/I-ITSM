package com.iitsm.backend.event;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.sla.SlaTracker;
import com.iitsm.backend.domain.sla.SlaViolation;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.SlaViolationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * <b>Observer:</b> keeps a permanent record of every missed SLA deadline (the spec's "record SLA violations").
 * Reacts only to {@code SLA_BREACHED} events, and is idempotent: the same violation is never recorded twice.
 */
@Component
public class SlaViolationRecorder {

    private final IncidentRepository incidents;
    private final SlaViolationRepository violations;

    public SlaViolationRecorder(IncidentRepository incidents, SlaViolationRepository violations) {
        this.incidents = incidents;
        this.violations = violations;
    }

    @EventListener
    public void on(IncidentEvent event) {
        if (event.type() != IncidentEventType.SLA_BREACHED) {
            return;
        }
        Incident incident = incidents.findById(event.incidentId()).orElse(null);
        if (incident == null) {
            return;
        }
        SlaTracker sla = incident.getSla();
        Instant at = event.occurredAt();
        if (sla.isResponseBreached(at)) {
            recordOnce(incident, SlaViolation.Kind.RESPONSE, sla.responseDue(), at);
        }
        if (sla.isResolutionBreached(at)) {
            recordOnce(incident, SlaViolation.Kind.RESOLUTION, sla.resolutionDue(at), at);
        }
    }

    private void recordOnce(Incident incident, SlaViolation.Kind kind, Instant deadline, Instant detectedAt) {
        if (!violations.existsByIncidentAndKind(incident, kind)) {
            violations.save(new SlaViolation(incident, kind, deadline, detectedAt));
        }
    }
}
