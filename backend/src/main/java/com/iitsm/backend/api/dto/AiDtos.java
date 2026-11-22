package com.iitsm.backend.api.dto;

import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Urgency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request and response shapes for the AI copilot. */
public final class AiDtos {

    private AiDtos() {
    }

    /** Whether the AI copilot is switched on (an API key is configured). */
    public record AiStatus(boolean available, String provider) {
    }

    /**
     * Suggested fix for an open incident, grounded in similar resolved incidents.
     *
     * @param basedOn numbers of the past incidents the suggestion drew on (always a subset of what was provided)
     */
    public record ResolutionSuggestion(String summary, List<String> steps, String likelyCause, List<String> basedOn,
                                       String model) {
    }

    public record ChatTurn(@NotNull @Pattern(regexp = "user|assistant") String role,
                           @NotBlank @Size(max = 4000) String text) {
    }

    /** The conversation so far, oldest first; the last turn is the user's newest message. */
    public record ReportChatRequest(@NotEmpty @Size(max = 30) List<@Valid ChatTurn> messages) {
    }

    /**
     * The assistant's next message and, once it knows enough, a filled-in report for the user to review and submit.
     *
     * @param ready true when {@code draft} is complete enough to review (the service may still need choosing)
     */
    public record ReportChatReply(String reply, boolean ready, ReportDraft draft, String model) {
    }

    /** A report the assistant prepared. The user reviews and submits it: nothing is created automatically. */
    public record ReportDraft(String title, String description, Long serviceId, String serviceName, Long categoryId,
                              String categoryName, Impact impact, Urgency urgency, Integer affectedUsers) {
    }
}
