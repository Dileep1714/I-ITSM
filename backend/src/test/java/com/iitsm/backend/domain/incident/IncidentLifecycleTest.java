package com.iitsm.backend.domain.incident;

import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.common.exception.BusinessRuleException;
import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.common.exception.InvalidStateTransitionException;
import com.iitsm.backend.common.exception.TechnicianUnavailableException;
import com.iitsm.backend.common.exception.WorkloadExceededException;
import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.sla.SlaStatus;
import com.iitsm.backend.domain.user.Availability;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.iitsm.backend.domain.TestFixtures.T0;
import static com.iitsm.backend.domain.TestFixtures.category;
import static com.iitsm.backend.domain.TestFixtures.employee;
import static com.iitsm.backend.domain.TestFixtures.minutesAfterT0;
import static com.iitsm.backend.domain.TestFixtures.p1Policy;
import static com.iitsm.backend.domain.TestFixtures.p1Priority;
import static com.iitsm.backend.domain.TestFixtures.service;
import static com.iitsm.backend.domain.TestFixtures.technician;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The incident lifecycle (State pattern) and the business rules attached to each transition. */
class IncidentLifecycleTest {

    private Employee reporter;
    private Employee otherEmployee;
    private Technician technician;
    private Technician otherTechnician;
    private Category application;
    private ItService payment;
    private SlaPolicy p1Policy;
    private Incident incident;

    @BeforeEach
    void setUp() {
        reporter = employee("Asha Rao");
        otherEmployee = employee("Vikram Shah");
        technician = technician("Ravi Kumar", 5);
        otherTechnician = technician("Meera Iyer", 5);
        application = category("Application");
        payment = service("Payment", BusinessCriticality.MISSION_CRITICAL);
        p1Policy = p1Policy();
        incident = newIncident("INC-2026-000001");
    }

    private Incident newIncident(String number) {
        return Incident.builder()
                .number(number)
                .title("Payment application unavailable")
                .description("Users are unable to complete transactions since 09:55.")
                .reporter(reporter)
                .service(payment)
                .impact(Impact.HIGH)
                .urgency(Urgency.CRITICAL)
                .affectedUsers(200)
                .reportedAt(T0)
                .build();
    }

    /** Classified at +1, assigned at +2, acknowledged at +3, work started at +4 minutes. */
    private void bringToInProgress() {
        incident.classify(application, p1Priority(), p1Policy, minutesAfterT0(1));
        incident.assign(technician, 0, minutesAfterT0(2));
        incident.acknowledge(technician, minutesAfterT0(3));
        incident.startWork(technician, minutesAfterT0(4));
    }

    private void bringToResolved() {
        bringToInProgress();
        incident.resolve(technician, "Restarted payment gateway pool", "Connection pool exhausted",
                ResolutionCode.WORKAROUND, minutesAfterT0(20));
    }

    // ----- happy path --------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Full lifecycle: NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED")
    void fullLifecycle() {
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.NEW);

        bringToResolved();
        incident.acceptResolution(reporter, 5, "Works again, thanks", minutesAfterT0(25));
        incident.close(minutesAfterT0(30));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getClosedAt()).isEqualTo(minutesAfterT0(30));
        assertThat(incident.getSla().getRespondedAt()).isEqualTo(minutesAfterT0(3));
        assertThat(incident.slaStatus(minutesAfterT0(30))).isEqualTo(SlaStatus.MET);
        assertThat(incident.getFeedback().getRating()).isEqualTo(5);
        assertThat(incident.allowedOperations()).isEmpty();
        assertThat(incident.isOpen()).isFalse();
    }

    @Test
    @DisplayName("Each state exposes exactly the operations it allows")
    void allowedOperationsFollowTheState() {
        assertThat(incident.allowedOperations())
                .containsExactlyInAnyOrder(IncidentOperation.CLASSIFY, IncidentOperation.CANCEL,
                        IncidentOperation.MARK_DUPLICATE);

        incident.classify(application, p1Priority(), p1Policy, minutesAfterT0(1));
        assertThat(incident.allowedOperations())
                .containsExactlyInAnyOrder(IncidentOperation.ASSIGN, IncidentOperation.CANCEL,
                        IncidentOperation.MARK_DUPLICATE, IncidentOperation.TAKE);

        incident.assign(technician, 0, minutesAfterT0(2));
        assertThat(incident.allowedOperations())
                .containsExactlyInAnyOrder(IncidentOperation.ACKNOWLEDGE, IncidentOperation.ASSIGN,
                        IncidentOperation.MARK_DUPLICATE);
    }

    // ----- invalid transitions -----------------------------------------------------------------------------------

    @Test
    @DisplayName("NEW → CLOSED is rejected and the incident is left unchanged")
    void newCannotBeClosed() {
        assertThatThrownBy(() -> incident.close(minutesAfterT0(1)))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("CLOSE")
                .hasMessageContaining("NEW");

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.NEW);
        assertThat(incident.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("An unclassified incident cannot be assigned")
    void newCannotBeAssigned() {
        assertThatThrownBy(() -> incident.assign(technician, 0, minutesAfterT0(1)))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(incident.getAssignee()).isNull();
    }

    @Test
    @DisplayName("A resolved incident cannot be resolved again")
    void cannotResolveTwice() {
        bringToResolved();

        assertThatThrownBy(() -> incident.resolve(technician, "Again", null, ResolutionCode.PERMANENT_FIX,
                minutesAfterT0(21)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ----- classification ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("Classification starts the SLA clock from the moment the incident was reported")
    void slaClockStartsAtReportTime() {
        incident.classify(application, p1Priority(), p1Policy, minutesAfterT0(1));

        assertThat(incident.getSla().getStartedAt()).isEqualTo(T0);
        assertThat(incident.getSla().responseDue()).isEqualTo(minutesAfterT0(5));
        assertThat(incident.getSla().resolutionDue(minutesAfterT0(1))).isEqualTo(minutesAfterT0(30));
    }

    @Test
    @DisplayName("An inactive category cannot be used")
    void inactiveCategoryRejected() {
        application.deactivate();

        assertThatThrownBy(() -> incident.classify(application, p1Priority(), p1Policy, minutesAfterT0(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer in use");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.NEW);
    }

    @Test
    @DisplayName("An SLA policy for a different priority is rejected")
    void mismatchedSlaPolicyRejected() {
        SlaPolicy p2Policy = SlaPolicy.standard(PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2));

        assertThatThrownBy(() -> incident.classify(application, p1Priority(), p2Policy, minutesAfterT0(1)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("SLA_POLICY_MISMATCH");
    }

    // ----- assignment --------------------------------------------------------------------------------------------

    @Test
    @DisplayName("A technician on leave cannot be assigned")
    void unavailableTechnicianRejected() {
        incident.classify(application, p1Priority(), p1Policy, minutesAfterT0(1));
        technician.changeAvailability(Availability.ON_LEAVE);

        assertThatThrownBy(() -> incident.assign(technician, 0, minutesAfterT0(2)))
                .isInstanceOf(TechnicianUnavailableException.class);
    }

    @Test
    @DisplayName("Workload boundary: a technician with capacity 5 can take a 5th incident but not a 6th")
    void workloadLimitEnforced() {
        incident.classify(application, p1Priority(), p1Policy, minutesAfterT0(1));

        assertThatThrownBy(() -> incident.assign(technician, 5, minutesAfterT0(2)))
                .isInstanceOf(WorkloadExceededException.class);

        incident.assign(technician, 4, minutesAfterT0(2));
        assertThat(incident.getAssignee()).isSameAs(technician);
    }

    @Test
    @DisplayName("Reassigning during work hands over to the new technician but keeps the original response time")
    void reassignmentKeepsResponseTime() {
        bringToInProgress();

        incident.assign(otherTechnician, 0, minutesAfterT0(10));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ASSIGNED);
        assertThat(incident.getAssignee()).isSameAs(otherTechnician);
        assertThat(incident.getSla().getRespondedAt()).isEqualTo(minutesAfterT0(3));
    }

    @Test
    @DisplayName("Assigning to the current assignee again is rejected")
    void sameTechnicianTwiceRejected() {
        incident.classify(application, p1Priority(), p1Policy, minutesAfterT0(1));
        incident.assign(technician, 0, minutesAfterT0(2));

        assertThatThrownBy(() -> incident.assign(technician, 1, minutesAfterT0(3)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ALREADY_ASSIGNED");
    }

    // ----- authorization -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("Only the assigned technician can acknowledge")
    void onlyAssigneeCanAcknowledge() {
        incident.classify(application, p1Priority(), p1Policy, minutesAfterT0(1));
        incident.assign(technician, 0, minutesAfterT0(2));

        assertThatThrownBy(() -> incident.acknowledge(otherTechnician, minutesAfterT0(3)))
                .isInstanceOf(AuthorizationException.class);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ASSIGNED);
        assertThat(incident.getSla().getRespondedAt()).isNull();
    }

    @Test
    @DisplayName("Only the reporter can accept a resolution")
    void onlyReporterCanAccept() {
        bringToResolved();

        assertThatThrownBy(() -> incident.acceptResolution(otherEmployee, 5, null, minutesAfterT0(25)))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Employees cannot write internal notes or comment on other people's incidents")
    void employeeNoteRules() {
        assertThatThrownBy(() -> incident.addNote(reporter, "Secret", true, minutesAfterT0(1)))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> incident.addNote(otherEmployee, "Me too", false, minutesAfterT0(1)))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Internal notes are hidden from the reporter but visible to technicians")
    void internalNotesHiddenFromEmployees() {
        incident.addNote(technician, "Suspect DB connection pool", true, minutesAfterT0(1));
        incident.addNote(reporter, "It also fails on mobile", false, minutesAfterT0(2));

        assertThat(incident.notesVisibleTo(reporter)).hasSize(1);
        assertThat(incident.notesVisibleTo(technician)).hasSize(2);
    }

    // ----- SLA behaviour -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("Acknowledging after the 5-minute response target breaches the SLA")
    void lateAcknowledgementBreachesSla() {
        incident.classify(application, p1Priority(), p1Policy, minutesAfterT0(1));
        incident.assign(technician, 0, minutesAfterT0(2));
        incident.acknowledge(technician, minutesAfterT0(6));

        assertThat(incident.getSla().isResponseBreached(minutesAfterT0(6))).isTrue();
        assertThat(incident.slaStatus(minutesAfterT0(6))).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    @DisplayName("Waiting for the reporter pauses the SLA: 15 minutes on hold moves the deadline by 15 minutes")
    void onHoldPausesSla() {
        bringToInProgress();

        incident.requestInfo(technician, "Which browser are you using?", minutesAfterT0(10));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ON_HOLD);
        assertThat(incident.slaStatus(minutesAfterT0(20))).isEqualTo(SlaStatus.PAUSED);

        incident.provideInfo(reporter, "Chrome 131", minutesAfterT0(25));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(incident.getSla().resolutionDue(minutesAfterT0(25))).isEqualTo(minutesAfterT0(45));
        assertThat(incident.getOpenInfoRequest()).isEmpty();
        assertThat(incident.getInfoRequests().get(0).getAnswer()).isEqualTo("Chrome 131");
    }

    @Test
    @DisplayName("Only the reporter can answer a technician's question")
    void onlyReporterCanProvideInfo() {
        bringToInProgress();
        incident.requestInfo(technician, "Which browser?", minutesAfterT0(10));

        assertThatThrownBy(() -> incident.provideInfo(otherEmployee, "Firefox", minutesAfterT0(11)))
                .isInstanceOf(AuthorizationException.class);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ON_HOLD);
    }

    // ----- verification ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Rejecting a resolution reopens the incident and clears the resolution")
    void rejectionReopens() {
        bringToResolved();

        incident.rejectResolution(reporter, "Still failing for card payments", minutesAfterT0(25));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(incident.getResolution()).isNull();
        assertThat(incident.getSla().getResolvedAt()).isNull();
        assertThat(incident.getFeedback().isAccepted()).isFalse();
        assertThat(incident.getNotes()).anyMatch(note -> note.getText().contains("Still failing"));
    }

    @Test
    @DisplayName("Auto-verification boundary: rejected at 71 h after resolution, allowed at 72 h")
    void autoVerifyAfter72Hours() {
        bringToResolved();
        var resolvedAt = minutesAfterT0(20);

        assertThatThrownBy(() -> incident.autoVerify(resolvedAt.plus(Duration.ofHours(71))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("AUTO_VERIFY_TOO_EARLY");

        incident.autoVerify(resolvedAt.plus(Duration.ofHours(72)));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.VERIFIED);
    }

    @Test
    @DisplayName("Rating outside 1–5 is rejected when accepting a resolution")
    void invalidRatingRejected() {
        bringToResolved();

        assertThatThrownBy(() -> incident.acceptResolution(reporter, 6, null, minutesAfterT0(25)))
                .isInstanceOf(InvalidInputException.class);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
    }

    // ----- duplicates & cancellation -----------------------------------------------------------------------------

    @Test
    @DisplayName("A duplicate is cancelled and linked to the original incident")
    void markDuplicate() {
        Incident original = newIncident("INC-2026-000002");

        incident.markDuplicateOf(original, minutesAfterT0(1));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CANCELLED);
        assertThat(incident.getDuplicateOf()).isSameAs(original);
        assertThat(incident.getCancellationReason()).contains("INC-2026-000002");
    }

    @Test
    @DisplayName("An incident cannot duplicate itself or an incident that is no longer open")
    void invalidDuplicateLinksRejected() {
        Incident cancelled = newIncident("INC-2026-000003");
        cancelled.cancel("Reported by mistake", minutesAfterT0(1));

        assertThatThrownBy(() -> incident.markDuplicateOf(incident, minutesAfterT0(2)))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> incident.markDuplicateOf(cancelled, minutesAfterT0(2)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ORIGINAL_NOT_OPEN");
    }

    // ----- creation ----------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Creating an incident")
    class Creation {

        @Test
        @DisplayName("A new incident starts in NEW with no SLA, priority or assignee")
        void newIncidentDefaults() {
            assertThat(incident.getStatus()).isEqualTo(IncidentStatus.NEW);
            assertThat(incident.getPriority()).isNull();
            assertThat(incident.getAssignee()).isNull();
            assertThat(incident.slaStatus(T0)).isEqualTo(SlaStatus.NOT_STARTED);
        }

        @Test
        @DisplayName("Description shorter than 10 characters is rejected")
        void shortDescriptionRejected() {
            assertThatThrownBy(() -> Incident.builder().number("INC-2026-000009").title("Down")
                    .description("broken").reporter(reporter).service(payment)
                    .impact(Impact.LOW).urgency(Urgency.LOW).reportedAt(T0).build())
                    .isInstanceOf(InvalidInputException.class);
        }

        @Test
        @DisplayName("Malformed incident numbers and zero affected users are rejected")
        void invalidFieldsRejected() {
            assertThatThrownBy(() -> newIncidentWith("INC-1", 1)).isInstanceOf(InvalidInputException.class);
            assertThatThrownBy(() -> newIncidentWith("INC-2026-000010", 0)).isInstanceOf(InvalidInputException.class);
        }

        @Test
        @DisplayName("A deactivated employee cannot report incidents")
        void inactiveReporterRejected() {
            reporter.deactivate();

            assertThatThrownBy(() -> newIncident("INC-2026-000011"))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo("REPORTER_INACTIVE");
        }

        private Incident newIncidentWith(String number, int affectedUsers) {
            return Incident.builder().number(number).title("Payment down")
                    .description("Users cannot pay at checkout.").reporter(reporter).service(payment)
                    .impact(Impact.HIGH).urgency(Urgency.HIGH).affectedUsers(affectedUsers).reportedAt(T0).build();
        }
    }
}
