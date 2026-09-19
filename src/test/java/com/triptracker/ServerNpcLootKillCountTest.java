package com.triptracker;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that kills are counted per {@link ServerNpcLoot} event — one kill and one drop each —
 * including multiple kills on the same game tick (bug #25).
 */
public class ServerNpcLootKillCountTest {

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

        when(mockItemManager.getItemComposition(anyInt())).thenReturn(mockComposition);
        when(mockItemManager.getItemPrice(anyInt())).thenReturn(10);
        when(mockComposition.getMembersName()).thenReturn("Test Item");
        when(mockComposition.getHaPrice()).thenReturn(5);
        when(mockPanel.getSelectedTrackingMode()).thenReturn(0);

        EnhancedLootTrackerConfig mockConfig = mock(EnhancedLootTrackerConfig.class);
        when(mockConfig.maxDrops()).thenReturn(500);
        when(mockConfig.maxTrips()).thenReturn(50);
        when(mockConfig.debugMode()).thenReturn(false);

        setField(plugin, "client", mockClient);
        setField(plugin, "itemManager", mockItemManager);
        setField(plugin, "panel", mockPanel);
        setField(plugin, "config", mockConfig);
        setField(plugin, "storageService", mock(TripStorageService.class));
        setField(plugin, "chatMessageManager", mock(net.runelite.client.chat.ChatMessageManager.class));
        setField(plugin, "executor", java.util.concurrent.Executors.newSingleThreadScheduledExecutor());
    }

    private NPCComposition npc(String name, int combat) {
        NPCComposition c = mock(NPCComposition.class);
        when(c.getName()).thenReturn(name);
        when(c.getCombatLevel()).thenReturn(combat);
        return c;
    }

    private void fireKill(String name, int combat, ItemStack... items) {
        plugin.onServerNpcLoot(new ServerNpcLoot(npc(name, combat), Arrays.asList(items)));
    }

    @Test
    public void eachServerNpcLootIsOneDropAndOneKill() {
        fireKill("Dust devil", 110, new ItemStack(526, 1)); // bones
        fireKill("Dust devil", 110, new ItemStack(526, 1));
        fireKill("Dust devil", 110, new ItemStack(526, 1));

        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals("Each ServerNpcLoot should create its own drop", 3, drops.size());

        NpcLootAggregate agg = plugin.getNpcAggregate("Dust devil");
        assertNotNull(agg);
        assertEquals("Kill count should equal the number of ServerNpcLoot events", 3, agg.getNumberOfKills());
    }

    @Test
    public void sameTickMultiKillCountsEachKill() {
        // Barrage scenario: each kill arrives as its own ServerNpcLoot event.
        for (int i = 0; i < 8; i++) {
            fireKill("Dust devil", 110, new ItemStack(526, 1));
        }

        NpcLootAggregate agg = plugin.getNpcAggregate("Dust devil");
        assertNotNull(agg);
        assertEquals("All 8 same-tick kills must be counted", 8, agg.getNumberOfKills());
        assertEquals(8, plugin.getListViewDropArray().size());
    }

    @Test
    public void killCountAccumulatesOnTripAggregate() {
        plugin.initTrip("Dust devils");
        for (int i = 0; i < 5; i++) {
            fireKill("Dust devil", 110, new ItemStack(526, 1));
        }

        Trip trip = plugin.getActiveTrip();
        assertNotNull(trip);
        assertEquals("Trip kill count should reflect every ServerNpcLoot event", 5, trip.getTripKills());
    }

    @Test
    public void npcNameAndCombatLevelAreRecorded() {
        fireKill("Guard", 21, new ItemStack(526, 1), new ItemStack(995, 30));

        List<TrackableItemDrop> drops = plugin.getListViewDropArray();
        assertEquals(1, drops.size());
        assertEquals("Guard", drops.get(0).getDropNpcName());
        assertEquals(21, drops.get(0).getDropNpcLevel());
        assertEquals(2, drops.get(0).getDroppedItems().size());
    }

    // === Helpers ===

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
