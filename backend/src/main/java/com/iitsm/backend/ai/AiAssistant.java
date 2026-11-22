package com.iitsm.backend.ai;

import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * <b>Strategy / Adapter</b> for a large language model. The copilot only depends on this interface, so the
 * provider (Gemini today) can be swapped, and a "no AI" implementation keeps every feature working without a key.
 */
public interface AiAssistant {

    /** False when no provider is configured: callers then use their non-AI fallback. */
    boolean available();

    /**
     * Asks the model for a JSON answer that matches {@code responseSchema}.
     *
     * @param instructions  what the assistant is and must do (system prompt)
     * @param conversation  the user's message(s), oldest first
     * @param responseSchema the JSON shape required (Gemini/OpenAPI schema subset)
     * @return the parsed answer, or empty when no model could answer (busy, quota, network, invalid output)
     */
    Optional<AiReply> generateJson(String instructions, List<AiMessage> conversation, JsonNode responseSchema);
}
