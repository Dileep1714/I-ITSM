package com.iitsm.backend.api.dto;

import com.iitsm.backend.domain.audit.AuditEntry;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.IncidentOperation;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.incident.ResolutionCode;
import com.iitsm.backend.domain.incident.Urgency;
import com.iitsm.backend.domain.sla.SlaStatus;
import com.iitsm.backend.domain.user.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Request and response bodies for incidents.
 *
 * <p>Annotations such as {@code @NotBlank} give fast, field-by-field error messages at the API edge; the domain
 * objects validate again in their constructors, so no route around the API can create invalid data.
 */
public final class IncidentDtos {

    private IncidentDtos() {
    }

    // ----- requests ----------------------------------------------------------------------------------------------

    public record ReportIncidentRequest(
            @NotBlank @Size(max = Incident.MAX_TITLE_LENGTH) String title,
            @NotBlank @Size(min = Incident.MIN_DESCRIPTION_LENGTH, max = Incident.MAX_DESCRIPTION_LENGTH)
            String description,
            @NotNull Long serviceId,
            Long categoryId,
            @NotNull Impact impact,
            @NotNull Urgency urgency,
            @Min(1) @Max(Incident.MAX_AFFECTED_USERS) Integer affectedUsers) {
    }

    public record ClassifyRequest(@NotNull Long categoryId) {
    }

    public record AssignRequest(@NotNull Long technicianId) {
    }

    /** One free-text field: a question, an answer, a rejection reason or a cancellation reason. */
    public record TextRequest(@NotBlank @Size(max = 2000) String text) {
    }

    public record ResolveRequest(@NotBlank @Size(max = 2000) String summary,
                                 @Size(max = 2000) String rootCause,
                                 @NotNull ResolutionCode code) {
    }

    public record AcceptRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {
    }

    public record NoteRequest(@NotBlank @Size(max = 4000) String text, boolean internal) {
    }

    // ----- responses ---------------------------------------------------------------------------------------------

    /** One row in an incident list. */
    public record IncidentSummary(Long id, String number, String title, IncidentStatus status,
                                  PriorityLevel priority, String serviceName, String categoryName,
                                  String reporterName, String assigneeName, SlaStatus slaStatus,
                                  Instant createdAt, Instant updatedAt) {
    }

    /** Everything about one incident, as visible to the requesting user. */
    public record IncidentDetail(Long id, String number, String title, String description, IncidentStatus status,
                                 Impact impact, Urgency urgency, int affectedUsers,
                                 PersonRef reporter, PersonRef assignee,
                                 NamedRef service, NamedRef category,
                                 PriorityView priority, SlaView sla,
                                 List<NoteView> notes, List<InfoRequestView> infoRequests,
                                 ResolutionView resolution, FeedbackView feedback,
                                 String duplicateOfNumber, String cancellationReason,
                                 Instant createdAt, Instant updatedAt, Instant closedAt,
                                 Set<IncidentOperation> allowedOperations) {
    }

    public record PersonRef(Long id, String fullName, Role role) {
    }

    public record NamedRef(Long id, String name) {
    }

    public record PriorityView(PriorityLevel level, String label, double score, String rationale) {
    }

    public record SlaView(SlaStatus status, Instant responseDue, Instant resolutionDue, Instant respondedAt,
                          Instant resolvedAt, boolean responseBreached, boolean resolutionBreached,
                          double resolutionElapsedPercent) {
    }

    public record NoteView(Long id, PersonRef author, String text, boolean internal, Instant createdAt) {
    }

    public record InfoRequestView(Long id, PersonRef askedBy, String question, Instant askedAt,
                                  String answer, Instant answeredAt) {
    }

    public record ResolutionView(String summary, String rootCause, ResolutionCode code, PersonRef resolvedBy,
                                 Instant resolvedAt) {
    }

    public record FeedbackView(boolean accepted, Integer rating, String comment, Instant givenAt) {
    }

    /** One line of an incident's history. */
    public record AuditEntryView(Long id, String incidentNumber, IncidentEventType action,
                                 IncidentStatus previousStatus, IncidentStatus newStatus, String description,
                                 String actorName, Instant occurredAt) {

        public static AuditEntryView of(AuditEntry entry) {
            return new AuditEntryView(entry.getId(), entry.getIncidentNumber(), entry.getAction(),
                    entry.getPreviousStatus(), entry.getNewStatus(), entry.getDescription(),
                    entry.getActorName(), entry.getOccurredAt());
        }
    }

    /** A ranked technician suggestion with the reasons behind it. */
    public record Recommendation(Long technicianId, String fullName, double score, List<String> reasons,
                                 int activeIncidents, int maxActiveIncidents) {
    }
}
