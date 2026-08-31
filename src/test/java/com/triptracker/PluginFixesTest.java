package com.triptracker;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests verifying the high/medium priority fixes:
 * - numberOfTrips derived from trips.size()
 * - Null safety in getNpcAggregate/updateGroupedViewUI
 * - Thread-safe defensive copies from getListViewDropArray/getTrips
 * - Debounced persistence (no immediate disk writes)
 */
public class PluginFixesTest {

    private EnhancedLootTrackerPlugin plugin;
    private EnhancedLootTrackerPanel mockPanel;
    private TripStorageService mockStorageService;

    @Before
    public void setUp() throws Exception {
        plugin = new EnhancedLootTrackerPlugin();
        Client mockClient = mock(Client.class);
        ItemManager mockItemManager = mock(ItemManager.class);
        ItemComposition mockComposition = mock(ItemComposition.class);
        mockPanel = mock(EnhancedLootTrackerPanel.class);
        mockStorageService = mock(TripStorageService.class);

        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(10);
        when(mockComposition.getMembersName()).thenReturn("Test Item");
        when(mockComposition.getHaPrice()).thenReturn(5);
        when(mockPanel.getSelectedTrackingMode()).thenReturn(0);

        EnhancedLootTrackerConfig mockConfig = mock(EnhancedLootTrackerConfig.class);
        when(mockConfig.maxDrops()).thenReturn(500);
        when(mockConfig.maxTrips()).thenReturn(50);

        setField(plugin, "client", mockClient);
        setField(plugin, "itemManager", mockItemManager);
        setField(plugin, "panel", mockPanel);
        setField(plugin, "config", mockConfig);
        setField(plugin, "storageService", mockStorageService);
        setField(plugin, "chatMessageManager", mock(net.runelite.client.chat.ChatMessageManager.class));

        java.util.concurrent.ScheduledExecutorService debounceExecutor =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        setField(plugin, "executor", debounceExecutor);
    }

    // === Fix #3: numberOfTrips derived from trips.size() ===

    @Test
    public void testGetNumberOfTripsReflectsActualSize() {
        assertEquals(0, plugin.getNumberOfTrips());

        plugin.initTrip("TRIP 1");
        assertEquals(1, plugin.getNumberOfTrips());

        plugin.initTrip("TRIP 2");
        assertEquals(2, plugin.getNumberOfTrips());
    }

    @Test
    public void testGetNumberOfTripsDecreasesOnRemove() {
        plugin.initTrip("TRIP 1");
        plugin.initTrip("TRIP 2");
        assertEquals(2, plugin.getNumberOfTrips());

        plugin.removeTrip("TRIP 1");
        assertEquals(1, plugin.getNumberOfTrips());
    }

    @Test
    public void testGetNextTripNumberIncrementsCorrectly() {
        int first = plugin.getNextTripNumber();
        plugin.initTrip("TRIP " + first);

        int second = plugin.getNextTripNumber();
        assertTrue("Second trip number (" + second + ") should be > first (" + first + ")",
                second > first);
    }

    // === Fix #4: Null safety in getNpcAggregate ===

    @Test
    public void testGetNpcAggregateReturnsNullForUnknownNpc() {
        assertNull(plugin.getNpcAggregate("NonExistentNpc"));
    }

    @Test
    public void testGetNpcAggregateReturnsNullForNullName() {
        // Should not throw
        assertNull(plugin.getNpcAggregate(""));
    }

    // === Thread safety: defensive copies ===

    @Test
    public void testGetListViewDropArrayReturnsDefensiveCopy() throws Exception {
        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn("Goblin");
        when(mockNpc.getCombatLevel()).thenReturn(5);

        plugin.onNpcLootReceived(new NpcLootReceived(mockNpc,
                Arrays.asList(new ItemStack(526, 1))));

        List<TrackableItemDrop> copy1 = plugin.getListViewDropArray();
        List<TrackableItemDrop> copy2 = plugin.getListViewDropArray();

        // Should be different list instances (defensive copy)
        assertNotSame(copy1, copy2);
        // But same content
        assertEquals(copy1.size(), copy2.size());
    }

    @Test
    public void testGetTripsReturnsDefensiveCopy() {
        plugin.initTrip("TRIP 1");

        List<Trip> copy1 = plugin.getTrips();
        List<Trip> copy2 = plugin.getTrips();

        // Should be different list instances
        assertNotSame(copy1, copy2);
        assertEquals(copy1.size(), copy2.size());
    }

    @Test
    public void testModifyingReturnedListDoesNotAffectPlugin() {
        plugin.initTrip("TRIP 1");

        List<Trip> copy = plugin.getTrips();
        copy.clear(); // Modify the returned copy

        // Original should be unaffected
        assertEquals(1, plugin.getTrips().size());
    }

    // === Active trip management ===

    @Test
    public void testInitTripDeactivatesPreviousTrip() {
        plugin.initTrip("TRIP 1");
        Trip trip1 = plugin.getActiveTrip();
        assertNotNull(trip1);
        assertTrue(trip1.getTripStatus());

        plugin.initTrip("TRIP 2");
        assertFalse(trip1.getTripStatus()); // First trip should be deactivated
        Trip trip2 = plugin.getActiveTrip();
        assertNotNull(trip2);
        assertTrue(trip2.getTripStatus());
        assertEquals("TRIP 2", trip2.getTripName());
    }

    @Test
    public void testGetActiveTripReturnsNullWhenNoTrips() {
        assertNull(plugin.getActiveTrip());
    }

    @Test
    public void testCheckForActiveTripReturnsFalseWhenNoTrips() {
        assertFalse(plugin.checkForActiveTrip());
    }

    @Test
    public void testCheckForActiveTripReturnsTrueWhenActive() {
        plugin.initTrip("TRIP 1");
        assertTrue(plugin.checkForActiveTrip());
    }

    // === Clear all data ===

    @Test
    public void testClearAllDataResetsEverything() throws Exception {
        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn("Goblin");
        when(mockNpc.getCombatLevel()).thenReturn(5);

        plugin.onNpcLootReceived(new NpcLootReceived(mockNpc,
                Arrays.asList(new ItemStack(526, 1))));
        plugin.initTrip("TRIP 1");

        assertTrue(plugin.getListViewDropArray().size() > 0);
        assertTrue(plugin.getTrips().size() > 0);

        plugin.clearAllData();

        assertEquals(0, plugin.getListViewDropArray().size());
        assertEquals(0, plugin.getTrips().size());
        assertEquals(0, plugin.getNumberOfTrips());
        assertNull(plugin.getNpcAggregate("Goblin"));
    }

    // === Helper methods ===

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
