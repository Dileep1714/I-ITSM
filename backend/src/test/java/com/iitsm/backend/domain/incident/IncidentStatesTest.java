package com.iitsm.backend.domain.incident;

import com.iitsm.backend.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Guards the State pattern itself: for every status and every operation, the state's
 * {@code allowedOperations()} must agree with what its methods actually do.
 *
 * <p>Allowed operations must <em>not</em> throw {@link InvalidStateTransitionException} (they fail later, on the
 * {@code null} arguments used here, which is fine). Every other operation must throw it. If someone adds an
 * override but forgets to update {@code allowedOperations()} (or the reverse), this test fails.
 */
class IncidentStatesTest {

    private static final Map<IncidentOperation, Consumer<IncidentState>> CALLS = new EnumMap<>(IncidentOperation.class);

    static {
        CALLS.put(IncidentOperation.CLASSIFY, s -> s.classify(null, null, null, null, null));
        CALLS.put(IncidentOperation.ASSIGN, s -> s.assign(null, null, 0, null));
        CALLS.put(IncidentOperation.ACKNOWLEDGE, s -> s.acknowledge(null, null, null));
        CALLS.put(IncidentOperation.START_WORK, s -> s.startWork(null, null, null));
        CALLS.put(IncidentOperation.REQUEST_INFO, s -> s.requestInfo(null, null, null, null));
        CALLS.put(IncidentOperation.PROVIDE_INFO, s -> s.provideInfo(null, null, null, null));
        CALLS.put(IncidentOperation.RESOLVE, s -> s.resolve(null, null, null, null, null, null));
        CALLS.put(IncidentOperation.ACCEPT_RESOLUTION, s -> s.acceptResolution(null, null, 0, null, null));
        CALLS.put(IncidentOperation.REJECT_RESOLUTION, s -> s.rejectResolution(null, null, null, null));
        CALLS.put(IncidentOperation.AUTO_VERIFY, s -> s.autoVerify(null, null));
        CALLS.put(IncidentOperation.CLOSE, s -> s.close(null, null));
        CALLS.put(IncidentOperation.CANCEL, s -> s.cancel(null, null, null, null));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IncidentStatus.class)
    @DisplayName("allowedOperations() matches the behaviour of every state")
    void allowedOperationsMatchBehaviour(IncidentStatus status) {
        assertThat(CALLS).as("every operation is covered by this test").hasSize(IncidentOperation.values().length);

        IncidentState state = IncidentStates.of(status);
        assertThat(state.status()).isEqualTo(status);

        for (IncidentOperation operation : IncidentOperation.values()) {
            Throwable thrown = catchThrowable(() -> CALLS.get(operation).accept(state));
            if (state.allowedOperations().contains(operation)) {
                assertThat(thrown).as("%s should allow %s", status, operation)
                        .isNotInstanceOf(InvalidStateTransitionException.class);
            } else {
                assertThat(thrown).as("%s should reject %s", status, operation)
                        .isInstanceOf(InvalidStateTransitionException.class);
            }
        }
    }
}
