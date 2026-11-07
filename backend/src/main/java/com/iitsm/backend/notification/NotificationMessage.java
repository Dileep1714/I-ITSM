package com.iitsm.backend.notification;

import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.user.User;

import java.time.Instant;

/** One message for one recipient, independent of how it will be delivered. */
public record NotificationMessage(User recipient, Long incidentId, String incidentNumber, IncidentEventType type,
                                  PriorityLevel priority, String title, String body, Instant createdAt) {
}
