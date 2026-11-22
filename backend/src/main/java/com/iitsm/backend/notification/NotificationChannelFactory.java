package com.iitsm.backend.notification;

import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.user.Role;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Factory pattern:</b> decides which channels a message goes through, and hands out the channel objects.
 *
 * <ul>
 *   <li><b>In-app:</b> always.</li>
 *   <li><b>Email:</b> for events worth leaving the app for (assignment, questions, resolution, SLA…), but not for
 *       routine ones (a comment, work started).</li>
 *   <li><b>SMS:</b> only urgent events, for support staff: SLA breaches, escalations, and new P1 assignments.
 *       Nobody wants a text message for a printer jam.</li>
 * </ul>
 * The routing rules live in one place, so changing "who gets texted" is a one-line change.
 */
@Component
public class NotificationChannelFactory {

    private static final Set<IncidentEventType> EMAIL_WORTHY = EnumSet.of(
            IncidentEventType.CREATED, IncidentEventType.ASSIGNED, IncidentEventType.REASSIGNED,
            IncidentEventType.INFO_REQUESTED, IncidentEventType.INFO_PROVIDED, IncidentEventType.RESOLVED,
            IncidentEventType.RESOLUTION_REJECTED, IncidentEventType.CLOSED, IncidentEventType.CANCELLED,
            IncidentEventType.PRIORITY_CHANGED, IncidentEventType.SLA_WARNING, IncidentEventType.SLA_BREACH_PREDICTED,
            IncidentEventType.SLA_BREACHED,
            IncidentEventType.ESCALATED);

    private static final Set<IncidentEventType> ALWAYS_URGENT = EnumSet.of(
            IncidentEventType.SLA_BREACHED, IncidentEventType.ESCALATED);

    private static final Set<IncidentEventType> URGENT_IF_P1 = EnumSet.of(
            IncidentEventType.ASSIGNED, IncidentEventType.REASSIGNED);

    private final Map<ChannelType, NotificationChannel> channels = new EnumMap<>(ChannelType.class);

    /** Spring passes every {@link NotificationChannel} bean, so a new channel class is picked up automatically. */
    public NotificationChannelFactory(List<NotificationChannel> available) {
        available.forEach(channel -> channels.put(channel.type(), channel));
    }

    public List<NotificationChannel> channelsFor(NotificationMessage message) {
        List<NotificationChannel> selected = new ArrayList<>();
        add(selected, ChannelType.IN_APP);
        if (EMAIL_WORTHY.contains(message.type())) {
            add(selected, ChannelType.EMAIL);
        }
        if (isUrgent(message) && message.recipient().getRole() != Role.EMPLOYEE) {
            add(selected, ChannelType.SMS);
        }
        return selected;
    }

    private static boolean isUrgent(NotificationMessage message) {
        return ALWAYS_URGENT.contains(message.type())
                || (URGENT_IF_P1.contains(message.type()) && message.priority() == PriorityLevel.P1);
    }

    private void add(List<NotificationChannel> selected, ChannelType type) {
        NotificationChannel channel = channels.get(type);
        if (channel != null) {
            selected.add(channel);
        }
    }
}
