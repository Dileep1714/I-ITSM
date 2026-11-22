package com.iitsm.backend.ai;

import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** Used when no API key is configured (Null Object): never answers, so every caller falls back gracefully. */
public class DisabledAssistant implements AiAssistant {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public Optional<AiReply> generateJson(String instructions, List<AiMessage> conversation, JsonNode responseSchema) {
        return Optional.empty();
    }
}
