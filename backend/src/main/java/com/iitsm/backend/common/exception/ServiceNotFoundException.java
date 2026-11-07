package com.iitsm.backend.common.exception;

/** The IT service named in a request does not exist. Listed explicitly in the spec's error-handling section. */
public class ServiceNotFoundException extends NotFoundException {

    public ServiceNotFoundException(Long serviceId) {
        super("SERVICE_NOT_FOUND", "IT service '" + serviceId + "' was not found");
    }
}
