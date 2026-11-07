package com.iitsm.backend.api.dto;

import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.notification.Notification;
import com.iitsm.backend.domain.sla.SlaViolation;

import java.time.Instant;

/** Inbox notifications and SLA violation records. */
public final class MonitoringDtos {

    private MonitoringDtos() {
    }

    public record NotificationView(Long id, Long incidentId, String incidentNumber, IncidentEventType type,
                                   String title, String message, Instant createdAt, boolean read) {

        public static NotificationView of(Notification notification) {
            return new NotificationView(notification.getId(), notification.getIncidentId(),
                    notification.getIncidentNumber(), notification.getType(), notification.getTitle(),
                    notification.getMessage(), notification.getCreatedAt(), notification.isRead());
        }
    }

    public record UnreadCount(long unread) {
    }

    public record SlaViolationView(Long id, Long incidentId, String incidentNumber, String incidentTitle,
                                   SlaViolation.Kind kind, PriorityLevel priority, String serviceName,
                                   Instant deadline, Instant detectedAt) {

        public static SlaViolationView of(SlaViolation violation) {
            var incident = violation.getIncident();
            return new SlaViolationView(violation.getId(), incident.getId(), incident.getNumber(),
                    incident.getTitle(), violation.getKind(), violation.getPriorityLevel(),
                    incident.getService().getName(), violation.getDeadline(), violation.getDetectedAt());
        }
    }
}
