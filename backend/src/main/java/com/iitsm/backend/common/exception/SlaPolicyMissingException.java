package com.iitsm.backend.common.exception;

/** No SLA policy exists for this priority (neither for the service nor a generic one). An admin must configure one. */
public class SlaPolicyMissingException extends BusinessRuleException {

    public SlaPolicyMissingException(String priority, String serviceName) {
        super("SLA_POLICY_MISSING", "No SLA policy is configured for " + priority + " incidents on '"
                + serviceName + "'. An administrator must add one.");
    }
}
