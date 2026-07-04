package com.triptracker;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for LootAggregation, verifying:
 * - Correct value computation
 * - No retained references to ItemManager/ItemComposition (memory fix)
 * - Quantity handling
 */
public class LootAggregationTest {

    private ItemManager mockItemManager;

    @Before
    public void setUp() {
        mockItemManager = mock(ItemManager.class);
        ItemComposition mockComposition = mock(ItemComposition.class);

        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(100);
        when(mockComposition.getMembersName()).thenReturn("Dragon Bones");
        when(mockComposition.getHaPrice()).thenReturn(50);
    }

    @Test
    public void testConstructionLooksUpValues() {
        LootAggregation agg = new LootAggregation(536, 5, mockItemManager);

        assertEquals(536, agg.getItemId());
        assertEquals("Dragon Bones", agg.getItemName());
        assertEquals(5, agg.getQuantity());
        assertEquals(500, agg.getTotalGePrice());  // 100 * 5
        assertEquals(250, agg.getTotalHaPrice());  // 50 * 5
    }

    @Test
    public void testNoItemManagerFieldRetained() throws Exception {
        LootAggregation agg = new LootAggregation(536, 5, mockItemManager);

        // Verify the class does NOT have an itemManager field
        boolean hasItemManagerField = false;
        for (Field field : agg.getClass().getDeclaredFields()) {
            if (field.getName().equals("itemManager") || field.getName().equals("itemComposition")) {
                hasItemManagerField = true;
                break;
            }
        }
        assertFalse("LootAggregation should not retain ItemManager or ItemComposition references",
                hasItemManagerField);
    }

    @Test
    public void testUpdateItemAggregation() {
        LootAggregation agg = new LootAggregation(536, 2, mockItemManager);
        assertEquals(2, agg.getQuantity());

        agg.updateItemAggregation(3);
        assertEquals(5, agg.getQuantity());
        assertEquals(500, agg.getTotalGePrice()); // 100 * 5
    }

    @Test
    public void testZeroQuantityDefaultsToOne() {
        LootAggregation agg = new LootAggregation(536, 0, mockItemManager);
        assertEquals(1, agg.getQuantity());
    }

    @Test
    public void testNegativeQuantityDefaultsToOne() {
        LootAggregation agg = new LootAggregation(536, -5, mockItemManager);
        assertEquals(1, agg.getQuantity());
    }

    @Test
    public void testMatchesById() {
        LootAggregation agg = new LootAggregation(536, 1, mockItemManager);
        assertTrue(agg.matches(536));
        assertFalse(agg.matches(537));
    }

    @Test
    public void testCompareByTotalValue() {
        ItemComposition mockComp1 = mock(ItemComposition.class);
        when(mockComp1.getMembersName()).thenReturn("Cheap Item");
        when(mockComp1.getHaPrice()).thenReturn(1);

        ItemComposition mockComp2 = mock(ItemComposition.class);
        when(mockComp2.getMembersName()).thenReturn("Expensive Item");
        when(mockComp2.getHaPrice()).thenReturn(500);

        ItemManager im = mock(ItemManager.class);
        when(im.getItemComposition(1)).thenReturn(mockComp1);
        when(im.getItemPrice(1)).thenReturn(10);
        when(im.getItemComposition(2)).thenReturn(mockComp2);
        when(im.getItemPrice(2)).thenReturn(1000);

        LootAggregation cheap = new LootAggregation(1, 1, im);
        LootAggregation expensive = new LootAggregation(2, 1, im);

        // Expensive should sort before cheap (descending by value)
        assertTrue(cheap.compareTo(expensive) > 0);
        assertTrue(expensive.compareTo(cheap) < 0);
    }
}
