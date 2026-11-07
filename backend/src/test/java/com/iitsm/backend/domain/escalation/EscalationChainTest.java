package com.iitsm.backend.domain.escalation;

import com.iitsm.backend.common.exception.BusinessRuleException;
import com.iitsm.backend.domain.assignment.AssignmentCandidate;
import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentChange;
import com.iitsm.backend.domain.incident.IncidentEventType;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.incident.Urgency;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static com.iitsm.backend.domain.TestFixtures.HASHER;
import static com.iitsm.backend.domain.TestFixtures.T0;
import static com.iitsm.backend.domain.TestFixtures.category;
import static com.iitsm.backend.domain.TestFixtures.employee;
import static com.iitsm.backend.domain.TestFixtures.minutesAfterT0;
import static com.iitsm.backend.domain.TestFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The escalation chain, Technician → Team Lead → System Admin → IT Manager, for a P1 with the spec's targets
 * (respond within 5 minutes, resolve within 30).
 */
class EscalationChainTest {

    private final EscalationHandler chain = EscalationChain.standard();
    private final Map<SupportLevel, Technician> staff = new EnumMap<>(SupportLevel.class);
    /** Fake context: one available technician per level (removable to simulate "nobody available"). */
    private final EscalationContext context = (level, incident) ->
            Optional.ofNullable(staff.get(level)).map(technician -> new AssignmentCandidate(technician, 0, 0));

    private Category application;
    private Incident incident;

    @BeforeEach
    void setUp() {
        application = category("Application");
        staff.put(SupportLevel.L1_TECHNICIAN, technician("Ravi Kumar", SupportLevel.L1_TECHNICIAN));
        staff.put(SupportLevel.L2_TEAM_LEAD, technician("Kavya Reddy", SupportLevel.L2_TEAM_LEAD));
        staff.put(SupportLevel.L3_SYSTEM_ADMIN, technician("Farhan Ali", SupportLevel.L3_SYSTEM_ADMIN));
        staff.put(SupportLevel.L4_IT_MANAGER, technician("Sunita Rao", SupportLevel.L4_IT_MANAGER));
        incident = incidentWithPriority(PriorityLevel.P1);
    }

    private static Technician technician(String name, SupportLevel level) {
        return new Technician(name, name.toLowerCase().replace(' ', '.') + "@example.com", "secret123", HASHER,
                level, 5, 5);
    }

    private Incident incidentWithPriority(PriorityLevel level) {
        Incident created = Incident.builder().number("INC-2026-000001").title("Payment down")
                .description("Users cannot complete transactions.").reporter(employee("Asha Rao"))
                .service(service("Payment", BusinessCriticality.MISSION_CRITICAL))
                .impact(Impact.HIGH).urgency(Urgency.CRITICAL).reportedAt(T0).build();
        created.classify(application, new Priority(level, 50, "test"),
                SlaPolicy.standard(level, Duration.ofMinutes(5), Duration.ofMinutes(30)), minutesAfterT0(0));
        created.assign(staff.get(SupportLevel.L1_TECHNICIAN), 0, minutesAfterT0(0));
        created.pullChanges();
        return created;
    }

    @Test
    @DisplayName("Within targets: no escalation")
    void noEscalationWhenOnTrack() {
        assertThat(chain.handle(incident, context, minutesAfterT0(2))).isFalse();
        assertThat(incident.getEscalationLevel()).isNull();
    }

    @Test
    @DisplayName("Step 1: unacknowledged P1 at 80 % of the response time goes to the Team Lead")
    void stepOneTeamLead() {
        assertThat(chain.handle(incident, context, minutesAfterT0(4))).isTrue();

        assertThat(incident.getEscalationLevel()).isEqualTo(SupportLevel.L2_TEAM_LEAD);
        assertThat(incident.getAssignee().getFullName()).isEqualTo("Kavya Reddy");
        assertThat(incident.pullChanges()).extracting(IncidentChange::type)
                .containsExactly(IncidentEventType.ESCALATED, IncidentEventType.REASSIGNED);
    }

    @Test
    @DisplayName("Escalation climbs one level per check, never skipping: Team Lead → System Admin → IT Manager")
    void climbsOneLevelAtATime() {
        // At 6 minutes the response is already breached, but the first step is still the Team Lead.
        chain.handle(incident, context, minutesAfterT0(6));
        assertThat(incident.getEscalationLevel()).isEqualTo(SupportLevel.L2_TEAM_LEAD);

        chain.handle(incident, context, minutesAfterT0(7));
        assertThat(incident.getEscalationLevel()).isEqualTo(SupportLevel.L3_SYSTEM_ADMIN);
        assertThat(incident.getAssignee().getFullName()).isEqualTo("Farhan Ali");

        // IT Manager only for a P1 far past its resolution deadline (≥ 150 % of 30 minutes = 45 minutes).
        assertThat(chain.handle(incident, context, minutesAfterT0(40))).isFalse();
        assertThat(chain.handle(incident, context, minutesAfterT0(46))).isTrue();
        assertThat(incident.getEscalationLevel()).isEqualTo(SupportLevel.L4_IT_MANAGER);

        // Top of the chain: nothing further.
        assertThat(chain.handle(incident, context, minutesAfterT0(120))).isFalse();
    }

    @Test
    @DisplayName("Low-priority incidents are never escalated")
    void lowPriorityNotEscalated() {
        Incident p3 = incidentWithPriority(PriorityLevel.P3);

        assertThat(chain.handle(p3, context, minutesAfterT0(500))).isFalse();
    }

    @Test
    @DisplayName("An acknowledged incident is not escalated for its response time")
    void acknowledgedNotEscalatedForResponse() {
        incident.acknowledge(staff.get(SupportLevel.L1_TECHNICIAN), minutesAfterT0(1));

        assertThat(chain.handle(incident, context, minutesAfterT0(10))).isFalse(); // 33 % of resolution time
    }

    @Test
    @DisplayName("If nobody is available at the next level, the escalation is still recorded (level is notified)")
    void escalatesWithoutReassignmentWhenNobodyAvailable() {
        staff.remove(SupportLevel.L2_TEAM_LEAD);

        assertThat(chain.handle(incident, context, minutesAfterT0(4))).isTrue();

        assertThat(incident.getEscalationLevel()).isEqualTo(SupportLevel.L2_TEAM_LEAD);
        assertThat(incident.getAssignee().getFullName()).isEqualTo("Ravi Kumar");
    }

    @Test
    @DisplayName("An incident cannot be escalated to a level it is already at")
    void cannotEscalateSideways() {
        assertThatThrownBy(() -> incident.escalate(SupportLevel.L1_TECHNICIAN, null, 0, "test", minutesAfterT0(1)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ESCALATION_NOT_HIGHER");
    }
}
