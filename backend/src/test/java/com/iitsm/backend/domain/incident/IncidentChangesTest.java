package com.iitsm.backend.domain.incident;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.iitsm.backend.domain.TestFixtures.T0;
import static com.iitsm.backend.domain.TestFixtures.category;
import static com.iitsm.backend.domain.TestFixtures.employee;
import static com.iitsm.backend.domain.TestFixtures.minutesAfterT0;
import static com.iitsm.backend.domain.TestFixtures.p1Policy;
import static com.iitsm.backend.domain.TestFixtures.p1Priority;
import static com.iitsm.backend.domain.TestFixtures.service;
import static com.iitsm.backend.domain.TestFixtures.technician;
import static org.assertj.core.api.Assertions.assertThat;

/** The incident records what happened to it, for the audit trail and notifications (Observer pattern input). */
class IncidentChangesTest {

    private Employee reporter;
    private Technician ravi;
    private Technician meera;
    private Category application;
    private ItService payment;
    private Incident incident;

    @BeforeEach
    void setUp() {
        reporter = employee("Asha Rao");
        ravi = technician("Ravi Kumar", 5);
        meera = technician("Meera Iyer", 5);
        application = category("Application");
        payment = service("Payment", BusinessCriticality.MISSION_CRITICAL);
        incident = Incident.builder().number("INC-2026-000001").title("Payment down")
                .description("Users cannot complete transactions.").reporter(reporter).service(payment)
                .impact(Impact.HIGH).urgency(Urgency.CRITICAL).reportedAt(T0).build();
    }

    private static List<IncidentEventType> types(List<IncidentChange> changes) {
        return changes.stream().map(IncidentChange::type).toList();
    }

    @Test
    @DisplayName("Every step of the lifecycle is recorded, in order, with old and new status")
    void lifecycleIsRecorded() {
        incident.classify(application, p1Priority(), p1Policy(), minutesAfterT0(1));
        incident.assign(ravi, 0, minutesAfterT0(2));
        incident.acknowledge(ravi, minutesAfterT0(3));

        List<IncidentChange> changes = incident.pullChanges();

        assertThat(types(changes)).containsExactly(IncidentEventType.CREATED, IncidentEventType.CLASSIFIED,
                IncidentEventType.ASSIGNED, IncidentEventType.ACKNOWLEDGED);
        IncidentChange assigned = changes.get(2);
        assertThat(assigned.previousStatus()).isEqualTo(IncidentStatus.CLASSIFIED);
        assertThat(assigned.newStatus()).isEqualTo(IncidentStatus.ASSIGNED);
        assertThat(assigned.description()).isEqualTo("Assigned to Ravi Kumar");
        assertThat(assigned.occurredAt()).isEqualTo(minutesAfterT0(2));
    }

    @Test
    @DisplayName("pullChanges() hands each change over once")
    void pullClears() {
        assertThat(incident.pullChanges()).hasSize(1);
        assertThat(incident.pullChanges()).isEmpty();
    }

    @Test
    @DisplayName("A second assignment is recorded as a reassignment naming both technicians")
    void reassignment() {
        incident.classify(application, p1Priority(), p1Policy(), minutesAfterT0(1));
        incident.assign(ravi, 0, minutesAfterT0(2));
        incident.pullChanges();

        incident.assign(meera, 0, minutesAfterT0(3));

        IncidentChange change = incident.pullChanges().get(0);
        assertThat(change.type()).isEqualTo(IncidentEventType.REASSIGNED);
        assertThat(change.description()).contains("Ravi Kumar", "Meera Iyer");
    }

    @Test
    @DisplayName("Internal notes and SLA signals are marked as hidden from the reporter")
    void staffOnlyChangesAreMarked() {
        incident.classify(application, p1Priority(), p1Policy(), minutesAfterT0(1));
        incident.addNote(ravi, "Suspect the DB pool", true, minutesAfterT0(2));
        incident.addNote(reporter, "Still failing", false, minutesAfterT0(3));
        incident.evaluateSla(minutesAfterT0(6)); // 5-minute response target missed
        List<IncidentChange> changes = incident.pullChanges();

        assertThat(changes).filteredOn(c -> c.type() == IncidentEventType.NOTE_ADDED)
                .extracting(IncidentChange::visibleToReporter).containsExactly(false, true);
        assertThat(changes).filteredOn(c -> c.type() == IncidentEventType.SLA_BREACHED)
                .singleElement().extracting(IncidentChange::visibleToReporter).isEqualTo(false);
    }

    @Test
    @DisplayName("A failed operation records nothing")
    void failedOperationRecordsNothing() {
        incident.pullChanges();

        try {
            incident.close(minutesAfterT0(1)); // NEW → CLOSED is not allowed
        } catch (RuntimeException expected) {
            // ignored: this test is about the side effects
        }

        assertThat(incident.pullChanges()).isEmpty();
    }
}
