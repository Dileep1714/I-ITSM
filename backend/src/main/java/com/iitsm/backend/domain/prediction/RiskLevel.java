package com.iitsm.backend.domain.prediction;

/** How likely a breach is, in three bands. HIGH triggers the early warning. */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    static final double MEDIUM_FROM = 0.30;
    static final double HIGH_FROM = 0.60;

    public static RiskLevel of(double probability) {
        if (probability >= HIGH_FROM) {
            return HIGH;
        }
        return probability >= MEDIUM_FROM ? MEDIUM : LOW;
    }
}
