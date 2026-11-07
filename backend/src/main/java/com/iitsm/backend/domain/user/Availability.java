package com.iitsm.backend.domain.user;

/** Whether a technician can currently take new incidents. */
public enum Availability {
    AVAILABLE,
    BUSY,
    OFF_DUTY,
    ON_LEAVE;

    public boolean acceptsNewWork() {
        return this == AVAILABLE;
    }
}
