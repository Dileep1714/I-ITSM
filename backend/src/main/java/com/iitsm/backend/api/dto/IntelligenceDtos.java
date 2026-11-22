package com.iitsm.backend.api.dto;

import com.iitsm.backend.api.dto.IncidentDtos.IncidentSummary;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.incident.ResolutionCode;
import com.iitsm.backend.domain.intelligence.CategorySuggester;
import com.iitsm.backend.domain.intelligence.RelatedIncidentFinder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Category suggestions, similar/duplicate incidents and root-cause correlations. */
public final class IntelligenceDtos {

    private IntelligenceDtos() {
    }

    /** The "report an incident" form while it is being filled in: nothing is saved. */
    public record DraftAnalysisRequest(@NotBlank @Size(max = Incident.MAX_TITLE_LENGTH) String title,
                                       @Size(max = Incident.MAX_DESCRIPTION_LENGTH) String description,
                                       Long serviceId) {
    }

    public record DraftAnalysis(List<CategorySuggestionView> categorySuggestions,
                                List<RelatedIncidentView> similarIncidents) {
    }

    public record CategorySuggestionView(Long categoryId, String name, double confidence, List<String> reasons) {

        public static CategorySuggestionView of(CategorySuggester.Suggestion suggestion) {
            return new CategorySuggestionView(suggestion.category().getId(), suggestion.category().getName(),
                    suggestion.confidence(), suggestion.reasons());
        }
    }

    /**
     * Another incident that resembles this one. {@code resolutionSummary} is filled in for resolved incidents seen
     * by support staff: "this fixed it last time".
     */
    public record RelatedIncidentView(Long id, String number, String title, IncidentStatus status,
                                      PriorityLevel priority, String serviceName, Instant createdAt, double score,
                                      RelatedIncidentFinder.Relation relation, List<String> reasons,
                                      String resolutionSummary, ResolutionCode resolutionCode) {
    }

    public record CorrelationView(Long rootServiceId, String rootServiceName, Set<String> affectedServices,
                                  String explanation, List<IncidentSummary> incidents) {
    }

    public record MarkDuplicateRequest(@NotNull Long originalId) {
    }

    /**
     * Predictive SLA: will this open incident miss its resolution deadline?
     *
     * @param probability  0..1 chance of missing the resolution deadline
     * @param risk         LOW, MEDIUM or HIGH
     * @param expectedFinish when it will most likely be resolved
     * @param deadline     the resolution deadline (pauses included)
     * @param lowConfidence true when few comparable incidents exist and the estimate leans on the SLA target
     * @param reasons      the factors behind the number, most important first
     */
    public record SlaForecastView(Long incidentId, String number, String title, PriorityLevel priority,
                                  IncidentStatus status, String assigneeName, double probability, String risk,
                                  Instant expectedFinish, Instant deadline, long expectedRemainingMinutes,
                                  int sampleSize, boolean lowConfidence, String basis, List<String> reasons) {
    }
}
