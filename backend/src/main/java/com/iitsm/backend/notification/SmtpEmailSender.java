package com.iitsm.backend.notification;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;

/**
 * <b>Adapter:</b> real email through any SMTP server (e.g. Gmail with an app password), via Spring's
 * {@link JavaMailSender}. Used only when {@code spring.mail.host} is configured.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final OutboundMessageLog log;
    private final Clock clock;

    public SmtpEmailSender(JavaMailSender mailSender, String from, OutboundMessageLog log, Clock clock) {
        this.mailSender = mailSender;
        this.from = from;
        this.log = log;
        this.clock = clock;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.add(ChannelType.EMAIL, to, subject, body, clock.instant());
    }

    @Override
    public boolean isSimulated() {
        return false;
    }
}
