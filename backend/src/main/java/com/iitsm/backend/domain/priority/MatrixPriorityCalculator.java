package com.iitsm.backend.domain.priority;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.incident.Priority;
import com.iitsm.backend.domain.incident.PriorityLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The classic ITIL approach: look up Impact × Urgency in a fixed matrix, then adjust for the service and the
 * number of affected users.
 *
 * <pre>
 *                    Urgency:  LOW   MEDIUM  HIGH  CRITICAL
 *   Impact LOW                 P4    P4      P3    P3
 *          MEDIUM              P4    P3      P3    P2
 *          HIGH                P3    P3      P2    P1
 *          EXTENSIVE           P3    P2      P1    P1
 * </pre>
 *
 * Then: mission-critical service → one level higher; low-criticality service → one level lower;
 * more than 100 affected users → one level higher. The result is capped at P1 and P4.
 *
 * <p>Compared with {@link WeightedScoreCalculator}, the matrix is easier for staff to predict by eye; the weighted
 * score distinguishes cases more finely. Offering both shows the Strategy pattern doing real work.
 */
public class MatrixPriorityCalculator implements PriorityCalculator {

    public static final String KEY = "ITIL_MATRIX";
    static final int LARGE_GROUP_USERS = 100;

    private static final PriorityLevel[][] MATRIX = {
            //            LOW               MEDIUM            HIGH              CRITICAL
            /* LOW */    {PriorityLevel.P4, PriorityLevel.P4, PriorityLevel.P3, PriorityLevel.P3},
            /* MEDIUM */ {PriorityLevel.P4, PriorityLevel.P3, PriorityLevel.P3, PriorityLevel.P2},
            /* HIGH */   {PriorityLevel.P3, PriorityLevel.P3, PriorityLevel.P2, PriorityLevel.P1},
            /* EXT. */   {PriorityLevel.P3, PriorityLevel.P2, PriorityLevel.P1, PriorityLevel.P1},
    };

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "ITIL priority matrix";
    }

    @Override
    public String description() {
        return "Impact × Urgency lookup table, adjusted one level for service criticality and for more than "
                + LARGE_GROUP_USERS + " affected users.";
    }

    @Override
    public Priority calculate(PriorityFactors factors) {
        Validate.requireNonNull(factors, "Priority factors");
        PriorityLevel base = MATRIX[factors.impact().ordinal()][factors.urgency().ordinal()];

        int shift = 0; // negative = more urgent (towards P1)
        List<String> adjustments = new ArrayList<>();
        if (factors.criticality() == BusinessCriticality.MISSION_CRITICAL) {
            shift--;
            adjustments.add("mission-critical service ↑");
        } else if (factors.criticality() == BusinessCriticality.LOW) {
            shift++;
            adjustments.add("low-criticality service ↓");
        }
        if (factors.affectedUsers() > LARGE_GROUP_USERS) {
            shift--;
            adjustments.add(factors.affectedUsers() + " users affected ↑");
        }

        PriorityLevel[] levels = PriorityLevel.values();
        int index = Math.clamp(base.ordinal() + shift, 0, levels.length - 1);
        PriorityLevel level = levels[index];

        String rationale = "Matrix: Impact " + factors.impact() + " × Urgency " + factors.urgency() + " = " + base
                + (adjustments.isEmpty() ? "" : "; " + String.join(", ", adjustments))
                + " → " + level + " " + level.label();
        // Score: 4 for P1 … 1 for P4, so scores stay comparable in reports.
        return new Priority(level, levels.length - index, rationale);
    }
}
