package com.iitsm.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/** Development default: "sends" email by logging it and recording it in the {@link OutboundMessageLog}. */
public class SimulatedEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailSender.class);

    private final OutboundMessageLog outbox;
    private final Clock clock;

    public SimulatedEmailSender(OutboundMessageLog outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("[simulated email] to={} subject=\"{}\"", to, subject);
        outbox.add(ChannelType.EMAIL, to, subject, body, clock.instant());
    }

    @Override
    public boolean isSimulated() {
        return true;
    }
}
