package com.iitsm.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Type-safe access to the {@code iitsm.*} settings in {@code application.properties}.
 * Records are immutable and validated once at startup, so a bad setting stops the app immediately instead of
 * failing later at an unpredictable moment.
 */
public final class ItsmProperties {

    private ItsmProperties() {
    }

    @ConfigurationProperties("iitsm.security")
    public record Security(String jwtSecret,
                           @DefaultValue("8h") Duration tokenLifetime,
                           @DefaultValue("http://localhost:5173") List<String> allowedOrigins,
                           @DefaultValue("i-itsm") String issuer) {

        public static final int MIN_SECRET_LENGTH = 32;

        public Security {
            if (jwtSecret == null || jwtSecret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException("iitsm.security.jwt-secret must be at least "
                        + MIN_SECRET_LENGTH + " characters (HMAC-SHA256 needs a 256-bit key)");
            }
            if (tokenLifetime.isNegative() || tokenLifetime.isZero() || tokenLifetime.compareTo(Duration.ofDays(7)) > 0) {
                throw new IllegalStateException("iitsm.security.token-lifetime must be between 1 second and 7 days");
            }
        }
    }

    @ConfigurationProperties("iitsm.assignment")
    public record Assignment(@DefaultValue("SMART") String defaultStrategy,
                             @DefaultValue("true") boolean autoAssign) {
    }

    @ConfigurationProperties("iitsm.priority")
    public record PriorityConfig(@DefaultValue("WEIGHTED_SCORE") String defaultCalculator) {
    }

    /**
     * Intelligent features.
     *
     * @param autoClassify              classify an incident reported without a category when a suggestion is
     *                                  confident enough
     * @param autoClassifyMinConfidence minimum suggestion confidence (0–1) for automatic classification
     * @param ownDuplicateThreshold     text similarity (0–1) above which a reporter's new incident is refused as a
     *                                  repeat of one of their own open incidents on the same service
     * @param correlationWindow         how far back root-cause correlation looks for open incidents
     */
    @ConfigurationProperties("iitsm.intelligence")
    public record Intelligence(@DefaultValue("true") boolean autoClassify,
                               @DefaultValue("0.5") double autoClassifyMinConfidence,
                               @DefaultValue("0.8") double ownDuplicateThreshold,
                               @DefaultValue("24h") Duration correlationWindow) {

        public Intelligence {
            if (autoClassifyMinConfidence < 0 || autoClassifyMinConfidence > 1
                    || ownDuplicateThreshold < 0 || ownDuplicateThreshold > 1) {
                throw new IllegalStateException("iitsm.intelligence thresholds must be between 0 and 1");
            }
        }
    }
}
