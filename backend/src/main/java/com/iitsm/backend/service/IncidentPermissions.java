package com.iitsm.backend.service;

import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentOperation;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.domain.user.User;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Role-based authorization for incident operations: <em>which kind of user</em> may perform each operation.
 *
 * <p>Three layers work together:
 * <ol>
 *   <li><b>Role</b> (this class): employees cannot classify; technicians cannot accept a resolution…</li>
 *   <li><b>Lifecycle state</b> (the State pattern): no one can close a NEW incident.</li>
 *   <li><b>Ownership</b> (inside {@link Incident}): only <em>the</em> assignee resolves, only <em>the</em>
 *       reporter accepts.</li>
 * </ol>
 * {@link #allowedFor} combines all three, so the UI shows a user exactly the buttons that will work for them.
 */
@Component
public class IncidentPermissions {

    private static final Map<Role, Set<IncidentOperation>> BY_ROLE = new EnumMap<>(Role.class);

    static {
        BY_ROLE.put(Role.EMPLOYEE, EnumSet.of(
                IncidentOperation.PROVIDE_INFO, IncidentOperation.ACCEPT_RESOLUTION,
                IncidentOperation.REJECT_RESOLUTION, IncidentOperation.CANCEL));
        BY_ROLE.put(Role.TECHNICIAN, EnumSet.of(
                IncidentOperation.CLASSIFY, IncidentOperation.ASSIGN, IncidentOperation.ACKNOWLEDGE,
                IncidentOperation.START_WORK, IncidentOperation.REQUEST_INFO, IncidentOperation.RESOLVE,
                IncidentOperation.CLOSE, IncidentOperation.CANCEL, IncidentOperation.MARK_DUPLICATE));
        BY_ROLE.put(Role.ADMINISTRATOR, EnumSet.of(
                IncidentOperation.CLASSIFY, IncidentOperation.ASSIGN, IncidentOperation.CLOSE,
                IncidentOperation.CANCEL, IncidentOperation.MARK_DUPLICATE));
        // AUTO_VERIFY is performed by the system only.
    }

    /** Operations only the assigned technician performs. */
    private static final Set<IncidentOperation> ASSIGNEE_ONLY = EnumSet.of(
            IncidentOperation.ACKNOWLEDGE, IncidentOperation.START_WORK,
            IncidentOperation.REQUEST_INFO, IncidentOperation.RESOLVE);

    /** Throws if this kind of user may never perform {@code operation}. */
    public void require(User user, IncidentOperation operation) {
        if (!roleAllows(user, operation)) {
            throw new AuthorizationException(user.getRole() + " users cannot perform " + operation);
        }
    }

    /** Employees may only see their own incidents; support staff see all. */
    public void requireVisible(User user, Incident incident) {
        if (user.getRole() == Role.EMPLOYEE && !user.equals(incident.getReporter())) {
            throw new AuthorizationException("You can only view incidents you reported");
        }
    }

    /** What this user can do to this incident right now (role ∩ state ∩ ownership). */
    public Set<IncidentOperation> allowedFor(User user, Incident incident) {
        Set<IncidentOperation> result = EnumSet.noneOf(IncidentOperation.class);
        for (IncidentOperation operation : incident.allowedOperations()) {
            if (roleAllows(user, operation) && ownershipAllows(user, incident, operation)) {
                result.add(operation);
            }
        }
        return result;
    }

    private boolean roleAllows(User user, IncidentOperation operation) {
        if (!BY_ROLE.getOrDefault(user.getRole(), Set.of()).contains(operation)) {
            return false;
        }
        // Only team leads and above may (re)assign incidents to others.
        return operation != IncidentOperation.ASSIGN
                || !(user instanceof Technician technician)
                || technician.getSupportLevel().isAtLeast(SupportLevel.L2_TEAM_LEAD);
    }

    private boolean ownershipAllows(User user, Incident incident, IncidentOperation operation) {
        if (user.getRole() == Role.EMPLOYEE) {
            return user.equals(incident.getReporter());
        }
        if (ASSIGNEE_ONLY.contains(operation)) {
            return user.equals(incident.getAssignee());
        }
        return true;
    }
}
