package com.iitsm.backend.domain.priority;

import com.iitsm.backend.common.strategy.NamedStrategy;
import com.iitsm.backend.domain.incident.Priority;

/**
 * <b>Strategy pattern:</b> a way of turning priority factors into a P1–P4 priority.
 *
 * <p>Implementations are interchangeable: the administrator chooses which one is active, and nothing else in
 * the system changes. Every result carries a rationale string explaining how it was reached.
 */
public interface PriorityCalculator extends NamedStrategy {

    Priority calculate(PriorityFactors factors);
}
