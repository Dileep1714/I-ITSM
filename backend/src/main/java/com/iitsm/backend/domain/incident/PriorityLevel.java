package com.iitsm.backend.domain.incident;

public enum PriorityLevel {
    P1("Critical"),
    P2("High"),
    P3("Medium"),
    P4("Low");

    private final String label;

    PriorityLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isHigherThan(PriorityLevel other) {
        return this.ordinal() < other.ordinal(); // P1 has the lowest ordinal and the highest priority
    }
}
