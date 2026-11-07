package com.iitsm.backend.domain.incident;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up the state object for a stored {@link IncidentStatus}.
 *
 * <p>States hold no data of their own, so one shared instance per status serves every incident (the
 * <b>Flyweight</b> idea): no objects are created per transition. The database stores only the status name;
 * this registry turns it back into behaviour when an incident is loaded.
 */
final class IncidentStates {

    private static final Map<IncidentStatus, IncidentState> STATES = new EnumMap<>(IncidentStatus.class);

    static {
        List.of(new NewState(), new ClassifiedState(), new AssignedState(), new AcknowledgedState(),
                        new InProgressState(), new OnHoldState(), new ResolvedState(), new VerifiedState(),
                        new ClosedState(), new CancelledState())
                .forEach(state -> STATES.put(state.status(), state));

        // Fail fast at startup if a status was added without a matching state class.
        for (IncidentStatus status : IncidentStatus.values()) {
            if (!STATES.containsKey(status)) {
                throw new IllegalStateException("No IncidentState registered for status " + status);
            }
        }
    }

    private IncidentStates() {
    }

    static IncidentState of(IncidentStatus status) {
        return STATES.get(status);
    }
}
