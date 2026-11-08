package com.iitsm.backend.common.exception;

/**
 * The reporter already has an open incident that says the same thing on the same service. Returned as 409 so the
 * client can show the existing incident and let the user submit anyway if it really is a different problem.
 */
public class DuplicateIncidentException extends ItsmException {

    private final Long existingIncidentId;

    public DuplicateIncidentException(Long existingIncidentId, String existingNumber, String existingTitle) {
        super("DUPLICATE_INCIDENT", "You already reported this problem as " + existingNumber + " (\"" + existingTitle
                + "\"), which is still open. Add a note there instead, or submit anyway if this is a different problem.");
        this.existingIncidentId = existingIncidentId;
    }

    public Long getExistingIncidentId() {
        return existingIncidentId;
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
