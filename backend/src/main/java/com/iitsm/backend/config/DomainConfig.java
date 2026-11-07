package com.iitsm.backend.config;

import com.iitsm.backend.common.strategy.StrategyRegistry;
import com.iitsm.backend.domain.assignment.AssignmentStrategy;
import com.iitsm.backend.domain.assignment.ExperienceBasedStrategy;
import com.iitsm.backend.domain.assignment.LoadBasedStrategy;
import com.iitsm.backend.domain.assignment.RoundRobinStrategy;
import com.iitsm.backend.domain.assignment.SkillBasedStrategy;
import com.iitsm.backend.domain.assignment.SmartCompositeStrategy;
import com.iitsm.backend.domain.priority.MatrixPriorityCalculator;
import com.iitsm.backend.domain.priority.PriorityCalculator;
import com.iitsm.backend.domain.priority.WeightedScoreCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * Wires the plain-Java domain objects into Spring.
 *
 * <p>The domain classes have no Spring annotations at all; this class is the only place that knows how they are
 * assembled. Adding a new assignment strategy means writing one class and adding it to the list below.
 */
@Configuration
public class DomainConfig {

    /** One clock for the whole application. Tests can replace it with a fixed clock to control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public StrategyRegistry<PriorityCalculator> priorityCalculators(ItsmProperties.PriorityConfig config) {
        return new StrategyRegistry<>("Priority calculator",
                List.of(new WeightedScoreCalculator(), new MatrixPriorityCalculator()),
                config.defaultCalculator());
    }

    @Bean
    public StrategyRegistry<AssignmentStrategy> assignmentStrategies(ItsmProperties.Assignment config) {
        return new StrategyRegistry<>("Assignment strategy",
                List.of(SmartCompositeStrategy.standard(), new SkillBasedStrategy(), new LoadBasedStrategy(),
                        new ExperienceBasedStrategy(), new RoundRobinStrategy()),
                config.defaultStrategy());
    }
}
