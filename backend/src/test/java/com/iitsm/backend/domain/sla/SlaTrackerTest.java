package com.iitsm.backend.domain.sla;

import com.iitsm.backend.domain.incident.PriorityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.iitsm.backend.domain.TestFixtures.T0;
import static com.iitsm.backend.domain.TestFixtures.minutesAfterT0;
import static org.assertj.core.api.Assertions.assertThat;

/** P2 policy throughout: respond within 15 minutes, resolve within 2 hours, warn at 75 %. */
class SlaTrackerTest {

    private SlaPolicy p2Policy;
    private SlaTracker tracker;

    @BeforeEach
    void setUp() {
        p2Policy = SlaPolicy.standard(PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2));
        tracker = new SlaTracker();
    }

    @Test
    @DisplayName("Before it starts, the SLA has no deadlines and reports NOT_STARTED")
    void notStarted() {
        assertThat(tracker.status(T0)).isEqualTo(SlaStatus.NOT_STARTED);
        assertThat(tracker.responseDue()).isNull();
        assertThat(tracker.isResponseBreached(minutesAfterT0(1000))).isFalse();
    }

    @Test
    @DisplayName("Deadlines are calculated from the start time and the policy targets")
    void deadlinesCalculated() {
        tracker.start(p2Policy, T0);

        assertThat(tracker.responseDue()).isEqualTo(minutesAfterT0(15));
        assertThat(tracker.resolutionDue(T0)).isEqualTo(minutesAfterT0(120));
    }

    @Test
    @DisplayName("Response boundary: exactly on the deadline is met, one second later is breached")
    void responseBoundary() {
        tracker.start(p2Policy, T0);

        assertThat(tracker.isResponseBreached(minutesAfterT0(15))).isFalse();
        assertThat(tracker.isResponseBreached(minutesAfterT0(15).plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("Warning threshold boundary: 74 % is ON_TRACK, 75 % is AT_RISK")
    void warningThresholdBoundary() {
        tracker.start(p2Policy, T0);
        tracker.recordResponse(minutesAfterT0(5));

        assertThat(tracker.status(minutesAfterT0(89))).isEqualTo(SlaStatus.ON_TRACK);  // 89/120 = 74.2 %
        assertThat(tracker.status(minutesAfterT0(90))).isEqualTo(SlaStatus.AT_RISK);   // 90/120 = 75 %
    }

    @Test
    @DisplayName("An incident nobody has responded to is AT_RISK near the response deadline")
    void responseAtRisk() {
        tracker.start(p2Policy, T0);

        assertThat(tracker.status(minutesAfterT0(10))).isEqualTo(SlaStatus.ON_TRACK);  // 67 %
        assertThat(tracker.status(minutesAfterT0(12))).isEqualTo(SlaStatus.AT_RISK);   // 80 %
    }

    @Test
    @DisplayName("Resolved within both targets: MET, and it stays MET however late you look")
    void metStaysMet() {
        tracker.start(p2Policy, T0);
        tracker.recordResponse(minutesAfterT0(5));
        tracker.recordResolution(minutesAfterT0(60));

        assertThat(tracker.status(minutesAfterT0(60))).isEqualTo(SlaStatus.MET);
        assertThat(tracker.status(minutesAfterT0(10_000))).isEqualTo(SlaStatus.MET);
    }

    @Test
    @DisplayName("Pausing for 20 minutes extends the resolution deadline by 20 minutes")
    void pauseExtendsDeadline() {
        tracker.start(p2Policy, T0);
        tracker.recordResponse(minutesAfterT0(5));

        tracker.pause(minutesAfterT0(30));
        assertThat(tracker.status(minutesAfterT0(40))).isEqualTo(SlaStatus.PAUSED);
        tracker.resume(minutesAfterT0(50));

        assertThat(tracker.pausedTotal(minutesAfterT0(50))).isEqualTo(Duration.ofMinutes(20));
        assertThat(tracker.resolutionDue(minutesAfterT0(50))).isEqualTo(minutesAfterT0(140));
    }

    @Test
    @DisplayName("Editing a policy later does not move the deadlines of an incident already in progress")
    void targetsAreSnapshotted() {
        tracker.start(p2Policy, T0);

        p2Policy.updateTargets(Duration.ofHours(1), Duration.ofHours(4));

        assertThat(tracker.responseDue()).isEqualTo(minutesAfterT0(15));
        assertThat(tracker.resolutionDue(T0)).isEqualTo(minutesAfterT0(120));
    }

    @Test
    @DisplayName("Only the first response counts")
    void firstResponseCounts() {
        tracker.start(p2Policy, T0);
        tracker.recordResponse(minutesAfterT0(5));
        tracker.recordResponse(minutesAfterT0(50));

        assertThat(tracker.getRespondedAt()).isEqualTo(minutesAfterT0(5));
    }
}
