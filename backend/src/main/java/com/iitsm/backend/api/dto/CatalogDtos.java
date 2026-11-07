package com.iitsm.backend.api.dto;

import com.iitsm.backend.common.strategy.NamedStrategy;
import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.catalog.ServiceStatus;
import com.iitsm.backend.domain.incident.Impact;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.Urgency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Services, categories, priority preview and strategy selection. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record ServiceView(Long id, String name, String description, BusinessCriticality criticality,
                              ServiceStatus status, List<String> dependsOn) {

        public static ServiceView of(ItService service) {
            return new ServiceView(service.getId(), service.getName(), service.getDescription(),
                    service.getCriticality(), service.getStatus(),
                    service.getDependsOn().stream().map(ItService::getName).sorted().toList());
        }
    }

    public record CategoryView(Long id, String name, String description, boolean active) {

        public static CategoryView of(Category category) {
            return new CategoryView(category.getId(), category.getName(), category.getDescription(),
                    category.isActive());
        }
    }

    public record PriorityPreviewRequest(@NotNull Impact impact, @NotNull Urgency urgency, @NotNull Long serviceId,
                                         @NotNull @Min(1) @Max(Incident.MAX_AFFECTED_USERS) Integer affectedUsers) {
    }

    public record StrategyView(String key, String displayName, String description, boolean active) {

        public static StrategyView of(NamedStrategy strategy, NamedStrategy active) {
            return new StrategyView(strategy.key(), strategy.displayName(), strategy.description(),
                    strategy.key().equals(active.key()));
        }
    }

    public record StrategySettings(List<StrategyView> priorityCalculators, List<StrategyView> assignmentStrategies,
                                   boolean autoAssign) {
    }

    public record SelectStrategyRequest(@NotBlank String key) {
    }
}
