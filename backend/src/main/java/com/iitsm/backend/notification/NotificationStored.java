package com.iitsm.backend.notification;

import com.iitsm.backend.domain.incident.IncidentEventType;

/**
 * Published when an in-app notification has been saved, so other observers (the real-time push) can react without
 * the inbox knowing about them.
 */
public record NotificationStored(Long recipientId, Long incidentId, String incidentNumber, IncidentEventType type,
                                 String title) {
}
