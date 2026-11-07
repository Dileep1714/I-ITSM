package com.iitsm.backend.api;

import com.iitsm.backend.common.strategy.StrategyRegistry;
import com.iitsm.backend.domain.assignment.AssignmentStrategy;
import com.iitsm.backend.domain.assignment.SmartCompositeStrategy;
import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Administrator;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.PasswordHasher;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.repository.SlaPolicyRepository;
import com.iitsm.backend.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests: real HTTP requests through security, controllers, services and the database.
 *
 * <p>Organization used throughout: employees Asha and Vikram, technician Ravi (Application skill 5), team lead
 * Kavya (Application skill 3), administrator Meera, a mission-critical Payment service, and the spec's SLA table.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {

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
    private PasswordHasher hasher;
    @Autowired
    private StrategyRegistry<AssignmentStrategy> assignmentStrategies;

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
        users.save(new Employee("Vikram Singh", "vikram@test.com", PASSWORD, hasher, "Sales", null));
        users.save(new Administrator("Meera Iyer", "admin@test.com", PASSWORD, hasher));

        Technician ravi = new Technician("Ravi Kumar", "ravi@test.com", PASSWORD, hasher,
                SupportLevel.L1_TECHNICIAN, 3, 5);
        ravi.addOrUpdateSkill(application, 5);
        users.save(ravi);
        Technician kavya = new Technician("Kavya Reddy", "kavya@test.com", PASSWORD, hasher,
                SupportLevel.L2_TEAM_LEAD, 8, 5);
        kavya.addOrUpdateSkill(application, 3);
        users.save(kavya);
    }

    // ----- helpers -----------------------------------------------------------------------------------------------

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private ResultActions postAs(String token, String url, String json) throws Exception {
        return mvc.perform(post(url).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", token));
    }

    /** Asha reports the spec's scenario: payment application down for 200 users. Returns the incident id. */
    private int reportPaymentOutage(String ashaToken) throws Exception {
        String body = postAs(ashaToken, "/api/incidents", """
                {"title": "Payment application unavailable",
                 "description": "Users are unable to complete transactions since 09:55.",
                 "serviceId": %d, "categoryId": %d,
                 "impact": "HIGH", "urgency": "CRITICAL", "affectedUsers": 200}
                """.formatted(paymentId, applicationId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // ----- authentication ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("Requests without a token are rejected with 401 in the standard error format")
        void noTokenRejected() throws Exception {
            mvc.perform(get("/api/incidents"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.path").value("/api/incidents"));
        }

        @Test
        @DisplayName("A forged or garbled token is rejected with 401")
        void forgedTokenRejected() throws Exception {
            mvc.perform(get("/api/incidents").header("Authorization", "Bearer not.a.real-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Wrong password and unknown email give the same 401 response (no account enumeration)")
        void loginFailuresLookIdentical() throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"asha@test.com\",\"password\":\"WrongPass1\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@test.com\",\"password\":\"WrongPass1\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        @Test
        @DisplayName("Login is case-insensitive for the email and returns the user's role")
        void loginReturnsRole() throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"  RAVI@Test.com \",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user.role").value("TECHNICIAN"));
        }

        @Test
        @DisplayName("Registration creates an EMPLOYEE (never a higher role) and the token works immediately")
        void registerCreatesEmployee() throws Exception {
            String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fullName": "New Person", "email": "new@test.com",
                                     "password": "Secret123", "department": "Ops"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
                    .andReturn().getResponse().getContentAsString();

            getAs("Bearer " + JsonPath.read(body, "$.accessToken"), "/api/auth/me")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("new@test.com"));
        }

        @Test
        @DisplayName("Registering an existing email is rejected")
        void duplicateEmailRejected() throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fullName": "Asha Again", "email": "ASHA@test.com", "password": "Secret123"}
                                    """))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        }

        @Test
        @DisplayName("Invalid registration data lists every bad field")
        void invalidRegistrationListsFields() throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\": \"\", \"email\": \"not-an-email\", \"password\": \"short\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors[*].field",
                            containsInAnyOrder("fullName", "email", "password")));
        }
    }

    // ----- workflow ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Workflow 1+2: report → auto-prioritize → auto-assign → acknowledge → work → resolve → verify → close")
    void fullIncidentWorkflow() throws Exception {
        String asha = login("asha@test.com");
        String ravi = login("ravi@test.com");

        int id = reportPaymentOutage(asha);

        // The system classified, prioritized, applied the SLA and assigned the best technician automatically.
        getAs(asha, "/api/incidents/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number", startsWith("INC-")))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.priority.level").value("P1"))
                .andExpect(jsonPath("$.priority.rationale").value(org.hamcrest.Matchers.containsString("MISSION_CRITICAL")))
                .andExpect(jsonPath("$.assignee.fullName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.sla.status").value("ON_TRACK"))
                .andExpect(jsonPath("$.allowedOperations", empty()));   // nothing for the reporter to do yet

        getAs(ravi, "/api/incidents/" + id)
                .andExpect(jsonPath("$.allowedOperations", containsInAnyOrder("ACKNOWLEDGE")));

        postAs(ravi, "/api/incidents/" + id + "/acknowledge", "")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        postAs(ravi, "/api/incidents/" + id + "/start", "")
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        postAs(ravi, "/api/incidents/" + id + "/notes", "{\"text\": \"Gateway pool exhausted\", \"internal\": true}")
                .andExpect(status().isCreated());
        postAs(ravi, "/api/incidents/" + id + "/resolve", """
                {"summary": "Increased gateway connection pool", "rootCause": "Pool too small",
                 "code": "CONFIGURATION_CHANGE"}
                """).andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.sla.status").value("MET"));

        // The reporter does not see the internal note, and can now accept or reject.
        getAs(asha, "/api/incidents/" + id)
                .andExpect(jsonPath("$.notes", empty()))
                .andExpect(jsonPath("$.allowedOperations",
                        containsInAnyOrder("ACCEPT_RESOLUTION", "REJECT_RESOLUTION")));

        postAs(asha, "/api/incidents/" + id + "/accept", "{\"rating\": 5, \"comment\": \"Thanks!\"}")
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.feedback.rating").value(5));
        postAs(ravi, "/api/incidents/" + id + "/close", "")
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.allowedOperations", empty()));
    }

    @Test
    @DisplayName("'My incidents' shows reporters their own reports and technicians their assignments")
    void listMine() throws Exception {
        String asha = login("asha@test.com");
        reportPaymentOutage(asha);

        getAs(asha, "/api/incidents").andExpect(jsonPath("$.totalElements").value(1));
        getAs(login("ravi@test.com"), "/api/incidents").andExpect(jsonPath("$.totalElements").value(1));
        getAs(login("vikram@test.com"), "/api/incidents").andExpect(jsonPath("$.totalElements").value(0));
    }

    // ----- authorization -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @DisplayName("An employee cannot view someone else's incident")
        void employeeCannotViewOthers() throws Exception {
            int id = reportPaymentOutage(login("asha@test.com"));

            getAs(login("vikram@test.com"), "/api/incidents/" + id)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("An employee cannot perform technician actions")
        void employeeCannotAcknowledge() throws Exception {
            String asha = login("asha@test.com");
            int id = reportPaymentOutage(asha);

            postAs(asha, "/api/incidents/" + id + "/acknowledge", "").andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Only the assigned technician can acknowledge, not another technician")
        void otherTechnicianCannotAcknowledge() throws Exception {
            int id = reportPaymentOutage(login("asha@test.com"));

            postAs(login("kavya@test.com"), "/api/incidents/" + id + "/acknowledge", "")
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin area: employees get 403, administrators can switch the assignment strategy")
        void adminArea() throws Exception {
            getAs(login("asha@test.com"), "/api/admin/strategies").andExpect(status().isForbidden());

            String admin = login("admin@test.com");
            try {
                getAs(admin, "/api/admin/strategies")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.assignmentStrategies[?(@.active == true)].key", hasItem("SMART")));
                mvc.perform(put("/api/admin/strategies/assignment").header("Authorization", admin)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"key\": \"LOAD_BASED\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.assignmentStrategies[?(@.active == true)].key", hasItem("LOAD_BASED")));
                mvc.perform(put("/api/admin/strategies/assignment").header("Authorization", admin)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"key\": \"MAGIC\"}"))
                        .andExpect(status().isNotFound());
            } finally {
                assignmentStrategies.activate(SmartCompositeStrategy.KEY); // the registry is shared between tests
            }
        }
    }

    // ----- errors ------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Error handling")
    class Errors {

        @Test
        @DisplayName("An invalid lifecycle jump returns 409 INVALID_STATE_TRANSITION")
        void invalidTransition() throws Exception {
            int id = reportPaymentOutage(login("asha@test.com"));

            postAs(login("ravi@test.com"), "/api/incidents/" + id + "/close", "")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        }

        @Test
        @DisplayName("Reporting against an unknown service returns 404 SERVICE_NOT_FOUND")
        void unknownService() throws Exception {
            postAs(login("asha@test.com"), "/api/incidents", """
                    {"title": "Broken", "description": "Something is broken badly.",
                     "serviceId": 999999, "impact": "LOW", "urgency": "LOW"}
                    """)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
        }

        @Test
        @DisplayName("Missing SLA configuration returns 422 SLA_POLICY_MISSING")
        void missingSlaPolicy() throws Exception {
            String asha = login("asha@test.com");
            slaPolicies.deleteAll();

            postAs(asha, "/api/incidents", """
                    {"title": "Payment down", "description": "Nobody can pay at checkout.",
                     "serviceId": %d, "categoryId": %d, "impact": "HIGH", "urgency": "HIGH"}
                    """.formatted(paymentId, applicationId))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SLA_POLICY_MISSING"));
        }

        @Test
        @DisplayName("Malformed JSON and unknown enum values return 400")
        void malformedRequests() throws Exception {
            String asha = login("asha@test.com");

            postAs(asha, "/api/incidents", "{ this is not json").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
            postAs(asha, "/api/incidents", """
                    {"title": "x", "description": "long enough text", "serviceId": 1,
                     "impact": "APOCALYPTIC", "urgency": "LOW"}
                    """).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("A technician cannot report incidents (only employees can)")
        void technicianCannotReport() throws Exception {
            postAs(login("ravi@test.com"), "/api/incidents", """
                    {"title": "Broken", "description": "Something is broken badly.",
                     "serviceId": %d, "impact": "LOW", "urgency": "LOW"}
                    """.formatted(paymentId))
                    .andExpect(status().isForbidden());
        }
    }
}
