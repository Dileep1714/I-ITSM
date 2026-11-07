package com.iitsm.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Simulated SMS (the spec allows simulation): logs the message and records it in the {@link OutboundMessageLog}
 * so it can be shown on the admin screen.
 */
@Component
public class SimulatedSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSmsGateway.class);
    static final int MAX_SMS_LENGTH = 160;

    private final OutboundMessageLog outbox;
    private final Clock clock;

    public SimulatedSmsGateway(OutboundMessageLog outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void send(String recipientName, String text) {
        String sms = text.length() <= MAX_SMS_LENGTH ? text : text.substring(0, MAX_SMS_LENGTH - 1) + "…";
        log.info("[simulated SMS] to={} text=\"{}\"", recipientName, sms);
        outbox.add(ChannelType.SMS, recipientName, null, sms, clock.instant());
    }
}
