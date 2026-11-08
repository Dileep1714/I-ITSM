package com.iitsm.backend.realtime;

import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.event.IncidentEvent;
import com.iitsm.backend.notification.NotificationStored;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * <b>Observer:</b> pushes incident changes and new notifications to connected browsers.
 *
 * <p>Runs only <em>after the transaction commits</em>, so a browser never hears about a change that was rolled
 * back, and when it reloads the data the change is already visible. Messages are small "something changed" hints
 * (ids and types); the browser then fetches the details through the normal, permission-checked REST API.
 * A push failure is logged and ignored: real-time updates are a convenience, never a reason to fail a request.
 */
@Component
public class LiveUpdateBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdateBroadcaster.class);

    /** What staff dashboards receive on {@code /topic/incidents}. */
    public record IncidentUpdate(Long incidentId, String incidentNumber, IncidentEventType type,
                                 IncidentStatus newStatus, String actorName, Instant occurredAt) {
    }

    /** What a user receives on their own {@code /user/queue/notifications}. */
    public record NotificationPush(Long incidentId, String incidentNumber, IncidentEventType type, String title) {
    }

    private final SimpMessagingTemplate messaging;

    public LiveUpdateBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(IncidentEvent event) {
        send(() -> messaging.convertAndSend(WebSocketConfig.INCIDENTS_TOPIC, new IncidentUpdate(event.incidentId(),
                event.incidentNumber(), event.type(), event.newStatus(), event.actorName(), event.occurredAt())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(NotificationStored stored) {
        send(() -> messaging.convertAndSendToUser(String.valueOf(stored.recipientId()),
                WebSocketConfig.NOTIFICATIONS_QUEUE,
                new NotificationPush(stored.incidentId(), stored.incidentNumber(), stored.type(), stored.title())));
    }

    private static void send(Runnable push) {
        try {
            push.run();
        } catch (RuntimeException e) {
            log.warn("Real-time push failed: {}", e.getMessage());
        }
    }
}
