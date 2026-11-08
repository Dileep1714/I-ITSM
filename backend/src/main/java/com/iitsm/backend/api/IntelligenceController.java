package com.iitsm.backend.api;

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

    public IntelligenceController(IntelligenceService intelligence, IncidentService incidents) {
        this.intelligence = intelligence;
        this.incidents = incidents;
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
