package com.triptracker;

import javax.inject.Inject;
import javax.swing.*;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.ItemManager;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

@Slf4j
@PluginDescriptor(
		name = "Trip tracker",
		description = "Loot tracker with trip scoping capabilities",
		tags = {"loot", "tracker", "drops", "drop", "trip"}
)

public class EnhancedLootTrackerPlugin extends Plugin  {
	 @Inject
	 private ChatMessageManager chatMessageManager;

	 @Inject
	 private EnhancedLootTrackerConfig config;

	 @Inject
	 private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	private EnhancedLootTrackerPanel panel;
	private NavigationButton navButton;

	private final ArrayList<TrackableItemDrop> listViewDropArray = new ArrayList<>();
/*	private final LinkedHashMap<String, ArrayList<TrackableItemDrop>> aggregatedViewDropArray = new LinkedHashMap<>();*/

	private final LinkedHashMap<String, LinkedHashMap<String, Object>> dropQuantitiesByTypeOfNpc = new LinkedHashMap<>();
	private LinkedHashMap<String, LinkedHashMap<String, Object>> tripQuantitiesByTypeOfNpc = new LinkedHashMap<>();
	private String lastNpcKilled;

	@Provides
	EnhancedLootTrackerConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(EnhancedLootTrackerConfig.class);
	}

	@Override
	protected void startUp() throws Exception {
		panel = injector.getInstance(EnhancedLootTrackerPanel.class);
		panel.setParentPlugin(this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Enhanced loot tracker")
				.icon(icon)
				.priority(17)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception {
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onNpcLootReceived(final NpcLootReceived npcLootReceived) {
		final NPC npc = npcLootReceived.getNpc();
		final Collection<ItemStack> items = npcLootReceived.getItems();

		final String npcName = npc.getName();
		lastNpcKilled = npcName;
		final int combat = npc.getCombatLevel();

		TrackableItemDrop newItemDrop = new TrackableItemDrop(npcName, combat);

		for (final ItemStack item: items) {
			TrackableDroppedItem droppedItem = buildTrackableItem(item.getId(), item.getQuantity());
			newItemDrop.addLootToDrop(droppedItem);
		}

		processNewDrop(newItemDrop);
	}

	private TrackableDroppedItem buildTrackableItem(int itemId, int quantity)
	{
		final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
		final int gePrice = itemManager.getItemPrice(itemId);
		final int haPrice = itemComposition.getHaPrice();

		return new TrackableDroppedItem(
				itemId,
				itemComposition.getMembersName(),
				quantity,
				gePrice,
				haPrice);
	}

	private void processNewDrop(TrackableItemDrop newItemDrop) {
		updateItemMaps(newItemDrop);

		int trackingMode = panel.getSelectedTrackingMode();
		switch (trackingMode) {
			case 0:
				updateListViewUi(newItemDrop);
				break;
			case 1:
				updateGroupedViewUI();
				break;
			case 2:
				updateCurrentTripUi();
				break;
			default:
				break;
		}

	}

	private void updateItemMaps(TrackableItemDrop newItemDrop) {
		listViewDropArray.add(newItemDrop);
		updateDropSummaryMaps(dropQuantitiesByTypeOfNpc, newItemDrop);
		updateDropSummaryMaps(tripQuantitiesByTypeOfNpc, newItemDrop);
	}

	private void updateListViewUi(TrackableItemDrop newItemDrop) {
		SwingUtilities.invokeLater(() -> panel.addLootBox(newItemDrop));
	}

	private void updateGroupedViewUI() {
		SwingUtilities.invokeLater(() -> panel.addLootBox(dropQuantitiesByTypeOfNpc.get(lastNpcKilled), lastNpcKilled));
	}


	private void updateCurrentTripUi() {
		SwingUtilities.invokeLater(() ->
				panel.addLootBox(tripQuantitiesByTypeOfNpc.get(lastNpcKilled), lastNpcKilled, panel.getActiveTripName(), false)
		);
	}

	public ArrayList<TrackableItemDrop> getListViewDropArray() {
		return listViewDropArray;
	}

	public LinkedHashMap<String, LinkedHashMap<String, Object>> getDropQuantitiesByTypeOfNpc() {
		return dropQuantitiesByTypeOfNpc;
	}

	public void clearTripMap() {
		tripQuantitiesByTypeOfNpc = new LinkedHashMap<>();
	}

	public void updateDropSummaryMaps(LinkedHashMap<String, LinkedHashMap<String, Object>> mapToUpdate, TrackableItemDrop newItemDrop) {
		// Get the name of the NPC associated with the item drop
		String npcName = newItemDrop.getDropNpcName();

		// Get the map of information about the NPC from the mapToUpdate
		// If the map doesn't contain the NPC's information, create a new empty map
		LinkedHashMap<String, Object> npcMap = mapToUpdate.getOrDefault(npcName, new LinkedHashMap<>());

		// Update the values of the keys in the npcMap
		npcMap.putIfAbsent("totalGeValue", (long) 0);
		npcMap.putIfAbsent("numberOfDrops", 0);
		npcMap.putIfAbsent("lastKillTime", System.currentTimeMillis());

		npcMap.replace("totalGeValue", (long) npcMap.get("totalGeValue") + newItemDrop.getTotalDropGeValue());
		npcMap.replace("numberOfDrops", (int) npcMap.get("numberOfDrops") + 1);
		npcMap.replace("lastKillTime", System.currentTimeMillis());

		// Loop through the list of dropped items
		for (TrackableDroppedItem item : newItemDrop.getDroppedItems()) {
			// Get the name of the item and its quantity
			String itemName = item.getItemName();
			npcMap.putIfAbsent(itemName, item.getQuantity());
			npcMap.replace(itemName, (int) npcMap.get(itemName) + item.getQuantity());

			// Update the npcMap in the map being updated
			mapToUpdate.remove(npcName);
			mapToUpdate.put(npcName, npcMap);
		}
	}
}
