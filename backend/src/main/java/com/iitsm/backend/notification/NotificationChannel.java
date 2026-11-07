package com.iitsm.backend.notification;

/**
 * A way of delivering a notification: in-app, email, SMS…
 *
 * <p><b>Strategy/polymorphism:</b> the notification service sends to a list of channels without knowing what any
 * of them does. Adding a channel (push notifications, Slack, Microsoft Teams) means writing one class that
 * implements this interface. Spring picks it up automatically, and nothing else changes.
 */
public interface NotificationChannel {

    ChannelType type();

    void deliver(NotificationMessage message);
}
