package com.iitsm.backend.domain.sla;

import com.iitsm.backend.domain.incident.PriorityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.iitsm.backend.domain.TestFixtures.T0;
import static com.iitsm.backend.domain.TestFixtures.minutesAfterT0;
import static org.assertj.core.api.Assertions.assertThat;

/** SLA signals (warnings and breaches) are raised exactly once, at the right moment. P2: respond 15m, resolve 2h. */
class SlaSignalsTest {

    private SlaTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SlaTracker();
        tracker.start(SlaPolicy.standard(PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2)), T0);
    }

    @Test
    @DisplayName("Nothing is signalled while comfortably within both targets")
    void quietWhenOnTrack() {
        assertThat(tracker.evaluate(minutesAfterT0(5))).isEmpty();
    }

    @Test
    @DisplayName("A response warning is raised once at 75 %, then not again")
    void responseWarningOnce() {
        assertThat(tracker.evaluate(minutesAfterT0(12))).containsExactly(SlaSignal.RESPONSE_AT_RISK);
        assertThat(tracker.evaluate(minutesAfterT0(13))).isEmpty();
        assertThat(tracker.evaluate(minutesAfterT0(14))).isEmpty();
    }

    @Test
    @DisplayName("A breach is raised once, even if the monitor checks every minute afterwards")
    void breachOnce() {
        assertThat(tracker.evaluate(minutesAfterT0(16))).containsExactly(SlaSignal.RESPONSE_BREACHED);
        assertThat(tracker.evaluate(minutesAfterT0(17))).isEmpty();
        assertThat(tracker.evaluate(minutesAfterT0(60))).isEmpty();
    }

    @Test
    @DisplayName("Jumping straight past a deadline reports the breach, not a stale warning")
    void breachSupersedesWarning() {
        assertThat(tracker.evaluate(minutesAfterT0(20))).containsExactly(SlaSignal.RESPONSE_BREACHED);
    }

    @Test
    @DisplayName("Once responded, the response target can no longer be breached; resolution is tracked separately")
    void respondedThenResolutionWarning() {
        tracker.recordResponse(minutesAfterT0(5));

        assertThat(tracker.evaluate(minutesAfterT0(60))).isEmpty();
        assertThat(tracker.evaluate(minutesAfterT0(91))).containsExactly(SlaSignal.RESOLUTION_AT_RISK);
        assertThat(tracker.evaluate(minutesAfterT0(121))).containsExactly(SlaSignal.RESOLUTION_BREACHED);
    }

    @Test
    @DisplayName("No resolution warning while paused (waiting for the reporter)")
    void noWarningWhilePaused() {
        tracker.recordResponse(minutesAfterT0(5));
        tracker.pause(minutesAfterT0(30));

        assertThat(tracker.evaluate(minutesAfterT0(100))).isEmpty();
    }

    @Test
    @DisplayName("A late resolution is still recorded as a resolution breach")
    void lateResolutionRecorded() {
        tracker.recordResponse(minutesAfterT0(5));
        tracker.recordResolution(minutesAfterT0(130));

        assertThat(tracker.evaluate(minutesAfterT0(130))).contains(SlaSignal.RESOLUTION_BREACHED);
    }
}
