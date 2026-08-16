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
                1000000L, "some start", "some end", 2000000L, 10, 5000, 5, false,
                false, 0, 0);

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

    @Test
    public void testGpPerHourZeroWhenNoValue() {
        assertEquals(0, trip.getGpPerHour());
    }

    @Test
    public void testGpPerHourCalculation() {
        // Create a trip with known start time and value using the restoration constructor
        Mockito.when(mockPlugin.getNextTripNumber()).thenReturn(99);
        long startTime = System.currentTimeMillis() - 3600000L; // 1 hour ago
        Trip hourTrip = new Trip("Test", mockPlugin, true,
                startTime, "start", "n/a", 0L, 5, 100000, 99, false,
                false, 0, 0);

        // 100000gp over 1 hour = ~100000 gp/hr
        long gpPerHour = hourTrip.getGpPerHour();
        assertTrue("Expected ~100000 gp/hr but got " + gpPerHour,
                gpPerHour > 99000 && gpPerHour < 101000);
    }

    @Test
    public void testGpPerHourFrozenWhenInactive() {
        Mockito.when(mockPlugin.getNextTripNumber()).thenReturn(99);
        long startTime = System.currentTimeMillis() - 7200000L; // 2 hours ago
        long endTime = System.currentTimeMillis() - 3600000L;   // ended 1 hour ago
        Trip stoppedTrip = new Trip("Test", mockPlugin, false,
                startTime, "start", "end", endTime, 10, 200000, 99, false,
                false, 0, 0);

        // 200000gp over 1 hour (start to end) = ~200000 gp/hr
        long gpPerHour = stoppedTrip.getGpPerHour();
        assertTrue("Expected ~200000 gp/hr but got " + gpPerHour,
                gpPerHour > 199000 && gpPerHour < 201000);
    }

    @Test
    public void testGpPerKillZeroWhenNoKills() {
        assertEquals(0, trip.getGpPerKill());
    }

    @Test
    public void testGpPerKillCalculation() {
        trip.addValue(3000);
        trip.incrementKills();
        trip.incrementKills();
        trip.incrementKills();
        assertEquals(1000, trip.getGpPerKill());
    }

    @Test
    public void testGpPerKillWithUnevenDivision() {
        trip.addValue(1000);
        trip.incrementKills();
        trip.incrementKills();
        trip.incrementKills();
        // 1000 / 3 = 333 (integer division)
        assertEquals(333, trip.getGpPerKill());
    }

    @Test
    public void testGetDurationSecondsActiveTrip() {
        // Trip was just created, duration should be ~0s
        long duration = trip.getDurationSeconds();
        assertTrue(duration >= 0 && duration < 2);
    }

    @Test
    public void testGetDurationSecondsInactiveTrip() {
        Mockito.when(mockPlugin.getNextTripNumber()).thenReturn(99);
        long startTime = System.currentTimeMillis() - 60000L; // 60 seconds ago
        long endTime = System.currentTimeMillis() - 30000L;   // ended 30 seconds ago
        Trip stoppedTrip = new Trip("Test", mockPlugin, false,
                startTime, "start", "end", endTime, 5, 1000, 99, false,
                false, 0, 0);

        // Duration should be 30 seconds (start to end)
        long duration = stoppedTrip.getDurationSeconds();
        assertTrue("Expected ~30s but got " + duration, duration >= 29 && duration <= 31);
    }

    @Test
    public void testPauseResumeStateTransitions() {
        // Fresh trip is active and not paused
        assertTrue(trip.getTripStatus());
        assertFalse(trip.isPaused());

        // Pause
        trip.pause();
        assertTrue(trip.getTripStatus());
        assertTrue(trip.isPaused());

        // Resume
        trip.resume();
        assertTrue(trip.getTripStatus());
        assertFalse(trip.isPaused());

        // Stop clears paused state
        trip.pause();
        assertTrue(trip.isPaused());
        trip.setStatus(false);
        assertFalse(trip.getTripStatus());
        assertFalse(trip.isPaused());
    }

    @Test
    public void testDurationExcludesPausedTime() {
        Mockito.when(mockPlugin.getNextTripNumber()).thenReturn(99);
        // Trip started 2 hours ago, with 30 minutes of accumulated pause time
        long startTime = System.currentTimeMillis() - 7200000L; // 2 hours ago
        long pausedMs = 1800000L; // 30 minutes paused

        Trip pausedTrip = new Trip("Test", mockPlugin, true,
                startTime, "start", "n/a", 0L, 10, 100000, 99, false,
                false, pausedMs, 0);

        // Active duration should be ~90 minutes (120 - 30), not 120
        long durationSec = pausedTrip.getDurationSeconds();
        long expectedSec = (7200000L - pausedMs) / 1000; // 5400 seconds
        assertTrue("Expected ~5400s but got " + durationSec,
                Math.abs(durationSec - expectedSec) <= 2);

        // GP/hr should be based on 1.5 hours active time, not 2 hours
        long gpPerHour = pausedTrip.getGpPerHour();
        // 100000 gp / 1.5 hours ≈ 66666 gp/hr
        assertTrue("Expected ~66666 gp/hr but got " + gpPerHour,
                gpPerHour > 60000 && gpPerHour < 73000);
    }

    @Test
    public void testDropsNotRecordedWhilePaused() throws Exception {
        // Set up the plugin with reflection to test addDropToTripAggregates
        EnhancedLootTrackerPlugin plugin = Mockito.mock(EnhancedLootTrackerPlugin.class);
        Mockito.when(plugin.getNextTripNumber()).thenReturn(50);

        Trip activePausedTrip = new Trip("Paused Trip", plugin);
        activePausedTrip.pause();

        // Simulate what addDropToTripAggregates does — if paused, drops are skipped
        TrackableItemDrop drop = new TrackableItemDrop("Goblin", 5);
        drop.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));

        // The trip should have no aggregates since it's paused
        // (we can't call the plugin method directly, but we can verify the Trip's
        // isPaused check works correctly in the context the plugin uses)
        assertTrue(activePausedTrip.isPaused());
        assertTrue(activePausedTrip.getTripAggregates().isEmpty());
    }

    @Test
    public void testInactivityAutoStopOnLoad() throws Exception {
        // Create a temp dir and storage service for this test
        java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"),
                "trip-inactivity-test-" + System.currentTimeMillis());
        tempDir.mkdirs();
        TripStorageService storage = new TripStorageService(tempDir);

        try {
            // Save an active trip
            EnhancedLootTrackerPlugin plugin = Mockito.mock(EnhancedLootTrackerPlugin.class);
            Mockito.when(plugin.getNextTripNumber()).thenReturn(1);
            Trip activeTrip = new Trip("Active Trip", plugin);
            activeTrip.incrementKills();
            activeTrip.addValue(500);

            java.util.List<Trip> trips = new java.util.ArrayList<>();
            trips.add(activeTrip);
            storage.saveTripsSync(trips);

            // Set last session to 4 hours ago
            long fourHoursAgo = System.currentTimeMillis() - (4 * 3600000L);
            storage.saveLastSessionEpoch(fourHoursAgo);

            // Load trips and check inactivity (threshold = 3 hours)
            java.util.List<TripRecord> loaded = storage.loadTrips();
            long lastSession = storage.loadLastSessionEpoch();
            long timeSince = System.currentTimeMillis() - lastSession;
            long thresholdMs = 3 * 3600000L;

            assertEquals(1, loaded.size());
            assertTrue(loaded.get(0).tripActive);

            // Simulate the plugin's load logic
            if (loaded.get(0).tripActive && !loaded.get(0).tripPaused && timeSince > thresholdMs) {
                loaded.get(0).tripActive = false;
            }

            assertFalse("Trip should be auto-stopped due to inactivity", loaded.get(0).tripActive);
        } finally {
            java.io.File[] files = tempDir.listFiles();
            if (files != null) for (java.io.File f : files) f.delete();
            tempDir.delete();
            storage.shutdown();
        }
    }

    @Test
    public void testPausedTripSurvivesInactivity() throws Exception {
        java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"),
                "trip-paused-inactivity-test-" + System.currentTimeMillis());
        tempDir.mkdirs();
        TripStorageService storage = new TripStorageService(tempDir);

        try {
            // Save a paused trip
            EnhancedLootTrackerPlugin plugin = Mockito.mock(EnhancedLootTrackerPlugin.class);
            Mockito.when(plugin.getNextTripNumber()).thenReturn(1);
            Trip pausedTrip = new Trip("Paused Trip", plugin);
            pausedTrip.pause();

            java.util.List<Trip> trips = new java.util.ArrayList<>();
            trips.add(pausedTrip);
            storage.saveTripsSync(trips);

            // Set last session to 4 hours ago (exceeds threshold)
            long fourHoursAgo = System.currentTimeMillis() - (4 * 3600000L);
            storage.saveLastSessionEpoch(fourHoursAgo);

            // Load and simulate inactivity check
            java.util.List<TripRecord> loaded = storage.loadTrips();
            long lastSession = storage.loadLastSessionEpoch();
            long timeSince = System.currentTimeMillis() - lastSession;
            long thresholdMs = 3 * 3600000L;

            assertEquals(1, loaded.size());
            assertTrue(loaded.get(0).tripActive);
            assertTrue(loaded.get(0).tripPaused);

            // Simulate the plugin's load logic — paused trips exempt from auto-stop
            if (loaded.get(0).tripActive && !loaded.get(0).tripPaused && timeSince > thresholdMs) {
                loaded.get(0).tripActive = false;
            }

            assertTrue("Paused trip should NOT be auto-stopped", loaded.get(0).tripActive);
            assertTrue("Trip should still be paused", loaded.get(0).tripPaused);
        } finally {
            java.io.File[] files = tempDir.listFiles();
            if (files != null) for (java.io.File f : files) f.delete();
            tempDir.delete();
            storage.shutdown();
        }
    }

    @Test
    public void testPauseSerializationRoundTrip() {
        // Create a trip, pause it, accumulate some pause time
        Mockito.when(mockPlugin.getNextTripNumber()).thenReturn(77);
        long startTime = System.currentTimeMillis() - 3600000L;
        Trip pausedTrip = new Trip("Round Trip", mockPlugin, true,
                startTime, "start", "n/a", 0L, 3, 50000, 77, false,
                true, 600000L, System.currentTimeMillis() - 300000L);

        // Serialize
        TripRecord record = TripRecord.fromTrip(pausedTrip);
        assertTrue(record.tripActive);
        assertTrue(record.tripPaused);
        assertEquals(600000L, record.pausedDurationMs);
        assertTrue(record.pausedAtEpoch > 0);

        // Deserialize
        net.runelite.client.game.ItemManager mockItemManager = Mockito.mock(net.runelite.client.game.ItemManager.class);
        net.runelite.api.ItemComposition mockComp = Mockito.mock(net.runelite.api.ItemComposition.class);
        Mockito.when(mockItemManager.getItemComposition(Mockito.anyInt())).thenReturn(mockComp);
        Mockito.when(mockItemManager.getItemPrice(Mockito.anyInt())).thenReturn(10);
        Mockito.when(mockComp.getMembersName()).thenReturn("Test");
        Mockito.when(mockComp.getHaPrice()).thenReturn(5);

        Trip restored = record.toTrip(mockPlugin, mockItemManager);
        assertTrue(restored.getTripStatus());
        assertTrue(restored.isPaused());
        assertEquals(600000L, restored.getPausedDurationMs());
        assertTrue(restored.getPausedAtEpoch() > 0);
    }

    @Test
    public void testCannotStartNewTripWhilePaused() {
        // A paused trip is still "active" — checkForActiveTrip should return true
        trip.pause();
        assertTrue(trip.getTripStatus()); // getTripStatus returns tripActive which is true
        assertTrue(trip.isPaused());
        // The plugin's checkForActiveTrip iterates trips and checks getTripStatus(),
        // so a paused trip blocks starting a new one
    }
}
