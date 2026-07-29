package dev.lavra.identity.domain;

/**
 * Plan catalogue codes. The limits behind each code live in the database
 * (spec 0004) — this enum only names them.
 */
public enum PlanCode {

    FREE,
    CREATOR,
    STUDIO;

    /** The plan every new user starts on. */
    public static PlanCode defaultForNewUser() {
        return FREE;
    }
}
