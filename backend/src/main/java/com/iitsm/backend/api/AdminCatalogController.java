package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.AdminDtos.CategoryAdminView;
import com.iitsm.backend.api.dto.AdminDtos.CategoryRequest;
import com.iitsm.backend.api.dto.AdminDtos.ServiceAdminView;
import com.iitsm.backend.api.dto.AdminDtos.ServiceRequest;
import com.iitsm.backend.api.dto.AdminDtos.SlaPolicyRequest;
import com.iitsm.backend.api.dto.AdminDtos.SlaPolicyView;
import com.iitsm.backend.service.AdminCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Categories, services and SLA policies. Admin only, via the {@code /api/admin/**} security rule. */
@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final AdminCatalogService catalog;

    public AdminCatalogController(AdminCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<CategoryAdminView> categories() {
        return catalog.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryAdminView createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalog.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public CategoryAdminView updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return catalog.updateCategory(id, request);
    }

    @GetMapping("/services")
    public List<ServiceAdminView> services() {
        return catalog.services();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceAdminView createService(@Valid @RequestBody ServiceRequest request) {
        return catalog.createService(request);
    }

    @PutMapping("/services/{id}")
    public ServiceAdminView updateService(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return catalog.updateService(id, request);
    }

    @GetMapping("/sla-policies")
    public List<SlaPolicyView> slaPolicies() {
        return catalog.slaPolicies();
    }

    @PostMapping("/sla-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public SlaPolicyView createSlaPolicy(@Valid @RequestBody SlaPolicyRequest request) {
        return catalog.createSlaPolicy(request);
    }

    @PutMapping("/sla-policies/{id}")
    public SlaPolicyView updateSlaPolicy(@PathVariable Long id, @Valid @RequestBody SlaPolicyRequest request) {
        return catalog.updateSlaPolicy(id, request);
    }

    @DeleteMapping("/sla-policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlaPolicy(@PathVariable Long id) {
        catalog.deleteSlaPolicy(id);
    }
}
