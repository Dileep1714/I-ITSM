package com.iitsm.backend.domain.catalog;

/** How important an IT service is to the business. One of the three factors in priority calculation. */
public enum BusinessCriticality {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    MISSION_CRITICAL(4);

    private final int weight;

    BusinessCriticality(int weight) {
        this.weight = weight;
    }

    /** Numeric weight 1–4 used by the priority formula. */
    public int weight() {
        return weight;
    }
}
