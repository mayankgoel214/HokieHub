package edu.vt.hokiehub.domain;

public enum BidStatus {
    ACTIVE("active"),
    WITHDRAWN("withdrawn"),
    ACCEPTED("accepted"),
    DECLINED("declined");

    private final String value;

    BidStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static BidStatus from(String value) {
        for (BidStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown bid status: " + value);
    }
}
