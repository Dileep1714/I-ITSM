package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.IncidentDtos.AcceptRequest;
import com.iitsm.backend.api.dto.IncidentDtos.AssignRequest;
import com.iitsm.backend.api.dto.IncidentDtos.AuditEntryView;
import com.iitsm.backend.api.dto.IncidentDtos.ClassifyRequest;
import com.iitsm.backend.api.dto.IncidentDtos.IncidentDetail;
import com.iitsm.backend.api.dto.IncidentDtos.IncidentSummary;
import com.iitsm.backend.api.dto.IncidentDtos.NoteRequest;
import com.iitsm.backend.api.dto.IncidentDtos.Recommendation;
import com.iitsm.backend.api.dto.IncidentDtos.ReportIncidentRequest;
import com.iitsm.backend.api.dto.IncidentDtos.ResolveRequest;
import com.iitsm.backend.api.dto.IncidentDtos.TextRequest;
import com.iitsm.backend.api.dto.PageResponse;
import com.iitsm.backend.service.IncidentSearchCriteria;
import com.iitsm.backend.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Incident endpoints. Each lifecycle step is its own POST action ({@code /acknowledge}, {@code /resolve}…) rather
 * than a generic "update status" call. The API mirrors the domain's operations, so an invalid jump like
 * NEW → CLOSED cannot even be expressed.
 *
 * <p>Controllers are deliberately thin: validate the request shape, call the service, return the result.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidents;

    public IncidentController(IncidentService incidents) {
        this.incidents = incidents;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentDetail report(@Valid @RequestBody ReportIncidentRequest request) {
        return incidents.report(request);
    }

    /**
     * Search and list incidents, newest first. With no parameters: "my incidents" (reported by me / assigned to
     * me; everything for administrators). Filters: see {@link IncidentSearchCriteria}.
     */
    @GetMapping
    public PageResponse<IncidentSummary> search(
            IncidentSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return incidents.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    public IncidentDetail get(@PathVariable Long id) {
        return incidents.get(id);
    }

    /** Full audit trail of the incident (employees see only the entries meant for them). */
    @GetMapping("/{id}/history")
    public List<AuditEntryView> history(@PathVariable Long id) {
        return incidents.history(id);
    }

    @PostMapping("/{id}/classify")
    public IncidentDetail classify(@PathVariable Long id, @Valid @RequestBody ClassifyRequest request) {
        return incidents.classify(id, request.categoryId());
    }

    @GetMapping("/{id}/recommendations")
    public List<Recommendation> recommendations(@PathVariable Long id) {
        return incidents.recommendations(id);
    }

    @PostMapping("/{id}/assign")
    public IncidentDetail assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return incidents.assign(id, request.technicianId());
    }

    /** Take an unassigned incident for yourself (any technician). */
    @PostMapping("/{id}/take")
    public IncidentDetail take(@PathVariable Long id) {
        return incidents.take(id);
    }

    @PostMapping("/{id}/acknowledge")
    public IncidentDetail acknowledge(@PathVariable Long id) {
        return incidents.acknowledge(id);
    }

    @PostMapping("/{id}/start")
    public IncidentDetail startWork(@PathVariable Long id) {
        return incidents.startWork(id);
    }

    @PostMapping("/{id}/request-info")
    public IncidentDetail requestInfo(@PathVariable Long id, @Valid @RequestBody TextRequest request) {
        return incidents.requestInfo(id, request.text());
    }

    @PostMapping("/{id}/provide-info")
    public IncidentDetail provideInfo(@PathVariable Long id, @Valid @RequestBody TextRequest request) {
        return incidents.provideInfo(id, request.text());
    }

    @PostMapping("/{id}/resolve")
    public IncidentDetail resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest request) {
        return incidents.resolve(id, request);
    }

    @PostMapping("/{id}/accept")
    public IncidentDetail accept(@PathVariable Long id, @Valid @RequestBody AcceptRequest request) {
        return incidents.acceptResolution(id, request);
    }

    @PostMapping("/{id}/reject")
    public IncidentDetail reject(@PathVariable Long id, @Valid @RequestBody TextRequest request) {
        return incidents.rejectResolution(id, request.text());
    }

    @PostMapping("/{id}/close")
    public IncidentDetail close(@PathVariable Long id) {
        return incidents.close(id);
    }

    @PostMapping("/{id}/cancel")
    public IncidentDetail cancel(@PathVariable Long id, @Valid @RequestBody TextRequest request) {
        return incidents.cancel(id, request.text());
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentDetail addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return incidents.addNote(id, request);
    }
}
