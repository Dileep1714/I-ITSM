package com.iitsm.backend.notification;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.event.IncidentEvent;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.TechnicianRepository;
import com.iitsm.backend.repository.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <b>Observer:</b> decides who needs to hear about each incident event, writes the message, and sends it through
 * the channels chosen by {@link NotificationChannelFactory}.
 *
 * <p>Rules:
 * <ul>
 *   <li>Never notify the person who caused the event (you know you just resolved it).</li>
 *   <li>Employees never receive staff-only events (internal notes, SLA warnings, escalations).</li>
 *   <li>SLA breaches and escalations also go to every administrator; escalations also go to everyone at the
 *       escalated support level.</li>
 * </ul>
 */
@Component
public class NotificationDispatcher {

    private final IncidentRepository incidents;
    private final UserRepository users;
    private final TechnicianRepository technicians;
    private final NotificationChannelFactory channels;

    public NotificationDispatcher(IncidentRepository incidents, UserRepository users,
                                  TechnicianRepository technicians, NotificationChannelFactory channels) {
        this.incidents = incidents;
        this.users = users;
        this.technicians = technicians;
        this.channels = channels;
    }

    @EventListener
    public void on(IncidentEvent event) {
        Incident incident = incidents.findById(event.incidentId()).orElse(null);
        if (incident == null) {
            return;
        }
        PriorityLevel priority = incident.getPriority() != null ? incident.getPriority().getLevel() : null;
        String title = headline(event.type()) + " – " + event.incidentNumber();
        String body = event.description() + "\n\n" + incident.getTitle();

        for (User recipient : recipientsFor(event, incident)) {
            NotificationMessage message = new NotificationMessage(recipient, event.incidentId(),
                    event.incidentNumber(), event.type(), priority, title, body, event.occurredAt());
            channels.channelsFor(message).forEach(channel -> channel.deliver(message));
        }
    }

    Collection<User> recipientsFor(IncidentEvent event, Incident incident) {
        Map<Long, User> recipients = new LinkedHashMap<>();
        User reporter = incident.getReporter();
        User assignee = incident.getAssignee();

        switch (event.type()) {
            case CREATED, ACKNOWLEDGED, WORK_STARTED, INFO_REQUESTED, RESOLVED, AUTO_VERIFIED, CLOSED, CANCELLED ->
                    add(recipients, reporter);
            case ASSIGNED, REASSIGNED, PRIORITY_CHANGED -> {
                add(recipients, assignee);
                add(recipients, reporter);
            }
            case INFO_PROVIDED, RESOLUTION_ACCEPTED, RESOLUTION_REJECTED, DETAILS_UPDATED -> add(recipients, assignee);
            case NOTE_ADDED -> {
                add(recipients, reporter);
                add(recipients, assignee);
            }
            case SLA_WARNING -> {
                add(recipients, assignee);
                if (assignee == null) {
                    addAll(recipients, users.findActiveAdministrators());
                }
            }
            case SLA_BREACHED -> {
                add(recipients, assignee);
                addAll(recipients, users.findActiveAdministrators());
            }
            case SLA_BREACH_PREDICTED -> {
                // Early warning: the person doing the work and the team leads who can rebalance it.
                add(recipients, assignee);
                addAll(recipients, technicians.findBySupportLevelAndActiveTrue(SupportLevel.L2_TEAM_LEAD));
                if (assignee == null) {
                    addAll(recipients, users.findActiveAdministrators());
                }
            }
            case ESCALATED -> {
                add(recipients, assignee);
                SupportLevel level = incident.getEscalationLevel();
                if (level != null) {
                    addAll(recipients, technicians.findBySupportLevelAndActiveTrue(level));
                }
                addAll(recipients, users.findActiveAdministrators());
            }
            case CLASSIFIED -> {
                // Recorded in the audit trail only: it is immediately followed by ASSIGNED, which is notified.
            }
        }

        if (event.actorId() != null) {
            recipients.remove(event.actorId());
        }
        recipients.values().removeIf(user -> !user.isActive()
                || (user.getRole() == Role.EMPLOYEE && !event.visibleToReporter()));
        return recipients.values();
    }

    private static void add(Map<Long, User> recipients, User user) {
        if (user != null) {
            recipients.putIfAbsent(user.getId(), user);
        }
    }

    private static void addAll(Map<Long, User> recipients, List<? extends User> users) {
        users.stream().filter(Objects::nonNull).forEach(user -> add(recipients, user));
    }

    static String headline(IncidentEventType type) {
        return switch (type) {
            case CREATED -> "We received your incident";
            case CLASSIFIED -> "Incident classified";
            case PRIORITY_CHANGED -> "Priority changed";
            case DETAILS_UPDATED -> "Incident details updated";
            case ASSIGNED -> "Incident assigned";
            case REASSIGNED -> "Incident reassigned";
            case ACKNOWLEDGED -> "A technician has picked up your incident";
            case WORK_STARTED -> "Work has started";
            case INFO_REQUESTED -> "More information needed";
            case INFO_PROVIDED -> "The reporter answered your question";
            case NOTE_ADDED -> "New comment";
            case RESOLVED -> "Incident resolved, please confirm";
            case RESOLUTION_ACCEPTED -> "Resolution confirmed";
            case RESOLUTION_REJECTED -> "Resolution rejected, incident reopened";
            case AUTO_VERIFIED -> "Resolution verified automatically";
            case CLOSED -> "Incident closed";
            case CANCELLED -> "Incident cancelled";
            case SLA_WARNING -> "SLA deadline approaching";
            case SLA_BREACHED -> "SLA breached";
            case SLA_BREACH_PREDICTED -> "SLA breach likely";
            case ESCALATED -> "Incident escalated";
        };
    }
}
