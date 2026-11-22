package com.iitsm.backend.service;

import com.iitsm.backend.ai.AiAssistant;
import com.iitsm.backend.ai.AiReply;
import com.iitsm.backend.ai.DisabledAssistant;
import com.iitsm.backend.api.dto.AiDtos.ChatTurn;
import com.iitsm.backend.api.dto.AiDtos.ReportChatReply;
import com.iitsm.backend.api.dto.AiDtos.ResolutionSuggestion;
import com.iitsm.backend.common.exception.AiUnavailableException;
import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.domain.TestFixtures;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.Urgency;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The AI copilot must never trust the model blindly: unknown ids are dropped, enums are validated, invented
 * incident numbers are removed, and without AI every feature degrades cleanly.
 */
class AiCopilotServiceTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final AiAssistant ai = mock(AiAssistant.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ItServiceRepository services = mock(ItServiceRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final Clock clock = Clock.fixed(TestFixtures.T0, ZoneOffset.UTC);
    private final ServiceHealthService serviceHealth = mock(ServiceHealthService.class);

    private AiCopilotService copilot;

    @BeforeEach
    void setUp() {
        Category network = mock(Category.class);
        when(network.getId()).thenReturn(5L);
        when(network.getName()).thenReturn("Network");
        ItService vpn = mock(ItService.class);
        when(vpn.getId()).thenReturn(7L);
        when(vpn.getName()).thenReturn("VPN");
        when(vpn.getDescription()).thenReturn("Remote access");
        when(categories.findByActiveTrueOrderByName()).thenReturn(List.of(network));
        when(services.findAllByOrderByName()).thenReturn(List.of(vpn));
        when(currentUser.require()).thenReturn(TestFixtures.employee("Asha Patel"));
        when(ai.available()).thenReturn(true);
        copilot = new AiCopilotService(ai, incidents, categories, services, currentUser, mapper, clock, serviceHealth);
    }

    private void aiAnswers(String json) {
        when(ai.generateJson(any(), any(), any())).thenReturn(Optional.of(new AiReply(mapper.readTree(json), "test-model")));
    }

    @Test
    @DisplayName("Chat draft: valid ids and enums are kept, the answer is marked ready")
    void chatDraftValidated() {
        aiAnswers("""
                {"reply":"Prepared.","ready":true,"draft":{"title":"VPN drops","description":"Drops every 20 min",
                 "serviceId":7,"categoryId":5,"impact":"MEDIUM","urgency":"HIGH","affectedUsers":8}}""");

        ReportChatReply reply = copilot.reportChat(List.of(new ChatTurn("user", "vpn keeps dropping")));

        assertThat(reply.ready()).isTrue();
        assertThat(reply.draft().serviceName()).isEqualTo("VPN");
        assertThat(reply.draft().categoryName()).isEqualTo("Network");
        assertThat(reply.draft().impact()).isEqualTo(Impact.MEDIUM);
        assertThat(reply.draft().urgency()).isEqualTo(Urgency.HIGH);
        assertThat(reply.draft().affectedUsers()).isEqualTo(8);
        assertThat(reply.model()).isEqualTo("test-model");
    }

    @Test
    @DisplayName("Chat draft: an unknown service id and invalid values from the model are discarded")
    void chatDraftRejectsInventedValues() {
        aiAnswers("""
                {"reply":"Prepared.","ready":true,"draft":{"title":"Wi-Fi down","description":"No Wi-Fi",
                 "serviceId":999,"categoryId":42,"impact":"GIGANTIC","urgency":"SOON","affectedUsers":-3}}""");

        ReportChatReply reply = copilot.reportChat(List.of(new ChatTurn("user", "wifi broken")));

        assertThat(reply.draft().serviceId()).isNull();
        assertThat(reply.draft().categoryId()).isNull();
        assertThat(reply.draft().impact()).isEqualTo(Impact.LOW);
        assertThat(reply.draft().urgency()).isEqualTo(Urgency.MEDIUM);
        assertThat(reply.draft().affectedUsers()).isEqualTo(1);
        assertThat(reply.ready()).isTrue(); // reviewable: the employee picks the service in the form
    }

    @Test
    @DisplayName("Classification: applied only when confident and the category exists")
    void classifyNeedsConfidenceAndRealCategory() {
        aiAnswers("""
                {"categoryId":5,"confidence":0.9,"reason":"VPN is network"}""");
        assertThat(copilot.classify("VPN drops", "every 20 minutes")).map(Category::getName).contains("Network");

        aiAnswers("""
                {"categoryId":5,"confidence":0.3,"reason":"unsure"}""");
        assertThat(copilot.classify("Something odd", "")).isEmpty();

        aiAnswers("""
                {"categoryId":99,"confidence":0.95,"reason":"invented"}""");
        assertThat(copilot.classify("VPN drops", "")).isEmpty();
    }

    @Test
    @DisplayName("Fix suggestion: incident numbers the model invents are removed from the citations")
    void suggestionDropsInventedCitations() {
        when(currentUser.require()).thenReturn(TestFixtures.technician("Kavya Reddy", 5));
        ItService vpn = services.findAllByOrderByName().getFirst();
        Incident open = mock(Incident.class);
        when(open.getId()).thenReturn(14L);
        when(open.getNumber()).thenReturn("INC-2026-000012");
        when(open.getTitle()).thenReturn("VPN drops");
        when(open.getDescription()).thenReturn("Every 20 minutes");
        when(open.getService()).thenReturn(vpn);
        when(incidents.findById(14L)).thenReturn(Optional.of(open));
        when(incidents.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        aiAnswers("""
                {"summary":"Check the tunnel.","likelyCause":"Idle timeout","steps":["Check logs","Raise keepalive"],
                 "basedOn":["INC-2026-009999"]}""");

        ResolutionSuggestion s = copilot.suggestResolution(14L);

        assertThat(s.steps()).containsExactly("Check logs", "Raise keepalive");
        assertThat(s.basedOn()).isEmpty();
    }

    @Test
    @DisplayName("Fix suggestions are for support staff only")
    void suggestionStaffOnly() {
        assertThatThrownBy(() -> copilot.suggestResolution(14L)).isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Without an API key: classification quietly falls back, chat reports AI_UNAVAILABLE")
    void disabledAssistantDegradesCleanly() {
        AiCopilotService offline = new AiCopilotService(new DisabledAssistant(), incidents, categories, services,
                currentUser, mapper, clock, serviceHealth);

        assertThat(offline.status().available()).isFalse();
        assertThat(offline.classify("VPN drops", "")).isEmpty();
        assertThatThrownBy(() -> offline.reportChat(List.of(new ChatTurn("user", "help"))))
                .isInstanceOf(AiUnavailableException.class);
    }
}
