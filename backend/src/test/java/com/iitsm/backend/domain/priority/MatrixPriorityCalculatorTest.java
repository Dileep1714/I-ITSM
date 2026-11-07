package com.iitsm.backend.domain.priority;

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

class MatrixPriorityCalculatorTest {

    private final MatrixPriorityCalculator calculator = new MatrixPriorityCalculator();

    private Priority calculate(Impact impact, Urgency urgency, BusinessCriticality criticality, int users) {
        return calculator.calculate(new PriorityFactors(impact, urgency, criticality, users));
    }

    @ParameterizedTest(name = "{0} × {1} → {2}")
    @CsvSource({
            "LOW, LOW, P4", "LOW, CRITICAL, P3", "MEDIUM, MEDIUM, P3", "MEDIUM, CRITICAL, P2",
            "HIGH, HIGH, P2", "HIGH, CRITICAL, P1", "EXTENSIVE, MEDIUM, P2", "EXTENSIVE, HIGH, P1",
    })
    @DisplayName("Base matrix lookup (medium-criticality service, few users: no adjustment)")
    void baseMatrix(Impact impact, Urgency urgency, PriorityLevel expected) {
        assertThat(calculate(impact, urgency, BusinessCriticality.MEDIUM, 1).getLevel()).isEqualTo(expected);
    }

    @Test
    @DisplayName("A mission-critical service raises the priority one level; a low-criticality one lowers it")
    void criticalityAdjustsOneLevel() {
        assertThat(calculate(Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.MISSION_CRITICAL, 1).getLevel())
                .isEqualTo(PriorityLevel.P2);
        assertThat(calculate(Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.LOW, 1).getLevel())
                .isEqualTo(PriorityLevel.P4);
    }

    @Test
    @DisplayName("Adjustments never go beyond P1 or P4")
    void adjustmentsAreCapped() {
        Priority top = calculate(Impact.EXTENSIVE, Urgency.CRITICAL, BusinessCriticality.MISSION_CRITICAL, 500);
        Priority bottom = calculate(Impact.LOW, Urgency.LOW, BusinessCriticality.LOW, 1);

        assertThat(top.getLevel()).isEqualTo(PriorityLevel.P1);
        assertThat(top.getScore()).isEqualTo(4.0);
        assertThat(bottom.getLevel()).isEqualTo(PriorityLevel.P4);
        assertThat(bottom.getScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("More than 100 affected users raises one level, and the rationale says so")
    void manyUsersRaiseOneLevel() {
        Priority hundred = calculate(Impact.HIGH, Urgency.HIGH, BusinessCriticality.MEDIUM, 100);
        Priority hundredOne = calculate(Impact.HIGH, Urgency.HIGH, BusinessCriticality.MEDIUM, 101);

        assertThat(hundred.getLevel()).isEqualTo(PriorityLevel.P2);
        assertThat(hundredOne.getLevel()).isEqualTo(PriorityLevel.P1);
        assertThat(hundredOne.getRationale()).contains("101 users affected", "→ P1");
    }
}
