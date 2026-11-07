package com.iitsm.backend.domain.escalation;

/** Builds the standard escalation chain: Team Lead → System Admin → IT Manager. */
public final class EscalationChain {

    private EscalationChain() {
    }

    /** @return the first handler; the rest are linked behind it */
    public static EscalationHandler standard() {
        EscalationHandler head = new TeamLeadEscalation();
        head.then(new SystemAdminEscalation())
                .then(new ItManagerEscalation());
        return head;
    }
}
