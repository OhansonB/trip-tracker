package com.triptracker;

import javax.inject.Inject;
import javax.swing.*;

import com.google.common.collect.*;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.LootManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.loottracker.LootRecordType;
import org.apache.commons.text.WordUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
	private LootManager lootManager;
	@Inject
	private ClientToolbar clientToolbar;
	private static final Pattern PICKPOCKET_REGEX = Pattern.compile("You pick (the )?(?<target>.+)'s? pocket.*");
	private static final Multimap<String, String> PICKPOCKET_DISAMBIGUATION_MAP = ImmutableMultimap.of(
			"H.A.M. Member", "Man",
			"H.A.M. Member", "Woman"
	);
	private String lastPickpocketTarget;
	private InventoryID inventoryId;
	private Multiset<Integer> inventorySnapshot;
	private Multiset<Integer> referenceInventorySnapshot;
	private EnhancedLootTrackerPanel panel;
	private NavigationButton navButton;
	private final ArrayList<TrackableItemDrop> listViewDropArray = new ArrayList<>();
	private String lastNpcKilled;
	private final ArrayList<NpcLootAggregate> npcLootAggregates = new ArrayList<>();
	private final ArrayList<Trip> trips = new ArrayList<>();
	public boolean activeTripExists = false;
	public String activeTripName = null;
	private static int numberOfTrips = 0;
	private boolean pickpocketHasOccurred;

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

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		System.out.println("EnhancedLootTrackerPlugin.onChatMessage");
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		final String message = event.getMessage();

		final Matcher pickpocketMatcher = PICKPOCKET_REGEX.matcher(message);
		if (pickpocketMatcher.matches())
		{
			pickpocketHasOccurred = true;

			// Get the target's name as listed in the chat box
			String pickpocketTarget = WordUtils.capitalize(pickpocketMatcher.group("target"));
			lastPickpocketTarget = pickpocketTarget;

			referenceInventorySnapshot = getPlayerInventorySnapshot();
		}
	}

	private Multiset<Integer> getPlayerInventorySnapshot() {
		Multiset<Integer> multiset = HashMultiset.create();
		final ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
		if (itemContainer != null)
		{
			Arrays.stream(itemContainer.getItems())
					.forEach(item -> multiset.add(item.getId(), item.getQuantity()));
		}

		return multiset;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		// If the change has occurred in the player's inventory
		if (event.getContainerId() == InventoryID.INVENTORY.getId()) {

			// pickpocketHasOccurred is set to true as a result of a certain chat message being detected
			// in onChatMessage
			if (pickpocketHasOccurred) {
				// Set to false to signify that the pickpocketing event has been processed
				pickpocketHasOccurred = false;

				// Get a snapshot of the players inventory (after the change)
				inventorySnapshot = getPlayerInventorySnapshot();

				// Create a difference between the post-change and pre-change inventory
				Multiset<Integer> newItems = compareInventorySnapshot(inventorySnapshot, referenceInventorySnapshot);

				// Generate a RuneLite List<ItemStack> object from the difference between current and reference
				// inventory snapshots
				final List<ItemStack> itemStacks = newItems.entrySet().stream()
						.map(e -> new ItemStack(e.getElement(), e.getCount(), client.getLocalPlayer().getLocalLocation()))
						.collect(Collectors.toList());

				// Create a new itemDrop object
				TrackableItemDrop itemDrop = new TrackableItemDrop(lastPickpocketTarget, 0);

				// Iterate over itemStacks and create TrackableDroppedItem for each item stack in that list
				// and add TrackableDroppedItem to TrackableItemDrop
				for (ItemStack itemStack : itemStacks) {
					int itemId = itemStack.getId();
					int itemQuantity = itemStack.getQuantity() > 0 ? itemStack.getQuantity() : 1;

					TrackableDroppedItem newDroppedItem = buildTrackableItem(itemId, itemQuantity);
					itemDrop.addLootToDrop(newDroppedItem);
				}

				// Process TrackableItemDrop (add to UI elements and such)
				processNewDrop(itemDrop);
			}
		}
	}

	private Multiset<Integer> compareInventorySnapshot(Multiset<Integer> multiset1, Multiset<Integer> multiset2) {
		return Multisets.difference(multiset1, multiset2);
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
				updateGroupedViewUI();
				updateCurrentTripUi();
				break;

			case 1:
			case 2:
				updateGroupedViewUI();
				updateCurrentTripUi();
				break;

			default:
				break;
		}
	}

	private void updateGroupedViewUI() {
		ArrayList<LootAggregation> lootAggregation = getNpcAggregate(lastNpcKilled).aggregateNpcDrops();
		SwingUtilities.invokeLater(() -> panel.addLootBox(getNpcAggregate(lastNpcKilled), lootAggregation));
	}

	private void updateCurrentTripUi() {
		if (getActiveTrip() != null) {
			Trip aTrip = getActiveTrip();
			aTrip.tripKills++;

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
		} else {
			System.out.println("getActiveTrip() is null");
		}
	}

	public ArrayList<TrackableItemDrop> getListViewDropArray() {
		return listViewDropArray;
	}

	public void addDropToTripAggregates(TrackableItemDrop itemDrop) {
		if (getActiveTrip() != null) {
			Trip trip = getActiveTrip();
			trip.tripValue += itemDrop.getTotalDropGeValue();

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
			// Add a drop to an existing record
			npcLootAggregates.get(i).addDropToNpcAggregate(itemDrop);

			// Create a copy of that record
			NpcLootAggregate tempTempAggregate = npcLootAggregates.get(i);

			// Remove the record from the ArrayList
			npcLootAggregates.remove(i);

			// Add the copy back to the ArrayList
			// This is required to float the record to the top of the list so that when grouped view rebuilds it
			// does so no the correct order
			npcLootAggregates.add(tempTempAggregate);
		}

		getItemAggregations(npcName);
	}


	public Trip getActiveTrip() {
		Trip activeTrip = null;

		// Loop through all current trips
		for (Trip trip : trips) {
			// If one of those trips is currently marked as active set activeTrip to that trip and break the loop
			if (trip.getTripStatus()) {
				activeTrip = trip;
				break;
			}
		}

		// If no active trip has been found
		if (activeTrip == null) {
			System.out.println("There is not an active trip");
		}

		return activeTrip;
	}

	public boolean checkForActiveTrip() {
		// Set a flag for whether there is currently an active trip
		boolean isActiveTrip = false;

		// Loop through all trips
		for (Trip trip : trips) {

			// If a trip has tripActive == true
			if (trip.getTripStatus()) {
				isActiveTrip = true;
				break;
			}
		}

		activeTripExists = isActiveTrip;
		return isActiveTrip;
	}

	public void initTrip(String tripName) {
		if (getActiveTrip() != null) {
			getActiveTrip().setStatus(false);
		}

		trips.add(new Trip(tripName, this));
		numberOfTrips++;
		activeTripName = tripName;
	}

	public int getNumberOfTrips() {
		// If size is 0 then the first trip is named trip 1; leading to a situation where if size is 1 then name is
		// also trip 1. Therefore, trip name is based on size + 1.
		return numberOfTrips;
	}

	public void getItemAggregations(String npcName) {
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

	public ArrayList<Trip> getTrips() {
		return trips;
	}

	public void removeTrip(String tripName) {
		for (int i = 0; i < trips.size(); i++) {
			if (trips.get(i).getTripName().equals(tripName)) {
				trips.remove(i);
				panel.removeTrip(tripName);
			}
		}
	}
}
