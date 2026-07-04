package com.triptracker;

public enum TrackingMode {
    LIST(0),
    GROUPED(1),
    TRIP(2);

    private final int id;

    TrackingMode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TrackingMode fromId(int id) {
        for (TrackingMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return LIST;
    }
}
