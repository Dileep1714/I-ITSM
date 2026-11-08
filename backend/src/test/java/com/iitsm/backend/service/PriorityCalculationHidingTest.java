package com.iitsm.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** History written before the priority calculation became staff-only is cleaned when an employee reads it. */
class PriorityCalculationHidingTest {

    @Test
    @DisplayName("The bracketed formula is removed from old history text; everything else is kept")
    void stripsOldFormula() {
        assertThat(IncidentService.withoutCalculation("Classified as Network, priority P1 Critical (Impact EXTENSIVE(4) "
                + "× Urgency CRITICAL(4) × Criticality HIGH(3) × 300 users (×1.50) = 72.0 → P1 Critical)"))
                .isEqualTo("Classified as Network, priority P1 Critical");
        assertThat(IncidentService.withoutCalculation("Priority changed from P3 to P2 (Matrix: Impact HIGH × Urgency LOW = 3 → P2 High)"))
                .isEqualTo("Priority changed from P3 to P2");
        assertThat(IncidentService.withoutCalculation("Assigned to Ravi Kumar (team lead request)"))
                .isEqualTo("Assigned to Ravi Kumar (team lead request)");
        assertThat(IncidentService.withoutCalculation(null)).isNull();
    }
}
