package com.iitsm.backend.api;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.sla.SlaViolation;
import com.iitsm.backend.domain.user.Administrator;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.PasswordHasher;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.repository.SlaPolicyRepository;
import com.iitsm.backend.repository.SlaViolationRepository;
import com.iitsm.backend.repository.UserRepository;
import com.iitsm.backend.service.SlaMonitorService;
import com.iitsm.backend.service.SlaMonitorService.CycleReport;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit trail, notifications, SLA monitoring and escalation, end to end against the database.
 *
 * <p>Team: Ravi (L1, Application 5), Kavya (L2 Team Lead, Application 3), Farhan (L3 System Admin),
 * Sunita (L4 IT Manager); administrator Meera; reporter Asha. P1 SLA: respond in 5 minutes, resolve in 30.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MonitoringIntegrationTest {

    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private ItServiceRepository services;
    @Autowired
    private SlaPolicyRepository slaPolicies;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private SlaViolationRepository violations;
    @Autowired
    private SlaMonitorService slaMonitor;
    @Autowired
    private PasswordHasher hasher;
    @Autowired
    private EntityManager entityManager;

    private Long applicationId;
    private Long paymentId;

    @BeforeEach
    void setUp() {
        Category application = categories.save(new Category("Application", "Business applications"));
        ItService payment = services.save(new ItService("Payment", "Checkout", BusinessCriticality.MISSION_CRITICAL));
        applicationId = application.getId();
        paymentId = payment.getId();
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30)));
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2)));
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P3, Duration.ofHours(1), Duration.ofHours(8)));
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P4, Duration.ofHours(4), Duration.ofHours(24)));

        users.save(new Employee("Asha Patel", "asha@test.com", PASSWORD, hasher, "Finance", null));
        users.save(new Administrator("Meera Iyer", "admin@test.com", PASSWORD, hasher));
        Technician ravi = technician("Ravi Kumar", "ravi", SupportLevel.L1_TECHNICIAN);
        ravi.addOrUpdateSkill(application, 5);
        Technician kavya = technician("Kavya Reddy", "kavya", SupportLevel.L2_TEAM_LEAD);
        kavya.addOrUpdateSkill(application, 3);
        users.save(ravi);
        users.save(kavya);
        users.save(technician("Farhan Ali", "farhan", SupportLevel.L3_SYSTEM_ADMIN));
        users.save(technician("Sunita Rao", "sunita", SupportLevel.L4_IT_MANAGER));
    }

    private Technician technician(String name, String login, SupportLevel level) {
        return new Technician(name, login + "@test.com", PASSWORD, hasher, level, 3, 5);
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", token));
    }

    private ResultActions postAs(String token, String url, String json) throws Exception {
        return mvc.perform(post(url).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Asha reports a P1 payment outage; it is auto-assigned to Ravi. Returns the incident id. */
    private long reportP1(String asha) throws Exception {
        String body = postAs(asha, "/api/incidents", """
                {"title": "Payment application unavailable",
                 "description": "Users are unable to complete transactions.",
                 "serviceId": %d, "categoryId": %d, "impact": "HIGH", "urgency": "CRITICAL", "affectedUsers": 200}
                """.formatted(paymentId, applicationId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignee.fullName").value("Ravi Kumar"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Incident reload(long id) {
        entityManager.flush();
        entityManager.clear();
        return incidents.findById(id).orElseThrow();
    }

    // ----- audit trail -------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Audit trail")
    class AuditTrail {

        @Test
        @DisplayName("Every step is recorded with who did it, the status change and a description")
        void historyIsComplete() throws Exception {
            String asha = login("asha@test.com");
            String ravi = login("ravi@test.com");
            long id = reportP1(asha);
            postAs(ravi, "/api/incidents/" + id + "/acknowledge", "").andExpect(status().isOk());

            getAs(ravi, "/api/incidents/" + id + "/history")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].action", contains("CREATED", "CLASSIFIED", "ASSIGNED", "ACKNOWLEDGED")))
                    .andExpect(jsonPath("$[0].actorName").value("Asha Patel"))
                    .andExpect(jsonPath("$[2].previousStatus").value("CLASSIFIED"))
                    .andExpect(jsonPath("$[2].newStatus").value("ASSIGNED"))
                    .andExpect(jsonPath("$[2].description").value("Assigned to Ravi Kumar"))
                    .andExpect(jsonPath("$[3].actorName").value("Ravi Kumar"));
        }

        @Test
        @DisplayName("The reporter's view of the history hides internal notes")
        void reporterViewHidesInternalEntries() throws Exception {
            String asha = login("asha@test.com");
            String ravi = login("ravi@test.com");
            long id = reportP1(asha);
            postAs(ravi, "/api/incidents/" + id + "/notes", "{\"text\": \"Check DB pool\", \"internal\": true}");

            getAs(ravi, "/api/incidents/" + id + "/history").andExpect(jsonPath("$[*].action", hasItem("NOTE_ADDED")));
            getAs(asha, "/api/incidents/" + id + "/history")
                    .andExpect(jsonPath("$[*].action", not(hasItem("NOTE_ADDED"))));
        }

        @Test
        @DisplayName("Administrators can browse the global audit log; employees cannot")
        void globalAuditLog() throws Exception {
            reportP1(login("asha@test.com"));

            getAs(login("admin@test.com"), "/api/admin/audit")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3));
            getAs(login("asha@test.com"), "/api/admin/audit").andExpect(status().isForbidden());
        }
    }

    // ----- notifications -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Notifications")
    class Notifications {

        @Test
        @DisplayName("Reporter and technician each get the notifications meant for them")
        void rightPeopleNotified() throws Exception {
            String asha = login("asha@test.com");
            String ravi = login("ravi@test.com");
            reportP1(asha);

            // Asha caused CREATED herself, so she is told only about the assignment.
            getAs(asha, "/api/notifications")
                    .andExpect(jsonPath("$.content[*].type", contains("ASSIGNED")))
                    .andExpect(jsonPath("$.content[0].read").value(false));
            getAs(ravi, "/api/notifications")
                    .andExpect(jsonPath("$.content[*].type", contains("ASSIGNED")))
                    .andExpect(jsonPath("$.content[0].title").value(org.hamcrest.Matchers.startsWith("Incident assigned")));
            getAs(login("kavya@test.com"), "/api/notifications").andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("When the technician acts, the reporter is notified, not the technician")
        void actorNotNotified() throws Exception {
            String asha = login("asha@test.com");
            String ravi = login("ravi@test.com");
            long id = reportP1(asha);

            postAs(ravi, "/api/incidents/" + id + "/acknowledge", "");

            getAs(asha, "/api/notifications").andExpect(jsonPath("$.content[0].type").value("ACKNOWLEDGED"));
            getAs(ravi, "/api/notifications").andExpect(jsonPath("$.content[*].type", not(hasItem("ACKNOWLEDGED"))));
        }

        @Test
        @DisplayName("Unread count, mark one read, mark all read")
        void readTracking() throws Exception {
            String asha = login("asha@test.com");
            String ravi = login("ravi@test.com");
            long id = reportP1(asha);
            postAs(ravi, "/api/incidents/" + id + "/acknowledge", "");
            postAs(ravi, "/api/incidents/" + id + "/start", "");

            getAs(asha, "/api/notifications/unread-count").andExpect(jsonPath("$.unread").value(3));
            String list = getAs(asha, "/api/notifications").andReturn().getResponse().getContentAsString();
            Integer firstId = JsonPath.read(list, "$.content[0].id");

            postAs(asha, "/api/notifications/" + firstId + "/read", "").andExpect(jsonPath("$.read").value(true));
            getAs(asha, "/api/notifications/unread-count").andExpect(jsonPath("$.unread").value(2));
            getAs(asha, "/api/notifications?unreadOnly=true").andExpect(jsonPath("$.totalElements").value(2));

            postAs(asha, "/api/notifications/read-all", "").andExpect(jsonPath("$.unread").value(0));
            getAs(asha, "/api/notifications/unread-count").andExpect(jsonPath("$.unread").value(0));
        }

        @Test
        @DisplayName("Nobody can mark someone else's notification as read")
        void cannotTouchOthersNotifications() throws Exception {
            String asha = login("asha@test.com");
            reportP1(asha);
            String list = getAs(asha, "/api/notifications").andReturn().getResponse().getContentAsString();
            Integer ashasNotification = JsonPath.read(list, "$.content[0].id");

            postAs(login("ravi@test.com"), "/api/notifications/" + ashasNotification + "/read", "")
                    .andExpect(status().isNotFound());
        }
    }

    // ----- SLA monitoring & escalation ---------------------------------------------------------------------------

    @Nested
    @DisplayName("SLA monitoring and escalation (Workflow 3)")
    class SlaMonitoring {

        @Test
        @DisplayName("Unacknowledged P1: warning + Team Lead at 4 min, breach + System Admin at 6 min, "
                + "IT Manager at 46 min")
        void escalationLadder() throws Exception {
            Instant start = Instant.now();
            long id = reportP1(login("asha@test.com"));

            CycleReport atFour = slaMonitor.runCycle(start.plus(Duration.ofMinutes(4)));
            assertThat(atFour.warnings()).isEqualTo(1);
            assertThat(atFour.escalations()).isEqualTo(1);
            Incident afterFour = reload(id);
            assertThat(afterFour.getEscalationLevel()).isEqualTo(SupportLevel.L2_TEAM_LEAD);
            assertThat(afterFour.getAssignee().getFullName()).isEqualTo("Kavya Reddy");

            CycleReport atSix = slaMonitor.runCycle(start.plus(Duration.ofMinutes(6)));
            assertThat(atSix.breaches()).isEqualTo(1);
            assertThat(atSix.escalations()).isEqualTo(1);
            Incident afterSix = reload(id);
            assertThat(afterSix.getEscalationLevel()).isEqualTo(SupportLevel.L3_SYSTEM_ADMIN);
            assertThat(afterSix.getAssignee().getFullName()).isEqualTo("Farhan Ali");
            assertThat(violations.findByIncident(afterSix)).extracting(SlaViolation::getKind)
                    .containsExactly(SlaViolation.Kind.RESPONSE);

            // Running again at the same moment changes nothing: every signal is raised once.
            CycleReport again = slaMonitor.runCycle(start.plus(Duration.ofMinutes(6)));
            assertThat(again.warnings() + again.breaches() + again.escalations()).isZero();

            slaMonitor.runCycle(start.plus(Duration.ofMinutes(46)));
            Incident afterFortySix = reload(id);
            assertThat(afterFortySix.getEscalationLevel()).isEqualTo(SupportLevel.L4_IT_MANAGER);
            assertThat(violations.findByIncident(afterFortySix)).extracting(SlaViolation::getKind)
                    .containsExactlyInAnyOrder(SlaViolation.Kind.RESPONSE, SlaViolation.Kind.RESOLUTION);

            getAs(login("ravi@test.com"), "/api/incidents/" + id + "/history")
                    .andExpect(jsonPath("$[*].action", hasItems("SLA_WARNING", "SLA_BREACHED", "ESCALATED",
                            "REASSIGNED")))
                    .andExpect(jsonPath("$[?(@.action == 'ESCALATED')].actorName", everyItem(
                            org.hamcrest.Matchers.is("System"))));
        }

        @Test
        @DisplayName("Breaches and escalations reach the administrator and the escalated team")
        void breachNotifiesAdminsAndLevel() throws Exception {
            Instant start = Instant.now();
            reportP1(login("asha@test.com"));

            slaMonitor.runCycle(start.plus(Duration.ofMinutes(4)));
            slaMonitor.runCycle(start.plus(Duration.ofMinutes(6)));

            getAs(login("admin@test.com"), "/api/notifications")
                    .andExpect(jsonPath("$.content[*].type", hasItems("SLA_BREACHED", "ESCALATED")));
            getAs(login("farhan@test.com"), "/api/notifications")
                    .andExpect(jsonPath("$.content[*].type", hasItems("ESCALATED", "REASSIGNED")));
            // The employee is never told about internal SLA signals or escalations.
            getAs(login("asha@test.com"), "/api/notifications")
                    .andExpect(jsonPath("$.content[*].type", not(hasItem("SLA_BREACHED"))))
                    .andExpect(jsonPath("$.content[*].type", not(hasItem("ESCALATED"))));
        }

        @Test
        @DisplayName("Resolved incidents the reporter ignores are auto-verified after 72 hours")
        void autoVerifyAfterThreeDays() throws Exception {
            String ravi = login("ravi@test.com");
            long id = reportP1(login("asha@test.com"));
            postAs(ravi, "/api/incidents/" + id + "/acknowledge", "");
            postAs(ravi, "/api/incidents/" + id + "/start", "");
            postAs(ravi, "/api/incidents/" + id + "/resolve",
                    "{\"summary\": \"Restarted gateway\", \"code\": \"WORKAROUND\"}").andExpect(status().isOk());

            CycleReport early = slaMonitor.runCycle(Instant.now().plus(Duration.ofHours(71)));
            assertThat(early.autoVerified()).isZero();
            CycleReport late = slaMonitor.runCycle(Instant.now().plus(Duration.ofHours(73)));
            assertThat(late.autoVerified()).isEqualTo(1);
            assertThat(reload(id).getStatus()).isEqualTo(IncidentStatus.VERIFIED);
        }

        @Test
        @DisplayName("Admins can run the monitor on demand and list SLA violations")
        void adminMonitoringEndpoints() throws Exception {
            String admin = login("admin@test.com");
            Instant start = Instant.now();
            reportP1(login("asha@test.com"));
            slaMonitor.runCycle(start.plus(Duration.ofMinutes(6)));

            postAs(admin, "/api/admin/sla-monitor/run", "").andExpect(status().isOk())
                    .andExpect(jsonPath("$.checked").value(1));
            getAs(admin, "/api/admin/sla-violations")
                    .andExpect(jsonPath("$.content[0].kind").value("RESPONSE"))
                    .andExpect(jsonPath("$.content[0].priority").value("P1"))
                    .andExpect(jsonPath("$.content[0].serviceName").value("Payment"));
            getAs(admin, "/api/admin/outbox").andExpect(jsonPath("$.emailSimulated").value(true));
            postAs(login("ravi@test.com"), "/api/admin/sla-monitor/run", "").andExpect(status().isForbidden());
        }
    }
}
