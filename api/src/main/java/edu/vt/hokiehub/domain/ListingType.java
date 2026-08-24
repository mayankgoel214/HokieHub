package edu.vt.hokiehub.domain;

/** An entry is either a physical item or a service such as tutoring. */
public enum ListingType {
    ITEM("item"),
    SERVICE("service");

    private final String value;

    ListingType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ListingType from(String value) {
        for (ListingType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown listing type: " + value);
    }
}
