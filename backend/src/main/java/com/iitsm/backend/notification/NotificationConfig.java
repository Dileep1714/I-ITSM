package com.iitsm.backend.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;

/**
 * Chooses the email adapter at startup: real SMTP if a mail server is configured ({@code spring.mail.host}),
 * otherwise the simulated sender. The rest of the application never knows which one it got.
 */
@Configuration
public class NotificationConfig {

    @Bean
    public EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender, OutboundMessageLog log, Clock clock,
                                   @Value("${iitsm.notifications.from:I-ITSM <no-reply@iitsm.example.com>}")
                                   String from) {
        JavaMailSender smtp = mailSender.getIfAvailable();
        return smtp != null ? new SmtpEmailSender(smtp, from, log, clock) : new SimulatedEmailSender(log, clock);
    }
}
