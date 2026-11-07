package com.iitsm.backend.domain.priority;

import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.incident.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightedScoreCalculatorTest {

    private final WeightedScoreCalculator calculator = new WeightedScoreCalculator();

    private Priority calculate(Impact impact, Urgency urgency, BusinessCriticality criticality, int users) {
        return calculator.calculate(new PriorityFactors(impact, urgency, criticality, users));
    }

    @Test
    @DisplayName("The spec's scenario, payment application down for 200 users, is P1")
    void paymentOutageIsP1() {
        Priority priority = calculate(Impact.HIGH, Urgency.CRITICAL, BusinessCriticality.MISSION_CRITICAL, 200);

        assertThat(priority.getLevel()).isEqualTo(PriorityLevel.P1);
        assertThat(priority.getScore()).isEqualTo(72.0); // 3 × 4 × 4 × 1.5
        assertThat(priority.getRationale())
                .contains("Impact HIGH(3)", "Urgency CRITICAL(4)", "MISSION_CRITICAL(4)", "×1.50", "P1 Critical");
    }

    @ParameterizedTest(name = "{0} × {1} × {2} = {3} → {4}")
    @CsvSource({
            // threshold boundaries, single user (factor 1.0)
            "HIGH,     HIGH,     MISSION_CRITICAL, 36, P1",   // exactly the P1 threshold
            "EXTENSIVE, CRITICAL, LOW,             16, P3",   // just below P2 (18)
            "HIGH,     HIGH,     MEDIUM,           18, P2",   // exactly the P2 threshold
            "MEDIUM,   MEDIUM,   MEDIUM,            8, P3",   // exactly the P3 threshold
            "LOW,      MEDIUM,   HIGH,              6, P4",   // below P3
            "LOW,      LOW,      LOW,               1, P4",   // minimum possible
            "EXTENSIVE, CRITICAL, MISSION_CRITICAL, 64, P1",  // maximum base score
    })
    @DisplayName("Threshold boundaries map scores to the right level")
    void thresholds(Impact impact, Urgency urgency, BusinessCriticality criticality, double score, PriorityLevel level) {
        Priority priority = calculate(impact, urgency, criticality, 1);

        assertThat(priority.getScore()).isEqualTo(score);
        assertThat(priority.getLevel()).isEqualTo(level);
    }

    @ParameterizedTest(name = "{0} users → ×{1}")
    @CsvSource({"1, 1.0", "10, 1.0", "11, 1.25", "100, 1.25", "101, 1.5", "5000, 1.5"})
    @DisplayName("User factor boundaries: 10 / 11 and 100 / 101")
    void userFactorBoundaries(int users, double expectedFactor) {
        assertThat(calculator.userFactor(users)).isEqualTo(expectedFactor);
    }

    @Test
    @DisplayName("Many affected users can lift an incident to a higher priority")
    void manyUsersRaisePriority() {
        // EXTENSIVE(4) × MEDIUM(2) × HIGH(3) = 24: P2 for 5 users; × 1.5 for 500 users = 36 → P1
        assertThat(calculate(Impact.EXTENSIVE, Urgency.MEDIUM, BusinessCriticality.HIGH, 5).getLevel())
                .isEqualTo(PriorityLevel.P2);
        assertThat(calculate(Impact.EXTENSIVE, Urgency.MEDIUM, BusinessCriticality.HIGH, 500).getLevel())
                .isEqualTo(PriorityLevel.P1);
    }

    @Test
    @DisplayName("Inconsistent configurations are rejected")
    void invalidConfigRejected() {
        assertThatThrownBy(() -> new WeightedScoringConfig(10, 20, 5, 10, 100, 1.25, 1.5))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> new WeightedScoringConfig(36, 18, 8, 100, 10, 1.25, 1.5))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> new WeightedScoringConfig(36, 18, 8, 10, 100, 0.5, 1.5))
                .isInstanceOf(InvalidInputException.class);
    }
}
