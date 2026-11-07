package com.iitsm.backend.domain.assignment;

import com.iitsm.backend.common.Validate;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.user.Technician;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Takes turns: each new incident goes to the next eligible technician after the one who got the previous incident,
 * wrapping around at the end. Simple and visibly fair, and it ignores skill and load on purpose.
 *
 * <p>Unlike the scoring strategies this one keeps state (who was last), so it implements {@link #onAssigned}.
 * Access is {@code synchronized} because several requests may assign incidents at the same moment.
 */
public class RoundRobinStrategy implements AssignmentStrategy {

    public static final String KEY = "ROUND_ROBIN";

    /** Fixed rotation order. Email is unique and stable, unlike a display name. */
    private static final Comparator<AssignmentCandidate> ROTATION_ORDER =
            Comparator.comparing(candidate -> candidate.technician().getEmail());

    private String lastAssignedEmail;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Round-robin";
    }

    @Override
    public String description() {
        return "Takes turns through the available technicians, regardless of skill or workload.";
    }

    @Override
    public synchronized List<TechnicianScore> rank(Incident incident, List<AssignmentCandidate> candidates) {
        Validate.requireNonNull(incident, "Incident");
        List<AssignmentCandidate> eligible = candidates.stream()
                .filter(candidate -> candidate.technician().isAssignable(candidate.activeIncidents()))
                .filter(candidate -> !candidate.technician().equals(incident.getAssignee()))
                .sorted(ROTATION_ORDER)
                .toList();
        if (eligible.isEmpty()) {
            return List.of();
        }

        // Start just after the last assigned technician, then wrap around.
        int start = 0;
        if (lastAssignedEmail != null) {
            start = eligible.size(); // if everyone sorts before the last one, wrap to index 0
            for (int i = 0; i < eligible.size(); i++) {
                if (eligible.get(i).technician().getEmail().compareTo(lastAssignedEmail) > 0) {
                    start = i;
                    break;
                }
            }
            start %= eligible.size();
        }

        List<TechnicianScore> ranking = new ArrayList<>();
        for (int position = 0; position < eligible.size(); position++) {
            AssignmentCandidate candidate = eligible.get((start + position) % eligible.size());
            double score = 1.0 - (double) position / eligible.size();
            String reason = position == 0 ? "next in rotation" : "position " + (position + 1) + " in rotation";
            ranking.add(new TechnicianScore(candidate, score, List.of(reason)));
        }
        return ranking;
    }

    @Override
    public synchronized void onAssigned(Technician technician) {
        this.lastAssignedEmail = Validate.requireNonNull(technician, "Technician").getEmail();
    }
}
