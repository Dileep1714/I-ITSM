package com.iitsm.backend.common.exception;

/** The chosen technician is off duty, on leave, busy or deactivated. */
public class TechnicianUnavailableException extends BusinessRuleException {

    public TechnicianUnavailableException(String technicianName) {
        super("TECHNICIAN_UNAVAILABLE", "Technician " + technicianName + " is not available for new incidents");
    }
}
