package com.triptracker;

import net.runelite.client.game.ItemManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class TripStorageServiceTest {

    private TripStorageService storageService;
    private File tempDir;
    private EnhancedLootTrackerPlugin mockPlugin;
    private ItemManager mockItemManager;
    private net.runelite.api.ItemComposition mockComposition;

    @Before
    public void setUp() {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "trip-tracker-test-" + System.currentTimeMillis());
        tempDir.mkdirs();
        storageService = new TripStorageService(tempDir);

        mockPlugin = Mockito.mock(EnhancedLootTrackerPlugin.class);
        mockItemManager = Mockito.mock(ItemManager.class);
        mockComposition = Mockito.mock(net.runelite.api.ItemComposition.class);

        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(10);
        when(mockComposition.getMembersName()).thenReturn("Test Item");
        when(mockComposition.getHaPrice()).thenReturn(5);
    }

    @After
    public void tearDown() {
        // Clean up temp files
        File[] files = tempDir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        tempDir.delete();
        storageService.shutdown();
    }

    @Test
    public void testSaveAndLoadDrops() throws InterruptedException {
        List<TrackableItemDrop> drops = new ArrayList<>();

        TrackableItemDrop drop1 = new TrackableItemDrop("Man", 2, 1000L);
        drop1.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        drops.add(drop1);

        TrackableItemDrop drop2 = new TrackableItemDrop("Guard", 21, 2000L);
        drop2.addLootToDrop(new TrackableDroppedItem(995, "Coins", 5, 1, 0));
        drops.add(drop2);

        // Save synchronously for test reliability
        storageService.saveDropsSync(drops);

        // Load
        List<DropRecord> loaded = storageService.loadDrops();

        assertEquals(2, loaded.size());
        assertEquals("Man", loaded.get(0).npcName);
        assertEquals("Guard", loaded.get(1).npcName);
        assertEquals(1000L, loaded.get(0).dropTime);
        assertEquals(2000L, loaded.get(1).dropTime);
    }

    @Test
    public void testSaveAndLoadTrips() {
        List<Trip> trips = new ArrayList<>();

        Trip trip = new Trip("TRIP 1", mockPlugin);
        trip.incrementKills();
        trip.incrementKills();
        trip.addValue(500);
        trip.setStatus(false);
        trips.add(trip);

        storageService.saveTripsSync(trips);

        List<TripRecord> loaded = storageService.loadTrips();

        assertEquals(1, loaded.size());
        TripRecord record = loaded.get(0);
        assertEquals("TRIP 1", record.tripName);
        assertFalse(record.tripActive);
        assertEquals(2, record.tripKills);
        assertEquals(500, record.tripValue);
    }

    @Test
    public void testLoadEmptyReturnsEmptyList() {
        List<DropRecord> drops = storageService.loadDrops();
        assertTrue(drops.isEmpty());

        List<TripRecord> trips = storageService.loadTrips();
        assertTrue(trips.isEmpty());
    }

    @Test
    public void testCorruptFileReturnsEmptyList() throws Exception {
        // Write garbage to the file
        File dropsFile = new File(tempDir, "drops.json");
        java.nio.file.Files.writeString(dropsFile.toPath(), "this is not valid json {{{");

        List<DropRecord> drops = storageService.loadDrops();
        assertTrue(drops.isEmpty());
    }

    @Test
    public void testTripRecordRoundTrip() {
        Trip trip = new Trip("TRIP 2", mockPlugin);
        trip.incrementKills();
        trip.addValue(1000);
        trip.setStatus(false);

        // Add an NPC aggregate to the trip
        NpcLootAggregate aggregate = new NpcLootAggregate("Goblin", mockItemManager);
        TrackableItemDrop drop = new TrackableItemDrop("Goblin", 5, 5000L);
        drop.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.addDropToNpcAggregate(drop);
        trip.addNpcAggregateToTrip(aggregate);

        // Serialize
        TripRecord record = TripRecord.fromTrip(trip);

        assertEquals("TRIP 2", record.tripName);
        assertEquals(1, record.npcAggregates.size());
        assertEquals("Goblin", record.npcAggregates.get(0).npcName);
        assertEquals(1, record.npcAggregates.get(0).numberOfKills);

        // Deserialize
        Trip restored = record.toTrip(mockPlugin, mockItemManager);

        assertEquals("TRIP 2", restored.getTripName());
        assertFalse(restored.getTripStatus());
        assertEquals(1, restored.getTripKills());
        assertEquals(1000, restored.getTripValue());
        assertEquals(1, restored.getTripAggregates().size());
        assertEquals("Goblin", restored.getTripAggregates().get(0).getNpcName());
        assertEquals(1, restored.getTripAggregates().get(0).getNumberOfKills());
    }
}
