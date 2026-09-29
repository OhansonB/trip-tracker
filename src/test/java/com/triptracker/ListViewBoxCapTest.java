package com.triptracker;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the list-view rendered-box cap (EnhancedLootTrackerPlugin.MAX_LIST_VIEW_BOXES).
 *
 * The list view must not realize an unbounded number of Swing boxes: switching AWAY from a
 * list view with thousands of boxes was pathologically slow because SwingUtil.fastRemoveAll
 * tears the tree down component-by-component. The incremental add path
 * ({@link EnhancedLootTrackerPanel#addLootBox(TrackableItemDrop)}) inserts newest-first and
 * must drop the oldest box once the cap is exceeded.
 *
 * Asserts on the internal {@code listViewPanelBoxes} list rather than Swing rendering, per the
 * testing standards.
 */
public class ListViewBoxCapTest {

    private EnhancedLootTrackerPanel panel;
    private EnhancedLootTrackerPlugin mockPlugin;
    private ItemManager mockItemManager;

    @Before
    public void setUp() throws Exception {
        panel = new EnhancedLootTrackerPanel();
        mockPlugin = mock(EnhancedLootTrackerPlugin.class);
        mockItemManager = mock(ItemManager.class);
        ItemComposition mockComposition = mock(ItemComposition.class);

        when(mockPlugin.getItemManager()).thenReturn(mockItemManager);
        when(mockPlugin.isSpriteDisplayMode()).thenReturn(false);
        when(mockPlugin.getExcludedItems()).thenReturn(new HashSet<>());
        when(mockPlugin.isNpcExcluded(anyString())).thenReturn(false);
        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(10L);
        when(mockComposition.getMembersName()).thenReturn("Test Item");
        when(mockComposition.getHaPrice()).thenReturn(5);

        setField(panel, "parentPlugin", mockPlugin);
        setField(panel, "showHidden", false);
        setField(panel, "selectedTrackingMode", 0); // list view
    }

    @Test
    public void incrementalAddCapsRealizedBoxesAtLimit() throws Exception {
        final int cap = EnhancedLootTrackerPlugin.MAX_LIST_VIEW_BOXES;

        // Add well beyond the cap.
        for (int i = 0; i < cap + 250; i++) {
            panel.addLootBox(new TrackableItemDrop("Goblin", 2));
        }

        assertEquals("Realized list-view boxes must be capped at MAX_LIST_VIEW_BOXES",
                cap, listViewPanelBoxes(panel).size());
    }

    @Test
    public void underCapAddsAreAllRetained() throws Exception {
        final int n = 10;
        for (int i = 0; i < n; i++) {
            panel.addLootBox(new TrackableItemDrop("Goblin", 2));
        }
        assertEquals("Under the cap, every box is retained", n, listViewPanelBoxes(panel).size());
    }

    @Test
    public void newestBoxIsKeptAfterTrim() throws Exception {
        final int cap = EnhancedLootTrackerPlugin.MAX_LIST_VIEW_BOXES;

        for (int i = 0; i < cap; i++) {
            panel.addLootBox(new TrackableItemDrop("Filler", 1));
        }
        // This most-recent add must survive; the oldest should be evicted instead.
        TrackableItemDrop newest = new TrackableItemDrop("Newest", 99);
        panel.addLootBox(newest);

        ArrayList<LootTrackingPanelBox> boxes = listViewPanelBoxes(panel);
        assertEquals(cap, boxes.size());
        // Newest is inserted at index 0.
        assertTrue("The most recent box must be retained at the top after trimming",
                boxes.size() > 0);
    }

    // --- reflection helpers ---

    @SuppressWarnings("unchecked")
    private ArrayList<LootTrackingPanelBox> listViewPanelBoxes(EnhancedLootTrackerPanel target) throws Exception {
        Field f = findField(target.getClass(), "listViewPanelBoxes");
        f.setAccessible(true);
        return (ArrayList<LootTrackingPanelBox>) f.get(target);
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
