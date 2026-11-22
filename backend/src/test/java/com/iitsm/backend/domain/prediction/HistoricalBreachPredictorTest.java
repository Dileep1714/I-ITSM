package com.iitsm.backend.domain.prediction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalBreachPredictorTest {

    private static final long DB = 4L;
    private final HistoricalBreachPredictor predictor = new HistoricalBreachPredictor();

    private static ResolutionSample sample(long minutes, Long category, String priority, Long tech) {
        return new ResolutionSample(Duration.ofMinutes(minutes), category, priority, tech);
    }

    /** Eight past Database P2 incidents taking 60..200 minutes (target 240: none of them breached). */
    private static List<ResolutionSample> fastDatabaseHistory() {
        List<ResolutionSample> history = new ArrayList<>();
        for (long m = 60; m <= 200; m += 20) {
            history.add(sample(m, DB, "P2", 9L));
        }
        return history;
    }

    private static PredictionContext context(long elapsedMin, long targetMin, int queueAhead, Long tech) {
        return new PredictionContext(DB, "Database", "P2", tech, tech == null ? null : "Priya",
                Duration.ofMinutes(elapsedMin), Duration.ofMinutes(targetMin), queueAhead);
    }

    @Test
    @DisplayName("Similar incidents always finished in time: a fresh incident is low risk")
    void lowRiskWhenHistoryIsComfortablyInside() {
        BreachForecast f = predictor.forecast(context(10, 240, 0, null), fastDatabaseHistory());

        assertThat(f.risk()).isEqualTo(RiskLevel.LOW);
        assertThat(f.sampleSize()).isEqualTo(8);
        assertThat(f.lowConfidence()).isFalse();
        assertThat(f.basis()).isEqualTo("Database P2 incidents");
    }

    @Test
    @DisplayName("Risk rises as the clock runs: later in the window the same incident is more likely to breach")
    void probabilityRisesWithElapsedTime() {
        List<ResolutionSample> history = new ArrayList<>(fastDatabaseHistory());
        history.add(sample(300, DB, "P2", 9L));
        history.add(sample(320, DB, "P2", 9L));

        double early = predictor.forecast(context(10, 240, 0, null), history).probability();
        double late = predictor.forecast(context(190, 240, 0, null), history).probability();

        assertThat(late).isGreaterThan(early);
    }

    @Test
    @DisplayName("A long queue ahead of the incident raises the breach probability")
    void queueAheadRaisesRisk() {
        double alone = predictor.forecast(context(60, 240, 0, 9L), fastDatabaseHistory()).probability();
        double queued = predictor.forecast(context(60, 240, 4, 9L), fastDatabaseHistory()).probability();

        assertThat(queued).isGreaterThan(alone);
    }

    @Test
    @DisplayName("Already slower than every comparable incident: high risk, explained")
    void slowerThanAllHistoryIsHighRisk() {
        BreachForecast f = predictor.forecast(context(230, 240, 0, null), fastDatabaseHistory());

        assertThat(f.risk()).isEqualTo(RiskLevel.HIGH);
        assertThat(f.reasons().getFirst()).contains("longer than every comparable incident");
    }

    @Test
    @DisplayName("No history at all: falls back to the SLA target and says it is low-confidence")
    void noHistoryUsesSlaPrior() {
        BreachForecast f = predictor.forecast(context(5, 240, 0, null), List.of());

        assertThat(f.lowConfidence()).isTrue();
        assertThat(f.sampleSize()).isZero();
        assertThat(f.probability()).isBetween(0.0, 1.0);
        assertThat(f.reasons().getFirst()).contains("SLA target");
    }

    @Test
    @DisplayName("Too few same-category incidents: widens the comparison to the priority")
    void backsOffToWiderPool() {
        List<ResolutionSample> history = new ArrayList<>();
        history.add(sample(100, DB, "P2", 9L));               // only one Database incident
        for (int i = 0; i < 6; i++) {
            history.add(sample(90 + i * 10, 7L, "P2", 3L));   // six other P2 incidents
        }

        BreachForecast f = predictor.forecast(context(10, 240, 0, null), history);

        assertThat(f.basis()).isEqualTo("P2 incidents");
        assertThat(f.sampleSize()).isEqualTo(7);
    }

    @Test
    @DisplayName("Past the deadline already: certain breach")
    void pastDeadlineIsCertain() {
        assertThat(predictor.forecast(context(250, 240, 0, null), fastDatabaseHistory()).probability()).isEqualTo(1.0);
    }
}
