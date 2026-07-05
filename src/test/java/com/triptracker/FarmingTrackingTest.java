package com.triptracker;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Tests for farming harvest tracking: chat triggers, XP fallback,
 * debouncing, weeds exclusion, and snapshot management.
 */
public class FarmingTrackingTest {

    private EnhancedLootTrackerPlugin plugin;
    private Client mockClient;
    private ItemManager mockItemManager;
    private ItemComposition mockComposition;
    private EnhancedLootTrackerPanel mockPanel;
    private ScheduledExecutorService debounceExecutor;
    private net.runelite.client.callback.ClientThread mockClientThread;

    @Before
    public void setUp() throws Exception {
        plugin = new EnhancedLootTrackerPlugin();
        mockClient = mock(Client.class);
        mockItemManager = mock(ItemManager.class);
        mockComposition = mock(ItemComposition.class);
        mockPanel = mock(EnhancedLootTrackerPanel.class);
        mockClientThread = mock(net.runelite.client.callback.ClientThread.class);

        // Default item composition mock
        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(100);
        when(mockComposition.getMembersName()).thenReturn("Test Item");
        when(mockComposition.getHaPrice()).thenReturn(50);
        when(mockComposition.getNote()).thenReturn(-1);
        when(mockComposition.getLinkedNoteId()).thenReturn(-1);

        when(mockPanel.getSelectedTrackingMode()).thenReturn(0);

        EnhancedLootTrackerConfig mockConfig = mock(EnhancedLootTrackerConfig.class);
        when(mockConfig.maxDrops()).thenReturn(500);
        when(mockConfig.maxTrips()).thenReturn(50);
        when(mockConfig.debugMode()).thenReturn(false);

        // ClientThread.invokeLater should run the Runnable immediately in tests
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(mockClientThread).invokeLater(any(Runnable.class));

        debounceExecutor = Executors.newSingleThreadScheduledExecutor();

        setField(plugin, "client", mockClient);
        setField(plugin, "itemManager", mockItemManager);
        setField(plugin, "panel", mockPanel);
        setField(plugin, "config", mockConfig);
        setField(plugin, "storageService", mock(TripStorageService.class));
        setField(plugin, "chatMessageManager", mock(net.runelite.client.chat.ChatMessageManager.class));
        setField(plugin, "debounceExecutor", debounceExecutor);
        setField(plugin, "clientThread", mockClientThread);
    }

    @After
    public void tearDown() {
        debounceExecutor.shutdownNow();
    }

    // === Chat Trigger Tests (Path A) ===

    @Test
    public void testHerbPatchChatMessageStartsHarvest() throws Exception {
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("You begin to harvest the herb patch.");

        // Set up inventory container for snapshot
        setupInventoryContainer(new int[]{5343, 1}, new int[]{952, 1}); // seed dibber, spade

        plugin.onChatMessage(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertTrue("Farming harvest should be in progress", inProgress);

        String patchType = (String) getField(plugin, "farmingPatchType");
        assertEquals("herb patch", patchType);

        boolean startedFromXp = (boolean) getField(plugin, "farmingStartedFromXp");
        assertFalse("Should not be XP-triggered", startedFromXp);
    }

    @Test
    public void testRepeatedHerbMessageDoesNotResetSnapshot() throws Exception {
        // Start a harvest
        setupInventoryContainer(new int[]{952, 1});
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("You begin to harvest the herb patch.");
        plugin.onChatMessage(event);

        // Get the snapshot reference
        Multiset<Integer> firstSnapshot = (Multiset<Integer>) getField(plugin, "farmingPreHarvestSnapshot");
        assertNotNull(firstSnapshot);

        // Change inventory (simulate herbs arriving)
        setupInventoryContainer(new int[]{952, 1}, new int[]{219, 5});

        // Fire same message again (spam clicking)
        plugin.onChatMessage(event);

        // Snapshot should NOT have been reset
        Multiset<Integer> secondSnapshot = (Multiset<Integer>) getField(plugin, "farmingPreHarvestSnapshot");
        assertSame("Snapshot should not be replaced on repeated message", firstSnapshot, secondSnapshot);
    }

    @Test
    public void testCactusChatMessageStartsHarvest() throws Exception {
        setupInventoryContainer(new int[]{952, 1});

        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("You carefully pick a spine from the cactus.");

        plugin.onChatMessage(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertTrue(inProgress);

        String patchType = (String) getField(plugin, "farmingPatchType");
        assertEquals("cactus patch", patchType);
    }

    @Test
    public void testCoconutPickMessageStartsHarvest() throws Exception {
        setupInventoryContainer(new int[]{952, 1});

        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("You pick a coconut.");

        plugin.onChatMessage(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertTrue(inProgress);

        String patchType = (String) getField(plugin, "farmingPatchType");
        assertEquals("coconut tree", patchType);
    }

    @Test
    public void testNonGameMessageIgnored() throws Exception {
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.PUBLICCHAT);
        event.setMessage("You begin to harvest the herb patch.");

        plugin.onChatMessage(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertFalse(inProgress);
    }

    // === XP Fallback Tests (Path B) ===

    @Test
    public void testFarmingXpWithSameTickInventoryChangeStartsHarvest() throws Exception {
        // Simulate: inventory change happened first on tick 100
        setField(plugin, "lastInventoryChangeTick", 100);
        when(mockClient.getTickCount()).thenReturn(100);

        // Set up valid previous snapshot
        Multiset<Integer> prevSnapshot = HashMultiset.create();
        prevSnapshot.add(952, 1);
        setField(plugin, "previousReferenceInventorySnapshot", prevSnapshot);
        setField(plugin, "referenceInventorySnapshot", prevSnapshot);

        // Fire StatChanged for Farming
        StatChanged event = new StatChanged(Skill.FARMING, 50000, 50, 50);
        plugin.onStatChanged(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertTrue("Should start farming harvest from XP with same-tick inv change", inProgress);

        String patchType = (String) getField(plugin, "farmingPatchType");
        assertEquals("farming patch", patchType);

        boolean startedFromXp = (boolean) getField(plugin, "farmingStartedFromXp");
        assertTrue(startedFromXp);
    }

    @Test
    public void testFarmingXpWithNoInventoryChangeRecordsTick() throws Exception {
        // No inventory change on this tick
        setField(plugin, "lastInventoryChangeTick", 50);
        when(mockClient.getTickCount()).thenReturn(100);

        Multiset<Integer> prevSnapshot = HashMultiset.create();
        setField(plugin, "previousReferenceInventorySnapshot", prevSnapshot);

        StatChanged event = new StatChanged(Skill.FARMING, 50000, 50, 50);
        plugin.onStatChanged(event);

        // Should NOT start harvest yet
        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertFalse("Should not start harvest without same-tick inv change", inProgress);

        // But should record the XP tick
        int lastXpTick = (int) getField(plugin, "lastFarmingXpTick");
        assertEquals(100, lastXpTick);
    }

    @Test
    public void testInventoryChangeOnSameTickAsXpStartsHarvest() throws Exception {
        // XP fired first on tick 100
        setField(plugin, "lastFarmingXpTick", 100);
        when(mockClient.getTickCount()).thenReturn(100);

        // Set up previous reference (before harvest items)
        Multiset<Integer> prevSnapshot = HashMultiset.create();
        prevSnapshot.add(952, 1); // spade
        setField(plugin, "referenceInventorySnapshot", prevSnapshot);

        // Current inventory now has limpwurt roots
        setupInventoryContainer(new int[]{952, 1}, new int[]{225, 5});

        ItemContainer mockContainer = mockClient.getItemContainer(93);
        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertTrue("Inventory change on same tick as XP should start harvest", inProgress);
    }

    @Test
    public void testNonFarmingXpIgnored() throws Exception {
        when(mockClient.getTickCount()).thenReturn(100);

        StatChanged event = new StatChanged(Skill.ATTACK, 50000, 50, 50);
        plugin.onStatChanged(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertFalse(inProgress);
    }

    @Test
    public void testFarmingXpDuringActiveHarvestResetsDebounce() throws Exception {
        // Start a harvest
        setField(plugin, "farmingHarvestInProgress", true);
        setField(plugin, "farmingPatchType", "herb patch");
        when(mockClient.getTickCount()).thenReturn(100);

        StatChanged event = new StatChanged(Skill.FARMING, 50000, 50, 50);
        plugin.onStatChanged(event);

        // Should still be in progress
        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertTrue(inProgress);

        // Debounce timer should be set
        Object timer = getField(plugin, "farmingDebounceTimer");
        assertNotNull("Debounce timer should be scheduled", timer);
    }

    // === Weeds Exclusion Tests ===

    @Test
    public void testWeedsExcludedFromFarmingDiff() throws Exception {
        // Simulate harvest completion with only weeds
        setField(plugin, "farmingHarvestInProgress", true);
        setField(plugin, "farmingStartedFromXp", false);
        setField(plugin, "farmingPatchType", "farming patch");

        // Pre-harvest snapshot: empty
        Multiset<Integer> preSnapshot = HashMultiset.create();
        preSnapshot.add(952, 1); // spade
        setField(plugin, "farmingPreHarvestSnapshot", preSnapshot);

        // Current inventory: spade + weeds
        setupInventoryContainer(new int[]{952, 1}, new int[]{6055, 3});

        // Manually invoke completeFarmingHarvest
        java.lang.reflect.Method method = EnhancedLootTrackerPlugin.class.getDeclaredMethod("completeFarmingHarvest");
        method.setAccessible(true);
        method.invoke(plugin);

        // No drop should be created (weeds only)
        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals("Weeds-only harvest should not create a drop", 0, drops.size());
    }

    @Test
    public void testWeedsFilteredButOtherItemsKept() throws Exception {
        // Simulate harvest with weeds AND real items
        setField(plugin, "farmingHarvestInProgress", true);
        setField(plugin, "farmingStartedFromXp", false);
        setField(plugin, "farmingPatchType", "farming patch");

        Multiset<Integer> preSnapshot = HashMultiset.create();
        preSnapshot.add(952, 1);
        setField(plugin, "farmingPreHarvestSnapshot", preSnapshot);

        // Current: spade + weeds + limpwurt roots
        setupInventoryContainer(new int[]{952, 1}, new int[]{6055, 2}, new int[]{225, 5});

        java.lang.reflect.Method method = EnhancedLootTrackerPlugin.class.getDeclaredMethod("completeFarmingHarvest");
        method.setAccessible(true);
        method.invoke(plugin);

        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals("Should create a drop with non-weed items", 1, drops.size());

        // Verify weeds are not in the drop
        TrackableItemDrop drop = drops.get(0);
        for (TrackableDroppedItem item : drop.getDroppedItems()) {
            assertNotEquals("Weeds should be excluded", 6055, item.getItemId());
        }
    }

    // === Snapshot Management Tests ===

    @Test
    public void testLoginScreenResetsSnapshots() throws Exception {
        // Set up existing snapshots
        Multiset<Integer> snapshot = HashMultiset.create();
        snapshot.add(995, 100);
        setField(plugin, "referenceInventorySnapshot", snapshot);
        setField(plugin, "previousReferenceInventorySnapshot", snapshot);

        // Fire LOGIN_SCREEN event
        GameStateChanged event = mock(GameStateChanged.class);
        when(event.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(event);

        assertNull("Reference snapshot should be null after logout",
                getField(plugin, "referenceInventorySnapshot"));
        assertNull("Previous reference should be null after logout",
                getField(plugin, "previousReferenceInventorySnapshot"));
    }

    @Test
    public void testLoadingDoesNotResetSnapshots() throws Exception {
        Multiset<Integer> snapshot = HashMultiset.create();
        snapshot.add(995, 100);
        setField(plugin, "referenceInventorySnapshot", snapshot);
        setField(plugin, "previousReferenceInventorySnapshot", snapshot);

        GameStateChanged event = mock(GameStateChanged.class);
        when(event.getGameState()).thenReturn(GameState.LOADING);
        plugin.onGameStateChanged(event);

        assertNotNull("Reference snapshot should persist through loading",
                getField(plugin, "referenceInventorySnapshot"));
        assertNotNull("Previous reference should persist through loading",
                getField(plugin, "previousReferenceInventorySnapshot"));
    }

    @Test
    public void testFirstInventoryChangeInitializesBothSnapshots() throws Exception {
        // Both null (fresh login)
        setField(plugin, "referenceInventorySnapshot", null);
        setField(plugin, "previousReferenceInventorySnapshot", null);
        when(mockClient.getTickCount()).thenReturn(10);

        setupInventoryContainer(new int[]{952, 1}, new int[]{995, 100});

        ItemContainer mockContainer = mockClient.getItemContainer(93);
        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        Multiset<Integer> ref = (Multiset<Integer>) getField(plugin, "referenceInventorySnapshot");
        Multiset<Integer> prev = (Multiset<Integer>) getField(plugin, "previousReferenceInventorySnapshot");

        assertNotNull("Reference should be set after first inv change", ref);
        assertNotNull("Previous should be set after first inv change", prev);
    }

    @Test
    public void testSnapshotRotatesOnSubsequentChanges() throws Exception {
        // Set up initial state
        Multiset<Integer> initialSnapshot = HashMultiset.create();
        initialSnapshot.add(952, 1);
        setField(plugin, "referenceInventorySnapshot", initialSnapshot);
        setField(plugin, "previousReferenceInventorySnapshot", null);
        when(mockClient.getTickCount()).thenReturn(20);

        // New inventory change
        setupInventoryContainer(new int[]{952, 1}, new int[]{995, 50});

        ItemContainer mockContainer = mockClient.getItemContainer(93);
        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        Multiset<Integer> prev = (Multiset<Integer>) getField(plugin, "previousReferenceInventorySnapshot");
        assertNotNull(prev);
        // Previous should be the old reference (just spade)
        assertEquals(1, prev.count(952));
        assertEquals(0, prev.count(995));

        Multiset<Integer> ref = (Multiset<Integer>) getField(plugin, "referenceInventorySnapshot");
        // Current reference should include coins
        assertEquals(50, ref.count(995));
    }

    // === Noted Item Normalization ===

    @Test
    public void testNotedItemsNormalizedInSnapshot() throws Exception {
        // Item 220 is noted grimy torstol, note template = 799, linked = 219
        ItemComposition notedComp = mock(ItemComposition.class);
        when(notedComp.getNote()).thenReturn(799);
        when(notedComp.getLinkedNoteId()).thenReturn(219);
        when(mockItemManager.getItemComposition(220)).thenReturn(notedComp);

        // Item 219 is unnoted grimy torstol
        ItemComposition unnotedComp = mock(ItemComposition.class);
        when(unnotedComp.getNote()).thenReturn(-1);
        when(unnotedComp.getMembersName()).thenReturn("Grimy torstol");
        when(unnotedComp.getHaPrice()).thenReturn(50);
        when(mockItemManager.getItemComposition(219)).thenReturn(unnotedComp);

        // Inventory has noted torstol
        setupInventoryContainerWithSpecificIds(new int[]{220}, new int[]{8});

        when(mockClient.getTickCount()).thenReturn(30);
        setField(plugin, "referenceInventorySnapshot", null);
        setField(plugin, "previousReferenceInventorySnapshot", null);

        ItemContainer mockContainer = mockClient.getItemContainer(93);
        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        Multiset<Integer> ref = (Multiset<Integer>) getField(plugin, "referenceInventorySnapshot");
        // Should be stored under unnoted ID 219, not 220
        assertEquals("Noted items should be normalized to unnoted ID", 8, ref.count(219));
        assertEquals("Noted ID should not appear in snapshot", 0, ref.count(220));
    }

    // === Chambers of Xeric Exclusion Tests ===

    @Test
    public void testLevelUpDuringHarvestExtendsDebounce() throws Exception {
        // Start a harvest
        setField(plugin, "farmingHarvestInProgress", true);
        setField(plugin, "farmingPatchType", "herb patch");
        setField(plugin, "lastKnownFarmingLevel", 84);
        when(mockClient.getTickCount()).thenReturn(100);
        when(mockClient.getVarbitValue(5432)).thenReturn(0);

        // Fire StatChanged with a level increase (84 -> 85)
        StatChanged event = new StatChanged(Skill.FARMING, 3500000, 85, 85);
        plugin.onStatChanged(event);

        // Should still be in progress
        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertTrue(inProgress);

        // Level should be updated
        int level = (int) getField(plugin, "lastKnownFarmingLevel");
        assertEquals(85, level);
    }

    @Test
    public void testNormalXpDoesNotExtendDebounce() throws Exception {
        // Start a harvest
        setField(plugin, "farmingHarvestInProgress", true);
        setField(plugin, "farmingPatchType", "herb patch");
        setField(plugin, "lastKnownFarmingLevel", 84);
        when(mockClient.getTickCount()).thenReturn(100);
        when(mockClient.getVarbitValue(5432)).thenReturn(0);

        // Fire StatChanged without level increase (still 84)
        StatChanged event = new StatChanged(Skill.FARMING, 3400000, 84, 84);
        plugin.onStatChanged(event);

        // Level stays the same
        int level = (int) getField(plugin, "lastKnownFarmingLevel");
        assertEquals(84, level);
    }

    // === Chambers of Xeric Exclusion Tests ===

    @Test
    public void testFarmingChatTriggerIgnoredInsideCoX() throws Exception {
        // Simulate being inside CoX (varbit 5432 = 1)
        when(mockClient.getVarbitValue(5432)).thenReturn(1);

        setupInventoryContainer(new int[]{952, 1});

        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("You begin to harvest the herb patch.");

        plugin.onChatMessage(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertFalse("Farming should not start inside CoX", inProgress);
    }

    @Test
    public void testFarmingXpIgnoredInsideCoX() throws Exception {
        // Simulate being inside CoX
        when(mockClient.getVarbitValue(5432)).thenReturn(1);
        when(mockClient.getTickCount()).thenReturn(100);
        setField(plugin, "lastInventoryChangeTick", 100);

        Multiset<Integer> prevSnapshot = HashMultiset.create();
        setField(plugin, "previousReferenceInventorySnapshot", prevSnapshot);

        StatChanged event = new StatChanged(Skill.FARMING, 50000, 50, 50);
        plugin.onStatChanged(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertFalse("Farming XP should be ignored inside CoX", inProgress);

        int lastXpTick = (int) getField(plugin, "lastFarmingXpTick");
        assertEquals("XP tick should not be recorded inside CoX", -1, lastXpTick);
    }

    @Test
    public void testFarmingWorksOutsideCoX() throws Exception {
        // Simulate being outside CoX (varbit 5432 = 0)
        when(mockClient.getVarbitValue(5432)).thenReturn(0);

        setupInventoryContainer(new int[]{952, 1});

        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("You begin to harvest the herb patch.");

        plugin.onChatMessage(event);

        boolean inProgress = (boolean) getField(plugin, "farmingHarvestInProgress");
        assertTrue("Farming should work normally outside CoX", inProgress);
    }

    // === Clockwork Exclusion Test ===

    @Test
    public void testClockworkExcludedFromBirdHouseLoot() throws Exception {
        // Set up bird house loot diff state
        setField(plugin, "awaitingLootDiff", true);
        setField(plugin, "pendingLootEventName", "Bird House");

        // Pre-loot snapshot: just a spade
        Multiset<Integer> preLoot = HashMultiset.create();
        preLoot.add(952, 1);
        setField(plugin, "preLootInventorySnapshot", preLoot);

        // Current inventory: spade + clockwork + feathers + bird nest
        setupInventoryContainer(new int[]{952, 1}, new int[]{8792, 1}, new int[]{314, 50}, new int[]{5073, 1});

        when(mockClient.getTickCount()).thenReturn(40);
        // Need referenceInventorySnapshot to not be null for the snapshot update
        setField(plugin, "referenceInventorySnapshot", preLoot);

        ItemContainer mockContainer = mockClient.getItemContainer(93);
        ItemContainerChanged event = new ItemContainerChanged(93, mockContainer);
        plugin.onItemContainerChanged(event);

        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(1, drops.size());

        TrackableItemDrop drop = drops.get(0);
        assertEquals("Bird House", drop.getDropNpcName());

        // Clockwork should NOT be in the drop
        for (TrackableDroppedItem item : drop.getDroppedItems()) {
            assertNotEquals("Clockwork should be excluded from bird house loot",
                    8792, item.getItemId());
        }
        // But feathers and bird nest should be there
        assertEquals(2, drop.getDroppedItems().size());
    }

    // === Helper Methods ===

    /**
     * Sets up a mock inventory container with items specified as pairs of {id, quantity}.
     */
    private void setupInventoryContainer(int[]... items) {
        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[items.length];
        for (int i = 0; i < items.length; i++) {
            inventoryItems[i] = new Item(items[i][0], items[i][1]);
        }
        when(mockContainer.getItems()).thenReturn(inventoryItems);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer);
    }

    /**
     * Sets up a mock inventory container with specific IDs and quantities as separate arrays.
     */
    private void setupInventoryContainerWithSpecificIds(int[] ids, int[] quantities) {
        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[ids.length];
        for (int i = 0; i < ids.length; i++) {
            inventoryItems[i] = new Item(ids[i], quantities[i]);
        }
        when(mockContainer.getItems()).thenReturn(inventoryItems);
        when(mockClient.getItemContainer(93)).thenReturn(mockContainer);
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
