package com.iitsm.backend.api;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.PasswordHasher;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.repository.SlaPolicyRepository;
import com.iitsm.backend.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Photos on incidents: upload from the phone, visibility rules, and validation of what is uploaded. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AttachmentIntegrationTest {

    private static final String PASSWORD = "Password123";
    /** Smallest valid-looking PNG header followed by some bytes. */
    private static final byte[] PNG = concat(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, new byte[64]);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private ItServiceRepository services;
    @Autowired
    private SlaPolicyRepository slaPolicies;
    @Autowired
    private PasswordHasher hasher;

    private Long serviceId;

    @BeforeEach
    void setUp() {
        serviceId = services.save(new ItService("VPN", "Remote access", BusinessCriticality.HIGH)).getId();
        for (PriorityLevel level : PriorityLevel.values()) {
            slaPolicies.save(SlaPolicy.standard(level, Duration.ofHours(1), Duration.ofHours(8)));
        }
        users.save(new Employee("Asha Patel", "asha@test.com", PASSWORD, hasher, "Finance", null));
        users.save(new Employee("Vikram Singh", "vikram@test.com", PASSWORD, hasher, "Sales", null));
        users.save(new Technician("Ravi Kumar", "ravi@test.com", PASSWORD, hasher, SupportLevel.L1_TECHNICIAN, 3, 10));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private int report(String token) throws Exception {
        String body = mvc.perform(post("/api/incidents").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "VPN error on login", "description": "The VPN client shows an error dialog",
                                 "serviceId": %d, "impact": "LOW", "urgency": "MEDIUM", "affectedUsers": 1}
                                """.formatted(serviceId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions upload(String token, int incidentId, String name, String type, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/incidents/{id}/attachments", incidentId)
                .file(new MockMultipartFile("file", name, type, bytes))
                .header("Authorization", token));
    }

    @Test
    @DisplayName("The reporter attaches a photo; staff can list and download it")
    void uploadListDownload() throws Exception {
        String asha = login("asha@test.com");
        int incident = report(asha);

        String body = upload(asha, incident, "../../error screen.png", "image/png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("error screen.png")) // path stripped
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.uploadedBy.fullName").value("Asha Patel"))
                .andReturn().getResponse().getContentAsString();
        int attachmentId = JsonPath.read(body, "$.id");

        String ravi = login("ravi@test.com");
        mvc.perform(get("/api/incidents/{id}/attachments", incident).header("Authorization", ravi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        byte[] downloaded = mvc.perform(get("/api/attachments/{id}/content", attachmentId).header("Authorization", ravi))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PNG);
    }

    @Test
    @DisplayName("Another employee can neither see nor add photos on someone else's incident")
    void otherEmployeesAreBlocked() throws Exception {
        int incident = report(login("asha@test.com"));
        String vikram = login("vikram@test.com");
        upload(vikram, incident, "x.png", "image/png", PNG).andExpect(status().isForbidden());
        mvc.perform(get("/api/incidents/{id}/attachments", incident).header("Authorization", vikram))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A file that is not really an image is refused, whatever it claims to be")
    void rejectsNonImages() throws Exception {
        String asha = login("asha@test.com");
        int incident = report(asha);
        upload(asha, incident, "evil.png", "image/png", "<script>alert(1)</script>".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
