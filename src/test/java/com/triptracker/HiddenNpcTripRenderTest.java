package com.triptracker;

import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for backlog bug #22: hidden (excluded) NPCs reappear in an
 * active trip after a new kill.
 *
 * The incremental trip-view render path,
 * {@link EnhancedLootTrackerPanel#addLootBox(NpcLootAggregate, ArrayList, int)},
 * was missing the NPC-exclusion guard that the list- and grouped-view overloads
 * and the full {@code rebuildTripEntries()} path all apply. A kill on an excluded
 * NPC therefore re-added and re-rendered its box during an active trip, even
 * though "show hidden" was off.
 *
 * These tests exercise the render path directly and assert on the stored panel
 * state ({@code tripPanelBoxes}) rather than Swing rendering, per the testing
 * standards (no UI rendering in unit tests).
 */
public class HiddenNpcTripRenderTest {

    private EnhancedLootTrackerPanel panel;
    private EnhancedLootTrackerPlugin mockPlugin;
    private ItemManager mockItemManager;

    private static final int TRIP_ID = 7;
    private static final String EXCLUDED_NPC = "Green dragon";
    private static final String NORMAL_NPC = "Goblin";

    @Before
    public void setUp() throws Exception {
        panel = new EnhancedLootTrackerPanel();
        mockPlugin = mock(EnhancedLootTrackerPlugin.class);
        mockItemManager = mock(ItemManager.class);

        when(mockPlugin.getItemManager()).thenReturn(mockItemManager);
        when(mockPlugin.isSpriteDisplayMode()).thenReturn(false);
        when(mockPlugin.getExcludedItems()).thenReturn(new HashSet<>());
        // Only EXCLUDED_NPC is hidden
        when(mockPlugin.isNpcExcluded(EXCLUDED_NPC)).thenReturn(true);
        when(mockPlugin.isNpcExcluded(NORMAL_NPC)).thenReturn(false);

        setField(panel, "parentPlugin", mockPlugin);
        setField(panel, "showHidden", false);
        // List mode (0): the render branch that touches a TripPanel is gated on
        // mode == 2, so this isolates the tripPanelBoxes state-store behaviour.
        setField(panel, "selectedTrackingMode", 0);
    }

    @Test
    public void excludedNpcNotStoredInTripPanelBoxesOnIncrementalKill() throws Exception {
        NpcLootAggregate excluded = new NpcLootAggregate(EXCLUDED_NPC, mockItemManager);

        panel.addLootBox(excluded, new ArrayList<>(), TRIP_ID);

        LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>> tripPanelBoxes =
                getTripPanelBoxes(panel);
        LinkedHashMap<String, LootTrackingPanelBox> boxesForTrip = tripPanelBoxes.get(TRIP_ID);

        // The excluded NPC must not have polluted the stored panel state. Either the
        // trip has no entry at all, or the map exists but does not contain the NPC.
        if (boxesForTrip != null) {
            assertFalse("Excluded NPC should not be stored in tripPanelBoxes",
                    boxesForTrip.containsKey(EXCLUDED_NPC));
        }
    }

    @Test
    public void nonExcludedNpcIsStoredInTripPanelBoxesOnIncrementalKill() throws Exception {
        NpcLootAggregate normal = new NpcLootAggregate(NORMAL_NPC, mockItemManager);

        panel.addLootBox(normal, new ArrayList<>(), TRIP_ID);

        LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>> tripPanelBoxes =
                getTripPanelBoxes(panel);
        LinkedHashMap<String, LootTrackingPanelBox> boxesForTrip = tripPanelBoxes.get(TRIP_ID);

        assertNotNull("Non-excluded NPC should create a trip entry", boxesForTrip);
        assertTrue("Non-excluded NPC should be stored in tripPanelBoxes",
                boxesForTrip.containsKey(NORMAL_NPC));
    }

    @Test
    public void mixedKillsStoreOnlyTheNonExcludedNpc() throws Exception {
        panel.addLootBox(new NpcLootAggregate(NORMAL_NPC, mockItemManager), new ArrayList<>(), TRIP_ID);
        panel.addLootBox(new NpcLootAggregate(EXCLUDED_NPC, mockItemManager), new ArrayList<>(), TRIP_ID);

        LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>> tripPanelBoxes =
                getTripPanelBoxes(panel);
        LinkedHashMap<String, LootTrackingPanelBox> boxesForTrip = tripPanelBoxes.get(TRIP_ID);

        assertNotNull(boxesForTrip);
        assertTrue("Normal NPC present", boxesForTrip.containsKey(NORMAL_NPC));
        assertFalse("Excluded NPC absent", boxesForTrip.containsKey(EXCLUDED_NPC));
        assertEquals("Only the non-excluded NPC should be stored", 1, boxesForTrip.size());
    }

    @Test
    public void excludedNpcIsRenderedWhenShowHiddenIsOn() throws Exception {
        // When the user toggles "show hidden", the guard must not fire.
        setField(panel, "showHidden", true);

        panel.addLootBox(new NpcLootAggregate(EXCLUDED_NPC, mockItemManager), new ArrayList<>(), TRIP_ID);

        LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>> tripPanelBoxes =
                getTripPanelBoxes(panel);
        LinkedHashMap<String, LootTrackingPanelBox> boxesForTrip = tripPanelBoxes.get(TRIP_ID);

        assertNotNull(boxesForTrip);
        assertTrue("With show-hidden on, excluded NPC should still be stored",
                boxesForTrip.containsKey(EXCLUDED_NPC));
    }

    // === Helpers ===

    @SuppressWarnings("unchecked")
    private LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>> getTripPanelBoxes(
            EnhancedLootTrackerPanel target) throws Exception {
        Field field = findField(target.getClass(), "tripPanelBoxes");
        field.setAccessible(true);
        return (LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>>) field.get(target);
    }

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
