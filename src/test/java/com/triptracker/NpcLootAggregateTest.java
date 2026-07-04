package com.triptracker;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the NpcLootAggregate class, including the O(1) HashMap-based aggregation.
 */
public class NpcLootAggregateTest {

    private ItemManager mockItemManager;
    private ItemComposition mockComposition;

    @Before
    public void setUp() {
        mockItemManager = mock(ItemManager.class);
        mockComposition = mock(ItemComposition.class);
        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(10);
        when(mockComposition.getMembersName()).thenReturn("Test Item");
        when(mockComposition.getHaPrice()).thenReturn(5);
    }

    @Test
    public void testNewAggregateHasZeroKills() {
        NpcLootAggregate aggregate = new NpcLootAggregate("Goblin", mockItemManager);
        assertEquals(0, aggregate.getNumberOfKills());
        assertEquals("Goblin", aggregate.getNpcName());
    }

    @Test
    public void testAddDropIncrementsKillCount() {
        NpcLootAggregate aggregate = new NpcLootAggregate("Goblin", mockItemManager);

        TrackableItemDrop drop1 = new TrackableItemDrop("Goblin", 5, 1000L);
        drop1.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.addDropToNpcAggregate(drop1);

        assertEquals(1, aggregate.getNumberOfKills());

        TrackableItemDrop drop2 = new TrackableItemDrop("Goblin", 5, 2000L);
        drop2.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.addDropToNpcAggregate(drop2);

        assertEquals(2, aggregate.getNumberOfKills());
    }

    @Test
    public void testAggregationCombinesSameItemIds() {
        NpcLootAggregate aggregate = new NpcLootAggregate("Goblin", mockItemManager);

        // First drop: 1 Bones
        TrackableItemDrop drop1 = new TrackableItemDrop("Goblin", 5, 1000L);
        drop1.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.addDropToNpcAggregate(drop1);

        // Second drop: 1 Bones + 3 Coins
        TrackableItemDrop drop2 = new TrackableItemDrop("Goblin", 5, 2000L);
        drop2.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        drop2.addLootToDrop(new TrackableDroppedItem(995, "Coins", 3, 1, 0));
        aggregate.addDropToNpcAggregate(drop2);

        ArrayList<LootAggregation> aggregations = aggregate.aggregateNpcDrops();
        assertNotNull(aggregations);
        assertEquals(2, aggregations.size()); // Bones + Coins

        // Find bones aggregation
        LootAggregation bonesAgg = null;
        LootAggregation coinsAgg = null;
        for (LootAggregation agg : aggregations) {
            if (agg.getItemId() == 526) bonesAgg = agg;
            if (agg.getItemId() == 995) coinsAgg = agg;
        }

        assertNotNull(bonesAgg);
        assertNotNull(coinsAgg);
        assertEquals(2, bonesAgg.getQuantity()); // 1 + 1
        assertEquals(3, coinsAgg.getQuantity()); // 3 from second drop
    }

    @Test
    public void testAggregateNpcDropsReturnsCachedList() {
        NpcLootAggregate aggregate = new NpcLootAggregate("Goblin", mockItemManager);

        TrackableItemDrop drop = new TrackableItemDrop("Goblin", 5, 1000L);
        drop.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.addDropToNpcAggregate(drop);

        // Calling aggregateNpcDrops multiple times should return the same cached result
        ArrayList<LootAggregation> first = aggregate.aggregateNpcDrops();
        ArrayList<LootAggregation> second = aggregate.aggregateNpcDrops();
        assertSame(first, second);
    }

    @Test
    public void testRebuildAggregationMapAfterDirectItemAdd() {
        NpcLootAggregate aggregate = new NpcLootAggregate("Goblin", mockItemManager);

        // Simulate what TripRecord.toTrip() does: add items directly to droppedItems
        aggregate.getDroppedItems().add(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.getDroppedItems().add(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.getDroppedItems().add(new TrackableDroppedItem(995, "Coins", 5, 1, 0));

        // Rebuild should produce correct aggregations
        aggregate.rebuildAggregationMap();

        ArrayList<LootAggregation> aggregations = aggregate.getNpcItemAggregations();
        assertNotNull(aggregations);
        assertEquals(2, aggregations.size());

        LootAggregation bonesAgg = null;
        for (LootAggregation agg : aggregations) {
            if (agg.getItemId() == 526) bonesAgg = agg;
        }
        assertNotNull(bonesAgg);
        assertEquals(2, bonesAgg.getQuantity());
    }

    @Test
    public void testPerformanceWithManyDrops() {
        NpcLootAggregate aggregate = new NpcLootAggregate("Goblin", mockItemManager);

        // Simulate 1000 kills with same items — should complete quickly with O(n) approach
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            TrackableItemDrop drop = new TrackableItemDrop("Goblin", 5, 1000L + i);
            drop.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
            drop.addLootToDrop(new TrackableDroppedItem(995, "Coins", 3, 1, 0));
            aggregate.addDropToNpcAggregate(drop);
        }
        long elapsed = System.currentTimeMillis() - startTime;

        assertEquals(1000, aggregate.getNumberOfKills());
        ArrayList<LootAggregation> aggregations = aggregate.aggregateNpcDrops();
        assertEquals(2, aggregations.size());

        // With O(n) implementation, 1000 drops should take well under 1 second
        assertTrue("Aggregation took too long: " + elapsed + "ms", elapsed < 2000);
    }

    @Test
    public void testLastKillTimeUpdated() {
        NpcLootAggregate aggregate = new NpcLootAggregate("Goblin", mockItemManager);

        TrackableItemDrop drop = new TrackableItemDrop("Goblin", 5, 1609459200000L); // 2021-01-01
        drop.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.addDropToNpcAggregate(drop);

        assertNotNull(aggregate.getLastKillTime());
        assertTrue(aggregate.getLastKillTime().contains("on"));
    }

    @Test
    public void testGetDroppedItemsContainsAllItems() {
        NpcLootAggregate aggregate = new NpcLootAggregate("Goblin", mockItemManager);

        TrackableItemDrop drop1 = new TrackableItemDrop("Goblin", 5, 1000L);
        drop1.addLootToDrop(new TrackableDroppedItem(526, "Bones", 1, 30, 10));
        aggregate.addDropToNpcAggregate(drop1);

        TrackableItemDrop drop2 = new TrackableItemDrop("Goblin", 5, 2000L);
        drop2.addLootToDrop(new TrackableDroppedItem(995, "Coins", 5, 1, 0));
        aggregate.addDropToNpcAggregate(drop2);

        // droppedItems should contain all individual items (not aggregated)
        assertEquals(2, aggregate.getDroppedItems().size());
    }
}
