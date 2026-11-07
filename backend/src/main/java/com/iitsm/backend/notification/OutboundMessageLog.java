package com.iitsm.backend.notification;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * The most recent simulated emails and text messages, so an administrator (and the demo video) can see exactly
 * what would have been sent. Bounded and thread-safe; kept in memory only.
 */
@Component
public class OutboundMessageLog {

    static final int CAPACITY = 200;

    private final Deque<OutboundMessage> messages = new ConcurrentLinkedDeque<>();

    public void add(ChannelType channel, String to, String subject, String body, Instant sentAt) {
        messages.addFirst(new OutboundMessage(channel, to, subject, body, sentAt));
        while (messages.size() > CAPACITY) {
            messages.pollLast();
        }
    }

    /** Newest first. */
    public List<OutboundMessage> recent(int limit) {
        List<OutboundMessage> result = new ArrayList<>();
        for (OutboundMessage message : messages) {
            if (result.size() >= limit) {
                break;
            }
            result.add(message);
        }
        return result;
    }

    public void clear() {
        messages.clear();
    }

    public record OutboundMessage(ChannelType channel, String to, String subject, String body, Instant sentAt) {
    }
}
