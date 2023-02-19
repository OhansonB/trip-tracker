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
	private ArrayList<NpcLootAggregate> npcLootAggregates = new ArrayList<>();
	private LinkedHashMap<String, ArrayList<NpcLootAggregate>> npcAggregateMap = new LinkedHashMap<>();
	private ArrayList<Trip> trips = new ArrayList<>();

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
				if (panel.getActiveTripName() != null) {
					updateCurrentTripUi();
				}
				break;

			default:
				break;
		}
	}


	private void updateGroupedViewUI() {
		ArrayList<LootAggregation> lootAggregation = getNpcAggregate(lastNpcKilled).aggregateNpcDrops();
		SwingUtilities.invokeLater(() -> panel.addLootBox(getNpcAggregate(lastNpcKilled), lootAggregation));
	}


/*	private void updateCurrentTripUi() {
		SwingUtilities.invokeLater(() ->
				panel.addLootBox(currentTripItemSummariesByNpC.get(lastNpcKilled), lastNpcKilled, panel.getActiveTripName(), false)
		);
	}*/

	private void updateCurrentTripUi() {
		Trip aTrip = getActiveTrip();
		ArrayList<NpcLootAggregate> tripNpcAggregates = aTrip.getTripAggregates();

		ArrayList<LootAggregation> tempLootAggregation = null;
		NpcLootAggregate tempNpcLootAggregate = null;

		for (NpcLootAggregate a: tripNpcAggregates) {
			if (a.getNpcName().equals(lastNpcKilled)) {
				tempNpcLootAggregate = a;
				tempLootAggregation = a.aggregateNpcDrops();
				break;
			}
		}

		NpcLootAggregate npcLootAggregate = tempNpcLootAggregate;
		ArrayList<LootAggregation> lootAggregation = tempLootAggregation;

		SwingUtilities.invokeLater(() ->
				panel.addLootBox(npcLootAggregate, lootAggregation, aTrip.getTripName())
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

//	public void updateDropSummaryMaps(LinkedHashMap<String, LinkedHashMap<String, Object>> mapToUpdate, TrackableItemDrop newItemDrop) {
//		String npcName = newItemDrop.getDropNpcName();
//		ArrayList<TrackableItemDrop> listOfDrops;
//		if (!aggregatedViewDropArray.containsKey(npcName)) {
//			listOfDrops = new ArrayList<>();
//			listOfDrops.add(newItemDrop);
//			aggregatedViewDropArray.put(npcName, listOfDrops);
//		} else {
//			listOfDrops = aggregatedViewDropArray.get(npcName);
//			listOfDrops.add(newItemDrop);
//			aggregatedViewDropArray.replace(npcName, listOfDrops);
//		}
//
//		// ArrayList of items just dropped by the NPC
//		ArrayList<TrackableDroppedItem> justDroppedItems = newItemDrop.getDroppedItems();
//
//		/*
//		Check if the outer map contains a record for the NPC from which the items just dropped and if not then create a
//		new inner map and add zeroed records for totalGeValue and numberOfDrops, and add that map to the outer map.
//		*/
//		if (!mapToUpdate.containsKey(newItemDrop.getDropNpcName())) {
//			// If the map does not contain that type of Npc, create one
//			LinkedHashMap<String, Object> newMap = new LinkedHashMap<>();
//			newMap.put("totalGeValue", (long) 0);
//			newMap.put("numberOfDrops", 0);
//			newMap.put("lastKillTime", System.currentTimeMillis());
//			mapToUpdate.put(npcName, newMap);
//		}
//
//		// From the outer map, get the inner map associated with NPC that just dropped items
//		LinkedHashMap<String, Object> oldNpcMap = mapToUpdate.get(npcName);
//
//		// Calculate the incrementation of the total GE value of all kills for this NPC
//		long totalGeFromThisNpc = newItemDrop.getTotalDropGeValue() + (long) oldNpcMap.get("totalGeValue");
//		// Calculate the incrementation of the total number of kills of this NPC
//		int newNumberOfKills = (int) oldNpcMap.get("numberOfDrops") + 1;
//
//		// Update inner map with new values for total GE value and number of kills
//		mapToUpdate.get(npcName).replace("totalGeValue", totalGeFromThisNpc);
//		mapToUpdate.get(npcName).replace("numberOfDrops", newNumberOfKills);
//		mapToUpdate.get(npcName).replace("lastKillTime", System.currentTimeMillis());
//
//		// Loop through the items just dropped and update the inner map with new item quantities
//		for (int i = 0; i < justDroppedItems.size(); i++) {
//			TrackableDroppedItem justDroppedItem = justDroppedItems.get(i);
//
//			if (!oldNpcMap.containsKey(justDroppedItem.getItemName())) {
//				mapToUpdate.get(npcName).put(justDroppedItem.getItemName(), justDroppedItem.getQuantity());
//			} else {
//				int oldQuantity = (int) mapToUpdate.get(npcName).get(justDroppedItem.getItemName());
//				int newQuantity = justDroppedItem.getQuantity() + oldQuantity;
//				mapToUpdate.get(npcName).replace(justDroppedItem.getItemName(), newQuantity);
//			}
//		}
//
//		if (panel.getActiveTripName() != null && mapToUpdate == currentTripItemSummariesByNpC) {
//			panel.updateTripMaps(mapToUpdate.get(npcName), npcName, panel.getActiveTripName());
//		}
//	}

	public void addDropToTripAggregates(TrackableItemDrop itemDrop) {
		if (panel.getActiveTripName() != null) {
			Trip trip = getActiveTrip();

			String npcName = itemDrop.getDropNpcName();
			boolean newAggregateRequired = true;

			for (NpcLootAggregate npcLootAggregate : trip.getTripAggregates()) {
				if (npcLootAggregate.getNpcName().equals(npcName)) {
					npcLootAggregate.addDropToNpcAggregate(itemDrop);
					newAggregateRequired = false;
					break;
				}
			}

			if (newAggregateRequired) {
				NpcLootAggregate newAgg = new NpcLootAggregate(npcName, itemManager);
				newAgg.addDropToNpcAggregate(itemDrop);
				trip.addNpcAggregateToTrip(newAgg);
			}
		}
	}

	/**
	 This method adds a given item drop to the list of NPC loot aggregates.
	 It first retrieves the name of the NPC associated with the item drop and creates a new NPC loot aggregate.
	 It then checks if there is an existing record for the NPC, and if not, adds the new record to the list of aggregates.
	 If there is an existing record, it adds the item drop to the existing record.

	 @param itemDrop The item drop to be added to the NPC loot aggregates.
	 */
	public void addDropToGroupedAggregates(TrackableItemDrop itemDrop) {
		// Get the name of the NPC associated with the item drop.
		String npcName = itemDrop.getDropNpcName();

		// Create a new aggregate which is placed into our ArrayList<NpcLootAggregate> in the event that a new record is found
		NpcLootAggregate tempAggregate = new NpcLootAggregate(npcName, itemManager);

		// Default value of "newRecordFound = true" to be overwritten if a record is found
		boolean newRecordFound = true;

		// Integer used for our for loop and also to retrieve an existing record from the ArrayList<NpcLootAggregate>
		// if found, so that it can be updated
		int i = 0;

		// Loop through our ArrayList<NpcLootAggregate> if it contains any elements; it will contain zero elements
		// upon first initialisation (start up) and after the first kill will then contain data.
		if (npcLootAggregates.size() != 0) {
			for (i = 0; i < npcLootAggregates.size(); i++) {
				// Get the name of the NPC associated with the NpcLootAggregate associated with the current iteration
				// index
				String tempName = npcLootAggregates.get(i).getNpcName();

				// If the npc name associated with the existing NpcLootAggregate record matches the name of the npc
				// that dropped the item; this means that we already have a record of all items that have dropped
				// for this npc and are therefore only updating the existing aggregate (not creating a new one)
				if (tempName.equals(npcName)) {
					newRecordFound = false;
					// exit looping
					break;
				}
			}
		}

		// If a new record has been found (i.e., we dont have an aggregate for the given npc already) then insert
		// a new aggregate record with the drop added
		if (newRecordFound) {
			tempAggregate.addDropToNpcAggregate(itemDrop);
			npcLootAggregates.add(tempAggregate);
		} else {
			// Otherwise update the existing record with the new drop
			npcLootAggregates.get(i).addDropToNpcAggregate(itemDrop);
		}

		getItemAggregations(npcName);
	}


	public Trip getActiveTrip() {
		Trip activeTrip = null;
		String activeTripName = panel.getActiveTripName();
		System.out.println("Active trip name is " + activeTripName);

		// Loop through all current trips
		for (Trip trip : trips) {
			// If one of those trips is currently marked as active
			if (trip.isActive(activeTripName)) {
				activeTrip = trip;
				break;
			} else {
				System.out.println("There is not active trip");
			}
		}

		return activeTrip;
	}

	public void initTrip(String tripName) {
		trips.add(new Trip(tripName));
	}

	public int getNumberOfTrips() {
		// If size is 0 then the first trip is named trip 1; leading to a situation where if size is 1 then name is
		// also trip 1. Therefore, trip name is based on size + 1.
		return trips.size() + 1;
	}

	public ArrayList<LootAggregation> getItemAggregations(String npcName) {
		ArrayList<LootAggregation> lootAggregation = null;

		for (NpcLootAggregate npcAggregate : npcLootAggregates) {
			if (npcAggregate.getNpcName().equals(npcName)) {
				lootAggregation = npcAggregate.getNpcItemAggregations();
			}
		}

		if (lootAggregation != null) {
			long totalGeValue = 0;
			for (LootAggregation itemStack : lootAggregation) {
				String line = "ItemId " + itemStack.getItemId() +
						" is called " + itemStack.getItemName() +
						" and has quantity " + itemStack.getQuantity() +
						" with value " + itemStack.getTotalGePrice();

				System.out.println(line);

				totalGeValue += itemStack.getTotalGePrice();
			}

			System.out.println("All kills of " + npcName + " are worth " + totalGeValue + "gp.\n");
		}

		return lootAggregation;
	}

	public NpcLootAggregate getNpcAggregate(String npcName) {
		NpcLootAggregate tempAggregate = null;

		for (NpcLootAggregate npcAggregate : npcLootAggregates) {
			if (npcAggregate.getNpcName().equals(npcName)) {
				tempAggregate = npcAggregate;
				break;
			}
		}
		return tempAggregate;
	}

	private void updateItemMaps(TrackableItemDrop newItemDrop) {
		listViewDropArray.add(newItemDrop);
		addDropToGroupedAggregates(newItemDrop);
		addDropToTripAggregates(newItemDrop);
//		updateDropSummaryMaps(currentTripItemSummariesByNpC, newItemDrop);
	}

	private void updateListViewUi(TrackableItemDrop newItemDrop) {
		SwingUtilities.invokeLater(() -> panel.addLootBox(newItemDrop));
	}

	public void rebuildLootPanel() {
		switch (panel.getSelectedTrackingMode()) {
			// Case 0 = list view mode
			case 0:
				for (TrackableItemDrop itemDrop : getListViewDropArray()) {
					panel.addLootBox(itemDrop);
				}

				break;
			// Case 1 = aggregated by npc name (grouped mode)
			case 1:
				for (NpcLootAggregate npcAggregate : npcLootAggregates) {
					// Get the name of the npc associated with the current npc loot aggregate
					String npcName = npcAggregate.getNpcName();

					// Get the npc's item aggregation
					ArrayList<LootAggregation> npcsLootAggregation = getAggregation(npcName);

					// Add loot box for the item aggregation
					SwingUtilities.invokeLater(() -> panel.addLootBox(getNpcAggregate(npcName), npcsLootAggregation));
				}
				break;
			case 2:



				break;
			default:
				System.out.println("You have tried to switch to a view mode that is not supported");
				break;
		}
	}

	public ArrayList<LootAggregation> getAggregation(String npcName) {
		return getNpcAggregate(npcName).getNpcItemAggregations();
	}
}
