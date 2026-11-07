package com.iitsm.backend.event;

import com.iitsm.backend.domain.audit.AuditEntry;
import com.iitsm.backend.repository.AuditEntryRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * <b>Observer:</b> writes every incident event to the audit trail.
 *
 * <p>Runs synchronously <em>inside</em> the same transaction as the change itself: either the change and its
 * audit entry are both saved, or neither is. A change without a history line is impossible.
 */
@Component
public class AuditTrailListener {

    private final AuditEntryRepository auditEntries;

    public AuditTrailListener(AuditEntryRepository auditEntries) {
        this.auditEntries = auditEntries;
    }

    @EventListener
    public void on(IncidentEvent event) {
        auditEntries.save(new AuditEntry(event.incidentId(), event.incidentNumber(), event.type(),
                event.previousStatus(), event.newStatus(), event.description(), event.actorId(),
                event.actorName(), event.visibleToReporter(), event.occurredAt()));
    }
}
