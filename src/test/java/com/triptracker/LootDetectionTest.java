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

        // Set up panel mock
        when(mockPanel.getSelectedTrackingMode()).thenReturn(0);

        // Inject mocks via reflection
        setField(plugin, "client", mockClient);
        setField(plugin, "itemManager", mockItemManager);
        setField(plugin, "panel", mockPanel);
        setField(plugin, "storageService", mock(TripStorageService.class));
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

        ArrayList<TrackableItemDrop> drops = plugin.getListViewDropArray();
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

        ArrayList<TrackableItemDrop> drops = plugin.getListViewDropArray();
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
        ArrayList<TrackableItemDrop> drops = plugin.getListViewDropArray();
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
        ArrayList<TrackableItemDrop> drops = plugin.getListViewDropArray();
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
        ArrayList<TrackableItemDrop> drops = plugin.getListViewDropArray();
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
