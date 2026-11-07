package com.iitsm.backend.notification;

import org.springframework.stereotype.Component;

/** Sends a short text once the transaction commits. Reserved for urgent events (see the channel factory). */
@Component
public class SmsChannel implements NotificationChannel {

    private final SmsGateway gateway;

    public SmsChannel(SmsGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public ChannelType type() {
        return ChannelType.SMS;
    }

    @Override
    public void deliver(NotificationMessage message) {
        String name = message.recipient().getFullName();
        String text = "I-ITSM " + message.incidentNumber() + ": " + message.title();
        AfterCommit.run("send SMS to " + name, () -> gateway.send(name, text));
    }
}
