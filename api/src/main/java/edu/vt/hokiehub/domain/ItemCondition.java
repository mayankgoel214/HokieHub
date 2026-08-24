package edu.vt.hokiehub.domain;

public enum ItemCondition {
    NEW("new"),
    LIKE_NEW("like_new"),
    GOOD("good"),
    FAIR("fair"),
    POOR("poor");

    private final String value;

    ItemCondition(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ItemCondition from(String value) {
        for (ItemCondition c : values()) {
            if (c.value.equals(value)) return c;
        }
        throw new IllegalArgumentException("Unknown condition: " + value);
    }
}
