package com.iitsm.backend.common.exception;

/** A requested entity (incident, user, service…) does not exist. */
public class NotFoundException extends ItsmException {

    public NotFoundException(String entityName, Object id) {
        this("NOT_FOUND", entityName + " '" + id + "' was not found");
    }

    /** For subclasses that want a more specific code, for example {@code "SERVICE_NOT_FOUND"}. */
    protected NotFoundException(String code, String message) {
        super(code, message);
    }

    @Override
    public int httpStatus() {
        return 404;
    }
}
