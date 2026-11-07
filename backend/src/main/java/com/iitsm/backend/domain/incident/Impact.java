package com.iitsm.backend.domain.incident;

/** How widely an incident affects the business. One of the three priority factors. */
public enum Impact {
    LOW(1),        // a single user, workaround available
    MEDIUM(2),     // a team or department
    HIGH(3),       // a whole site or a key business function
    EXTENSIVE(4);  // the whole organization or customers

    private final int weight;

    Impact(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
