package com.triptracker;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Tests the loot detection pipeline: NPC kills and pickpocketing.
 * Uses reflection to access plugin internals since @Inject fields aren't available without RuneLite DI.
 */
public class LootDetectionTest {

    private EnhancedLootTrackerPlugin plugin;
    private Client mockClient;
    private ItemManager mockItemManager;
    private ItemComposition mockComposition;
    private EnhancedLootTrackerPanel mockPanel;

    @Before
    public void setUp() throws Exception {
        plugin = new EnhancedLootTrackerPlugin();
        mockClient = mock(Client.class);
        mockItemManager = mock(ItemManager.class);
        mockComposition = mock(ItemComposition.class);
        mockPanel = mock(EnhancedLootTrackerPanel.class);

        // Set up ItemManager mocks
        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(10);
        when(mockComposition.getMembersName()).thenReturn("Test Item");
        when(mockComposition.getHaPrice()).thenReturn(5);
        when(mockComposition.getNote()).thenReturn(-1);
        when(mockComposition.getLinkedNoteId()).thenReturn(-1);

        // Set up panel mock
        when(mockPanel.getSelectedTrackingMode()).thenReturn(0);

        // Set up config mock
        EnhancedLootTrackerConfig mockConfig = mock(EnhancedLootTrackerConfig.class);
        when(mockConfig.maxDrops()).thenReturn(500);
        when(mockConfig.maxTrips()).thenReturn(50);

        // Inject mocks via reflection
        setField(plugin, "client", mockClient);
        setField(plugin, "itemManager", mockItemManager);
        setField(plugin, "panel", mockPanel);
        setField(plugin, "config", mockConfig);
        setField(plugin, "storageService", mock(TripStorageService.class));
        setField(plugin, "chatMessageManager", mock(net.runelite.client.chat.ChatMessageManager.class));

        // Set up the debounce executor for processNewDrop
        java.util.concurrent.ScheduledExecutorService debounceExecutor =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        setField(plugin, "executor", debounceExecutor);
    }

    // === NPC Kill Tests ===

    @Test
    public void testNpcKillCreatesDropWithCorrectNpcName() throws Exception {
        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn("Guard");
        when(mockNpc.getCombatLevel()).thenReturn(21);

        Collection<ItemStack> items = Arrays.asList(
                new ItemStack(526, 1),  // Bones
                new ItemStack(995, 30)  // Coins
        );

        NpcLootReceived event = new NpcLootReceived(mockNpc, items);
        plugin.onNpcLootReceived(event);

        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(1, drops.size());
        assertEquals("Guard", drops.get(0).getDropNpcName());
        assertEquals(21, drops.get(0).getDropNpcLevel());
    }

    @Test
    public void testNpcKillCreatesDropWithCorrectItems() throws Exception {
        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn("Man");
        when(mockNpc.getCombatLevel()).thenReturn(2);

        Collection<ItemStack> items = Arrays.asList(
                new ItemStack(526, 1),
                new ItemStack(995, 3)
        );

        plugin.onNpcLootReceived(new NpcLootReceived(mockNpc, items));

        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        TrackableItemDrop drop = drops.get(0);
        assertEquals(2, drop.getDroppedItems().size());
    }

    @Test
    public void testNpcKillUpdatesLastNpcKilled() throws Exception {
        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn("Goblin");
        when(mockNpc.getCombatLevel()).thenReturn(5);

        plugin.onNpcLootReceived(new NpcLootReceived(mockNpc, Arrays.asList(new ItemStack(526, 1))));

        String lastNpcKilled = (String) getField(plugin, "lastNpcKilled");
        assertEquals("Goblin", lastNpcKilled);
    }

    // === Pickpocket Tests ===

    @Test
    public void testPickpocketChatMessageSetsFlag() throws Exception {
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("You pick the Man's pocket.");

        plugin.onChatMessage(event);

        boolean flagSet = (boolean) getField(plugin, "pickpocketHasOccurred");
        assertTrue(flagSet);

        String target = (String) getField(plugin, "lastPickpocketTarget");
        assertEquals("Man", target);
    }

    @Test
    public void testPickpocketIgnoresNonGameMessages() throws Exception {
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.PUBLICCHAT);
        event.setMessage("You pick the Man's pocket.");

        plugin.onChatMessage(event);

        boolean flagSet = (boolean) getField(plugin, "pickpocketHasOccurred");
        assertFalse(flagSet);
    }

    @Test
    public void testPickpocketWithCoinPouchUsesEstimatedValue() throws Exception {
        // Set up: simulate a pickpocket of a Man
        setField(plugin, "pickpocketHasOccurred", true);
        setField(plugin, "lastPickpocketTarget", "Man");

        // Set up a reference inventory snapshot (before pickpocket)
        Multiset<Integer> beforeSnapshot = HashMultiset.create();
        beforeSnapshot.add(995, 100); // 100 coins already in inventory
        setField(plugin, "referenceInventorySnapshot", beforeSnapshot);

        // Simulate the inventory after pickpocket (gained 1 coin pouch, ID 22521)
        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[2];
        inventoryItems[0] = mockItem(995, 100);  // 100 coins (unchanged)
        inventoryItems[1] = mockItem(22521, 1);  // 1 coin pouch (new)
        when(mockContainer.getItems()).thenReturn(inventoryItems);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer);

        // Fire the event
        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        // Verify a drop was created
        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(1, drops.size());

        TrackableItemDrop drop = drops.get(0);
        assertEquals("Man", drop.getDropNpcName());

        // Coin pouch should be valued at 3gp (Man's average)
        TrackableDroppedItem pouchItem = drop.getDroppedItems().get(0);
        assertEquals("Coin pouch", pouchItem.getItemName());
        assertEquals(3, pouchItem.getTotalGePrice()); // 1 pouch * 3gp per pouch
    }

    @Test
    public void testPickpocketEmptyDiffCreatesNoDrop() throws Exception {
        // Set up: simulate a pickpocket that was interrupted
        setField(plugin, "pickpocketHasOccurred", true);
        setField(plugin, "lastPickpocketTarget", "Guard");

        // Reference and current inventory are the same (no change)
        Multiset<Integer> snapshot = HashMultiset.create();
        snapshot.add(995, 100);
        setField(plugin, "referenceInventorySnapshot", snapshot);

        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[1];
        inventoryItems[0] = mockItem(995, 100); // Same as before
        when(mockContainer.getItems()).thenReturn(inventoryItems);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer);

        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        // No drop should be created
        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(0, drops.size());
    }

    @Test
    public void testPickpocketAttributesToCorrectNpcNotLastKilled() throws Exception {
        // First, kill a Goblin (sets lastNpcKilled to "Goblin")
        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn("Goblin");
        when(mockNpc.getCombatLevel()).thenReturn(5);
        plugin.onNpcLootReceived(new NpcLootReceived(mockNpc, Arrays.asList(new ItemStack(526, 1))));

        String lastKilled = (String) getField(plugin, "lastNpcKilled");
        assertEquals("Goblin", lastKilled);

        // Now pickpocket a Guard
        setField(plugin, "pickpocketHasOccurred", true);
        setField(plugin, "lastPickpocketTarget", "Guard");

        Multiset<Integer> beforeSnapshot = HashMultiset.create();
        setField(plugin, "referenceInventorySnapshot", beforeSnapshot);

        // Inventory gains a coin pouch
        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[1];
        inventoryItems[0] = mockItem(22521, 1);
        when(mockContainer.getItems()).thenReturn(inventoryItems);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer);

        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        // The pickpocket drop should be attributed to "Guard", not "Goblin"
        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(2, drops.size()); // Goblin kill + Guard pickpocket
        assertEquals("Guard", drops.get(1).getDropNpcName());

        // lastNpcKilled should now be "Guard" (updated for grouped/trip views)
        lastKilled = (String) getField(plugin, "lastNpcKilled");
        assertEquals("Guard", lastKilled);
    }

    @Test
    public void testPickpocketFlagResetAfterProcessing() throws Exception {
        setField(plugin, "pickpocketHasOccurred", true);
        setField(plugin, "lastPickpocketTarget", "Man");

        Multiset<Integer> beforeSnapshot = HashMultiset.create();
        setField(plugin, "referenceInventorySnapshot", beforeSnapshot);

        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[1];
        inventoryItems[0] = mockItem(22521, 1);
        when(mockContainer.getItems()).thenReturn(inventoryItems);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer);

        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        // Flag should be reset
        boolean flagSet = (boolean) getField(plugin, "pickpocketHasOccurred");
        assertFalse(flagSet);
    }

    @Test
    public void testNonPickpocketInventoryChangeUpdatesReferenceSnapshot() throws Exception {
        // No pickpocket in progress
        setField(plugin, "pickpocketHasOccurred", false);

        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[1];
        inventoryItems[0] = mockItem(995, 50);
        when(mockContainer.getItems()).thenReturn(inventoryItems);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer);

        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        // Reference snapshot should be updated
        Multiset<Integer> snapshot = (Multiset<Integer>) getField(plugin, "referenceInventorySnapshot");
        assertNotNull(snapshot);
        assertEquals(50, snapshot.count(995));
    }

    // === Bird Nest Tests ===

    @Test
    public void testBirdNestSearchCreatesDropWithCorrectSource() throws Exception {
        // Set up: bird nest search triggered (simulates onMenuOptionClicked setting state)
        Multiset<Integer> beforeSnapshot = HashMultiset.create();
        beforeSnapshot.add(5073, 1); // 1 seed nest in inventory
        setField(plugin, "awaitingBirdNestDiff", true);
        setField(plugin, "preLootInventorySnapshot", beforeSnapshot);

        // Simulate inventory after search: nest consumed, seeds gained
        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[1];
        inventoryItems[0] = mockItem(5295, 1); // Ranarr seed
        when(mockContainer.getItems()).thenReturn(inventoryItems);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer);
        when(mockClient.getTickCount()).thenReturn(1);

        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(1, drops.size());
        assertEquals("Bird nest", drops.get(0).getDropNpcName());
    }

    @Test
    public void testMultipleBirdNestsSearchedSequentially() throws Exception {
        // Simulates auto-searching multiple nests: the flag stays active across inventory
        // changes and re-snapshots after each so subsequent diffs are correct.

        // Start: 3 seed nests in inventory, player clicks "Search"
        Multiset<Integer> beforeSnapshot = HashMultiset.create();
        beforeSnapshot.add(5073, 3); // 3 seed nests
        setField(plugin, "awaitingBirdNestDiff", true);
        setField(plugin, "preLootInventorySnapshot", beforeSnapshot);

        // First nest searched: 2 nests remain, 1 seed gained
        ItemContainer mockContainer1 = mock(ItemContainer.class);
        Item[] afterFirst = new Item[2];
        afterFirst[0] = mockItem(5073, 2); // 2 nests remain
        afterFirst[1] = mockItem(5295, 1); // 1 ranarr seed
        when(mockContainer1.getItems()).thenReturn(afterFirst);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer1);
        when(mockClient.getTickCount()).thenReturn(1);

        plugin.onItemContainerChanged(new ItemContainerChanged(93, mockContainer1));

        // First nest should be tracked, flag should remain active for subsequent nests
        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(1, drops.size());
        assertTrue("Flag should remain active for subsequent auto-searched nests",
                (boolean) getField(plugin, "awaitingBirdNestDiff"));

        // Second nest auto-searched (next tick): 1 nest remains, 2nd seed gained
        ItemContainer mockContainer2 = mock(ItemContainer.class);
        Item[] afterSecond = new Item[2];
        afterSecond[0] = mockItem(5073, 1); // 1 nest remains
        afterSecond[1] = mockItem(5295, 2); // 2 seeds total
        when(mockContainer2.getItems()).thenReturn(afterSecond);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer2);
        when(mockClient.getTickCount()).thenReturn(2);

        plugin.onItemContainerChanged(new ItemContainerChanged(93, mockContainer2));

        // Second nest should also be tracked
        drops = plugin.getListViewDropArray();
        assertEquals(2, drops.size());
        assertEquals("Bird nest", drops.get(1).getDropNpcName());

        // Third nest auto-searched: 0 nests remain, 3rd seed gained
        ItemContainer mockContainer3 = mock(ItemContainer.class);
        Item[] afterThird = new Item[1];
        afterThird[0] = mockItem(5295, 3); // 3 seeds total
        when(mockContainer3.getItems()).thenReturn(afterThird);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer3);
        when(mockClient.getTickCount()).thenReturn(3);

        plugin.onItemContainerChanged(new ItemContainerChanged(93, mockContainer3));

        // Third nest should also be tracked
        drops = plugin.getListViewDropArray();
        assertEquals(3, drops.size());
        assertEquals("Bird nest", drops.get(2).getDropNpcName());
    }

    @Test
    public void testBirdNestFlagClearsAfterDebounce() throws Exception {
        // Verify the debounce timer clears the flag after no more inventory changes

        Multiset<Integer> beforeSnapshot = HashMultiset.create();
        beforeSnapshot.add(5073, 1);
        setField(plugin, "awaitingBirdNestDiff", true);
        setField(plugin, "preLootInventorySnapshot", beforeSnapshot);

        // Process one nest
        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[1];
        inventoryItems[0] = mockItem(5295, 1);
        when(mockContainer.getItems()).thenReturn(inventoryItems);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer);
        when(mockClient.getTickCount()).thenReturn(1);

        plugin.onItemContainerChanged(new ItemContainerChanged(93, mockContainer));

        // Flag should still be true immediately after (waiting for potential auto-searches)
        assertTrue("Flag should remain active after processing (awaiting more nests)",
                (boolean) getField(plugin, "awaitingBirdNestDiff"));

        // Drop should have been recorded
        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(1, drops.size());
        assertEquals("Bird nest", drops.get(0).getDropNpcName());

        // Wait for debounce to expire (2000ms + buffer)
        Thread.sleep(2500);

        // Flag should now be cleared by the debounce timer
        assertFalse("Flag should be cleared after debounce expires",
                (boolean) getField(plugin, "awaitingBirdNestDiff"));
    }

    // === Retention and Clear Tests ===

    @Test
    public void testDropsAreTrimmedWhenOverLimit() throws Exception {
        // Set max drops to 3
        EnhancedLootTrackerConfig mockConfig = (EnhancedLootTrackerConfig) getField(plugin, "config");
        when(mockConfig.maxDrops()).thenReturn(3);

        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn("Man");
        when(mockNpc.getCombatLevel()).thenReturn(2);

        // Add 5 drops
        for (int i = 0; i < 5; i++) {
            plugin.onNpcLootReceived(new NpcLootReceived(mockNpc, Arrays.asList(new ItemStack(526, 1))));
        }

        // Only 3 should remain
        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(3, drops.size());
    }

    @Test
    public void testAggregatesRebuiltAfterTrim() throws Exception {
        // Set max drops to 2
        EnhancedLootTrackerConfig mockConfig = (EnhancedLootTrackerConfig) getField(plugin, "config");
        when(mockConfig.maxDrops()).thenReturn(2);

        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn("Guard");
        when(mockNpc.getCombatLevel()).thenReturn(21);

        // Add 4 drops (will trim to 2)
        for (int i = 0; i < 4; i++) {
            plugin.onNpcLootReceived(new NpcLootReceived(mockNpc, Arrays.asList(new ItemStack(526, 1))));
        }

        // Aggregate should reflect only 2 kills (the retained ones)
        NpcLootAggregate aggregate = plugin.getNpcAggregate("Guard");
        assertNotNull(aggregate);
        assertEquals(2, aggregate.getNumberOfKills());
    }

    @Test
    public void testClearAllDataEmptiesEverything() throws Exception {
        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn("Goblin");
        when(mockNpc.getCombatLevel()).thenReturn(5);

        // Add some drops
        plugin.onNpcLootReceived(new NpcLootReceived(mockNpc, Arrays.asList(new ItemStack(526, 1))));
        plugin.onNpcLootReceived(new NpcLootReceived(mockNpc, Arrays.asList(new ItemStack(995, 10))));

        assertEquals(2, plugin.getListViewDropArray().size());
        assertNotNull(plugin.getNpcAggregate("Goblin"));

        // Clear
        plugin.clearAllData();

        assertEquals(0, plugin.getListViewDropArray().size());
        assertNull(plugin.getNpcAggregate("Goblin"));
        assertEquals(0, plugin.getTrips().size());
    }

    // === Helper Methods ===

    private Item mockItem(int id, int quantity) {
        return new Item(id, quantity);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(target);
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
