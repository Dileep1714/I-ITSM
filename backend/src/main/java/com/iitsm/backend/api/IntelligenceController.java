package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.IntelligenceDtos.SlaForecastView;
import com.iitsm.backend.domain.prediction.RiskLevel;
import com.iitsm.backend.service.SlaForecastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import com.iitsm.backend.api.dto.IncidentDtos.IncidentDetail;
import com.iitsm.backend.api.dto.IntelligenceDtos.CorrelationView;
import com.iitsm.backend.api.dto.IntelligenceDtos.DraftAnalysis;
import com.iitsm.backend.api.dto.IntelligenceDtos.DraftAnalysisRequest;
import com.iitsm.backend.api.dto.IntelligenceDtos.MarkDuplicateRequest;
import com.iitsm.backend.api.dto.IntelligenceDtos.RelatedIncidentView;
import com.iitsm.backend.service.IncidentService;
import com.iitsm.backend.service.IntelligenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Intelligent features: category suggestion, duplicate/similar detection, root-cause correlation. */
@RestController
public class IntelligenceController {

    private final IntelligenceService intelligence;
    private final IncidentService incidents;
    private final SlaForecastService forecasts;

    public IntelligenceController(IntelligenceService intelligence, IncidentService incidents,
                                  SlaForecastService forecasts) {
        this.intelligence = intelligence;
        this.incidents = incidents;
        this.forecasts = forecasts;
    }

    /** Predictive SLA for one incident; 204 when no forecast applies (not started, paused, resolved or late). */
    @GetMapping("/api/incidents/{id}/sla-forecast")
    public ResponseEntity<SlaForecastView> slaForecast(@PathVariable Long id) {
        return forecasts.forecastFor(id).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    /** Open incidents likely to miss their resolution deadline, most likely first. Support staff only. */
    @GetMapping("/api/intelligence/sla-forecasts")
    public List<SlaForecastView> slaForecasts(@RequestParam(defaultValue = "MEDIUM") RiskLevel minRisk) {
        return forecasts.atRisk(minRisk);
    }

    /** Analyse the report form as the user types (nothing is saved). */
    @PostMapping("/api/intelligence/analyze")
    public DraftAnalysis analyze(@Valid @RequestBody DraftAnalysisRequest request) {
        return intelligence.analyzeDraft(request);
    }

    /** Open incidents from the last day that share a likely root-cause service. Support staff only. */
    @GetMapping("/api/intelligence/correlations")
    public List<CorrelationView> correlations() {
        return intelligence.correlations();
    }

    /** Likely duplicates and similar past incidents (with their fixes). Support staff only. */
    @GetMapping("/api/incidents/{id}/similar")
    public List<RelatedIncidentView> similar(@PathVariable Long id) {
        return intelligence.similarTo(id);
    }

    /** Cancel this incident as a duplicate of another, keeping the link. Support staff only. */
    @PostMapping("/api/incidents/{id}/duplicate-of")
    public IncidentDetail markDuplicate(@PathVariable Long id, @Valid @RequestBody MarkDuplicateRequest request) {
        return incidents.markDuplicate(id, request.originalId());
    }
}
