package com.iitsm.backend.notification;

import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.iitsm.backend.domain.TestFixtures.T0;
import static com.iitsm.backend.domain.TestFixtures.employee;
import static com.iitsm.backend.domain.TestFixtures.technician;
import static org.assertj.core.api.Assertions.assertThat;

/** Channel selection (Factory) and the simulated email/SMS adapters. */
class NotificationChannelsTest {

    /** A channel that only remembers what it was asked to deliver. */
    private static final class RecordingChannel implements NotificationChannel {
        private final ChannelType type;
        private final List<NotificationMessage> delivered = new ArrayList<>();

        private RecordingChannel(ChannelType type) {
            this.type = type;
        }

        @Override
        public ChannelType type() {
            return type;
        }

        @Override
        public void deliver(NotificationMessage message) {
            delivered.add(message);
        }
    }

    private final NotificationChannelFactory factory = new NotificationChannelFactory(List.of(
            new RecordingChannel(ChannelType.IN_APP), new RecordingChannel(ChannelType.EMAIL),
            new RecordingChannel(ChannelType.SMS)));

    private final Employee asha = employee("Asha Rao");
    private final Technician ravi = technician("Ravi Kumar", 5);

    private static NotificationMessage message(User to, IncidentEventType type, PriorityLevel priority) {
        return new NotificationMessage(to, 1L, "INC-2026-000001", type, priority, "Title", "Body", T0);
    }

    private List<ChannelType> channelsFor(User to, IncidentEventType type, PriorityLevel priority) {
        return factory.channelsFor(message(to, type, priority)).stream().map(NotificationChannel::type).toList();
    }

    @Test
    @DisplayName("Routine events go in-app only")
    void routineInAppOnly() {
        assertThat(channelsFor(asha, IncidentEventType.WORK_STARTED, PriorityLevel.P3))
                .containsExactly(ChannelType.IN_APP);
    }

    @Test
    @DisplayName("Important events go in-app and by email")
    void importantByEmail() {
        assertThat(channelsFor(asha, IncidentEventType.RESOLVED, PriorityLevel.P3))
                .containsExactly(ChannelType.IN_APP, ChannelType.EMAIL);
    }

    @Test
    @DisplayName("A P1 assignment also texts the technician; a P3 assignment does not")
    void p1AssignmentTexted() {
        assertThat(channelsFor(ravi, IncidentEventType.ASSIGNED, PriorityLevel.P1)).contains(ChannelType.SMS);
        assertThat(channelsFor(ravi, IncidentEventType.ASSIGNED, PriorityLevel.P3)).doesNotContain(ChannelType.SMS);
    }

    @Test
    @DisplayName("Employees are never texted, even about urgent events")
    void employeesNeverTexted() {
        assertThat(channelsFor(asha, IncidentEventType.SLA_BREACHED, PriorityLevel.P1))
                .doesNotContain(ChannelType.SMS);
        assertThat(channelsFor(ravi, IncidentEventType.SLA_BREACHED, PriorityLevel.P1))
                .contains(ChannelType.SMS);
    }

    @Test
    @DisplayName("Simulated email and SMS appear in the outbox; long SMS are cut to 160 characters")
    void simulatedAdaptersRecordMessages() {
        OutboundMessageLog outbox = new OutboundMessageLog();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

        // Outside a transaction, AfterCommit delivers immediately.
        new EmailChannel(new SimulatedEmailSender(outbox, clock)).deliver(message(asha, IncidentEventType.RESOLVED,
                PriorityLevel.P2));
        new SimulatedSmsGateway(outbox, clock).send("Ravi Kumar", "x".repeat(500));

        List<OutboundMessageLog.OutboundMessage> sent = outbox.recent(10);
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).channel()).isEqualTo(ChannelType.SMS);
        assertThat(sent.get(0).body()).hasSize(SimulatedSmsGateway.MAX_SMS_LENGTH);
        assertThat(sent.get(1).to()).isEqualTo("asha.rao@example.com");
        assertThat(sent.get(1).subject()).isEqualTo("[I-ITSM] Title");
    }

    @Test
    @DisplayName("The outbox keeps only the most recent messages")
    void outboxIsBounded() {
        OutboundMessageLog outbox = new OutboundMessageLog();
        for (int i = 0; i < OutboundMessageLog.CAPACITY + 50; i++) {
            outbox.add(ChannelType.SMS, "x", null, "message " + i, T0);
        }

        assertThat(outbox.recent(1000)).hasSize(OutboundMessageLog.CAPACITY);
        assertThat(outbox.recent(1).get(0).body()).isEqualTo("message " + (OutboundMessageLog.CAPACITY + 49));
    }
}
