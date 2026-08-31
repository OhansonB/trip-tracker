package com.triptracker;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.ClientToolbar;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Tests the save and shutdown sequence logic.
 *
 * Key invariants tested:
 * 1. Each drop triggers an immediate async save.
 * 2. Multiple rapid drops each trigger their own save.
 * 3. Shutdown performs a sync save with all current data.
 * 4. Shutdown skips sync save when no account is loaded.
 * 5. Shutdown cancels farming/bird-nest debounce timers.
 * 6. Save data contains the correct number of drops.
 */
public class DebounceSaveShutdownTest {

    private EnhancedLootTrackerPlugin plugin;
    private TripStorageService mockStorageService;
    private ScheduledExecutorService executor;
    private Client mockClient;
    private ItemManager mockItemManager;
    private EnhancedLootTrackerPanel mockPanel;

    @Before
    public void setUp() throws Exception {
        plugin = new EnhancedLootTrackerPlugin();
        mockClient = mock(Client.class);
        mockItemManager = mock(ItemManager.class);
        mockPanel = mock(EnhancedLootTrackerPanel.class);
        mockStorageService = mock(TripStorageService.class);

        ItemComposition mockComposition = mock(ItemComposition.class);
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
        when(mockConfig.showLootInChat()).thenReturn(false);

        executor = Executors.newSingleThreadScheduledExecutor();

        setField(plugin, "client", mockClient);
        setField(plugin, "itemManager", mockItemManager);
        setField(plugin, "panel", mockPanel);
        setField(plugin, "config", mockConfig);
        setField(plugin, "storageService", mockStorageService);
        setField(plugin, "chatMessageManager", mock(net.runelite.client.chat.ChatMessageManager.class));
        setField(plugin, "executor", executor);
        setField(plugin, "clientToolbar", mock(ClientToolbar.class));
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    // === Immediate Save Tests ===

    @Test
    public void testSingleDropTriggersImmediateSave() throws Exception {
        fireNpcDrop("Goblin", 5, 526, 1);

        // saveDrops and saveTrips should be called immediately (no debounce)
        verify(mockStorageService, times(1)).saveDrops(any());
        verify(mockStorageService, times(1)).saveTrips(any());
    }

    @Test
    public void testMultipleDropsEachTriggerSave() throws Exception {
        fireNpcDrop("Goblin", 5, 526, 1);
        fireNpcDrop("Guard", 21, 995, 30);
        fireNpcDrop("Man", 2, 526, 1);

        // Each drop triggers its own save
        verify(mockStorageService, times(3)).saveDrops(any());
        verify(mockStorageService, times(3)).saveTrips(any());
    }

    @Test
    public void testSaveContainsCorrectDropCount() throws Exception {
        fireNpcDrop("Goblin", 5, 526, 1);
        fireNpcDrop("Guard", 21, 995, 30);
        fireNpcDrop("Man", 2, 526, 1);

        // The last save should contain all 3 drops
        verify(mockStorageService).saveDrops(argThat(drops -> drops.size() == 3));
    }

    // === Shutdown Sequence Tests ===

    @Test
    public void testShutdownPerformsSyncSave() throws Exception {
        fireNpcDrop("Guard", 21, 526, 1);

        setField(plugin, "currentAccountHash", 12345L);
        invokeShutDown();

        verify(mockStorageService).saveTripsSync(any());
        verify(mockStorageService).saveDropsSync(any());
        verify(mockStorageService).saveLastSessionEpoch(anyLong());
    }

    @Test
    public void testShutdownSkipsSyncSaveWhenNoAccount() throws Exception {
        fireNpcDrop("Guard", 21, 526, 1);

        setField(plugin, "currentAccountHash", -1L);
        invokeShutDown();

        verify(mockStorageService, never()).saveTripsSync(any());
        verify(mockStorageService, never()).saveDropsSync(any());
    }

    @Test
    public void testSyncSaveOnShutdownContainsAllDrops() throws Exception {
        fireNpcDrop("Goblin", 5, 526, 1);
        fireNpcDrop("Guard", 21, 995, 30);
        fireNpcDrop("Man", 2, 526, 1);

        setField(plugin, "currentAccountHash", 12345L);
        invokeShutDown();

        verify(mockStorageService).saveDropsSync(argThat(drops -> drops.size() == 3));
    }

    @Test
    public void testShutdownCancelsFarmingDebounceTimer() throws Exception {
        ScheduledFuture<?> farmingTimer = executor.schedule(() -> {}, 10, TimeUnit.SECONDS);
        setField(plugin, "farmingDebounceTimer", farmingTimer);
        setField(plugin, "currentAccountHash", 12345L);

        invokeShutDown();

        assertTrue("farmingDebounceTimer should be cancelled after shutDown", farmingTimer.isCancelled());
    }

    @Test
    public void testShutdownCancelsBirdNestDebounceTimer() throws Exception {
        ScheduledFuture<?> birdNestTimer = executor.schedule(() -> {}, 10, TimeUnit.SECONDS);
        setField(plugin, "birdNestDebounceTimer", birdNestTimer);
        setField(plugin, "currentAccountHash", 12345L);

        invokeShutDown();

        assertTrue("birdNestDebounceTimer should be cancelled after shutDown", birdNestTimer.isCancelled());
    }

    @Test
    public void testShutdownCallsStorageShutdown() throws Exception {
        setField(plugin, "currentAccountHash", 12345L);
        invokeShutDown();

        verify(mockStorageService).shutdown();
    }

    // === Helper methods ===

    private void fireNpcDrop(String npcName, int combatLevel, int itemId, int quantity) {
        NPC mockNpc = mock(NPC.class);
        when(mockNpc.getName()).thenReturn(npcName);
        when(mockNpc.getCombatLevel()).thenReturn(combatLevel);

        plugin.onNpcLootReceived(new NpcLootReceived(mockNpc,
                Arrays.asList(new ItemStack(itemId, quantity))));
    }

    private void invokeShutDown() throws Exception {
        Method shutDown = EnhancedLootTrackerPlugin.class.getDeclaredMethod("shutDown");
        shutDown.setAccessible(true);
        shutDown.invoke(plugin);
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
