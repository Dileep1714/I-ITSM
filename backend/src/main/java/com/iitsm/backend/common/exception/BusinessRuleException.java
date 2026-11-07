package com.iitsm.backend.common.exception;

/**
 * The request was well-formed, but performing it would break a business rule
 * (for example assigning a technician whose workload is already full).
 */
public class BusinessRuleException extends ItsmException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }

    @Override
    public int httpStatus() {
        return 422; // Unprocessable Content
    }
}
