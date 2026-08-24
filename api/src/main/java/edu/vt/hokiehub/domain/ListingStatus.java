package edu.vt.hokiehub.domain;

public enum ListingStatus {
    AVAILABLE("available"),
    PENDING("pending"),
    SOLD("sold"),
    UNAVAILABLE("unavailable");

    private final String value;

    ListingStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ListingStatus from(String value) {
        for (ListingStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown listing status: " + value);
    }
}
