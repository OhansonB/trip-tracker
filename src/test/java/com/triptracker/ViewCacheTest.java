package com.triptracker;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JPanel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the CardLayout view-cache behaviour in {@link EnhancedLootTrackerPanel}: each view is a
 * long-lived card, and incremental drop updates must keep the (possibly hidden) cards fresh so
 * switching to them shows correct data without a rebuild.
 *
 * Asserts on internal state (the per-mode card containers) rather than Swing rendering, per the
 * testing standards.
 */
public class ViewCacheTest {

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
    }

    @Test
    public void listDropIsAddedToTheListCardEvenWhenGroupedIsActive() throws Exception {
        // Simulate the user being on the grouped view when a kill happens.
        setField(panel, "selectedTrackingMode", 1);

        int before = listCard(panel).getComponentCount();
        panel.addLootBox(new TrackableItemDrop("Goblin", 2));
        int after = listCard(panel).getComponentCount();

        assertTrue("A drop must be added to the (hidden) list card so it isn't stale on switch",
                after == before + 1);
    }

    @Test
    public void groupedDropIsAddedToTheGroupedCardEvenWhenListIsActive() throws Exception {
        // Simulate the user being on the list view when a kill happens.
        setField(panel, "selectedTrackingMode", 0);

        NpcLootAggregate agg = new NpcLootAggregate("Goblin", mockItemManager);
        ArrayList<LootAggregation> aggs = new ArrayList<>();
        aggs.add(new LootAggregation(526, 1, mockItemManager));

        int before = groupedCard(panel).getComponentCount();
        panel.addLootBox(agg, aggs);
        int after = groupedCard(panel).getComponentCount();

        assertTrue("A grouped update must land on the (hidden) grouped card",
                after == before + 1);
    }

    // --- reflection helpers ---

    private JPanel listCard(EnhancedLootTrackerPanel p) throws Exception {
        return (JPanel) get(p, "listCard");
    }

    private JPanel groupedCard(EnhancedLootTrackerPanel p) throws Exception {
        return (JPanel) get(p, "groupedCard");
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
}
