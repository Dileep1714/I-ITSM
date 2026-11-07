package com.iitsm.backend.notification;

import org.springframework.stereotype.Component;

/** Emails the recipient once the transaction commits. Uses whichever {@link EmailSender} is configured. */
@Component
public class EmailChannel implements NotificationChannel {

    private final EmailSender sender;

    public EmailChannel(EmailSender sender) {
        this.sender = sender;
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    public void deliver(NotificationMessage message) {
        String to = message.recipient().getEmail();
        String subject = "[I-ITSM] " + message.title();
        String body = message.body() + "\n\nOpen I-ITSM to view incident " + message.incidentNumber() + ".";
        AfterCommit.run("email " + to, () -> sender.send(to, subject, body));
    }
}
