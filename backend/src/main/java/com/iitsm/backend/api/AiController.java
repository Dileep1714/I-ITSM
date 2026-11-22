package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.AiDtos.AiStatus;
import com.iitsm.backend.api.dto.AiDtos.ReportChatReply;
import com.iitsm.backend.api.dto.AiDtos.ReportChatRequest;
import com.iitsm.backend.api.dto.AiDtos.ResolutionSuggestion;
import com.iitsm.backend.service.AiCopilotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The AI copilot (Google Gemini). Every endpoint answers 503 AI_UNAVAILABLE when no model can respond. */
@RestController
public class AiController {

    private final AiCopilotService copilot;

    public AiController(AiCopilotService copilot) {
        this.copilot = copilot;
    }

    /** Whether AI features should be shown. */
    @GetMapping("/api/ai/status")
    public AiStatus status() {
        return copilot.status();
    }

    /** Suggested fix steps for an open incident, from similar resolved ones. Support staff only. */
    @PostMapping("/api/incidents/{id}/ai-suggestion")
    public ResolutionSuggestion suggestResolution(@PathVariable Long id) {
        return copilot.suggestResolution(id);
    }

    /** One turn of the chat-based reporting assistant; returns a draft to review once it knows enough. */
    @PostMapping("/api/ai/report-chat")
    public ReportChatReply reportChat(@Valid @RequestBody ReportChatRequest request) {
        return copilot.reportChat(request.messages());
    }
}
