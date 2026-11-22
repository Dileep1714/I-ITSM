package com.iitsm.backend.api;

import com.iitsm.backend.service.ServiceHealthService;
import com.iitsm.backend.api.dto.CatalogDtos.CategoryView;
import com.iitsm.backend.api.dto.CatalogDtos.PriorityPreviewRequest;
import com.iitsm.backend.api.dto.CatalogDtos.ServiceView;
import com.iitsm.backend.api.dto.IncidentDtos.PriorityView;
import com.iitsm.backend.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reference data any logged-in user needs, e.g. to fill in the "report an incident" form. */
@RestController
public class CatalogController {

    private final CatalogService catalog;
    private final ServiceHealthService health;

    public CatalogController(CatalogService catalog, ServiceHealthService health) {
        this.catalog = catalog;
        this.health = health;
    }

    /** Service map / status page: every service's live health, dependencies and blast radius. */
    @GetMapping("/api/catalog/health")
    public List<ServiceHealthService.ServiceHealthView> health() {
        return health.healthMap();
    }

    @GetMapping("/api/catalog/services")
    public List<ServiceView> services() {
        return catalog.services();
    }

    @GetMapping("/api/catalog/categories")
    public List<CategoryView> categories() {
        return catalog.activeCategories();
    }

    @PostMapping("/api/priority/preview")
    public PriorityView previewPriority(@Valid @RequestBody PriorityPreviewRequest request) {
        return catalog.previewPriority(request);
    }
}
