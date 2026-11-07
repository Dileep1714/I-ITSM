package com.iitsm.backend.common.strategy;

/**
 * Anything an administrator can choose between at runtime: priority calculators, assignment strategies…
 * The {@link #key()} is what gets stored in configuration and sent by the admin UI.
 */
public interface NamedStrategy {

    /** Stable identifier, e.g. {@code "SKILL_BASED"}. Never shown to users. */
    String key();

    /** Human-readable name for the admin UI. */
    String displayName();

    /** One or two sentences explaining how it decides, shown next to the choice in the admin UI. */
    String description();
}
