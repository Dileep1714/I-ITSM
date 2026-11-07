package com.iitsm.backend.event;

import com.iitsm.backend.domain.audit.AuditEntry;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentChange;
import com.iitsm.backend.domain.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * <b>Observer pattern, the subject side:</b> turns the changes an incident recorded into events and announces
 * them. The publisher does not know who listens; listeners register themselves with {@code @EventListener}.
 * Adding a new reaction (e.g. a Slack integration) means adding a listener, with no change here or in the
 * incident code.
 */
@Component
public class IncidentEventPublisher {

    private final ApplicationEventPublisher publisher;

    public IncidentEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Publishes every change recorded on {@code incident} since the last call.
     *
     * @param actor the user who caused the changes, or {@code null} for the system
     */
    public void publishChanges(Incident incident, User actor) {
        Long actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null ? actor.getFullName() : AuditEntry.SYSTEM_ACTOR;
        for (IncidentChange change : incident.pullChanges()) {
            publisher.publishEvent(IncidentEvent.of(incident.getId(), incident.getNumber(), change, actorId, actorName));
        }
    }
}
