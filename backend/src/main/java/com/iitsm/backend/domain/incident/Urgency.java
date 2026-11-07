package com.iitsm.backend.domain.incident;

/** How quickly the business needs the incident fixed. One of the three priority factors. */
public enum Urgency {
    LOW(1),       // can wait days
    MEDIUM(2),    // needed within the working day
    HIGH(3),      // work is blocked
    CRITICAL(4);  // revenue or safety at risk right now

    private final int weight;

    Urgency(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
