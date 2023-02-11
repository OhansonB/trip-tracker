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
	private final LinkedHashMap<String, ArrayList<TrackableItemDrop>> aggregatedViewDropArray = new LinkedHashMap<>();
	private final LinkedHashMap<String, LinkedHashMap<String, Object>> dropQuantitiesByTypeOfNpc = new LinkedHashMap<>();
	private LinkedHashMap<String, LinkedHashMap<String, Object>> currentTripItemSummariesByNpC = new LinkedHashMap<>();
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
		updateDropSummaryMaps(currentTripItemSummariesByNpC, newItemDrop);
	}

	private void updateListViewUi(TrackableItemDrop newItemDrop) {
		SwingUtilities.invokeLater(() -> panel.addLootBox(newItemDrop));
	}

	private void updateGroupedViewUI() {
		SwingUtilities.invokeLater(() -> panel.addLootBox(dropQuantitiesByTypeOfNpc.get(lastNpcKilled), lastNpcKilled));
	}


	private void updateCurrentTripUi() {
		SwingUtilities.invokeLater(() ->
				panel.addLootBox(currentTripItemSummariesByNpC.get(lastNpcKilled), lastNpcKilled, panel.getActiveTripName(), false)
		);
	}

	public ArrayList<TrackableItemDrop> getListViewDropArray() {
		return listViewDropArray;
	}

	public LinkedHashMap<String, LinkedHashMap<String, Object>> getDropQuantitiesByTypeOfNpc() {
		return dropQuantitiesByTypeOfNpc;
	}

	public void clearTripMap() {
		currentTripItemSummariesByNpC = new LinkedHashMap<>();
	}

	public void updateDropSummaryMaps(LinkedHashMap<String, LinkedHashMap<String, Object>> mapToUpdate, TrackableItemDrop newItemDrop) {
		ArrayList<TrackableItemDrop> listOfDrops;
		if (!aggregatedViewDropArray.containsKey(newItemDrop.getDropNpcName())) {
			listOfDrops = new ArrayList<>();
			listOfDrops.add(newItemDrop);
			aggregatedViewDropArray.put(newItemDrop.getDropNpcName(), listOfDrops);
		} else {
			listOfDrops = aggregatedViewDropArray.get(newItemDrop.getDropNpcName());
			listOfDrops.add(newItemDrop);
			aggregatedViewDropArray.replace(newItemDrop.getDropNpcName(), listOfDrops);
		}

		// ArrayList of items just dropped by the NPC
		ArrayList<TrackableDroppedItem> justDroppedItems = newItemDrop.getDroppedItems();

		/*
		Check if the outer map contains a record for the NPC from which the items just dropped and if not then create a
		new inner map and add zeroed records for totalGeValue and numberOfDrops, and add that map to the outer map.
		*/
		if (!mapToUpdate.containsKey(newItemDrop.getDropNpcName())) {
			// If the map does not contain that type of Npc, create one
			LinkedHashMap<String, Object> newMap = new LinkedHashMap<>();
			newMap.put("totalGeValue", (long) 0);
			newMap.put("numberOfDrops", 0);
			newMap.put("lastKillTime", System.currentTimeMillis());
			mapToUpdate.put(newItemDrop.getDropNpcName(), newMap);
		}

		// From the outer map, get the inner map associated with NPC that just dropped items
		LinkedHashMap<String, Object> oldNpcMap = mapToUpdate.get(newItemDrop.getDropNpcName());

		// Calculate the incrementation of the total GE value of all kills for this NPC
		long totalGeFromThisNpc = newItemDrop.getTotalDropGeValue() + (long) oldNpcMap.get("totalGeValue");
		// Calculate the incrementation of the total number of kills of this NPC
		int newNumberOfKills = (int) oldNpcMap.get("numberOfDrops") + 1;

		// Update inner map with new values for total GE value and number of kills
		mapToUpdate.get(newItemDrop.getDropNpcName()).replace("totalGeValue", totalGeFromThisNpc);
		mapToUpdate.get(newItemDrop.getDropNpcName()).replace("numberOfDrops", newNumberOfKills);
		mapToUpdate.get(newItemDrop.getDropNpcName()).replace("lastKillTime", System.currentTimeMillis());

		// Loop through the items just dropped and update the inner map with new item quantities
		for (int i = 0; i < justDroppedItems.size(); i++) {
			TrackableDroppedItem justDroppedItem = justDroppedItems.get(i);

			if (!oldNpcMap.containsKey(justDroppedItem.getItemName())) {
				mapToUpdate.get(newItemDrop.getDropNpcName()).put(justDroppedItem.getItemName(), justDroppedItem.getQuantity());
			} else {
				int oldQuantity = (int) mapToUpdate.get(newItemDrop.getDropNpcName()).get(justDroppedItem.getItemName());
				int newQuantity = justDroppedItem.getQuantity() + oldQuantity;
				mapToUpdate.get(newItemDrop.getDropNpcName()).replace(justDroppedItem.getItemName(), newQuantity);
			}
		}
	}
}
