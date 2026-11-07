package com.iitsm.backend.common.strategy;

import com.iitsm.backend.common.exception.NotFoundException;
import com.iitsm.backend.domain.assignment.AssignmentStrategy;
import com.iitsm.backend.domain.assignment.LoadBasedStrategy;
import com.iitsm.backend.domain.assignment.RoundRobinStrategy;
import com.iitsm.backend.domain.assignment.SkillBasedStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRegistryTest {

    private StrategyRegistry<AssignmentStrategy> registry() {
        return new StrategyRegistry<>("Assignment strategy",
                List.of(new SkillBasedStrategy(), new LoadBasedStrategy(), new RoundRobinStrategy()),
                SkillBasedStrategy.KEY);
    }

    @Test
    @DisplayName("The default strategy is active at startup and can be switched at runtime")
    void switchAtRuntime() {
        StrategyRegistry<AssignmentStrategy> registry = registry();
        assertThat(registry.active()).isInstanceOf(SkillBasedStrategy.class);

        registry.activate(LoadBasedStrategy.KEY);

        assertThat(registry.active()).isInstanceOf(LoadBasedStrategy.class);
        assertThat(registry.all()).hasSize(3);
    }

    @Test
    @DisplayName("Activating an unknown strategy fails and keeps the current one")
    void unknownKeyRejected() {
        StrategyRegistry<AssignmentStrategy> registry = registry();

        assertThatThrownBy(() -> registry.activate("MAGIC")).isInstanceOf(NotFoundException.class);
        assertThat(registry.active()).isInstanceOf(SkillBasedStrategy.class);
    }

    @Test
    @DisplayName("Two strategies with the same key are rejected")
    void duplicateKeysRejected() {
        assertThatThrownBy(() -> new StrategyRegistry<AssignmentStrategy>("Assignment strategy",
                List.of(new SkillBasedStrategy(), new SkillBasedStrategy()), SkillBasedStrategy.KEY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
