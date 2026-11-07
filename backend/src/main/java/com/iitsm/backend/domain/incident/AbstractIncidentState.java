package com.iitsm.backend.domain.incident;

import com.iitsm.backend.common.exception.InvalidStateTransitionException;
import com.iitsm.backend.domain.catalog.Category;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;

import java.time.Instant;

/**
 * Rejects <em>every</em> operation by default. Concrete states override only the operations they allow.
 *
 * <p>This "deny by default" design means a new operation added to {@link IncidentState} is automatically
 * forbidden everywhere until a state explicitly allows it. Forgetting a rule fails safe.
 */
abstract class AbstractIncidentState implements IncidentState {

    protected InvalidStateTransitionException reject(IncidentOperation operation) {
        return new InvalidStateTransitionException(status().name(), operation.name());
    }

    @Override
    public void classify(Incident incident, Category category, Priority priority, SlaPolicy slaPolicy, Instant now) {
        throw reject(IncidentOperation.CLASSIFY);
    }

    @Override
    public void assign(Incident incident, Technician technician, int technicianActiveIncidents, Instant now) {
        throw reject(IncidentOperation.ASSIGN);
    }

    @Override
    public void acknowledge(Incident incident, Technician by, Instant now) {
        throw reject(IncidentOperation.ACKNOWLEDGE);
    }

    @Override
    public void startWork(Incident incident, Technician by, Instant now) {
        throw reject(IncidentOperation.START_WORK);
    }

    @Override
    public void requestInfo(Incident incident, Technician by, String question, Instant now) {
        throw reject(IncidentOperation.REQUEST_INFO);
    }

    @Override
    public void provideInfo(Incident incident, Employee by, String answer, Instant now) {
        throw reject(IncidentOperation.PROVIDE_INFO);
    }

    @Override
    public void resolve(Incident incident, Technician by, String summary, String rootCause, ResolutionCode code,
                        Instant now) {
        throw reject(IncidentOperation.RESOLVE);
    }

    @Override
    public void acceptResolution(Incident incident, Employee by, int rating, String comment, Instant now) {
        throw reject(IncidentOperation.ACCEPT_RESOLUTION);
    }

    @Override
    public void rejectResolution(Incident incident, Employee by, String reason, Instant now) {
        throw reject(IncidentOperation.REJECT_RESOLUTION);
    }

    @Override
    public void autoVerify(Incident incident, Instant now) {
        throw reject(IncidentOperation.AUTO_VERIFY);
    }

    @Override
    public void close(Incident incident, Instant now) {
        throw reject(IncidentOperation.CLOSE);
    }

    @Override
    public void cancel(Incident incident, String reason, Incident original, Instant now) {
        throw reject(IncidentOperation.CANCEL);
    }
}
