package edu.vt.hokiehub.domain;

/**
 * How much a flaw should weigh on a buyer's decision. Ordered least to worst, so
 * a listing can be summarised by the worst thing in it.
 */
public enum DefectSeverity {
    MINOR("minor"),
    MODERATE("moderate"),
    MAJOR("major");

    private final String value;

    DefectSeverity(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DefectSeverity from(String value) {
        for (DefectSeverity s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown defect severity: " + value);
    }
}
