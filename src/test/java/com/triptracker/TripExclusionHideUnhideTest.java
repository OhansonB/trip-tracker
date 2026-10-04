package com.triptracker;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that hiding/unhiding an individual NPC or item refreshes the trip view IN PLACE
 * (via {@code refreshAfterExclusionChange()} → {@code applyTripExclusionState()} +
 * {@code rebuildTripEntries()}) rather than tearing down and rebuilding the whole panel — the
 * cause of the trip-view flash. Also verifies the list/grouped fallback goes through the full
 * rebuild. Asserts on internal state (instance reuse, rendered boxes, re-synced sets), not Swing
 * pixels, per the testing standards.
 */
public class TripExclusionHideUnhideTest {

    private EnhancedLootTrackerPanel panel;
    private EnhancedLootTrackerPlugin mockPlugin;
    private ItemManager mockItemManager;

    private static final int TRIP_ID = 42;
    private static final String NPC_A = "Goblin";
    private static final String NPC_B = "Cow";
    private static final String ITEM_NAME = "bones";

    private Set<String> excludedItems;
    private Set<String> excludedNpcs;

    @Before
    public void setUp() throws Exception {
        panel = new EnhancedLootTrackerPanel();
        mockPlugin = mock(EnhancedLootTrackerPlugin.class);
        mockItemManager = mock(ItemManager.class);
        ItemComposition mockComposition = mock(ItemComposition.class);

        // Start with nothing excluded; tests mutate these sets to simulate hide/unhide.
        excludedItems = new HashSet<>();
        excludedNpcs = new HashSet<>();

        when(mockPlugin.getItemManager()).thenReturn(mockItemManager);
        when(mockPlugin.isSpriteDisplayMode()).thenReturn(false);
        // Return the live sets so flipping membership below is reflected on the next refresh.
        when(mockPlugin.getExcludedItems()).thenReturn(excludedItems);
        when(mockPlugin.getExcludedNpcs()).thenReturn(excludedNpcs);
        when(mockPlugin.isNpcExcluded(anyString())).thenAnswer(inv ->
                excludedNpcs.contains(((String) inv.getArgument(0)).toLowerCase().trim()));
        when(mockPlugin.isItemExcluded(anyString())).thenAnswer(inv ->
                excludedItems.contains(((String) inv.getArgument(0)).toLowerCase().trim()));
        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(10L);
        when(mockComposition.getMembersName()).thenReturn("Bones");
        when(mockComposition.getHaPrice()).thenReturn(5);

        setField(panel, "parentPlugin", mockPlugin);
        setField(panel, "showHidden", false);
        setField(panel, "selectedTrackingMode", 2); // trip view

        // One trip with two non-excluded NPCs, each carrying one item.
        Trip trip = new Trip("TRIP 1", mockPlugin, true,
                System.currentTimeMillis(), "start", "n/a", 0L, 2, 10, TRIP_ID, false,
                false, 0, 0);
        trip.addNpcAggregateToTrip(buildAggregate(NPC_A));
        trip.addNpcAggregateToTrip(buildAggregate(NPC_B));

        List<Trip> trips = new ArrayList<>();
        trips.add(trip);
        when(mockPlugin.getTrips()).thenReturn(trips);

        onEdt(panel::rebuildAfterLoad);
    }

    private NpcLootAggregate buildAggregate(String npcName) {
        NpcLootAggregate aggregate = new NpcLootAggregate(npcName, mockItemManager);
        TrackableItemDrop drop = new TrackableItemDrop(npcName, 1, 1000L);
        drop.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.addDropToNpcAggregate(drop);
        return aggregate;
    }

    @Test
    public void hidingNpcInTripViewRemovesItsBoxInPlace() throws Exception {
        TripPanel tripBefore = tripsMap().get(TRIP_ID);
        LootTrackingPanelBox survivingBoxBefore = tripPanelBoxes().get(TRIP_ID).get(NPC_B);
        assertNotNull("trip panel should exist after load", tripBefore);
        assertNotNull("surviving box should exist after load", survivingBoxBefore);
        assertTrue("both NPC boxes render before hiding", renderedBoxNames(tripBefore).contains(NPC_A));
        assertTrue(renderedBoxNames(tripBefore).contains(NPC_B));

        // Simulate right-click "Hide" on NPC_A, then the single refresh.
        excludedNpcs.add(NPC_A.toLowerCase());
        onEdt(() -> invokePrivate("refreshAfterExclusionChange"));

        TripPanel tripAfter = tripsMap().get(TRIP_ID);
        // No full rebuild: the TripPanel and surviving box instances are reused.
        assertSame("TripPanel instance must be reused (no rebuildAfterLoad teardown)", tripBefore, tripAfter);
        assertSame("surviving box instance must be reused", survivingBoxBefore,
                tripPanelBoxes().get(TRIP_ID).get(NPC_B));

        Set<String> rendered = renderedBoxNames(tripAfter);
        assertFalse("hidden NPC box must drop out of the live layout", rendered.contains(NPC_A));
        assertTrue("non-excluded NPC box must still render", rendered.contains(NPC_B));
    }

    @Test
    public void unhidingNpcInTripViewRestoresItsBoxInPlace() throws Exception {
        // Start from the hidden state.
        excludedNpcs.add(NPC_A.toLowerCase());
        onEdt(() -> invokePrivate("refreshAfterExclusionChange"));

        TripPanel tripBefore = tripsMap().get(TRIP_ID);
        assertFalse("precondition: NPC_A hidden", renderedBoxNames(tripBefore).contains(NPC_A));

        // Simulate "Unhide".
        excludedNpcs.remove(NPC_A.toLowerCase());
        onEdt(() -> invokePrivate("refreshAfterExclusionChange"));

        TripPanel tripAfter = tripsMap().get(TRIP_ID);
        assertSame("TripPanel instance must be reused (no rebuildAfterLoad teardown)", tripBefore, tripAfter);
        assertTrue("unhidden NPC box must render again", renderedBoxNames(tripAfter).contains(NPC_A));
    }

    @Test
    public void itemHideResyncsBoxExclusionInPlace() throws Exception {
        LootTrackingPanelBox boxBefore = tripPanelBoxes().get(TRIP_ID).get(NPC_A);
        assertTrue("box excluded items empty before hiding", boxExcludedItems(boxBefore).isEmpty());

        // Simulate right-click "Hide" on an item.
        excludedItems.add(ITEM_NAME);
        onEdt(() -> invokePrivate("refreshAfterExclusionChange"));

        LootTrackingPanelBox boxAfter = tripPanelBoxes().get(TRIP_ID).get(NPC_A);
        assertSame("box instance must be reused (in-place, not recreated)", boxBefore, boxAfter);
        assertEquals("box item-exclusion set re-synced to the plugin's excluded items",
                excludedItems, boxExcludedItems(boxAfter));

        // Unhide re-syncs back to empty.
        excludedItems.remove(ITEM_NAME);
        onEdt(() -> invokePrivate("refreshAfterExclusionChange"));
        assertTrue("box item-exclusion cleared after unhide",
                boxExcludedItems(tripPanelBoxes().get(TRIP_ID).get(NPC_A)).isEmpty());
    }

    @Test
    public void listModeFallsBackToFullRebuild() throws Exception {
        // Rebuild in trip mode first so maps are populated, then switch to list mode.
        setField(panel, "selectedTrackingMode", 0);
        TripPanel tripBefore = tripsMap().get(TRIP_ID);
        assertNotNull(tripBefore);

        excludedNpcs.add(NPC_A.toLowerCase());
        onEdt(() -> invokePrivate("refreshAfterExclusionChange"));

        // rebuildAfterLoad recreates tripsMap entries, so the instance must differ.
        TripPanel tripAfter = tripsMap().get(TRIP_ID);
        assertNotSame("list/grouped mode must take the full-rebuild fallback", tripBefore, tripAfter);
    }

    // === Helpers ===

    /** Names of the loot boxes currently laid out under the trip's loot panel. */
    private Set<String> renderedBoxNames(TripPanel tripPanel) {
        Set<String> names = new HashSet<>();
        for (Component c : tripPanel.getLootPanel().getComponents()) {
            if (c.getName() != null) {
                names.add(c.getName());
            }
        }
        return names;
    }

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
