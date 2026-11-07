package com.iitsm.backend.common.strategy;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.common.exception.NotFoundException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds every available implementation of one kind of strategy and which one is active.
 *
 * <p><b>Generic:</b> the same class manages priority calculators and assignment strategies
 * ({@code StrategyRegistry<PriorityCalculator>}, {@code StrategyRegistry<AssignmentStrategy>}), so switching
 * behaviour at runtime is written once. This is what makes "change the assignment strategy without redesigning
 * the application" a single admin action.
 *
 * <p>Thread-safe for reads and switches: {@code active} is {@code volatile}, so a switch made by one request is
 * immediately visible to all others.
 */
public class StrategyRegistry<T extends NamedStrategy> {

    private final String kind;
    private final Map<String, T> strategiesByKey = new LinkedHashMap<>();
    private volatile T active;

    /**
     * @param kind       what this registry holds, for error messages, e.g. "Assignment strategy"
     * @param strategies all available implementations; keys must be unique
     * @param defaultKey the strategy that is active at startup
     */
    public StrategyRegistry(String kind, Collection<? extends T> strategies, String defaultKey) {
        this.kind = Validate.requireText(kind, "Registry kind", 60);
        Validate.requireNonNull(strategies, "Strategies");
        for (T strategy : strategies) {
            T previous = strategiesByKey.putIfAbsent(strategy.key(), strategy);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate " + kind + " key: " + strategy.key());
            }
        }
        if (strategiesByKey.isEmpty()) {
            throw new IllegalArgumentException("At least one " + kind + " is required");
        }
        this.active = get(defaultKey);
    }

    public T active() {
        return active;
    }

    /** Switches the active strategy. Takes effect for the next decision; earlier decisions are unchanged. */
    public void activate(String key) {
        this.active = get(key);
    }

    public T get(String key) {
        T strategy = strategiesByKey.get(key);
        if (strategy == null) {
            throw new NotFoundException(kind, key);
        }
        return strategy;
    }

    /** All strategies in registration order, for the admin UI. */
    public List<T> all() {
        return List.copyOf(strategiesByKey.values());
    }
}
