package com.iitsm.backend.common.exception;

/** The user is logged in but is not allowed to perform this operation (e.g. an employee closing an incident). */
public class AuthorizationException extends ItsmException {

    public AuthorizationException(String message) {
        super("FORBIDDEN", message);
    }

    @Override
    public int httpStatus() {
        return 403;
    }
}
