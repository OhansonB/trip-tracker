package com.triptracker;

import javax.inject.Inject;
import javax.swing.*;

import com.google.common.collect.*;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.ItemManager;
import org.apache.commons.text.WordUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private EnhancedLootTrackerConfig config;
	@Inject
	private Client client;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private net.runelite.client.callback.ClientThread clientThread;
	@Inject
	private ChatMessageManager chatMessageManager;
	private static final Pattern PICKPOCKET_REGEX = Pattern.compile("You pick (the )?(?<target>.+)'s? pocket.*");

	// All known coin pouch item IDs in OSRS (different NPCs give different pouch IDs)
	private static final Set<Integer> COIN_POUCH_IDS = new HashSet<>(Arrays.asList(
			22521, 22522, 22523, 22524, 22525, 22526, 22527, 22528, 22529, 22530,
			22531, 22532, 22533, 22534, 22535, 22536, 22537, 22538, 24703
	));

	// Average coin value per pickpocket for each NPC (midpoint of their known ranges)
	private static final Map<String, Integer> PICKPOCKET_COIN_VALUES = new HashMap<>();
	static {
		PICKPOCKET_COIN_VALUES.put("Man", 3);
		PICKPOCKET_COIN_VALUES.put("Woman", 3);
		PICKPOCKET_COIN_VALUES.put("Farmer", 9);
		PICKPOCKET_COIN_VALUES.put("H.A.M. Member", 3);
		PICKPOCKET_COIN_VALUES.put("Warrior", 18);
		PICKPOCKET_COIN_VALUES.put("Al-Kharid Warrior", 18);
		PICKPOCKET_COIN_VALUES.put("Rogue", 40);
		PICKPOCKET_COIN_VALUES.put("Cave Goblin", 30);
		PICKPOCKET_COIN_VALUES.put("Guard", 30);
		PICKPOCKET_COIN_VALUES.put("Fremennik Citizen", 40);
		PICKPOCKET_COIN_VALUES.put("Bearded Pollnivnian Bandit", 40);
		PICKPOCKET_COIN_VALUES.put("Wealthy Citizen", 85);
		PICKPOCKET_COIN_VALUES.put("Desert Bandit", 30);
		PICKPOCKET_COIN_VALUES.put("Knight Of Ardougne", 50);
		PICKPOCKET_COIN_VALUES.put("Knight Of Varlamore", 50);
		PICKPOCKET_COIN_VALUES.put("Pollnivnian Bandit", 50);
		PICKPOCKET_COIN_VALUES.put("Pirate", 40);
		PICKPOCKET_COIN_VALUES.put("Watchman", 60);
		PICKPOCKET_COIN_VALUES.put("Menaphite Thug", 60);
		PICKPOCKET_COIN_VALUES.put("Paladin", 80);
		PICKPOCKET_COIN_VALUES.put("Gnome", 300);
		PICKPOCKET_COIN_VALUES.put("Hero", 200);
		PICKPOCKET_COIN_VALUES.put("Vyre", 700);
		PICKPOCKET_COIN_VALUES.put("Elf", 280);
		PICKPOCKET_COIN_VALUES.put("TzHaar-Hur", 80);
	}
	private String lastPickpocketTarget;
	private Multiset<Integer> inventorySnapshot;
	private Multiset<Integer> referenceInventorySnapshot;
	private EnhancedLootTrackerPanel panel;
	private NavigationButton navButton;
	private final ArrayList<TrackableItemDrop> listViewDropArray = new ArrayList<>();
	private String lastNpcKilled;
	private final ArrayList<NpcLootAggregate> npcLootAggregates = new ArrayList<>();
	private final ArrayList<Trip> trips = new ArrayList<>();
	private int numberOfTrips = 0;
	private boolean pickpocketHasOccurred;
	private boolean chestLooted;
	private TripStorageService storageService;

	@Provides
	EnhancedLootTrackerConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(EnhancedLootTrackerConfig.class);
	}

	@Subscribe
	public void onConfigChanged(net.runelite.client.events.ConfigChanged event) {
		// Reserved for future config reactions
	}

	@Override
	protected void startUp() throws Exception {
		storageService = new TripStorageService();

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

		// Restore persisted data on the client thread (ItemManager requires it)
		clientThread.invokeLater(this::loadPersistedData);
	}

	@Override
	protected void shutDown() throws Exception {
		// Persist data synchronously before shutdown, then clean up the executor
		storageService.saveTripsSync(trips);
		storageService.saveDropsSync(listViewDropArray);
		storageService.shutdown();

		clientToolbar.removeNavigation(navButton);
	}

	private void loadPersistedData() {
		// Load drop history
		List<DropRecord> dropRecords = storageService.loadDrops();
		for (DropRecord record : dropRecords) {
			TrackableItemDrop drop = record.toDrop();
			listViewDropArray.add(drop);
			addDropToGroupedAggregates(drop);
		}

		// Load trips (all restored trips are set to inactive since the session is new)
		List<TripRecord> tripRecords = storageService.loadTrips();
		for (TripRecord record : tripRecords) {
			// If the trip was still active when saved, mark it as ended now
			if (record.tripActive) {
				record.tripActive = false;
				if (record.tripEndTime == null || "n/a".equals(record.tripEndTime)) {
					long endEpoch = System.currentTimeMillis();
					record.tripEndTime = Trip.formatTime(endEpoch);
					record.tripEndTimeEpoch = endEpoch;
				}
			}
			Trip trip = record.toTrip(this, itemManager);
			trips.add(trip);
			numberOfTrips++;
		}

		log.debug("Loaded {} drops and {} trips from disk", dropRecords.size(), tripRecords.size());

		// Rebuild the panel UI on the EDT so the loaded data is displayed
		SwingUtilities.invokeLater(() -> panel.rebuildAfterLoad());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOADING) {
			chestLooted = false;
		}
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
	public void onPlayerLootReceived(final PlayerLootReceived playerLootReceived) {
		final String playerName = playerLootReceived.getPlayer().getName();
		final Collection<ItemStack> items = playerLootReceived.getItems();
		final int combat = playerLootReceived.getPlayer().getCombatLevel();

		lastNpcKilled = playerName;

		TrackableItemDrop newItemDrop = new TrackableItemDrop(playerName, combat);

		for (final ItemStack item : items) {
			TrackableDroppedItem droppedItem = buildTrackableItem(item.getId(), item.getQuantity());
			newItemDrop.addLootToDrop(droppedItem);
		}

		processNewDrop(newItemDrop);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
		RewardSource source = RewardSource.fromInterfaceId(widgetLoaded.getGroupId());
		if (source == null) {
			return;
		}

		// Raids can be opened multiple times - prevent duplicate tracking
		if (source == RewardSource.CHAMBERS_OF_XERIC || source == RewardSource.THEATRE_OF_BLOOD
				|| source == RewardSource.TOMBS_OF_AMASCUT || source == RewardSource.FORTIS_COLOSSEUM) {
			if (chestLooted) {
				return;
			}
			chestLooted = true;
		}

		final ItemContainer container = client.getItemContainer(source.getContainerID());
		if (container == null) {
			return;
		}

		final String eventName = source.getDisplayName();
		lastNpcKilled = eventName;

		TrackableItemDrop newItemDrop = new TrackableItemDrop(eventName, 0);

		for (Item item : container.getItems()) {
			if (item.getId() > -1 && item.getQuantity() > 0) {
				TrackableDroppedItem droppedItem = buildTrackableItem(item.getId(), item.getQuantity());
				newItemDrop.addLootToDrop(droppedItem);
			}
		}

		if (!newItemDrop.getDroppedItems().isEmpty()) {
			processNewDrop(newItemDrop);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
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

			// Use the pre-change snapshot that was captured on the last inventory change.
			// referenceInventorySnapshot is maintained continuously in onItemContainerChanged
			// so it always reflects the state *before* the pickpocket loot arrives.
		}
	}

	private static final int INVENTORY_CONTAINER_ID = 93; // Standard player inventory container ID

	private Multiset<Integer> getPlayerInventorySnapshot() {
		Multiset<Integer> multiset = HashMultiset.create();
		final ItemContainer itemContainer = client.getItemContainer(INVENTORY_CONTAINER_ID);
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
		if (event.getContainerId() == INVENTORY_CONTAINER_ID) {

			// pickpocketHasOccurred is set to true as a result of a certain chat message being detected
			// in onChatMessage
			if (pickpocketHasOccurred) {
				// Set to false to signify that the pickpocketing event has been processed
				pickpocketHasOccurred = false;

				// Get a snapshot of the players inventory (after the change)
				inventorySnapshot = getPlayerInventorySnapshot();

				// If we don't have a reference snapshot yet, skip processing
				if (referenceInventorySnapshot == null) {
					referenceInventorySnapshot = inventorySnapshot;
					return;
				}

				// Create a difference between the post-change and pre-change inventory
				Multiset<Integer> newItems = compareInventorySnapshot(inventorySnapshot, referenceInventorySnapshot);

				// Update the reference snapshot for next time
				referenceInventorySnapshot = inventorySnapshot;

				// If there's no difference (e.g., pickpocket was interrupted), skip processing
				if (newItems.isEmpty()) {
					return;
				}

				// Generate a RuneLite List<ItemStack> object from the difference between current and reference
				// inventory snapshots
				final List<ItemStack> itemStacks = newItems.entrySet().stream()
						.map(e -> new ItemStack(e.getElement(), e.getCount()))
						.collect(Collectors.toList());

				// Create a new itemDrop object
				TrackableItemDrop itemDrop = new TrackableItemDrop(lastPickpocketTarget, 0);

				// Look up the average coin value for this NPC's pickpocket
				int coinValuePerPouch = PICKPOCKET_COIN_VALUES.getOrDefault(lastPickpocketTarget, 1);

				// Iterate over itemStacks and create TrackableDroppedItem for each item stack in that list
				// and add TrackableDroppedItem to TrackableItemDrop
				for (ItemStack itemStack : itemStacks) {
					int itemId = itemStack.getId();
					int itemQuantity = itemStack.getQuantity() > 0 ? itemStack.getQuantity() : 1;

					// Coin pouches have no GE value, so we assign the estimated coin value per pouch
					if (COIN_POUCH_IDS.contains(itemId)) {
						TrackableDroppedItem pouchItem = new TrackableDroppedItem(
								itemId,
								"Coin pouch",
								itemQuantity,
								coinValuePerPouch,
								coinValuePerPouch);
						itemDrop.addLootToDrop(pouchItem);
					} else {
						TrackableDroppedItem newDroppedItem = buildTrackableItem(itemId, itemQuantity);
						itemDrop.addLootToDrop(newDroppedItem);
					}
				}

				// Set lastNpcKilled so trip/grouped views attribute loot to the correct target
				lastNpcKilled = lastPickpocketTarget;

				// Process TrackableItemDrop (add to UI elements and such)
				processNewDrop(itemDrop);
			} else {
				// No pickpocket in progress — maintain the reference snapshot so we always
				// have a clean "before" state when a pickpocket does occur
				referenceInventorySnapshot = getPlayerInventorySnapshot();
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

		// Send loot summary to chat if configured
		if (config.showLootInChat()) {
			String message = newItemDrop.getDropNpcName() + " drop: " +
					FormatUtil.shortenNumber(newItemDrop.getTotalDropGeValue()) + " gp";
			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.GAMEMESSAGE)
					.runeLiteFormattedMessage(message)
					.build());
		}

		// Trim if over limit
		int maxDrops = config.maxDrops();
		boolean trimmed = false;
		while (listViewDropArray.size() > maxDrops) {
			listViewDropArray.remove(0);
			trimmed = true;
		}

		int maxTrips = config.maxTrips();
		while (trips.size() > maxTrips) {
			trips.remove(0);
		}

		// If trimmed, rebuild aggregates from scratch and refresh UI
		if (trimmed) {
			npcLootAggregates.clear();
			for (TrackableItemDrop drop : listViewDropArray) {
				String npcName = drop.getDropNpcName();
				NpcLootAggregate existing = null;
				for (NpcLootAggregate agg : npcLootAggregates) {
					if (agg.getNpcName().equals(npcName)) {
						existing = agg;
						break;
					}
				}
				if (existing == null) {
					NpcLootAggregate newAgg = new NpcLootAggregate(npcName, itemManager);
					newAgg.addDropToNpcAggregate(drop);
					npcLootAggregates.add(newAgg);
				} else {
					existing.addDropToNpcAggregate(drop);
					npcLootAggregates.remove(existing);
					npcLootAggregates.add(existing);
				}
			}

			SwingUtilities.invokeLater(() -> panel.rebuildAfterLoad());
		} else {
			// Normal UI update (no trimming needed)
			TrackingMode trackingMode = TrackingMode.fromId(panel.getSelectedTrackingMode());
			switch (trackingMode) {
				case LIST:
					updateListViewUi(newItemDrop);
					updateGroupedViewUI();
					updateCurrentTripUi();
					break;

				case GROUPED:
				case TRIP:
					updateGroupedViewUI();
					updateCurrentTripUi();
					break;

				default:
					break;
			}
		}

		// Persist after every drop (async, non-blocking)
		storageService.saveDrops(listViewDropArray);
		storageService.saveTrips(trips);
	}

	private void updateGroupedViewUI() {
		ArrayList<LootAggregation> lootAggregation = getNpcAggregate(lastNpcKilled).aggregateNpcDrops();
		SwingUtilities.invokeLater(() -> panel.addLootBox(getNpcAggregate(lastNpcKilled), lootAggregation));
	}

	private void updateCurrentTripUi() {
		if (getActiveTrip() != null) {
			Trip aTrip = getActiveTrip();
			aTrip.incrementKills();

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
					panel.addLootBox(npcLootAggregate, lootAggregation, aTrip.getTripId())
			);
		} else {
			log.debug("getActiveTrip() is null");
		}
	}

	public ArrayList<TrackableItemDrop> getListViewDropArray() {
		return listViewDropArray;
	}

	public void addDropToTripAggregates(TrackableItemDrop itemDrop) {
		if (getActiveTrip() != null) {
			Trip trip = getActiveTrip();
			trip.addValue(itemDrop.getTotalDropGeValue());

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
	 * Adds a drop to the global NPC loot aggregates. If an aggregate for the NPC already exists,
	 * the drop is added to it and the aggregate is moved to the end of the list (most recent first).
	 */
	public void addDropToGroupedAggregates(TrackableItemDrop itemDrop) {
		String npcName = itemDrop.getDropNpcName();

		NpcLootAggregate existing = null;
		for (NpcLootAggregate agg : npcLootAggregates) {
			if (agg.getNpcName().equals(npcName)) {
				existing = agg;
				break;
			}
		}

		if (existing == null) {
			NpcLootAggregate newAggregate = new NpcLootAggregate(npcName, itemManager);
			newAggregate.addDropToNpcAggregate(itemDrop);
			npcLootAggregates.add(newAggregate);
		} else {
			existing.addDropToNpcAggregate(itemDrop);
			// Move to end so most-recently-updated NPCs appear first when list is iterated in reverse
			npcLootAggregates.remove(existing);
			npcLootAggregates.add(existing);
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
			log.debug("There is not an active trip");
		}

		return activeTrip;
	}

	public boolean checkForActiveTrip() {
		for (Trip trip : trips) {
			if (trip.getTripStatus()) {
				return true;
			}
		}
		return false;
	}

	public void initTrip(String tripName) {
		if (getActiveTrip() != null) {
			getActiveTrip().setStatus(false);
		}

		trips.add(new Trip(tripName, this));
		numberOfTrips++;

		// Persist trip state change
		storageService.saveTrips(trips);
	}

	public int getNumberOfTrips() {
		return numberOfTrips;
	}

	/**
	 * Returns the next trip number based on the highest existing trip ID.
	 */
	public int getNextTripNumber() {
		int maxId = 0;
		for (Trip trip : trips) {
			maxId = Math.max(maxId, trip.getTripId());
		}
		return Math.max(maxId, numberOfTrips) + 1;
	}

	public void getItemAggregations(String npcName) {
		if (!log.isDebugEnabled()) {
			return;
		}

		ArrayList<LootAggregation> lootAggregation = null;

		for (NpcLootAggregate npcAggregate : npcLootAggregates) {
			if (npcAggregate.getNpcName().equals(npcName)) {
				lootAggregation = npcAggregate.getNpcItemAggregations();
			}
		}

		if (lootAggregation != null) {
			long totalGeValue = 0;
			for (LootAggregation itemStack : lootAggregation) {
				log.debug("ItemId {} is called {} and has quantity {} with value {}",
						itemStack.getItemId(), itemStack.getItemName(),
						itemStack.getQuantity(), itemStack.getTotalGePrice());

				totalGeValue += itemStack.getTotalGePrice();
			}

			log.debug("All kills of {} are worth {}gp.", npcName, totalGeValue);
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
		TrackingMode mode = TrackingMode.fromId(panel.getSelectedTrackingMode());
		switch (mode) {
			case LIST:
				for (TrackableItemDrop itemDrop : getListViewDropArray()) {
					panel.addLootBox(itemDrop);
				}
				break;

			case GROUPED:
				for (NpcLootAggregate npcAggregate : npcLootAggregates) {
					String npcName = npcAggregate.getNpcName();
					ArrayList<LootAggregation> npcsLootAggregation = getAggregation(npcName);
					SwingUtilities.invokeLater(() -> panel.addLootBox(getNpcAggregate(npcName), npcsLootAggregation));
				}
				break;

			case TRIP:
				break;

			default:
				log.warn("Unsupported view mode: {}", panel.getSelectedTrackingMode());
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
				int tripId = trips.get(i).getTripId();
				trips.remove(i);
				panel.removeTrip(tripId);
				break;
			}
		}
		// Persist after trip removal
		storageService.saveTrips(trips);
	}

	public void onTripStatusChanged() {
		storageService.saveTrips(trips);
	}

	public void showTripComparison(int preSelectedTripId) {
		SwingUtilities.invokeLater(() -> panel.showComparisonView(preSelectedTripId));
	}

	/**
	 * Clears all persisted and in-memory loot data (drops, trips, aggregates).
	 */
	public void clearAllData() {
		listViewDropArray.clear();
		npcLootAggregates.clear();
		trips.clear();
		numberOfTrips = 0;

		storageService.saveDrops(listViewDropArray);
		storageService.saveTrips(trips);

		SwingUtilities.invokeLater(() -> panel.rebuildAfterClear());
	}
}
