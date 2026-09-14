package com.triptracker;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Regression tests for backlog bug #23: PvP / Emir's Arena loot mis-attributed to a
 * "Farming Patch".
 *
 * The arena loads a preset combat kit into the inventory (supply box) AND fires a
 * spurious Farming StatChanged on the same tick (reporting the unchanged existing total),
 * plus sets stats to 99 (a farming "level-up"). Together these mis-armed the XP-fallback
 * farming detector, which recorded the whole kit as a "Farming Patch" drop.
 *
 * The fix gates all farming arm-points on {@code isInsidePvpArena()} (staging OR battle),
 * and additionally ignores any Farming StatChanged whose total XP did not increase.
 *
 * The harvest arms during STAGING — before {@code PVP_AREA_CLIENT} flips to 1 — so the
 * gate must cover the staging timer too.
 */
public class PvpArenaFarmingTest {

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
        setField(plugin, "executor", debounceExecutor);
        setField(plugin, "clientThread", mockClientThread);
    }

    @After
    public void tearDown() {
        debounceExecutor.shutdownNow();
    }

    // === Arena gate: XP-fallback path (the actual bug path) ===

    @Test
    public void testFarmingXpIgnoredInStagingArea() throws Exception {
        // Staging phase: staging timer active, PVP_AREA_CLIENT not yet set.
        when(mockClient.getVarbitValue(VarbitID.PVPA_STAGINGAREA_TIMEREMAINING)).thenReturn(300);
        when(mockClient.getTickCount()).thenReturn(100);
        setField(plugin, "lastInventoryChangeTick", 100); // supply box loaded kit this tick

        Multiset<Integer> prevSnapshot = HashMultiset.create();
        setField(plugin, "previousReferenceInventorySnapshot", prevSnapshot);

        // Spurious arena Farming XP (a genuine increase, to prove the *location* gate alone stops it)
        StatChanged event = new StatChanged(Skill.FARMING, 50000, 99, 99);
        plugin.onStatChanged(event);

        assertFalse("Farming must not arm in the arena staging area",
                (boolean) getField(plugin, "farmingHarvestInProgress"));
        assertEquals("XP tick must not be recorded in the arena",
                -1, (int) getField(plugin, "lastFarmingXpTick"));
    }

    @Test
    public void testFarmingXpIgnoredInBattleArea() throws Exception {
        // Battle phase: PVP_AREA_CLIENT == 1, timers back to 0.
        when(mockClient.getVarbitValue(VarbitID.PVP_AREA_CLIENT)).thenReturn(1);
        when(mockClient.getTickCount()).thenReturn(200);
        setField(plugin, "lastInventoryChangeTick", 200);

        Multiset<Integer> prevSnapshot = HashMultiset.create();
        setField(plugin, "previousReferenceInventorySnapshot", prevSnapshot);

        StatChanged event = new StatChanged(Skill.FARMING, 50000, 99, 99);
        plugin.onStatChanged(event);

        assertFalse("Farming must not arm in the arena battle area",
                (boolean) getField(plugin, "farmingHarvestInProgress"));
    }

    @Test
    public void testFarmingXpIgnoredWhenBattleTimerActive() throws Exception {
        when(mockClient.getVarbitValue(VarbitID.PVPA_BATTLEAREA_TIMEREMAINING)).thenReturn(300);
        when(mockClient.getTickCount()).thenReturn(150);
        setField(plugin, "lastInventoryChangeTick", 150);

        Multiset<Integer> prevSnapshot = HashMultiset.create();
        setField(plugin, "previousReferenceInventorySnapshot", prevSnapshot);

        StatChanged event = new StatChanged(Skill.FARMING, 50000, 99, 99);
        plugin.onStatChanged(event);

        assertFalse("Farming must not arm while the battle-area timer is running",
                (boolean) getField(plugin, "farmingHarvestInProgress"));
    }

    // === Arena gate: the ItemContainerChanged same-tick branch ===

    @Test
    public void testInventoryChangeSameTickAsXpDoesNotArmInArena() throws Exception {
        // Simulate the reverse ordering: an XP tick was somehow recorded, then the supply
        // box fires ItemContainerChanged on the same tick while in the staging area.
        when(mockClient.getVarbitValue(VarbitID.PVPA_STAGINGAREA_TIMEREMAINING)).thenReturn(300);
        setField(plugin, "lastFarmingXpTick", 100);
        when(mockClient.getTickCount()).thenReturn(100);

        Multiset<Integer> prevSnapshot = HashMultiset.create();
        prevSnapshot.add(952, 1);
        setField(plugin, "referenceInventorySnapshot", prevSnapshot);
        setField(plugin, "previousReferenceInventorySnapshot", prevSnapshot);

        // Supply box dumps a big combat kit into inventory
        setupInventoryContainer(new int[]{952, 1}, new int[]{4587, 1}, new int[]{2434, 3});

        ItemContainer container = mockClient.getItemContainer(93);
        plugin.onItemContainerChanged(new ItemContainerChanged(93, container));

        assertFalse("Same-tick container branch must not arm a harvest in the arena",
                (boolean) getField(plugin, "farmingHarvestInProgress"));
    }

    // === Arena gate: chat-harvest path ===

    @Test
    public void testFarmingChatTriggerIgnoredInArena() throws Exception {
        when(mockClient.getVarbitValue(VarbitID.PVP_AREA_CLIENT)).thenReturn(1);
        setupInventoryContainer(new int[]{952, 1});

        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("You begin to harvest the herb patch.");
        plugin.onChatMessage(event);

        assertFalse("Chat-harvest trigger must be ignored in the arena",
                (boolean) getField(plugin, "farmingHarvestInProgress"));
    }

    // === Heuristic hardening: ignore Farming XP that did not actually increase ===

    @Test
    public void testFarmingXpWithNoIncreaseIsIgnored() throws Exception {
        // Not in the arena — this guard is a second layer against phantom XP anywhere.
        setField(plugin, "lastKnownFarmingXp", 13034431);
        when(mockClient.getTickCount()).thenReturn(100);
        setField(plugin, "lastInventoryChangeTick", 100);

        Multiset<Integer> prevSnapshot = HashMultiset.create();
        setField(plugin, "previousReferenceInventorySnapshot", prevSnapshot);

        // Same total as before — no real XP gained (the arena's spurious event)
        StatChanged event = new StatChanged(Skill.FARMING, 13034431, 99, 99);
        plugin.onStatChanged(event);

        assertFalse("A Farming StatChanged with no XP increase must not arm a harvest",
                (boolean) getField(plugin, "farmingHarvestInProgress"));
        assertEquals("XP tick must not be recorded for a non-increasing event",
                -1, (int) getField(plugin, "lastFarmingXpTick"));
    }

    @Test
    public void testFarmingXpWithIncreaseStillWorks() throws Exception {
        // A genuine increase outside the arena still arms the fallback path.
        setField(plugin, "lastKnownFarmingXp", 49000);
        when(mockClient.getTickCount()).thenReturn(100);
        setField(plugin, "lastInventoryChangeTick", 100);

        Multiset<Integer> prevSnapshot = HashMultiset.create();
        prevSnapshot.add(952, 1);
        setField(plugin, "previousReferenceInventorySnapshot", prevSnapshot);

        StatChanged event = new StatChanged(Skill.FARMING, 50000, 50, 50);
        plugin.onStatChanged(event);

        assertTrue("A genuine Farming XP increase should still arm the harvest outside the arena",
                (boolean) getField(plugin, "farmingHarvestInProgress"));
    }

    // === Control: normal farming outside the arena is unaffected ===

    @Test
    public void testFarmingChatTriggerWorksOutsideArena() throws Exception {
        // All arena varbits default to 0 (not stubbed) → not in arena.
        setupInventoryContainer(new int[]{952, 1});

        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("You begin to harvest the herb patch.");
        plugin.onChatMessage(event);

        assertTrue("Normal farming must still work outside the arena",
                (boolean) getField(plugin, "farmingHarvestInProgress"));
        assertEquals("herb patch", getField(plugin, "farmingPatchType"));
    }

    // === Helpers ===

    private void setupInventoryContainer(int[]... items) {
        ItemContainer mockContainer = mock(ItemContainer.class);
        Item[] inventoryItems = new Item[items.length];
        for (int i = 0; i < items.length; i++) {
            inventoryItems[i] = new Item(items[i][0], items[i][1]);
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
