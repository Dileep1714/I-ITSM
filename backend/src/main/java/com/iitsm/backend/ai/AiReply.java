package com.iitsm.backend.ai;

import tools.jackson.databind.JsonNode;

/** A structured answer from the AI, plus which model produced it (shown to users for transparency). */
public record AiReply(JsonNode json, String model) {
}
