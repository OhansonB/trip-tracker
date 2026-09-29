package com.triptracker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DropRecordTest {

    @Test
    public void testRoundTrip() {
        // Create a drop with items
        TrackableItemDrop original = new TrackableItemDrop("Guard", 21, 1000000L);
        original.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        original.addLootToDrop(new TrackableDroppedItem(995, "Coins", 5, 1, 0));

        // Serialize to record
        DropRecord record = DropRecord.fromDrop(original);

        assertEquals("Guard", record.npcName);
        assertEquals(21, record.npcCombatLevel);
        assertEquals(1000000L, record.dropTime);
        assertEquals(2, record.items.size());

        // Deserialize back to drop
        TrackableItemDrop restored = record.toDrop();

        assertEquals("Guard", restored.getDropNpcName());
        assertEquals(21, restored.getDropNpcLevel());
        assertEquals(1000000L, restored.getDropTimeDate());
        assertEquals(2, restored.getDroppedItems().size());
        assertEquals(35, restored.getTotalDropGeValue()); // 30 + 5
    }

    @Test
    public void testItemRecordPreservesPerUnitPrice() {
        TrackableItemDrop drop = new TrackableItemDrop("Man", 2);
        // 10 arrows at 4gp each = 40gp total
        drop.addLootToDrop(new TrackableDroppedItem(882, "Bronze arrow", 10, 4, 1));

        DropRecord record = DropRecord.fromDrop(drop);
        DropRecord.ItemRecord itemRecord = record.items.get(0);

        // Per-unit price should be stored
        assertEquals(4, itemRecord.gePrice);
        assertEquals(1, itemRecord.haPrice);
        assertEquals(10, itemRecord.quantity);

        // Restore and verify total value
        TrackableItemDrop restored = record.toDrop();
        assertEquals(40, restored.getTotalDropGeValue());
    }

    @Test
    public void testLargePriceSurvivesRoundTripWithoutTruncation() {
        // Jagex is widening item/cash-stack values beyond Integer.MAX_VALUE, and
        // RuneLite's getItemPrice now returns a long. A per-unit price above the int
        // ceiling must survive drop -> record -> drop without truncating or going negative.
        long bigPrice = (long) Integer.MAX_VALUE + 1_000_000L; // ~2.148b, overflows int
        TrackableItemDrop original = new TrackableItemDrop("Boss", 500, 1000000L);
        original.addLootToDrop(new TrackableDroppedItem(1234, "Huge stack", 1, bigPrice, 0));

        // Per-unit price is preserved intact in the serialized record
        DropRecord record = DropRecord.fromDrop(original);
        assertEquals(bigPrice, record.items.get(0).gePrice);

        // And after a full restore the total value is intact (no int overflow)
        TrackableItemDrop restored = record.toDrop();
        assertEquals(bigPrice, restored.getTotalDropGeValue());
        assertTrue("value must not go negative from int overflow", restored.getTotalDropGeValue() > 0);
    }

    @Test
    public void testEmptyDrop() {
        TrackableItemDrop drop = new TrackableItemDrop("Chicken", 1);
        DropRecord record = DropRecord.fromDrop(drop);

        assertEquals(0, record.items.size());

        TrackableItemDrop restored = record.toDrop();
        assertEquals(0, restored.getDroppedItems().size());
        assertEquals(0, restored.getTotalDropGeValue());
    }
}
