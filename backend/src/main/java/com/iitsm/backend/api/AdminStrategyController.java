package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.CatalogDtos.SelectStrategyRequest;
import com.iitsm.backend.api.dto.CatalogDtos.StrategySettings;
import com.iitsm.backend.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets an administrator switch the priority algorithm and the assignment strategy at runtime: the spec's
 * "change the assignment strategy without redesigning the application", as a single API call.
 * Secured by the {@code /api/admin/**} rule in the security configuration.
 */
@RestController
@RequestMapping("/api/admin/strategies")
public class AdminStrategyController {

    private final CatalogService catalog;

    public AdminStrategyController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public StrategySettings settings() {
        return catalog.strategySettings();
    }

    @PutMapping("/assignment")
    public StrategySettings selectAssignment(@Valid @RequestBody SelectStrategyRequest request) {
        return catalog.selectAssignmentStrategy(request.key());
    }

    @PutMapping("/priority")
    public StrategySettings selectPriority(@Valid @RequestBody SelectStrategyRequest request) {
        return catalog.selectPriorityCalculator(request.key());
    }
}
