package com.iitsm.backend.notification;

import com.iitsm.backend.domain.notification.Notification;
import com.iitsm.backend.repository.NotificationRepository;
import org.springframework.stereotype.Component;

/**
 * Stores the notification in the user's inbox. Saved in the same transaction as the incident change, so the
 * inbox is always consistent with what actually happened.
 */
@Component
public class InAppChannel implements NotificationChannel {

    private final NotificationRepository notifications;

    public InAppChannel(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public ChannelType type() {
        return ChannelType.IN_APP;
    }

    @Override
    public void deliver(NotificationMessage message) {
        notifications.save(new Notification(message.recipient(), message.incidentId(), message.incidentNumber(),
                message.type(), message.title(), message.body(), message.createdAt()));
    }
}
