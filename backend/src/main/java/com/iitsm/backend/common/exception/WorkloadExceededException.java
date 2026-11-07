package com.iitsm.backend.common.exception;

/** The chosen technician already has the maximum number of active incidents. */
public class WorkloadExceededException extends BusinessRuleException {

    public WorkloadExceededException(String technicianName, int maxActiveIncidents) {
        super("WORKLOAD_EXCEEDED", "Technician " + technicianName + " already has the maximum of "
                + maxActiveIncidents + " active incidents");
    }
}
