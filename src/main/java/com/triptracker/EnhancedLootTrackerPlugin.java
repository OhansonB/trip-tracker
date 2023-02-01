package com.triptracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

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
		name = "Enhanced loot tracker",
		description = "Loot tracker with advanced session and trip capability",
		tags = {"loot", "tracker", "drops", "drop"}
)

public class EnhancedLootTrackerPlugin extends Plugin  {
	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private EnhancedLootTrackerConfig config;

	@Inject
	private Client client;

	private EnhancedLootTrackerPanel panel;
	private NavigationButton navButton;

	private final ArrayList<TrackableItemDrop> listViewDropArray = new ArrayList<>();
	private final LinkedHashMap<String, ArrayList<TrackableItemDrop>> aggregatedViewDropArray = new LinkedHashMap<>();

	private final LinkedHashMap<String, LinkedHashMap<String, Object>> dropQuantitiesByTypeOfNpc = new LinkedHashMap<>();
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
				System.out.println("The logic for inserting boxes into trip view mode has not yet been created");
				break;
			default:
				break;
		}

	}

	private void updateItemMaps(TrackableItemDrop newItemDrop) {
		/*
		Add new drop to array of all drops (this array is analogous to list view UI and will eventually be persisted to re-draw
		the list view UI on startup)
		*/
		listViewDropArray.add(newItemDrop);

		/*
		Populate the map of 'NPC names' to 'ArrayList of item drops objects' with new item drop (this will
		eventually be persisted to re-draw the aggregated by NPC view UI on startup).
		*/
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

		//System.out.println(newItemDrop.describeTrackableDrop(chatMessageManager, config));
		//System.out.println(listViewDropArray);

		/*
		Create a map of maps which contains the sum of all kills, GE value of drops, and quantity of
		each item dropped by NPC name. The 'outer map' maps NPC name to a map, and the 'inner map' maps
		String to objects (e.g., itemName to quantity, totalGeValue to long, and numberOfDrops to integer). We
		then end up with a map where each NPC name has a summary of the quantity of each item, and total value
		of all drops, etc.

		Eventually the dropQuantitiesByTypeOfNpc map will be generated at start up by data persisted in
		map named aggregatedViewDropArray, and extended during game play (rather than created and extended during
		game play as is the case below).
		*/
		int numberOfDrops = aggregatedViewDropArray.get(newItemDrop.getDropNpcName()).size();

		// ArrayList of items just dropped by the NPC
		ArrayList<TrackableDroppedItem> justDroppedItems = newItemDrop.getDroppedItems();

		/*
		Check if the outer map contains a record for the NPC from which the items just dropped and if not then create a
		new inner map and add zeroed records for totalGeValue and numberOfDrops, and add that map to the outer map.
		*/
		if (!dropQuantitiesByTypeOfNpc.containsKey(newItemDrop.getDropNpcName())) {
			// If the map does not contain that type of Npc, create one
			LinkedHashMap<String, Object> newMap = new LinkedHashMap<>();
			newMap.put("totalGeValue", (long) 0);
			newMap.put("numberOfDrops", 0);
			dropQuantitiesByTypeOfNpc.put(newItemDrop.getDropNpcName(), newMap);
		}

		// From the outer map, get the inner map associated with NPC that just dropped items
		LinkedHashMap<String, Object> oldNpcMap = dropQuantitiesByTypeOfNpc.get(newItemDrop.getDropNpcName());

		// Calculate the incrementation of the total GE value of all kills for this NPC
		long totalGeFromThisNpc = newItemDrop.getTotalDropGeValue() + (long) oldNpcMap.get("totalGeValue");
		// Calculate the incrementation of the total number of kills of this NPC
		int newNumberOfKills = (int) oldNpcMap.get("numberOfDrops") + 1;

		// Update inner map with new values for total GE value and number of kills
		dropQuantitiesByTypeOfNpc.get(newItemDrop.getDropNpcName()).replace("totalGeValue", totalGeFromThisNpc);
		dropQuantitiesByTypeOfNpc.get(newItemDrop.getDropNpcName()).replace("numberOfDrops", newNumberOfKills);

		// Loop through the items just dropped and update the inner map with new item quantities
		for (int i = 0; i < justDroppedItems.size(); i++) {
			TrackableDroppedItem justDroppedItem = justDroppedItems.get(i);

			if (!oldNpcMap.containsKey(justDroppedItem.getItemName())) {
				dropQuantitiesByTypeOfNpc.get(newItemDrop.getDropNpcName()).put(justDroppedItem.getItemName(), justDroppedItem.getQuantity());
			} else {
				int oldQuantity = (int) dropQuantitiesByTypeOfNpc.get(newItemDrop.getDropNpcName()).get(justDroppedItem.getItemName());
				int newQuantity = justDroppedItem.getQuantity() + oldQuantity;
				dropQuantitiesByTypeOfNpc.get(newItemDrop.getDropNpcName()).replace(justDroppedItem.getItemName(), newQuantity);
			}
		}

//		System.out.println("Number of drops from " + newItemDrop.getDropNpcName() + " is " + numberOfDrops);
//		System.out.println("Total GE value of drops this NPC is " + totalGeFromThisNpc + "gp\n");
//		System.out.println("Rolling summary of kills for this NPC:\n" + dropQuantitiesByTypeOfNpc.get(newItemDrop.getDropNpcName()) + "\n");
//		System.out.println("Rolling summary of kills for all NPCs:\n" + dropQuantitiesByTypeOfNpc + "\n\n");
	}

	private void updateListViewUi(TrackableItemDrop newItemDrop) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				panel.addLootBox(newItemDrop);
			}
		});
	}

	private void updateGroupedViewUI() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				panel.addLootBox(dropQuantitiesByTypeOfNpc.get(lastNpcKilled), lastNpcKilled);
			}
		});
	}

	public ArrayList<TrackableItemDrop> getListViewDropArray() {
		return listViewDropArray;
	}

	public LinkedHashMap<String, LinkedHashMap<String, Object>> getDropQuantitiesByTypeOfNpc() {
		return dropQuantitiesByTypeOfNpc;
	}
}
