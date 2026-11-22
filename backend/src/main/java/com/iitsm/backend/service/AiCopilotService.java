package com.iitsm.backend.service;

import com.iitsm.backend.ai.AiAssistant;
import com.iitsm.backend.ai.AiMessage;
import com.iitsm.backend.ai.AiReply;
import com.iitsm.backend.api.dto.AiDtos.AiStatus;
import com.iitsm.backend.api.dto.AiDtos.ChatTurn;
import com.iitsm.backend.api.dto.AiDtos.ReportChatReply;
import com.iitsm.backend.api.dto.AiDtos.ReportDraft;
import com.iitsm.backend.api.dto.AiDtos.ResolutionSuggestion;
import com.iitsm.backend.common.exception.AiUnavailableException;
import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.common.exception.NotFoundException;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.Urgency;
import com.iitsm.backend.domain.intelligence.RelatedIncidentFinder;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.security.CurrentUserService;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The AI copilot (Google Gemini through {@link AiAssistant}). Three use cases:
 * <ol>
 *   <li><b>AI classification:</b> the category of a report the keyword classifier is unsure about.</li>
 *   <li><b>Resolution recommendation:</b> suggested fix steps for an open incident, grounded in similar
 *       incidents that were already resolved here, and citing them.</li>
 *   <li><b>Chat-based reporting:</b> an assistant that interviews the employee and prepares the report; the
 *       employee reviews and submits it, so nothing is created by the AI on its own.</li>
 * </ol>
 *
 * <p>Every answer is checked before use: ids must exist in the catalogue, enums must be valid, cited incidents
 * must be ones that were provided. Text from users goes to the model as data inside the conversation, never as
 * instructions.
 */
@Service
@Transactional(readOnly = true)
public class AiCopilotService {

    /** Below this the AI's category is not applied automatically. */
    static final double MIN_CLASSIFY_CONFIDENCE = 0.6;
    private static final Duration LOOKBACK = Duration.ofDays(365);
    private static final int MAX_EXAMPLES = 5;

    private final AiAssistant ai;
    private final IncidentRepository incidents;
    private final CategoryRepository categories;
    private final ItServiceRepository services;
    private final CurrentUserService currentUser;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final RelatedIncidentFinder relatedFinder = new RelatedIncidentFinder();

    public AiCopilotService(AiAssistant ai, IncidentRepository incidents, CategoryRepository categories,
                            ItServiceRepository services, CurrentUserService currentUser, ObjectMapper mapper,
                            Clock clock) {
        this.ai = ai;
        this.incidents = incidents;
        this.categories = categories;
        this.services = services;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.clock = clock;
    }

    public AiStatus status() {
        return new AiStatus(ai.available(), ai.available() ? "Google Gemini" : null);
    }

    // ----- 1. classification ------------------------------------------------------------------------------------

    /** The category for a report, when the AI is available and confident. Empty otherwise (caller falls back). */
    public Optional<Category> classify(String title, String description) {
        if (!ai.available()) {
            return Optional.empty();
        }
        List<Category> active = categories.findByActiveTrueOrderByName();
        String instructions = """
                You classify IT incidents for an IT service desk. Choose the single best category id from this list:
                %s
                Answer with the category id, your confidence from 0 to 1, and a short reason.
                If none fits, use confidence 0.""".formatted(categoryList(active));
        JsonNode schema = schema("""
                {"type":"OBJECT","properties":{"categoryId":{"type":"INTEGER"},"confidence":{"type":"NUMBER"},
                 "reason":{"type":"STRING"}},"required":["categoryId","confidence","reason"]}""");
        return ai.generateJson(instructions, List.of(AiMessage.user("Title: " + title + "\nDescription: " + description)), schema)
                .filter(reply -> reply.json().path("confidence").asDouble(0) >= MIN_CLASSIFY_CONFIDENCE)
                .flatMap(reply -> active.stream()
                        .filter(c -> c.getId() == reply.json().path("categoryId").asLong(-1))
                        .findFirst());
    }

    // ----- 2. resolution recommendation -------------------------------------------------------------------------

    /** Suggested fix steps for an open incident, from similar resolved incidents. Support staff only. */
    public ResolutionSuggestion suggestResolution(Long incidentId) {
        requireStaff();
        requireAvailable();
        Incident incident = incidents.findById(incidentId).orElseThrow(() -> new NotFoundException("Incident", incidentId));

        List<Incident> recent = incidents.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                clock.instant().minus(LOOKBACK), PageRequest.of(0, 500));
        List<Incident> examples = relatedFinder.find(RelatedIncidentFinder.Probe.of(incident), recent).stream()
                .map(RelatedIncidentFinder.Match::incident)
                .filter(i -> i.getResolution() != null)
                .limit(MAX_EXAMPLES)
                .toList();
        Set<String> provided = examples.stream().map(Incident::getNumber).collect(Collectors.toCollection(LinkedHashSet::new));

        StringBuilder context = new StringBuilder();
        context.append("OPEN INCIDENT ").append(incident.getNumber()).append('\n')
                .append("Service: ").append(incident.getService().getName()).append('\n')
                .append("Category: ").append(incident.getCategory() != null ? incident.getCategory().getName() : "unclassified").append('\n')
                .append("Title: ").append(incident.getTitle()).append('\n')
                .append("Description: ").append(incident.getDescription()).append("\n\n");
        if (examples.isEmpty()) {
            context.append("No similar resolved incidents exist yet.\n");
        }
        for (Incident past : examples) {
            context.append("RESOLVED ").append(past.getNumber()).append(" (").append(past.getService().getName()).append(")\n")
                    .append("Title: ").append(past.getTitle()).append('\n')
                    .append("Fix: ").append(past.getResolution().getSummary()).append('\n');
            if (past.getResolution().getRootCause() != null) {
                context.append("Root cause: ").append(past.getResolution().getRootCause()).append('\n');
            }
            context.append('\n');
        }

        String instructions = """
                You are a senior IT support engineer helping a technician fix an open incident.
                Use the similar resolved incidents provided when they are relevant; cite only their numbers.
                Give 3 to 6 short, concrete, ordered troubleshooting steps a technician can follow, a one-sentence
                summary, and the most likely cause. If the past incidents are not relevant, say so in the summary
                and give general best-practice steps. Never invent incident numbers.""";
        JsonNode schema = schema("""
                {"type":"OBJECT","properties":{"summary":{"type":"STRING"},"likelyCause":{"type":"STRING"},
                 "steps":{"type":"ARRAY","items":{"type":"STRING"}},
                 "basedOn":{"type":"ARRAY","items":{"type":"STRING"}}},
                 "required":["summary","likelyCause","steps","basedOn"]}""");
        AiReply reply = ai.generateJson(instructions, List.of(AiMessage.user(context.toString())), schema)
                .orElseThrow(() -> new AiUnavailableException("The AI assistant is busy right now. Try again in a minute."));

        JsonNode json = reply.json();
        List<String> steps = strings(json.path("steps")).stream().limit(8).toList();
        // Only keep citations the model was actually given (guards against invented incident numbers).
        List<String> basedOn = strings(json.path("basedOn")).stream().filter(provided::contains).distinct().toList();
        return new ResolutionSuggestion(json.path("summary").asString(""), steps,
                json.path("likelyCause").asString(""), basedOn, reply.model());
    }

    // ----- 3. chat-based reporting ------------------------------------------------------------------------------

    /** One turn of the reporting chat: the assistant's next message, and a draft once it knows enough. */
    public ReportChatReply reportChat(List<ChatTurn> turns) {
        currentUser.require();
        requireAvailable();
        List<ItService> serviceList = services.findAllByOrderByName();
        List<Category> categoryList = categories.findByActiveTrueOrderByName();

        String instructions = """
                You are the friendly IT help desk assistant of an organisation. You help an employee report an IT
                problem by chatting with them. Collect: what happened (symptoms, error messages, when it started),
                which IT service is affected, how many people are affected, and how urgent it is.
                Ask at most ONE short question per message. After at most 3 questions, or as soon as you know
                enough, set ready=true and fill in the draft. Keep replies under 50 words, plain language, no jargon.
                Never ask for passwords or personal data.
                You cannot create or submit tickets yourself. When ready=true, tell the employee you have prepared the
                report and that they should check it and press Submit; never say it has been logged or submitted.

                If no listed service fits the problem, use serviceId 0 and tell the employee to pick the closest
                service in the form.
                IT services (use the id): %s
                Categories (use the id, or 0 if unsure): %s
                Impact: LOW = only me; MEDIUM = my team; HIGH = a department or key function; EXTENSIVE = many
                departments or the whole organisation.
                Urgency: LOW = can wait days; MEDIUM = today; HIGH = my work is blocked; CRITICAL = money or safety at
                risk right now.
                The draft title is a short summary (max 100 characters); the description is a clear write-up of
                everything the employee told you, in their favour, including error messages.""".formatted(
                serviceList.stream().map(s -> s.getId() + "=" + s.getName() + " (" + s.getDescription() + ")")
                        .collect(Collectors.joining("; ")),
                categoryList(categoryList));
        JsonNode schema = schema("""
                {"type":"OBJECT","properties":{
                  "reply":{"type":"STRING"},"ready":{"type":"BOOLEAN"},
                  "draft":{"type":"OBJECT","properties":{
                    "title":{"type":"STRING"},"description":{"type":"STRING"},"serviceId":{"type":"INTEGER"},
                    "categoryId":{"type":"INTEGER"},
                    "impact":{"type":"STRING","enum":["LOW","MEDIUM","HIGH","EXTENSIVE"]},
                    "urgency":{"type":"STRING","enum":["LOW","MEDIUM","HIGH","CRITICAL"]},
                    "affectedUsers":{"type":"INTEGER"}}}},
                 "required":["reply","ready"]}""");
        List<AiMessage> conversation = turns.stream()
                .map(t -> new AiMessage("user".equals(t.role()) ? AiMessage.Speaker.USER : AiMessage.Speaker.ASSISTANT, t.text()))
                .toList();
        AiReply reply = ai.generateJson(instructions, conversation, schema)
                .orElseThrow(() -> new AiUnavailableException("The AI assistant is busy right now. Try again in a minute, or use the form."));

        JsonNode json = reply.json();
        ReportDraft draft = toDraft(json.path("draft"), serviceList, categoryList);
        // Ready once there is something to review; a missing service is chosen by the employee in the form.
        boolean ready = json.path("ready").asBoolean(false) && draft != null && draft.title() != null
                && draft.description() != null;
        return new ReportChatReply(json.path("reply").asString(""), ready, draft, reply.model());
    }

    /** Validates the model's draft against the real catalogue: unknown ids and invalid values are dropped. */
    private ReportDraft toDraft(JsonNode d, List<ItService> serviceList, List<Category> categoryList) {
        if (d == null || !d.isObject() || d.isEmpty()) {
            return null;
        }
        ItService service = serviceList.stream().filter(s -> s.getId() == d.path("serviceId").asLong(-1)).findFirst().orElse(null);
        Category category = categoryList.stream().filter(c -> c.getId() == d.path("categoryId").asLong(-1)).findFirst().orElse(null);
        String title = clean(d.path("title").asString(null), 150);
        String description = clean(d.path("description").asString(null), 4000);
        int users = d.path("affectedUsers").asInt(1);
        return new ReportDraft(title, description,
                service != null ? service.getId() : null, service != null ? service.getName() : null,
                category != null ? category.getId() : null, category != null ? category.getName() : null,
                parse(Impact.class, d.path("impact").asString(null), Impact.LOW),
                parse(Urgency.class, d.path("urgency").asString(null), Urgency.MEDIUM),
                Math.max(1, Math.min(users, 1_000_000)));
    }

    // ----- helpers ----------------------------------------------------------------------------------------------

    private void requireStaff() {
        if (currentUser.require().getRole() == Role.EMPLOYEE) {
            throw new AuthorizationException("Only support staff can ask for fix suggestions");
        }
    }

    private void requireAvailable() {
        if (!ai.available()) {
            throw new AiUnavailableException("The AI assistant is not configured on this server.");
        }
    }

    private static String categoryList(List<Category> list) {
        return list.stream().map(c -> c.getId() + "=" + c.getName()).collect(Collectors.joining(", "));
    }

    private JsonNode schema(String json) {
        return mapper.readTree(json);
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(item -> {
                String s = item.asString("").trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            });
        }
        return out;
    }

    private static String clean(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        return Arrays.stream(type.getEnumConstants()).filter(e -> e.name().equalsIgnoreCase(value)).findFirst().orElse(fallback);
    }
}
