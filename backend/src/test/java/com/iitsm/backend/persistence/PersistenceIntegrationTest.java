package com.iitsm.backend.persistence;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.incident.ResolutionCode;
import com.iitsm.backend.domain.incident.Urgency;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.sla.SlaStatus;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * Integration test against the real PostgreSQL database (start it with {@code docker compose up -d}).
 *
 * <p>Saves domain objects, clears Hibernate's cache, reads them back and checks nothing was lost. This catches
 * mapping problems that schema validation alone cannot: value objects that reload as {@code null}, collections
 * that are not cascaded, CHECK constraints that reject legitimate data…
 *
 * <p>{@code @Transactional} rolls everything back after each test, so the database stays clean.
 */
@SpringBootTest
@Transactional
class PersistenceIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    private Employee reporter;
    private Technician technician;
    private Category application;
    private ItService payment;
    private ItService database;
    private SlaPolicy policy;

    @BeforeEach
    void setUp() {
        reporter = employee("Asha Rao");
        application = category("Application");
        application.addKeyword("checkout");
        technician = technician("Ravi Kumar", 5);
        technician.addOrUpdateSkill(application, 4);
        database = service("Customer Database", BusinessCriticality.MISSION_CRITICAL);
        payment = service("Payment", BusinessCriticality.MISSION_CRITICAL);
        payment.addDependency(database);
        policy = p1Policy();

        persistAll(reporter, application, technician, database, payment, policy);
    }

    private void persistAll(Object... entities) {
        for (Object entity : entities) {
            entityManager.persist(entity);
        }
        entityManager.flush();
    }

    /** Writes pending changes and empties the cache, so the next read really comes from the database. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Incident newIncident() {
        return Incident.builder()
                .number("INC-2026-000001")
                .title("Payment application unavailable")
                .description("Users are unable to complete transactions.")
                .reporter(reporter).service(payment)
                .impact(Impact.HIGH).urgency(Urgency.CRITICAL).affectedUsers(200)
                .reportedAt(T0)
                .build();
    }

    @Test
    @DisplayName("An incident survives a full save/reload cycle at every stage of its lifecycle")
    void incidentRoundTrip() {
        Incident incident = newIncident();
        entityManager.persist(incident);

        incident.classify(application, p1Priority(), policy, minutesAfterT0(1));
        incident.assign(technician, 0, minutesAfterT0(2));
        incident.acknowledge(technician, minutesAfterT0(3));
        incident.startWork(technician, minutesAfterT0(4));
        incident.addNote(technician, "Gateway connection pool exhausted", true, minutesAfterT0(5));
        incident.requestInfo(technician, "Does it fail for card payments too?", minutesAfterT0(6));
        incident.provideInfo(reporter, "Yes, all payment types", minutesAfterT0(10));
        incident.resolve(technician, "Increased pool size and restarted gateway", "Pool too small for peak load",
                ResolutionCode.CONFIGURATION_CHANGE, minutesAfterT0(20));
        Long id = incident.getId();
        flushAndClear();

        Incident loaded = entityManager.find(Incident.class, id);

        assertThat(loaded.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(loaded.getNumber()).isEqualTo("INC-2026-000001");
        assertThat(loaded.getPriority().getLevel()).isEqualTo(PriorityLevel.P1);
        assertThat(loaded.getPriority().getRationale()).contains("Impact HIGH");
        assertThat(loaded.getCategory().getName()).isEqualTo("Application");
        assertThat(loaded.getAssignee().getFullName()).isEqualTo("Ravi Kumar");
        assertThat(loaded.getReporter().getEmail()).isEqualTo("asha.rao@example.com");

        // SLA tracker (embedded value object), including the 4-minute pause
        assertThat(loaded.getSla().getStartedAt()).isEqualTo(T0);
        assertThat(loaded.getSla().getRespondedAt()).isEqualTo(minutesAfterT0(3));
        assertThat(loaded.getSla().getResolvedAt()).isEqualTo(minutesAfterT0(20));
        assertThat(loaded.getSla().getPolicyId()).isEqualTo(policy.getId());
        assertThat(loaded.getSla().resolutionDue(minutesAfterT0(20))).isEqualTo(minutesAfterT0(34));
        assertThat(loaded.slaStatus(minutesAfterT0(20))).isEqualTo(SlaStatus.MET);

        // Owned collections and the resolution value object
        assertThat(loaded.getNotes()).hasSize(1);
        assertThat(loaded.getNotes().get(0).isInternal()).isTrue();
        assertThat(loaded.getInfoRequests()).hasSize(1);
        assertThat(loaded.getInfoRequests().get(0).getAnswer()).isEqualTo("Yes, all payment types");
        assertThat(loaded.getResolution().getCode()).isEqualTo(ResolutionCode.CONFIGURATION_CHANGE);
        assertThat(loaded.getResolution().getResolvedBy().getFullName()).isEqualTo("Ravi Kumar");
        assertThat(loaded.getFeedback()).isNull();

        // Continue the lifecycle on the reloaded object: the state is rebuilt from the stored status.
        loaded.acceptResolution(reporter(loaded), 5, "Fixed", minutesAfterT0(25));
        loaded.close(minutesAfterT0(30));
        flushAndClear();

        Incident closed = entityManager.find(Incident.class, id);
        assertThat(closed.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(closed.getFeedback().getRating()).isEqualTo(5);
        assertThat(closed.getClosedAt()).isEqualTo(minutesAfterT0(30));
        assertThat(closed.allowedOperations()).isEmpty();
    }

    /** The reporter as loaded in the current persistence context (a fresh object after clear()). */
    private Employee reporter(Incident incident) {
        return incident.getReporter();
    }

    @Test
    @DisplayName("Users, skills, keywords and service dependencies reload correctly (inheritance + collections)")
    void catalogueAndUsersRoundTrip() {
        Long technicianId = technician.getId();
        Long paymentId = payment.getId();
        flushAndClear();

        Technician loadedTechnician = entityManager.find(Technician.class, technicianId);
        ItService loadedPayment = entityManager.find(ItService.class, paymentId);

        assertThat(loadedTechnician.getSkills()).hasSize(1);
        assertThat(loadedTechnician.getSkills().get(0).getCategory().getKeywords()).containsExactly("checkout");
        assertThat(loadedTechnician.proficiencyIn(loadedTechnician.getSkills().get(0).getCategory())).isEqualTo(4);
        assertThat(loadedPayment.getDependsOn()).extracting(ItService::getName).containsExactly("Customer Database");
        assertThat(loadedPayment.dependsOnTransitively(entityManager.find(ItService.class, database.getId())))
                .isTrue();
    }

    @Test
    @DisplayName("The database itself rejects invalid data, even if it bypasses the Java checks (defence in depth)")
    void databaseConstraintsHold() {
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("UPDATE technicians SET max_active_incidents = 0 WHERE id = :id")
                    .setParameter("id", technician.getId())
                    .executeUpdate();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("Two generic SLA policies for the same priority are rejected by the database")
    void duplicateGenericSlaPolicyRejected() {
        // With IDENTITY ids Hibernate inserts immediately on persist(), so the violation can surface there or on flush.
        assertThatThrownBy(() -> {
            entityManager.persist(p1Policy());
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class)
                .hasMessageContaining("uq_sla_policies_generic");
    }
}
