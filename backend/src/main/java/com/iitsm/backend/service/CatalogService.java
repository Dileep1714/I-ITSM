package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.CatalogDtos.CategoryView;
import com.iitsm.backend.api.dto.CatalogDtos.PriorityPreviewRequest;
import com.iitsm.backend.api.dto.CatalogDtos.ServiceView;
import com.iitsm.backend.api.dto.CatalogDtos.StrategySettings;
import com.iitsm.backend.api.dto.CatalogDtos.StrategyView;
import com.iitsm.backend.api.dto.IncidentDtos.PriorityView;
import com.iitsm.backend.common.exception.ServiceNotFoundException;
import com.iitsm.backend.common.strategy.StrategyRegistry;
import com.iitsm.backend.config.ItsmProperties;
import com.iitsm.backend.domain.assignment.AssignmentStrategy;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.priority.PriorityCalculator;
import com.iitsm.backend.domain.priority.PriorityFactors;
import com.iitsm.backend.repository.CategoryRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Reference data for forms (services, categories), the live priority preview, and strategy settings. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final StrategyRegistry<PriorityCalculator> priorityCalculators;
    private final StrategyRegistry<AssignmentStrategy> assignmentStrategies;
    private final ItsmProperties.Assignment assignmentSettings;

    public CatalogService(ItServiceRepository services, CategoryRepository categories,
                          StrategyRegistry<PriorityCalculator> priorityCalculators,
                          StrategyRegistry<AssignmentStrategy> assignmentStrategies,
                          ItsmProperties.Assignment assignmentSettings) {
        this.services = services;
        this.categories = categories;
        this.priorityCalculators = priorityCalculators;
        this.assignmentStrategies = assignmentStrategies;
        this.assignmentSettings = assignmentSettings;
    }

    public List<ServiceView> services() {
        return services.findAllByOrderByName().stream().map(ServiceView::of).toList();
    }

    public List<CategoryView> activeCategories() {
        return categories.findByActiveTrueOrderByName().stream().map(CategoryView::of).toList();
    }

    /** Shows the reporter the priority their incident <em>would</em> get, while they fill in the form. */
    public PriorityView previewPriority(PriorityPreviewRequest request) {
        ItService service = services.findById(request.serviceId())
                .orElseThrow(() -> new ServiceNotFoundException(request.serviceId()));
        Priority priority = priorityCalculators.active().calculate(new PriorityFactors(
                request.impact(), request.urgency(), service.getCriticality(), request.affectedUsers()));
        return new PriorityView(priority.getLevel(), priority.getLevel().label(), priority.getScore(),
                priority.getRationale());
    }

    public StrategySettings strategySettings() {
        PriorityCalculator activeCalculator = priorityCalculators.active();
        AssignmentStrategy activeStrategy = assignmentStrategies.active();
        return new StrategySettings(
                priorityCalculators.all().stream().map(c -> StrategyView.of(c, activeCalculator)).toList(),
                assignmentStrategies.all().stream().map(s -> StrategyView.of(s, activeStrategy)).toList(),
                assignmentSettings.autoAssign());
    }

    public StrategySettings selectAssignmentStrategy(String key) {
        assignmentStrategies.activate(key);
        return strategySettings();
    }

    public StrategySettings selectPriorityCalculator(String key) {
        priorityCalculators.activate(key);
        return strategySettings();
    }
}
