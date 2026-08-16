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
        when(mockPlugin.getNextTripNumber()).thenReturn(1);
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
        // Write garbage to the drops.v1.json file (current version)
        File dropsFile = new File(tempDir, "drops.v1.json");
        java.nio.file.Files.writeString(dropsFile.toPath(), "this is not valid json {{{");

        List<DropRecord> drops = storageService.loadDrops();
        assertTrue(drops.isEmpty());
    }

    @Test
    public void testLoadLegacyBareArrayDrops() throws Exception {
        // Simulate legacy v0 format: bare JSON array in drops.json
        String legacyJson = "[{\"npcName\":\"Goblin\",\"npcCombatLevel\":5,\"dropTime\":1234,\"collapsed\":false,"
                + "\"items\":[{\"itemId\":526,\"itemName\":\"Bones\",\"quantity\":1,\"gePrice\":30,\"haPrice\":10}]}]";
        File legacyFile = new File(tempDir, "drops.json");
        java.nio.file.Files.writeString(legacyFile.toPath(), legacyJson);

        List<DropRecord> loaded = storageService.loadDrops();

        assertEquals(1, loaded.size());
        assertEquals("Goblin", loaded.get(0).npcName);
        assertEquals(5, loaded.get(0).npcCombatLevel);
        assertEquals(1234L, loaded.get(0).dropTime);
    }

    @Test
    public void testLoadLegacyBareArrayTrips() throws Exception {
        // Simulate legacy v0 format: bare JSON array in trips.json
        String legacyJson = "[{\"tripName\":\"TRIP 1\",\"tripActive\":false,\"collapsed\":false,"
                + "\"tripStartTimeEpoch\":1000,\"tripStartTime\":\"10:00\",\"tripEndTime\":\"11:00\","
                + "\"tripEndTimeEpoch\":2000,\"tripKills\":5,\"tripValue\":1000,\"tripId\":1}]";
        File legacyFile = new File(tempDir, "trips.json");
        java.nio.file.Files.writeString(legacyFile.toPath(), legacyJson);

        List<TripRecord> loaded = storageService.loadTrips();

        assertEquals(1, loaded.size());
        assertEquals("TRIP 1", loaded.get(0).tripName);
        assertEquals(5, loaded.get(0).tripKills);
        assertEquals(1000, loaded.get(0).tripValue);
    }

    @Test
    public void testVersionedFilePreferredOverLegacy() throws Exception {
        // Write legacy v0 with one drop
        String legacyJson = "[{\"npcName\":\"OldDrop\",\"npcCombatLevel\":1,\"dropTime\":100,\"collapsed\":false,\"items\":[]}]";
        File legacyFile = new File(tempDir, "drops.json");
        java.nio.file.Files.writeString(legacyFile.toPath(), legacyJson);

        // Write v1 with a different drop
        String v1Json = "{\"version\":1,\"drops\":[{\"npcName\":\"NewDrop\",\"npcCombatLevel\":2,\"dropTime\":200,\"collapsed\":false,\"items\":[]}]}";
        File v1File = new File(tempDir, "drops.v1.json");
        java.nio.file.Files.writeString(v1File.toPath(), v1Json);

        List<DropRecord> loaded = storageService.loadDrops();

        // Should load from v1, not legacy
        assertEquals(1, loaded.size());
        assertEquals("NewDrop", loaded.get(0).npcName);
    }

    @Test
    public void testFallsBackToLegacyWhenVersionedMissing() throws Exception {
        // Only legacy file exists, no v1
        String legacyJson = "[{\"npcName\":\"FallbackDrop\",\"npcCombatLevel\":3,\"dropTime\":300,\"collapsed\":false,\"items\":[]}]";
        File legacyFile = new File(tempDir, "drops.json");
        java.nio.file.Files.writeString(legacyFile.toPath(), legacyJson);

        List<DropRecord> loaded = storageService.loadDrops();

        assertEquals(1, loaded.size());
        assertEquals("FallbackDrop", loaded.get(0).npcName);
    }

    @Test
    public void testSaveWritesToVersionedFile() throws Exception {
        List<TrackableItemDrop> drops = new ArrayList<>();
        TrackableItemDrop drop = new TrackableItemDrop("Saved", 10, 5000L);
        drop.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        drops.add(drop);

        storageService.saveDropsSync(drops);

        // Should write to drops.v1.json, not drops.json
        File v1File = new File(tempDir, "drops.v1.json");
        File legacyFile = new File(tempDir, "drops.json");
        assertTrue(v1File.exists());
        assertFalse(legacyFile.exists());
    }

    @Test
    public void testLegacyFilePreservedAfterSave() throws Exception {
        // Write legacy file
        String legacyJson = "[{\"npcName\":\"Legacy\",\"npcCombatLevel\":1,\"dropTime\":100,\"collapsed\":false,\"items\":[]}]";
        File legacyFile = new File(tempDir, "drops.json");
        java.nio.file.Files.writeString(legacyFile.toPath(), legacyJson);

        // Load (reads from legacy) then save (writes to v1)
        List<DropRecord> loaded = storageService.loadDrops();
        assertEquals(1, loaded.size());

        List<TrackableItemDrop> drops = new ArrayList<>();
        drops.add(loaded.get(0).toDrop());
        storageService.saveDropsSync(drops);

        // Both files should exist — legacy preserved as backup
        assertTrue(legacyFile.exists());
        assertTrue(new File(tempDir, "drops.v1.json").exists());

        // Legacy file content unchanged
        String legacyContent = java.nio.file.Files.readString(legacyFile.toPath());
        assertEquals(legacyJson, legacyContent);
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
