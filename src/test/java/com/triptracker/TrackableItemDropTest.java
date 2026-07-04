package com.triptracker;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackableItemDropTest {

    @Test
    public void testNewDropHasCorrectNpcInfo() {
        TrackableItemDrop drop = new TrackableItemDrop("Goblin", 5);
        assertEquals("Goblin", drop.getDropNpcName());
        assertEquals(5, drop.getDropNpcLevel());
    }

    @Test
    public void testNewDropHasZeroValue() {
        TrackableItemDrop drop = new TrackableItemDrop("Goblin", 5);
        assertEquals(0, drop.getTotalDropGeValue());
        assertEquals(0, drop.getTotalDropHaValue());
    }

    @Test
    public void testNewDropHasTimestamp() {
        long before = System.currentTimeMillis();
        TrackableItemDrop drop = new TrackableItemDrop("Goblin", 5);
        long after = System.currentTimeMillis();

        assertTrue(drop.getDropTimeDate() >= before);
        assertTrue(drop.getDropTimeDate() <= after);
    }

    @Test
    public void testRestoredDropPreservesTimestamp() {
        TrackableItemDrop drop = new TrackableItemDrop("Goblin", 5, 123456789L);
        assertEquals(123456789L, drop.getDropTimeDate());
    }

    @Test
    public void testAddLootAccumulatesValue() {
        TrackableItemDrop drop = new TrackableItemDrop("Man", 2);

        TrackableDroppedItem bones = new TrackableDroppedItem(526, "Bones", 1, 30, 10);
        TrackableDroppedItem coins = new TrackableDroppedItem(995, "Coins", 3, 1, 0);

        drop.addLootToDrop(bones);
        drop.addLootToDrop(coins);

        assertEquals(2, drop.getDroppedItems().size());
        assertEquals(33, drop.getTotalDropGeValue()); // 30 + 3
        assertEquals(10, drop.getTotalDropHaValue()); // 10 + 0
    }

    @Test
    public void testGetDateFromLong() {
        TrackableItemDrop drop = new TrackableItemDrop("Test", 1);
        String formatted = drop.getDateFromLong(0);
        assertNotNull(formatted);
        assertTrue(formatted.contains("on"));
    }
}
