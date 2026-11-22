package com.iitsm.backend.ai;

import com.iitsm.backend.config.ItsmProperties;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link AiAssistant} backed by Google's Gemini API (free tier), using structured output: the model must answer
 * with JSON matching a schema, which is parsed here.
 *
 * <p><b>Resilience:</b> models are tried in the configured order; a busy (503), rate-limited (429) or failing
 * model hands over to the next. If none answers, the result is empty and the caller falls back to the app's own
 * keyword-based intelligence, so a demo never breaks on someone else's outage. The API key travels in a header,
 * never in a URL or a log line.
 */
public class GeminiAssistant implements AiAssistant {

    private static final Logger log = LoggerFactory.getLogger(GeminiAssistant.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final ItsmProperties.Gemini settings;
    private final ObjectMapper mapper;
    private final RestClient http;

    public GeminiAssistant(ItsmProperties.Gemini settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(settings.timeout()).build());
        factory.setReadTimeout(settings.timeout());
        this.http = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .defaultHeader("x-goog-api-key", settings.apiKey())
                .build();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Optional<AiReply> generateJson(String instructions, List<AiMessage> conversation, JsonNode responseSchema) {
        ObjectNode body = request(instructions, conversation, responseSchema);
        for (String model : settings.models()) {
            try {
                JsonNode response = http.post()
                        .uri("/models/{model}:generateContent", model)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                Optional<JsonNode> json = extractJson(response);
                if (json.isPresent()) {
                    return Optional.of(new AiReply(json.get(), model));
                }
                log.warn("Gemini model {} returned no usable JSON; trying the next model", model);
            } catch (RestClientException e) {
                // Busy, over quota, unknown model or network trouble: hand over to the next model.
                log.warn("Gemini model {} unavailable: {}", model, firstLine(e.getMessage()));
            } catch (RuntimeException e) {
                log.warn("Gemini model {} answer could not be parsed: {}", model, firstLine(e.getMessage()));
            }
        }
        return Optional.empty();
    }

    /** Builds a generateContent request: system instruction, conversation turns, and the JSON response schema. */
    private ObjectNode request(String instructions, List<AiMessage> conversation, JsonNode responseSchema) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", instructions);
        ArrayNode contents = body.putArray("contents");
        for (AiMessage message : conversation) {
            ObjectNode turn = contents.addObject();
            turn.put("role", message.speaker() == AiMessage.Speaker.USER ? "user" : "model");
            turn.putArray("parts").addObject().put("text", message.text());
        }
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseSchema", responseSchema);
        config.put("temperature", 0.3);
        return body;
    }

    private Optional<JsonNode> extractJson(JsonNode response) {
        if (response == null) {
            return Optional.empty();
        }
        JsonNode text = response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (!text.isString() || text.asString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(mapper.readTree(text.asString()));
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        String line = newline < 0 ? message : message.substring(0, newline);
        return line.length() > 200 ? line.substring(0, 200) : line;
    }
}
