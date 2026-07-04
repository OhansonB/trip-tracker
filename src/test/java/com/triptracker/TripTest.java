package com.triptracker;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class TripTest {

    private EnhancedLootTrackerPlugin mockPlugin;
    private Trip trip;

    @Before
    public void setUp() {
        mockPlugin = Mockito.mock(EnhancedLootTrackerPlugin.class);
        Mockito.when(mockPlugin.getNextTripNumber()).thenReturn(1);
        trip = new Trip("TRIP 1", mockPlugin);
    }

    @Test
    public void testNewTripIsActive() {
        assertTrue(trip.getTripStatus());
    }

    @Test
    public void testNewTripHasZeroKills() {
        assertEquals(0, trip.getTripKills());
    }

    @Test
    public void testNewTripHasZeroValue() {
        assertEquals(0, trip.getTripValue());
    }

    @Test
    public void testIncrementKills() {
        trip.incrementKills();
        trip.incrementKills();
        assertEquals(2, trip.getTripKills());
    }

    @Test
    public void testAddValue() {
        trip.addValue(100);
        trip.addValue(250);
        assertEquals(350, trip.getTripValue());
    }

    @Test
    public void testStopTrip() {
        trip.setStatus(false);
        assertFalse(trip.getTripStatus());
        assertNotEquals("n/a", trip.getTripEndTime());
        assertTrue(trip.getTripEndTimeEpoch() > 0);
    }

    @Test
    public void testTripDurationActiveTrip() {
        // Active trip duration should be > 0 (since time has passed since construction)
        String duration = trip.calculateTripDuration();
        assertNotNull(duration);
        assertTrue(duration.contains("s"));
    }

    @Test
    public void testTripDurationInactiveTrip() {
        trip.setStatus(false);
        String duration = trip.calculateTripDuration();
        assertNotNull(duration);
        // Duration should be frozen (not growing)
        String duration2 = trip.calculateTripDuration();
        assertEquals(duration, duration2);
    }

    @Test
    public void testTripNameMatching() {
        assertTrue(trip.matches("TRIP 1"));
        assertFalse(trip.matches("TRIP 2"));
    }

    @Test
    public void testGetTripName() {
        assertEquals("TRIP 1", trip.getTripName());
    }

    @Test
    public void testRestoredTripPreservesData() {
        Trip restored = new Trip("TRIP 5", mockPlugin, false,
                1000000L, "some start", "some end", 2000000L, 10, 5000, 5);

        assertEquals("TRIP 5", restored.getTripName());
        assertFalse(restored.getTripStatus());
        assertEquals(10, restored.getTripKills());
        assertEquals(5000, restored.getTripValue());
        assertEquals("some start", restored.getTripStartTime());
        assertEquals("some end", restored.getTripEndTime());
        assertEquals(1000000L, restored.getTripStartTimeEpoch());
        assertEquals(2000000L, restored.getTripEndTimeEpoch());
        assertEquals(5, restored.getTripId());
    }

    @Test
    public void testFormatTime() {
        String formatted = Trip.formatTime(0);
        assertNotNull(formatted);
        assertTrue(formatted.contains("on"));
    }

    @Test
    public void testTripId() {
        assertEquals(1, trip.getTripId());
    }

    @Test
    public void testRename() {
        trip.setTripName("Vorkath grind");
        assertEquals("Vorkath grind", trip.getTripName());
        // ID should not change
        assertEquals(1, trip.getTripId());
    }

    @Test
    public void testRenameDoesNotAffectMatching() {
        trip.setTripName("New Name");
        assertTrue(trip.matches("New Name"));
        assertFalse(trip.matches("TRIP 1"));
    }
}
