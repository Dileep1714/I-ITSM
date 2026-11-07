package com.iitsm.backend.domain.sla;

import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.PriorityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.iitsm.backend.domain.TestFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlaPolicyTest {

    @Test
    @DisplayName("Resolution target shorter than the response target is rejected")
    void resolutionShorterThanResponseRejected() {
        assertThatThrownBy(() -> SlaPolicy.standard(PriorityLevel.P1, Duration.ofHours(1), Duration.ofMinutes(30)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Warning threshold boundaries: 0.50 and 0.95 accepted, 0.49 and 0.96 rejected")
    void warningThresholdBoundaries() {
        SlaPolicy policy = SlaPolicy.standard(PriorityLevel.P3, Duration.ofHours(1), Duration.ofHours(8));

        policy.changeWarningThreshold(0.50);
        policy.changeWarningThreshold(0.95);
        assertThatThrownBy(() -> policy.changeWarningThreshold(0.49)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> policy.changeWarningThreshold(0.96)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("A generic policy applies to every service; a service-specific one only to its own service")
    void appliesTo() {
        ItService payment = service("Payment", BusinessCriticality.MISSION_CRITICAL);
        ItService email = service("Email", BusinessCriticality.MEDIUM);
        SlaPolicy generic = SlaPolicy.standard(PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30));
        SlaPolicy paymentOnly = new SlaPolicy(PriorityLevel.P1, payment, Duration.ofMinutes(2),
                Duration.ofMinutes(15), 0.75);

        assertThat(generic.appliesTo(PriorityLevel.P1, payment)).isTrue();
        assertThat(generic.appliesTo(PriorityLevel.P1, email)).isTrue();
        assertThat(generic.appliesTo(PriorityLevel.P2, payment)).isFalse();
        assertThat(paymentOnly.appliesTo(PriorityLevel.P1, payment)).isTrue();
        assertThat(paymentOnly.appliesTo(PriorityLevel.P1, email)).isFalse();
        assertThat(paymentOnly.isServiceSpecific()).isTrue();
    }
}
