package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.AdminDtos.CategoryAdminView;
import com.iitsm.backend.api.dto.AdminDtos.CategoryRequest;
import com.iitsm.backend.api.dto.AdminDtos.ServiceAdminView;
import com.iitsm.backend.api.dto.AdminDtos.ServiceRequest;
import com.iitsm.backend.api.dto.AdminDtos.SlaPolicyRequest;
import com.iitsm.backend.api.dto.AdminDtos.SlaPolicyView;
import com.iitsm.backend.common.exception.BusinessRuleException;
import com.iitsm.backend.common.exception.NotFoundException;
import com.iitsm.backend.common.exception.ServiceNotFoundException;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.repository.SlaPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Administrator management of categories, IT services (with their dependency graph) and SLA policies.
 *
 * <p>All the actual rules are enforced by the domain objects (name lengths, dependency cycles, SLA target ranges);
 * this service adds the rules that need the database: unique names and one policy per priority and service.
 */
@Service
@Transactional
public class AdminCatalogService {

    private final CategoryRepository categories;
    private final ItServiceRepository services;
    private final SlaPolicyRepository slaPolicies;

    public AdminCatalogService(CategoryRepository categories, ItServiceRepository services,
                               SlaPolicyRepository slaPolicies) {
        this.categories = categories;
        this.services = services;
        this.slaPolicies = slaPolicies;
    }

    // ----- categories --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CategoryAdminView> categories() {
        return categories.findAllByOrderByName().stream().map(CategoryAdminView::of).toList();
    }

    /** A new category is usable immediately: no code change, as the spec requires. */
    public CategoryAdminView createCategory(CategoryRequest request) {
        requireUniqueCategoryName(request.name(), null);
        Category category = new Category(request.name(), request.description());
        applyKeywords(category, request.keywords());
        if (Boolean.FALSE.equals(request.active())) {
            category.deactivate();
        }
        return CategoryAdminView.of(categories.save(category));
    }

    public CategoryAdminView updateCategory(Long id, CategoryRequest request) {
        Category category = categories.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
        requireUniqueCategoryName(request.name(), id);
        category.rename(request.name());
        category.describe(request.description());
        if (request.keywords() != null) {
            applyKeywords(category, request.keywords());
        }
        if (request.active() != null) {
            if (request.active()) {
                category.activate();
            } else {
                category.deactivate();
            }
        }
        return CategoryAdminView.of(category);
    }

    /** Replaces all keywords with the given ones (normalized and de-duplicated by {@link Category}). */
    private static void applyKeywords(Category category, List<String> keywords) {
        for (String existing : Set.copyOf(category.getKeywords())) {
            category.removeKeyword(existing);
        }
        if (keywords != null) {
            keywords.stream().map(String::strip).filter(k -> !k.isEmpty()).forEach(category::addKeyword);
        }
    }

    private void requireUniqueCategoryName(String name, Long ownId) {
        boolean taken = categories.findAllByOrderByName().stream()
                .anyMatch(c -> !Objects.equals(c.getId(), ownId) && c.getName().equalsIgnoreCase(name.strip()));
        if (taken) {
            throw new BusinessRuleException("CATEGORY_NAME_TAKEN", "A category named '" + name.strip() + "' already exists");
        }
    }

    // ----- services ----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ServiceAdminView> services() {
        return services.findAllByOrderByName().stream().map(ServiceAdminView::of).toList();
    }

    public ServiceAdminView createService(ServiceRequest request) {
        requireUniqueServiceName(request.name(), null);
        ItService service = services.save(new ItService(request.name(), request.description(), request.criticality()));
        if (request.status() != null) {
            service.changeStatus(request.status());
        }
        applyDependencies(service, request.dependsOnIds());
        return ServiceAdminView.of(service);
    }

    public ServiceAdminView updateService(Long id, ServiceRequest request) {
        ItService service = services.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));
        requireUniqueServiceName(request.name(), id);
        service.rename(request.name());
        service.describe(request.description());
        service.changeCriticality(request.criticality());
        if (request.status() != null) {
            service.changeStatus(request.status());
        }
        if (request.dependsOnIds() != null) {
            applyDependencies(service, request.dependsOnIds());
        }
        return ServiceAdminView.of(service);
    }

    /**
     * Makes the service depend on exactly the given services. {@link ItService#addDependency} rejects a cycle
     * (CIRCULAR_DEPENDENCY), and the whole update is rolled back with it.
     */
    private void applyDependencies(ItService service, List<Long> dependsOnIds) {
        Set<ItService> wanted = new HashSet<>();
        for (Long dependencyId : dependsOnIds == null ? List.<Long>of() : dependsOnIds) {
            wanted.add(services.findById(dependencyId).orElseThrow(() -> new ServiceNotFoundException(dependencyId)));
        }
        for (ItService current : Set.copyOf(service.getDependsOn())) {
            if (!wanted.contains(current)) {
                service.removeDependency(current);
            }
        }
        for (ItService dependency : wanted) {
            if (!service.getDependsOn().contains(dependency)) {
                service.addDependency(dependency);
            }
        }
    }

    private void requireUniqueServiceName(String name, Long ownId) {
        boolean taken = services.findAllByOrderByName().stream()
                .anyMatch(s -> !Objects.equals(s.getId(), ownId) && s.getName().equalsIgnoreCase(name.strip()));
        if (taken) {
            throw new BusinessRuleException("SERVICE_NAME_TAKEN", "A service named '" + name.strip() + "' already exists");
        }
    }

    // ----- SLA policies ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SlaPolicyView> slaPolicies() {
        return slaPolicies.findAllByOrderByPriority().stream()
                .sorted((a, b) -> {
                    int byPriority = a.getPriority().compareTo(b.getPriority());
                    if (byPriority != 0) {
                        return byPriority;
                    }
                    // generic policy first, then service-specific ones by name
                    if (a.getService() == null || b.getService() == null) {
                        return a.getService() == null ? (b.getService() == null ? 0 : -1) : 1;
                    }
                    return a.getService().getName().compareTo(b.getService().getName());
                })
                .map(SlaPolicyView::of)
                .toList();
    }

    public SlaPolicyView createSlaPolicy(SlaPolicyRequest request) {
        ItService service = request.serviceId() == null ? null : services.findById(request.serviceId())
                .orElseThrow(() -> new ServiceNotFoundException(request.serviceId()));
        boolean exists = service == null
                ? slaPolicies.findByPriorityAndServiceIsNull(request.priority()).isPresent()
                : slaPolicies.findByPriorityAndService(request.priority(), service).isPresent();
        if (exists) {
            throw new BusinessRuleException("SLA_POLICY_EXISTS", "There is already a " + request.priority()
                    + " policy for " + (service == null ? "all services" : service.getName()) + ": edit that one");
        }
        SlaPolicy policy = new SlaPolicy(request.priority(), service, Duration.ofMinutes(request.responseMinutes()),
                Duration.ofMinutes(request.resolutionMinutes()), request.warningThreshold());
        return SlaPolicyView.of(slaPolicies.save(policy));
    }

    /**
     * Changes targets and warning threshold. Incidents whose SLA already started keep their original targets
     * (they were snapshotted), so a change never moves an existing deadline.
     */
    public SlaPolicyView updateSlaPolicy(Long id, SlaPolicyRequest request) {
        SlaPolicy policy = slaPolicies.findById(id).orElseThrow(() -> new NotFoundException("SLA policy", id));
        if (policy.getPriority() != request.priority()
                || !Objects.equals(policy.getService() == null ? null : policy.getService().getId(), request.serviceId())) {
            throw new BusinessRuleException("SLA_POLICY_SCOPE_FIXED",
                    "The priority and service of a policy cannot be changed: create a new policy instead");
        }
        policy.updateTargets(Duration.ofMinutes(request.responseMinutes()), Duration.ofMinutes(request.resolutionMinutes()));
        policy.changeWarningThreshold(request.warningThreshold());
        return SlaPolicyView.of(policy);
    }

    /** Only service-specific policies can be removed: the generic ones are the fallback for every incident. */
    public void deleteSlaPolicy(Long id) {
        SlaPolicy policy = slaPolicies.findById(id).orElseThrow(() -> new NotFoundException("SLA policy", id));
        if (!policy.isServiceSpecific()) {
            throw new BusinessRuleException("SLA_POLICY_REQUIRED", "The generic " + policy.getPriority()
                    + " policy is the fallback for every service and cannot be deleted; edit its targets instead");
        }
        slaPolicies.delete(policy);
    }
}
