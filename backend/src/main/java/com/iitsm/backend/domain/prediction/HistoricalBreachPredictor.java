package com.iitsm.backend.domain.prediction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Forecasts SLA breaches from how long similar incidents took in the past.
 *
 * <p><b>How it works</b>
 * <ol>
 *   <li><b>Comparable history, with back-off.</b> It looks for resolved incidents with the same category and
 *       priority; if there are fewer than {@value #MIN_SAMPLES}, it widens to the category, then the priority,
 *       then everything.</li>
 *   <li><b>Thin history leans on the SLA.</b> With fewer than {@value #MIN_SAMPLES} real samples, pseudo-samples
 *       spread around the SLA target are added (a prior), so a new installation still gets sensible forecasts and
 *       says it is low-confidence.</li>
 *   <li><b>Who is working on it.</b> If the assignee has resolved enough incidents, their typical time relative to
 *       everyone's scales the history (clamped to 0.5x to 2x).</li>
 *   <li><b>What is in front of it.</b> Each incident ahead in the assignee's queue adds a share of a typical
 *       incident's duration.</li>
 *   <li><b>Conditional on time already spent.</b> The breach probability is the share of comparable incidents
 *       that were still unresolved at this point and then went past the target: "of the incidents that had
 *       already taken this long, how many ended up late?". It therefore rises as the clock runs.</li>
 * </ol>
 *
 * <p>Pure Java with no framework or clock dependency, so every rule is unit-testable with fixed numbers.
 */
public class HistoricalBreachPredictor implements BreachPredictor {

    /** Below this many comparable incidents the forecast widens its comparison and blends in the SLA prior. */
    public static final int MIN_SAMPLES = 5;
    /** Share of a typical incident's duration each queued incident ahead adds. */
    static final double QUEUE_WEIGHT = 0.35;
    /** Assignees need this many resolved incidents before their personal speed is used. */
    static final int MIN_TECHNICIAN_SAMPLES = 3;
    /** Pseudo-samples as fractions of the SLA target: most comparable incidents finish in time, some do not. */
    private static final double[] PRIOR = {0.35, 0.5, 0.65, 0.8, 0.95, 1.15};

    @Override
    public BreachForecast forecast(PredictionContext ctx, List<ResolutionSample> history) {
        Objects.requireNonNull(ctx, "context");
        List<ResolutionSample> all = history == null ? List.of() : history;
        List<String> reasons = new ArrayList<>();

        // 1. Comparable history, widening until there is enough.
        Pool pool = choosePool(ctx, all);
        List<Double> minutes = new ArrayList<>(pool.samples().stream().map(s -> minutesOf(s.activeDuration())).toList());
        int realSamples = minutes.size();

        // 2. Prior from the SLA target when the history is thin.
        double targetMinutes = minutesOf(ctx.target());
        if (realSamples < MIN_SAMPLES) {
            for (double f : PRIOR) {
                minutes.add(f * targetMinutes);
            }
        }
        double typical = median(minutes);
        if (realSamples > 0) {
            reasons.add(pool.label() + " usually take " + human(typical) + " (" + realSamples + " past "
                    + (realSamples == 1 ? "incident" : "incidents") + ")");
        } else {
            reasons.add("No resolved incidents to compare yet: estimate based on the " + human(targetMinutes)
                    + " SLA target");
        }

        // 3. The assignee's personal speed.
        double speed = technicianSpeed(ctx.technicianId(), all, typical);
        if (speed != 1.0) {
            int pct = (int) Math.round(Math.abs(1 - speed) * 100);
            if (pct >= 10) {
                String who = ctx.technicianName() != null ? ctx.technicianName() : "The assignee";
                reasons.add(who + " usually resolves " + pct + "% " + (speed < 1 ? "faster" : "slower") + " than average");
            }
        }

        // 4. Queue in front of it.
        double queueDelay = ctx.queueAhead() * QUEUE_WEIGHT * typical * speed;
        if (ctx.queueAhead() > 0) {
            reasons.add(ctx.queueAhead() + " other " + (ctx.queueAhead() == 1 ? "incident" : "incidents")
                    + " ahead in the assignee's queue");
        } else if (ctx.technicianId() == null) {
            reasons.add("Not assigned yet");
        }

        // 5. Conditional survival: of the incidents still open after `elapsed`, how many went past the target?
        double elapsed = minutesOf(ctx.elapsed());
        List<Double> adjusted = minutes.stream().map(m -> m * speed + queueDelay).sorted().toList();
        List<Double> stillOpen = adjusted.stream().filter(m -> m > elapsed).toList();
        double probability;
        double expectedRemaining;
        if (elapsed >= targetMinutes) {
            probability = 1.0;
            expectedRemaining = stillOpen.isEmpty() ? typical * 0.25 : median(stillOpen) - elapsed;
        } else if (stillOpen.isEmpty()) {
            // Already slower than every comparable incident: likely to keep running long.
            probability = 0.85;
            expectedRemaining = Math.max(typical * 0.25, 1);
            reasons.add(0, "Already running longer than every comparable incident");
        } else {
            long late = stillOpen.stream().filter(m -> m > targetMinutes).count();
            // Laplace-smoothed so a handful of samples never claims certainty.
            probability = (late + 0.5) / (stillOpen.size() + 1.0);
            expectedRemaining = median(stillOpen) - elapsed;
        }
        probability = Math.min(1.0, Math.max(0.0, probability));

        double left = targetMinutes - elapsed;
        if (left > 0) {
            reasons.add(human(left) + " left before the deadline, about " + human(expectedRemaining) + " of work expected");
        }

        return new BreachForecast(round2(probability), RiskLevel.of(probability),
                Duration.ofMinutes(Math.max(1, Math.round(expectedRemaining))), realSamples, pool.label(), reasons);
    }

    // ----- comparison pool --------------------------------------------------------------------------------------

    private record Pool(List<ResolutionSample> samples, String label) {
    }

    private static Pool choosePool(PredictionContext ctx, List<ResolutionSample> all) {
        String category = ctx.categoryName() != null ? ctx.categoryName() : "Uncategorised";
        String priority = ctx.priority();
        List<Pool> candidates = new ArrayList<>();
        if (ctx.categoryId() != null && priority != null) {
            candidates.add(new Pool(filter(all, s -> ctx.categoryId().equals(s.categoryId())
                    && priority.equals(s.priority())), category + " " + priority + " incidents"));
        }
        if (ctx.categoryId() != null) {
            candidates.add(new Pool(filter(all, s -> ctx.categoryId().equals(s.categoryId())), category + " incidents"));
        }
        if (priority != null) {
            candidates.add(new Pool(filter(all, s -> priority.equals(s.priority())), priority + " incidents"));
        }
        candidates.add(new Pool(all, "Incidents"));
        return candidates.stream()
                .filter(p -> p.samples().size() >= MIN_SAMPLES)
                .findFirst()
                // Nothing big enough: use the largest pool there is.
                .orElseGet(() -> candidates.stream().max(Comparator.comparingInt(p -> p.samples().size())).orElseThrow());
    }

    private static List<ResolutionSample> filter(List<ResolutionSample> all, Predicate<ResolutionSample> test) {
        return all.stream().filter(test).toList();
    }

    /** The assignee's median time relative to everyone's, clamped; 1.0 when unknown. */
    private static double technicianSpeed(Long technicianId, List<ResolutionSample> all, double typical) {
        if (technicianId == null || typical <= 0) {
            return 1.0;
        }
        List<Double> own = all.stream()
                .filter(s -> technicianId.equals(s.technicianId()))
                .map(s -> minutesOf(s.activeDuration()))
                .toList();
        if (own.size() < MIN_TECHNICIAN_SAMPLES) {
            return 1.0;
        }
        double everyone = median(all.stream().map(s -> minutesOf(s.activeDuration())).toList());
        if (everyone <= 0) {
            return 1.0;
        }
        return Math.min(2.0, Math.max(0.5, median(own) / everyone));
    }

    // ----- small numeric helpers --------------------------------------------------------------------------------

    static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double minutesOf(Duration d) {
        return d == null ? 0 : d.getSeconds() / 60.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** 42 -> "42 min", 190 -> "3.2 h", 3000 -> "2.1 days". */
    static String human(double minutes) {
        if (minutes < 60) {
            return Math.max(1, Math.round(minutes)) + " min";
        }
        if (minutes < 60 * 24) {
            return String.format(java.util.Locale.ROOT, "%.1f h", minutes / 60);
        }
        return String.format(java.util.Locale.ROOT, "%.1f days", minutes / 60 / 24);
    }
}
