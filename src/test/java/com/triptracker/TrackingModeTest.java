package com.triptracker;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackingModeTest {

    @Test
    public void testFromIdList() {
        assertEquals(TrackingMode.LIST, TrackingMode.fromId(0));
    }

    @Test
    public void testFromIdGrouped() {
        assertEquals(TrackingMode.GROUPED, TrackingMode.fromId(1));
    }

    @Test
    public void testFromIdTrip() {
        assertEquals(TrackingMode.TRIP, TrackingMode.fromId(2));
    }

    @Test
    public void testFromIdUnknownDefaultsToList() {
        assertEquals(TrackingMode.LIST, TrackingMode.fromId(99));
    }

    @Test
    public void testGetId() {
        assertEquals(0, TrackingMode.LIST.getId());
        assertEquals(1, TrackingMode.GROUPED.getId());
        assertEquals(2, TrackingMode.TRIP.getId());
    }
}
