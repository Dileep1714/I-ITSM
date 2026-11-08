package com.iitsm.backend.api;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Administrator;
import com.iitsm.backend.domain.user.Availability;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.PasswordHasher;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.realtime.StompAuthInterceptor;
import com.iitsm.backend.realtime.WebSocketConfig;
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
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the intelligent features, administrator management, reports and the real-time channel.
 *
 * <p>Organization: services Customer Database ← Payment, Customer Database ← Email, and VPN; categories Network
 * ("vpn"), Application ("checkout") and Database; employees Asha and Vikram; technician Ravi (L1; Network and
 * Application skills); team lead Kavya (L2); administrator Meera. Each test is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeaturesIntegrationTest {

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
    private StompAuthInterceptor stompAuth;

    private Long databaseId;
    private Long paymentId;
    private Long emailId;
    private Long vpnId;
    private Long networkId;
    private Long applicationId;
    private Long adminId;
    private Long raviId;

    @BeforeEach
    void setUp() {
        Category network = new Category("Network", "Network issues");
        network.addKeyword("vpn");
        network.addKeyword("packet loss");
        Category application = new Category("Application", "Business applications");
        application.addKeyword("checkout");
        application.addKeyword("error 500");
        Category database = new Category("Database", "Databases");
        database.addKeyword("deadlock");
        networkId = categories.save(network).getId();
        applicationId = categories.save(application).getId();
        categories.save(database);

        ItService db = services.save(new ItService("Customer Database", "Orders", BusinessCriticality.MISSION_CRITICAL));
        ItService payment = new ItService("Payment", "Checkout", BusinessCriticality.MISSION_CRITICAL);
        payment.addDependency(db);
        ItService email = new ItService("Email", "Mail", BusinessCriticality.MEDIUM);
        email.addDependency(db);
        databaseId = db.getId();
        paymentId = services.save(payment).getId();
        emailId = services.save(email).getId();
        vpnId = services.save(new ItService("VPN", "Remote access", BusinessCriticality.HIGH)).getId();

        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30)));
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2)));
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P3, Duration.ofHours(1), Duration.ofHours(8)));
        slaPolicies.save(SlaPolicy.standard(PriorityLevel.P4, Duration.ofHours(4), Duration.ofHours(24)));

        users.save(new Employee("Asha Patel", "asha@test.com", PASSWORD, hasher, "Finance", null));
        users.save(new Employee("Vikram Singh", "vikram@test.com", PASSWORD, hasher, "Sales", null));
        adminId = users.save(new Administrator("Meera Iyer", "admin@test.com", PASSWORD, hasher)).getId();
        Technician ravi = new Technician("Ravi Kumar", "ravi@test.com", PASSWORD, hasher, SupportLevel.L1_TECHNICIAN, 3, 10);
        ravi.addOrUpdateSkill(network, 5);
        ravi.addOrUpdateSkill(application, 4);
        raviId = users.save(ravi).getId();
        Technician kavya = new Technician("Kavya Reddy", "kavya@test.com", PASSWORD, hasher, SupportLevel.L2_TEAM_LEAD, 8, 10);
        kavya.addOrUpdateSkill(network, 2);   // Ravi is the only Application expert: assignments are predictable
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
        return mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putAs(String token, String url, String json) throws Exception {
        return mvc.perform(put(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", token));
    }

    /** Reports an incident without a category; returns its id. */
    private int report(String token, String title, String description, Long serviceId) throws Exception {
        String body = postAs(token, "/api/incidents", """
                {"title": "%s", "description": "%s", "serviceId": %d,
                 "impact": "MEDIUM", "urgency": "HIGH", "affectedUsers": 20}
                """.formatted(title, description, serviceId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // =============================================================================================================

    @Nested
    @DisplayName("Intelligent features")
    class Intelligence {

        @Test
        @DisplayName("While typing, the form gets a category suggestion and sees the problem was already reported")
        void draftAnalysis() throws Exception {
            report(login("vikram@test.com"), "VPN disconnects every few minutes",
                    "Remote VPN connection drops with packet loss since the morning", vpnId);

            postAs(login("asha@test.com"), "/api/intelligence/analyze", """
                    {"title": "VPN keeps disconnecting", "description": "My VPN connection drops, packet loss",
                     "serviceId": %d}
                    """.formatted(vpnId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categorySuggestions[0].name").value("Network"))
                    .andExpect(jsonPath("$.categorySuggestions[0].reasons[0]").value(containsString("vpn")))
                    .andExpect(jsonPath("$.similarIncidents[0].relation").value("LIKELY_DUPLICATE"))
                    // employees see that it was reported, not the technical reasons or other people's fixes
                    .andExpect(jsonPath("$.similarIncidents[0].reasons", hasSize(0)));
        }

        @Test
        @DisplayName("An incident reported without a category is classified, prioritised and assigned automatically")
        void autoClassification() throws Exception {
            String asha = login("asha@test.com");
            int id = report(asha, "Checkout shows error 500", "Customers get error 500 at checkout", paymentId);

            getAs(asha, "/api/incidents/" + id)
                    .andExpect(jsonPath("$.category.name").value("Application"))
                    .andExpect(jsonPath("$.priority.level").exists())
                    .andExpect(jsonPath("$.assignee.fullName").exists());
            getAs(login("admin@test.com"), "/api/incidents/" + id + "/history")
                    .andExpect(jsonPath("$[*].action", hasItem("CLASSIFIED")));
        }

        @Test
        @DisplayName("Reporting the same problem twice is refused with DUPLICATE_INCIDENT, unless the user insists")
        void ownDuplicateRefused() throws Exception {
            String asha = login("asha@test.com");
            int first = report(asha, "VPN disconnects every few minutes", "Remote VPN connection drops", vpnId);

            String repeat = """
                    {"title": "VPN disconnects every few minutes", "description": "Remote VPN connection drops",
                     "serviceId": %d, "impact": "MEDIUM", "urgency": "HIGH"%s}
                    """;
            postAs(asha, "/api/incidents", repeat.formatted(vpnId, ""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_INCIDENT"))
                    .andExpect(jsonPath("$.message").value(containsString("INC-")));
            postAs(asha, "/api/incidents", repeat.formatted(vpnId, ", \"allowDuplicate\": true"))
                    .andExpect(status().isCreated());
            // a different person reporting the same problem is fine: that is what duplicate detection is for
            report(login("vikram@test.com"), "VPN disconnects every few minutes", "Remote VPN connection drops", vpnId);
            assertThat(first).isPositive();
        }

        @Test
        @DisplayName("Staff see likely duplicates and can link one to the original; employees cannot")
        void similarAndMarkDuplicate() throws Exception {
            int original = report(login("asha@test.com"), "Checkout shows error 500", "Customers get error 500 at checkout", paymentId);
            int copy = report(login("vikram@test.com"), "Error 500 on checkout", "Checkout page fails with error 500", paymentId);
            String admin = login("admin@test.com");

            getAs(admin, "/api/incidents/" + copy + "/similar")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(original))
                    .andExpect(jsonPath("$[0].relation").value("LIKELY_DUPLICATE"));
            getAs(login("vikram@test.com"), "/api/incidents/" + copy + "/similar").andExpect(status().isForbidden());
            postAs(login("vikram@test.com"), "/api/incidents/" + copy + "/duplicate-of", "{\"originalId\": " + original + "}")
                    .andExpect(status().isForbidden());

            postAs(admin, "/api/incidents/" + copy + "/duplicate-of", "{\"originalId\": " + original + "}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.duplicateOfNumber").value(startsWithInc()));
        }

        @Test
        @DisplayName("Open incidents on services sharing a dependency are correlated to that dependency")
        void correlation() throws Exception {
            String asha = login("asha@test.com");
            report(asha, "Checkout hangs", "Payments time out after submitting", paymentId);
            report(login("vikram@test.com"), "Refunds failing", "Refund page shows an error", paymentId);
            report(asha, "Mailbox search broken", "Searching email returns nothing", emailId);

            getAs(login("kavya@test.com"), "/api/intelligence/correlations")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].rootServiceName").value("Customer Database"))
                    .andExpect(jsonPath("$[0].incidents", hasSize(3)))
                    .andExpect(jsonPath("$[0].affectedServices", containsInAnyOrder("Email", "Payment")));
            getAs(asha, "/api/intelligence/correlations").andExpect(status().isForbidden());
        }
    }

    private static org.hamcrest.Matcher<String> startsWithInc() {
        return org.hamcrest.Matchers.startsWith("INC-");
    }

    // =============================================================================================================

    @Nested
    @DisplayName("Administrator management")
    class Administration {

        @Test
        @DisplayName("A new category with keywords is usable at once, with no code change")
        void createCategory() throws Exception {
            String admin = login("admin@test.com");
            postAs(admin, "/api/admin/categories", """
                    {"name": "Telephony", "description": "Desk phones", "keywords": ["Desk Phone", "voip", "voip"]}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.keywords", contains("desk phone", "voip")));

            getAs(login("asha@test.com"), "/api/catalog/categories")
                    .andExpect(jsonPath("$[*].name", hasItem("Telephony")));
            postAs(login("asha@test.com"), "/api/intelligence/analyze",
                    "{\"title\": \"My desk phone is dead\", \"description\": \"No dial tone on the voip line\"}")
                    .andExpect(jsonPath("$.categorySuggestions[0].name").value("Telephony"));

            postAs(admin, "/api/admin/categories", "{\"name\": \"network\"}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));
        }

        @Test
        @DisplayName("Deactivated categories disappear from the report form")
        void deactivateCategory() throws Exception {
            putAs(login("admin@test.com"), "/api/admin/categories/" + networkId,
                    "{\"name\": \"Network\", \"active\": false}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
            getAs(login("asha@test.com"), "/api/catalog/categories")
                    .andExpect(jsonPath("$[*].name", org.hamcrest.Matchers.not(hasItem("Network"))));
        }

        @Test
        @DisplayName("Service dependencies can be edited, but a cycle is rejected")
        void serviceDependencies() throws Exception {
            String admin = login("admin@test.com");
            putAs(admin, "/api/admin/services/" + vpnId, """
                    {"name": "VPN", "criticality": "HIGH", "status": "DEGRADED", "dependsOnIds": [%d]}
                    """.formatted(databaseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DEGRADED"))
                    .andExpect(jsonPath("$.dependsOn[0].name").value("Customer Database"));

            putAs(admin, "/api/admin/services/" + databaseId, """
                    {"name": "Customer Database", "criticality": "MISSION_CRITICAL", "dependsOnIds": [%d]}
                    """.formatted(paymentId))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("CIRCULAR_DEPENDENCY"));
        }

        @Test
        @DisplayName("SLA policies: service-specific ones can be added and removed; generic ones are protected")
        void slaPolicies() throws Exception {
            String admin = login("admin@test.com");
            String body = postAs(admin, "/api/admin/sla-policies", """
                    {"priority": "P1", "serviceId": %d, "responseMinutes": 3, "resolutionMinutes": 20,
                     "warningThreshold": 0.8}
                    """.formatted(paymentId))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.serviceName").value("Payment"))
                    .andReturn().getResponse().getContentAsString();
            int specificId = JsonPath.read(body, "$.id");

            postAs(admin, "/api/admin/sla-policies", """
                    {"priority": "P1", "serviceId": %d, "responseMinutes": 4, "resolutionMinutes": 25,
                     "warningThreshold": 0.8}
                    """.formatted(paymentId))
                    .andExpect(jsonPath("$.code").value("SLA_POLICY_EXISTS"));
            postAs(admin, "/api/admin/sla-policies", """
                    {"priority": "P2", "responseMinutes": 30, "resolutionMinutes": 10, "warningThreshold": 0.8}
                    """)
                    .andExpect(status().isUnprocessableContent());   // generic P2 exists already

            Long genericP1 = slaPolicies.findByPriorityAndServiceIsNull(PriorityLevel.P1).orElseThrow().getId();
            mvc.perform(delete("/api/admin/sla-policies/" + genericP1).header("Authorization", admin))
                    .andExpect(jsonPath("$.code").value("SLA_POLICY_REQUIRED"));
            mvc.perform(delete("/api/admin/sla-policies/" + specificId).header("Authorization", admin))
                    .andExpect(status().isNoContent());

            getAs(login("kavya@test.com"), "/api/admin/sla-policies").andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("An administrator creates a technician with skills, who can log in at once")
        void createTechnician() throws Exception {
            postAs(login("admin@test.com"), "/api/admin/users", """
                    {"role": "TECHNICIAN", "fullName": "Arjun Das", "email": "Arjun@Test.com", "password": "Secret123",
                     "supportLevel": "L1_TECHNICIAN", "experienceYears": 2, "maxActiveIncidents": 4,
                     "skills": [{"categoryId": %d, "proficiency": 4}]}
                    """.formatted(networkId))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("arjun@test.com"))
                    .andExpect(jsonPath("$.skills[0].categoryName").value("Network"));

            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"arjun@test.com\",\"password\":\"Secret123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.role").value("TECHNICIAN"));
        }

        @Test
        @DisplayName("A deactivated user is locked out; an administrator cannot deactivate themselves")
        void deactivation() throws Exception {
            String admin = login("admin@test.com");
            String ravi = login("ravi@test.com");
            putAs(admin, "/api/admin/users/" + raviId, "{\"active\": false}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));

            getAs(ravi, "/api/incidents").andExpect(status().isForbidden());   // token still valid, account is not
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"ravi@test.com\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isUnauthorized());

            putAs(admin, "/api/admin/users/" + adminId, "{\"active\": false}")
                    .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
        }

        @Test
        @DisplayName("Technician settings: capacity, skills replaced; technicians set their own availability")
        void technicianSettings() throws Exception {
            putAs(login("admin@test.com"), "/api/admin/users/" + raviId + "/technician", """
                    {"maxActiveIncidents": 2, "skills": [{"categoryId": %d, "proficiency": 2}]}
                    """.formatted(applicationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.maxActiveIncidents").value(2))
                    .andExpect(jsonPath("$.skills", hasSize(1)))
                    .andExpect(jsonPath("$.skills[0].proficiency").value(2));

            String ravi = login("ravi@test.com");
            putAs(ravi, "/api/me/availability", "{\"availability\": \"OFF_DUTY\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availability").value("OFF_DUTY"));
            getAs(ravi, "/api/me/profile").andExpect(jsonPath("$.availability").value("OFF_DUTY"));
            putAs(login("asha@test.com"), "/api/me/availability", "{\"availability\": \"BUSY\"}")
                    .andExpect(jsonPath("$.code").value("NOT_A_TECHNICIAN"));
        }
    }

    // =============================================================================================================

    @Nested
    @DisplayName("Taking work and internal priority details")
    class TakingAndPriorityPrivacy {

        private void setAvailability(String email, Availability availability) {
            ((Technician) users.findByEmail(email).orElseThrow()).changeAvailability(availability);
        }

        @Test
        @DisplayName("Any technician can take an unassigned incident, but not one somebody already owns")
        void takeFromQueue() throws Exception {
            setAvailability("ravi@test.com", Availability.OFF_DUTY);
            setAvailability("kavya@test.com", Availability.OFF_DUTY);
            String body = postAs(login("asha@test.com"), "/api/incidents", """
                    {"title": "Wi-Fi drops on floor 3", "description": "Wireless keeps disconnecting all day",
                     "serviceId": %d, "categoryId": %d, "impact": "MEDIUM", "urgency": "MEDIUM"}
                    """.formatted(vpnId, networkId))
                    .andExpect(jsonPath("$.status").value("CLASSIFIED"))   // nobody was available to auto-assign
                    .andReturn().getResponse().getContentAsString();
            int id = JsonPath.read(body, "$.id");
            setAvailability("ravi@test.com", Availability.AVAILABLE);
            setAvailability("kavya@test.com", Availability.AVAILABLE);

            String ravi = login("ravi@test.com");   // L1: may take, may not assign
            getAs(ravi, "/api/incidents/" + id)
                    .andExpect(jsonPath("$.allowedOperations", hasItem("TAKE")))
                    .andExpect(jsonPath("$.allowedOperations", org.hamcrest.Matchers.not(hasItem("ASSIGN"))));
            postAs(ravi, "/api/incidents/" + id + "/assign", "{\"technicianId\": " + raviId + "}")
                    .andExpect(status().isForbidden());
            postAs(login("asha@test.com"), "/api/incidents/" + id + "/take", "{}").andExpect(status().isForbidden());

            postAs(ravi, "/api/incidents/" + id + "/take", "{}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ASSIGNED"))
                    .andExpect(jsonPath("$.assignee.fullName").value("Ravi Kumar"));

            // Now it is Ravi's: taking it away is a reassignment, which only team leads may do (via assign).
            postAs(login("kavya@test.com"), "/api/incidents/" + id + "/take", "{}")
                    .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        }

        @Test
        @DisplayName("Employees see the priority level, never the score or how it was calculated")
        void priorityCalculationIsInternal() throws Exception {
            String asha = login("asha@test.com");
            int id = report(asha, "Checkout shows error 500", "Customers get error 500 at checkout", paymentId);

            getAs(asha, "/api/incidents/" + id)
                    .andExpect(jsonPath("$.priority.level").exists())
                    .andExpect(jsonPath("$.priority.score").doesNotExist())
                    .andExpect(jsonPath("$.priority.rationale").doesNotExist());
            getAs(asha, "/api/incidents/" + id + "/history")
                    .andExpect(jsonPath("$[*].description", org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.not(containsString("×")))));
            getAs(login("ravi@test.com"), "/api/incidents/" + id)
                    .andExpect(jsonPath("$.priority.rationale").value(containsString("×")));

            String preview = """
                    {"impact": "HIGH", "urgency": "HIGH", "serviceId": %d, "affectedUsers": 10}
                    """.formatted(paymentId);
            postAs(asha, "/api/priority/preview", preview)
                    .andExpect(jsonPath("$.level").exists())
                    .andExpect(jsonPath("$.rationale").doesNotExist());
            postAs(login("ravi@test.com"), "/api/priority/preview", preview)
                    .andExpect(jsonPath("$.rationale").exists());
        }
    }

    // =============================================================================================================

    @Nested
    @DisplayName("Reports")
    class Reports {

        /** Asha reports, Ravi resolves, Asha accepts with 4 stars. */
        private void resolvedIncident() throws Exception {
            String asha = login("asha@test.com");
            String ravi = login("ravi@test.com");
            int id = report(asha, "Checkout shows error 500", "Customers get error 500 at checkout", paymentId);
            postAs(ravi, "/api/incidents/" + id + "/acknowledge", "{}").andExpect(status().isOk());
            postAs(ravi, "/api/incidents/" + id + "/start", "{}").andExpect(status().isOk());
            postAs(ravi, "/api/incidents/" + id + "/resolve",
                    "{\"summary\": \"Restarted the payment gateway\", \"code\": \"WORKAROUND\"}").andExpect(status().isOk());
            postAs(asha, "/api/incidents/" + id + "/accept", "{\"rating\": 4}").andExpect(status().isOk());
        }

        @Test
        @DisplayName("Incident overview: totals, SLA compliance, satisfaction and one row per day")
        void overview() throws Exception {
            resolvedIncident();
            report(login("vikram@test.com"), "VPN slow", "VPN has packet loss", vpnId);

            getAs(login("admin@test.com"), "/api/reports/overview")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totals.created").value(2))
                    .andExpect(jsonPath("$.totals.resolved").value(1))
                    .andExpect(jsonPath("$.totals.openNow").value(1))
                    .andExpect(jsonPath("$.totals.slaCompliancePercent").value(100.0))
                    .andExpect(jsonPath("$.totals.avgSatisfaction").value(4.0))
                    .andExpect(jsonPath("$.byCategory[*].key", containsInAnyOrder("Application", "Network")))
                    .andExpect(jsonPath("$.daily", hasSize(30)));
        }

        @Test
        @DisplayName("Workload: team leads and admins see everyone, a front-line technician only themselves")
        void workload() throws Exception {
            resolvedIncident();
            getAs(login("admin@test.com"), "/api/reports/technicians")
                    .andExpect(jsonPath("$.technicians[*].fullName", contains("Kavya Reddy", "Ravi Kumar")))
                    .andExpect(jsonPath("$.technicians[1].resolvedInPeriod").value(1))
                    .andExpect(jsonPath("$.technicians[1].avgSatisfaction").value(4.0));
            getAs(login("ravi@test.com"), "/api/reports/technicians")
                    .andExpect(jsonPath("$.technicians[*].fullName", contains("Ravi Kumar")));
        }

        @Test
        @DisplayName("Service analysis lists every service, busiest first; employees are refused")
        void services() throws Exception {
            resolvedIncident();
            getAs(login("kavya@test.com"), "/api/reports/services")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.services", hasSize(4)))
                    .andExpect(jsonPath("$.services[0].name").value("Payment"))
                    .andExpect(jsonPath("$.services[0].topCategory").value("Application"))
                    .andExpect(jsonPath("$.services[0].dependsOn[0]").value("Customer Database"));
            getAs(login("asha@test.com"), "/api/reports/services").andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid periods are rejected")
        void invalidPeriod() throws Exception {
            String admin = login("admin@test.com");
            getAs(admin, "/api/reports/overview?from=2026-03-10&to=2026-03-01")
                    .andExpect(status().isBadRequest());
            getAs(admin, "/api/reports/overview?from=2024-01-01&to=2026-01-01")
                    .andExpect(status().isBadRequest());
        }
    }

    // =============================================================================================================

    @Nested
    @DisplayName("Real-time channel security")
    class Realtime {

        private Message<byte[]> frame(StompCommand command, String destination, String authorization, Principal user) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
            if (destination != null) {
                accessor.setDestination(destination);
            }
            if (authorization != null) {
                accessor.addNativeHeader("Authorization", authorization);
            }
            accessor.setUser(user);
            accessor.setLeaveMutable(true);
            return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }

        /** Runs a CONNECT frame through the interceptor and returns the user it authenticated. */
        private Principal connect(String token) {
            Message<byte[]> connect = frame(StompCommand.CONNECT, null, token, null);
            stompAuth.preSend(connect, null);
            return MessageHeaderAccessor.getAccessor(connect, StompHeaderAccessor.class).getUser();
        }

        @Test
        @DisplayName("CONNECT needs a valid token; the connection's user is the token's user id")
        void connectRules() throws Exception {
            assertThat(connect(login("ravi@test.com")).getName()).isEqualTo(String.valueOf(raviId));
            assertThatThrownBy(() -> connect(null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> connect("Bearer forged.token.x")).isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Only support staff may subscribe to the incident feed; nobody may send")
        void subscribeRules() throws Exception {
            Principal asha = connect(login("asha@test.com"));
            Principal ravi = connect(login("ravi@test.com"));

            assertThatThrownBy(() -> stompAuth.preSend(
                    frame(StompCommand.SUBSCRIBE, WebSocketConfig.INCIDENTS_TOPIC, null, asha), null))
                    .isInstanceOf(AccessDeniedException.class);
            stompAuth.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/notifications", null, asha), null);
            stompAuth.preSend(frame(StompCommand.SUBSCRIBE, WebSocketConfig.INCIDENTS_TOPIC, null, ravi), null);

            assertThatThrownBy(() -> stompAuth.preSend(frame(StompCommand.SEND, "/app/anything", null, ravi), null))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
