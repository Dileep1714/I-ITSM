package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.IncidentDtos.FeedbackView;
import com.iitsm.backend.api.dto.IncidentDtos.IncidentDetail;
import com.iitsm.backend.api.dto.IncidentDtos.IncidentSummary;
import com.iitsm.backend.api.dto.IncidentDtos.InfoRequestView;
import com.iitsm.backend.api.dto.IncidentDtos.NamedRef;
import com.iitsm.backend.api.dto.IncidentDtos.NoteView;
import com.iitsm.backend.api.dto.IncidentDtos.PersonRef;
import com.iitsm.backend.api.dto.IncidentDtos.PriorityView;
import com.iitsm.backend.api.dto.IncidentDtos.ResolutionView;
import com.iitsm.backend.api.dto.IncidentDtos.SlaView;
import com.iitsm.backend.domain.incident.Feedback;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.incident.Resolution;
import com.iitsm.backend.domain.sla.SlaTracker;
import com.iitsm.backend.domain.user.User;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Converts domain objects into API responses. Runs inside the service transaction, so lazy associations
 * (reporter, notes…) can still be loaded.
 *
 * <p>The <em>viewer</em> matters: employees never receive internal notes, and {@code allowedOperations} lists only
 * what this particular user may do.
 */
@Component
public class IncidentMapper {

    private final IncidentPermissions permissions;
    private final Clock clock;

    public IncidentMapper(IncidentPermissions permissions, Clock clock) {
        this.permissions = permissions;
        this.clock = clock;
    }

    public IncidentSummary toSummary(Incident incident) {
        Instant now = clock.instant();
        return new IncidentSummary(incident.getId(), incident.getNumber(), incident.getTitle(),
                incident.getStatus(),
                incident.getPriority() != null ? incident.getPriority().getLevel() : null,
                incident.getService().getName(),
                incident.getCategory() != null ? incident.getCategory().getName() : null,
                incident.getReporter().getFullName(),
                incident.getAssignee() != null ? incident.getAssignee().getFullName() : null,
                incident.slaStatus(now), incident.getCreatedAt(), incident.getUpdatedAt());
    }

    public IncidentDetail toDetail(Incident incident, User viewer) {
        Instant now = clock.instant();
        return new IncidentDetail(
                incident.getId(), incident.getNumber(), incident.getTitle(), incident.getDescription(),
                incident.getStatus(), incident.getImpact(), incident.getUrgency(), incident.getAffectedUsers(),
                person(incident.getReporter()), person(incident.getAssignee()),
                new NamedRef(incident.getService().getId(), incident.getService().getName()),
                incident.getCategory() != null
                        ? new NamedRef(incident.getCategory().getId(), incident.getCategory().getName()) : null,
                priority(incident.getPriority()),
                sla(incident.getSla(), now),
                incident.notesVisibleTo(viewer).stream()
                        .map(note -> new NoteView(note.getId(), person(note.getAuthor()), note.getText(),
                                note.isInternal(), note.getCreatedAt()))
                        .toList(),
                incident.getInfoRequests().stream()
                        .map(request -> new InfoRequestView(request.getId(), person(request.getAskedBy()),
                                request.getQuestion(), request.getAskedAt(), request.getAnswer(),
                                request.getAnsweredAt()))
                        .toList(),
                resolution(incident.getResolution()),
                feedback(incident.getFeedback()),
                incident.getDuplicateOf() != null ? incident.getDuplicateOf().getNumber() : null,
                incident.getCancellationReason(),
                incident.getCreatedAt(), incident.getUpdatedAt(), incident.getClosedAt(),
                permissions.allowedFor(viewer, incident));
    }

    private static PersonRef person(User user) {
        return user == null ? null : new PersonRef(user.getId(), user.getFullName(), user.getRole());
    }

    private static PriorityView priority(Priority priority) {
        return priority == null ? null : new PriorityView(priority.getLevel(), priority.getLevel().label(),
                priority.getScore(), priority.getRationale());
    }

    private static SlaView sla(SlaTracker sla, Instant now) {
        if (!sla.isStarted()) {
            return new SlaView(sla.status(now), null, null, null, null, false, false, 0.0);
        }
        return new SlaView(sla.status(now), sla.responseDue(), sla.resolutionDue(now), sla.getRespondedAt(),
                sla.getResolvedAt(), sla.isResponseBreached(now), sla.isResolutionBreached(now),
                Math.round(sla.resolutionElapsedFraction(now) * 1000) / 10.0);
    }

    private static ResolutionView resolution(Resolution resolution) {
        return resolution == null ? null : new ResolutionView(resolution.getSummary(), resolution.getRootCause(),
                resolution.getCode(), person(resolution.getResolvedBy()), resolution.getResolvedAt());
    }

    private static FeedbackView feedback(Feedback feedback) {
        return feedback == null ? null : new FeedbackView(feedback.isAccepted(), feedback.getRating(),
                feedback.getComment(), feedback.getGivenAt());
    }
}
