package com.iitsm.backend.common.exception;

/** An incident was asked to move to a state that is not allowed from its current state (e.g. NEW → CLOSED). */
public class InvalidStateTransitionException extends BusinessRuleException {

    public InvalidStateTransitionException(String currentState, String operation) {
        super("INVALID_STATE_TRANSITION",
                "Operation '" + operation + "' is not allowed while the incident is " + currentState);
    }

    @Override
    public int httpStatus() {
        return 409; // Conflict: the request conflicts with the incident's current state
    }
}
