package com.triptracker;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the trip-view "show/hide excluded items" toggle updates in place
 * ({@code applyTripExclusionState()} + {@code rebuildTripEntries()}, reached by reflection) rather
 * than rebuilding the whole panel — the cause of the visible flash. Asserts on internal state
 * (instance reuse and re-synced excluded sets), not Swing rendering, per the testing standards.
 */
public class TripExclusionToggleTest {

    private EnhancedLootTrackerPanel panel;
    private EnhancedLootTrackerPlugin mockPlugin;
    private ItemManager mockItemManager;

    private static final int TRIP_ID = 42;
    private static final String EXCLUDED_NPC = "green dragon";
    private static final String NORMAL_NPC = "Goblin";
    private static final String EXCLUDED_ITEM = "bones";

    private Set<String> excludedItems;
    private Set<String> excludedNpcs;

    @Before
    public void setUp() throws Exception {
        panel = new EnhancedLootTrackerPanel();
        mockPlugin = mock(EnhancedLootTrackerPlugin.class);
        mockItemManager = mock(ItemManager.class);
        ItemComposition mockComposition = mock(ItemComposition.class);

        excludedItems = new HashSet<>(Arrays.asList(EXCLUDED_ITEM));
        excludedNpcs = new HashSet<>(Arrays.asList(EXCLUDED_NPC));

        when(mockPlugin.getItemManager()).thenReturn(mockItemManager);
        when(mockPlugin.isSpriteDisplayMode()).thenReturn(false);
        when(mockPlugin.getExcludedItems()).thenReturn(excludedItems);
        when(mockPlugin.getExcludedNpcs()).thenReturn(excludedNpcs);
        when(mockPlugin.isNpcExcluded(EXCLUDED_NPC)).thenReturn(true);
        when(mockPlugin.isNpcExcluded(NORMAL_NPC)).thenReturn(false);
        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(10L);
        when(mockComposition.getMembersName()).thenReturn("Bones");
        when(mockComposition.getHaPrice()).thenReturn(5);

        setField(panel, "parentPlugin", mockPlugin);
        setField(panel, "showHidden", false);
        // Trip view
        setField(panel, "selectedTrackingMode", 2);

        // One trip with a single non-excluded NPC carrying one item.
        Trip trip = new Trip("TRIP 1", mockPlugin, true,
                System.currentTimeMillis(), "start", "n/a", 0L, 1, 10, TRIP_ID, false,
                false, 0, 0);
        NpcLootAggregate aggregate = new NpcLootAggregate(NORMAL_NPC, mockItemManager);
        TrackableItemDrop drop = new TrackableItemDrop(NORMAL_NPC, 1, 1000L);
        drop.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.addDropToNpcAggregate(drop);
        trip.addNpcAggregateToTrip(aggregate);

        List<Trip> trips = new ArrayList<>();
        trips.add(trip);
        when(mockPlugin.getTrips()).thenReturn(trips);

        // On the EDT because the rebuild touches Swing (SwingUtil.fastRemoveAll asserts the EDT).
        onEdt(panel::rebuildAfterLoad);
    }

    @Test
    public void toggleReusesCachedTripPanelAndBoxInstances() throws Exception {
        TripPanel tripBefore = tripsMap().get(TRIP_ID);
        LootTrackingPanelBox boxBefore = tripPanelBoxes().get(TRIP_ID).get(NORMAL_NPC);
        assertNotNull("trip panel should exist after load", tripBefore);
        assertNotNull("loot box should exist after load", boxBefore);

        setField(panel, "showHidden", true);
        onEdt(() -> {
            invokePrivate("applyTripExclusionState");
            invokePrivate("rebuildTripEntries");
        });

        TripPanel tripAfter = tripsMap().get(TRIP_ID);
        LootTrackingPanelBox boxAfter = tripPanelBoxes().get(TRIP_ID).get(NORMAL_NPC);

        // A full rebuild (rebuildAfterLoad) clears and recreates these maps, so identity equality
        // cleanly proves the in-place path was taken instead.
        assertSame("TripPanel instance must be reused, not recreated", tripBefore, tripAfter);
        assertSame("LootTrackingPanelBox instance must be reused, not recreated", boxBefore, boxAfter);
    }

    @Test
    public void togglingShowHiddenReSyncsExcludedSetsInPlace() throws Exception {
        TripPanel trip = tripsMap().get(TRIP_ID);
        LootTrackingPanelBox box = tripPanelBoxes().get(TRIP_ID).get(NORMAL_NPC);

        // After load with showHidden=false, the cached objects carry the plugin's excluded sets.
        assertEquals(excludedItems, boxExcludedItems(box));
        assertEquals(excludedItems, tripExcludedItems(trip));
        assertEquals(excludedNpcs, tripExcludedNpcs(trip));

        // Toggle to show hidden: excluded sets must be emptied in place (same instances).
        setField(panel, "showHidden", true);
        onEdt(() -> invokePrivate("applyTripExclusionState"));

        assertTrue("box excluded items cleared when showing hidden", boxExcludedItems(box).isEmpty());
        assertTrue("trip excluded items cleared when showing hidden", tripExcludedItems(trip).isEmpty());
        assertTrue("trip excluded npcs cleared when showing hidden", tripExcludedNpcs(trip).isEmpty());
        assertSame("same TripPanel instance after re-sync", trip, tripsMap().get(TRIP_ID));
        assertSame("same box instance after re-sync", box, tripPanelBoxes().get(TRIP_ID).get(NORMAL_NPC));

        // Toggle back to hiding: excluded sets must be restored to the plugin's sets.
        setField(panel, "showHidden", false);
        onEdt(() -> invokePrivate("applyTripExclusionState"));

        assertEquals(excludedItems, boxExcludedItems(box));
        assertEquals(excludedItems, tripExcludedItems(trip));
        assertEquals(excludedNpcs, tripExcludedNpcs(trip));
    }

    // === Helpers ===

    @SuppressWarnings("unchecked")
    private LinkedHashMap<Integer, TripPanel> tripsMap() throws Exception {
        return (LinkedHashMap<Integer, TripPanel>) get(panel, "tripsMap");
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>> tripPanelBoxes() throws Exception {
        return (LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>>) get(panel, "tripPanelBoxes");
    }

    @SuppressWarnings("unchecked")
    private Set<String> boxExcludedItems(LootTrackingPanelBox box) throws Exception {
        return (Set<String>) get(box, "excludedItems");
    }

    @SuppressWarnings("unchecked")
    private Set<String> tripExcludedItems(TripPanel trip) throws Exception {
        return (Set<String>) get(trip, "excludedItems");
    }

    @SuppressWarnings("unchecked")
    private Set<String> tripExcludedNpcs(TripPanel trip) throws Exception {
        return (Set<String>) get(trip, "excludedNpcs");
    }

    /** Runs the given action on the EDT and surfaces any failure, since Swing mutations assert EDT. */
    private void onEdt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw e;
        }
    }

    private void invokePrivate(String methodName) {
        try {
            Method m = findMethod(panel.getClass(), methodName);
            m.setAccessible(true);
            m.invoke(panel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object get(Object target, String fieldName) throws Exception {
        Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        return f.get(target);
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

    private Method findMethod(Class<?> clazz, String methodName) throws NoSuchMethodException {
        while (clazz != null) {
            try {
                return clazz.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchMethodException(methodName);
    }
}
