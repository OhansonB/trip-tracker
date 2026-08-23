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
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
	private static final Pattern CLUE_SCROLL_PATTERN = Pattern.compile("You have completed (\\d+) ([a-z]+) Treasure Trails?\\.");

	// Chat-triggered loot sources
	private static final String WINTERTODT_LOOT_PREFIX = "You found some loot: ";
	private static final String TEMPOROSS_LOOT_PREFIX = "You found some loot: ";
	private static final String GUARDIANS_OF_THE_RIFT_LOOT_PREFIX = "You found some loot: ";
	private static final String HERBIBOAR_MESSAGE = "You harvest herbs from the herbiboar, whereupon it escapes.";
	private static final String CHEST_LOOTED_MESSAGE = "You find some treasure in the chest!";
	private static final String OTHER_CHEST_LOOTED_MESSAGE = "You steal some loot from the chest.";
	private static final Pattern LARRAN_CHEST_PATTERN = Pattern.compile("You have opened Larran's (big|small) chest .*");
	private static final Pattern BIRDHOUSE_PATTERN = Pattern.compile("You dismantle and discard the trap, retrieving .*");
	private static final int CLOCKWORK_ITEM_ID = 8792;
	private static final Pattern FARMING_HARVEST_PATTERN = Pattern.compile("You begin to harvest the (.+?)\\.");
	private static final String CACTUS_PICK_MESSAGE = "You carefully pick a spine from the cactus.";
	private static final Pattern FARMING_PICK_PATTERN = Pattern.compile("You pick (?:a |an |some )(.+?)\\.");

	// Bird nest item IDs (searchable nests that yield seeds/rings)
	private static final String BIRD_NEST_EVENT = "Bird nest";
	private static final Set<Integer> BIRD_NEST_IDS = new HashSet<>(Arrays.asList(
			5070, 5071, 5072,  // egg nests (red, green, blue)
			5073,              // seed nest
			5074,              // ring nest
			22798,             // clue nest (beginner)
			22800,             // clue nest (easy)
			22802,             // clue nest (medium)
			22804,             // clue nest (hard)
			22806              // clue nest (elite)
	));

	// Flag for bird nest search inventory diff
	private boolean awaitingBirdNestDiff;

	// Items to exclude from farming harvest tracking (not actual harvests)
	private static final Set<Integer> FARMING_EXCLUDED_ITEM_IDS = new HashSet<>(Arrays.asList(
			6055,  // Weeds
			6099,  // Teleport crystal (1 charge)
			6100,  // Teleport crystal (2 charges)
			6101,  // Teleport crystal (3 charges)
			6102,  // Teleport crystal (4 charges)
			6103,  // Crystal teleport seed (uncharged)
			23959, // Enhanced crystal teleport seed
			23968  // Eternal teleport crystal
	));

	// Varbit ID for Chambers of Xeric raid state (1 = inside raid)
	private static final int IN_RAID_VARBIT = 5432;

	// Region IDs for location-specific loot
	private static final int WINTERTODT_REGION = 6461;
	private static final int TEMPOROSS_REGION = 12588;
	private static final int GUARDIANS_OF_THE_RIFT_REGION = 14484;

	// Flag for inventory-diff based loot detection
	private boolean awaitingLootDiff;
	private String pendingLootEventName;
	private Multiset<Integer> preLootInventorySnapshot;

	// Farming harvest tracking state
	private boolean farmingHarvestInProgress;
	private boolean farmingStartedFromXp;
	private String farmingPatchType;
	private Multiset<Integer> farmingPreHarvestSnapshot;
	private ScheduledFuture<?> farmingDebounceTimer;
	private static final long FARMING_DEBOUNCE_TICKS_MS = 4200; // ~7 game ticks to cover the picking animation gap
	private static final long FARMING_LEVELUP_DEBOUNCE_MS = 10000; // extended debounce for level-up animation
	private int lastInventoryChangeTick = -1; // game tick of most recent ItemContainerChanged
	private int lastFarmingXpTick = -1; // game tick of most recent Farming XP event
	private int lastKnownFarmingLevel = -1; // track farming level to detect level-ups mid-harvest

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
	private Multiset<Integer> previousReferenceInventorySnapshot;
	private EnhancedLootTrackerPanel panel;
	private NavigationButton navButton;
	private final List<TrackableItemDrop> listViewDropArray = Collections.synchronizedList(new ArrayList<>());
	private String lastNpcKilled;
	private final List<NpcLootAggregate> npcLootAggregates = Collections.synchronizedList(new ArrayList<>());
	private final List<Trip> trips = Collections.synchronizedList(new ArrayList<>());
	private boolean pickpocketHasOccurred;
	private boolean chestLooted;
	private TripStorageService storageService;
	private long currentAccountHash = -1; // Tracks the currently loaded account

	// Debounce persistence: save at most once every 5 seconds
	private static final long SAVE_DEBOUNCE_MS = 5000;
	private ScheduledExecutorService debounceExecutor;
	private ScheduledFuture<?> pendingDropSave;
	private ScheduledFuture<?> pendingTripSave;
	private final Object saveLock = new Object();

	@Provides
	EnhancedLootTrackerConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(EnhancedLootTrackerConfig.class);
	}

	@Subscribe
	public void onConfigChanged(net.runelite.client.events.ConfigChanged event) {
		if ("triptracker".equals(event.getGroup()) && "spriteDisplayMode".equals(event.getKey())) {
			SwingUtilities.invokeLater(() -> panel.rebuildAfterLoad());
		}
	}

	@Override
	protected void startUp() throws Exception {
		storageService = new TripStorageService();

		debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "trip-tracker-debounce");
			t.setDaemon(true);
			return t;
		});

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
		// Cancel any pending debounced saves
		synchronized (saveLock) {
			if (pendingDropSave != null) {
				pendingDropSave.cancel(false);
			}
			if (pendingTripSave != null) {
				pendingTripSave.cancel(false);
			}
			if (farmingDebounceTimer != null) {
				farmingDebounceTimer.cancel(false);
			}
		}
		debounceExecutor.shutdown();

		// Persist data synchronously before shutdown, then clean up the executor
		if (currentAccountHash != -1) {
			storageService.saveTripsSync(trips);
			storageService.saveDropsSync(listViewDropArray);
			storageService.saveLastSessionEpoch(System.currentTimeMillis());
		}
		storageService.shutdown();

		currentAccountHash = -1;
		clientToolbar.removeNavigation(navButton);
	}

	private void loadPersistedData() {
		// Load drop history
		List<DropRecord> dropRecords = storageService.loadDrops();
		for (DropRecord record : dropRecords) {
			TrackableItemDrop drop = record.toDrop();
			// Strip farming-excluded items from legacy farming drops that were persisted
			// before the exclusion filter existed
			stripFarmingExcludedItems(drop);
			listViewDropArray.add(drop);
			addDropToGroupedAggregates(drop);
		}

		// Load trips — active trips persist across restarts, subject to inactivity timeout
		List<TripRecord> tripRecords = storageService.loadTrips();
		long lastSessionEpoch = storageService.loadLastSessionEpoch();
		long inactivityThresholdMs = config.tripInactivityTimeout() * 60000L;
		long timeSinceLastSession = (lastSessionEpoch > 0) ? System.currentTimeMillis() - lastSessionEpoch : 0;

		for (TripRecord record : tripRecords) {
			// Auto-stop active trips that exceeded the inactivity timeout
			if (record.tripActive && !record.tripPaused && lastSessionEpoch > 0
					&& timeSinceLastSession > inactivityThresholdMs) {
				record.tripActive = false;
				if (record.tripEndTime == null || "n/a".equals(record.tripEndTime)) {
					record.tripEndTime = Trip.formatTime(lastSessionEpoch);
					record.tripEndTimeEpoch = lastSessionEpoch;
				}
				log.debug("Auto-stopped trip '{}' due to inactivity ({} ms since last session)",
						record.tripName, timeSinceLastSession);
			}
			Trip trip = record.toTrip(this, itemManager);
			trips.add(trip);
		}

		log.debug("Loaded {} drops and {} trips from disk", dropRecords.size(), tripRecords.size());

		// Load collapsed NPC names for the grouped view
		Set<String> collapsedNpcs = storageService.loadCollapsedNpcs();

		// Rebuild the panel UI on the EDT so the loaded data is displayed
		SwingUtilities.invokeLater(() -> {
			panel.setCollapsedNpcs(collapsedNpcs);
			panel.rebuildAfterLoad();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOADING) {
			chestLooted = false;
		}
		if (event.getGameState() == GameState.LOGGED_IN) {
			long accountHash = client.getAccountHash();
			if (accountHash != -1 && accountHash != currentAccountHash) {
				// Save current account's data before switching (if we had one loaded)
				if (currentAccountHash != -1) {
					storageService.saveTripsSync(trips);
					storageService.saveDropsSync(listViewDropArray);
					storageService.saveLastSessionEpoch(System.currentTimeMillis());
				}

				// Switch to the new account's data directory
				currentAccountHash = accountHash;
				storageService.switchAccount(accountHash);

				// Clear in-memory state and reload from the new account's files
				clearTrackingState();
				loadPersistedData();
			}
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN) {
			// Only reset snapshots when actually logged out, not on world hops or loading
			referenceInventorySnapshot = null;
			previousReferenceInventorySnapshot = null;
			// Record logout time for trip inactivity checks
			if (currentAccountHash != -1) {
				storageService.saveLastSessionEpoch(System.currentTimeMillis());
			}
		}
	}

	/**
	 * Clears all in-memory tracking state in preparation for loading a different account's data.
	 */
	private void clearTrackingState() {
		synchronized (listViewDropArray) {
			listViewDropArray.clear();
		}
		synchronized (npcLootAggregates) {
			npcLootAggregates.clear();
		}
		synchronized (trips) {
			trips.clear();
		}
		lastNpcKilled = null;
		pickpocketHasOccurred = false;
		awaitingLootDiff = false;
		awaitingBirdNestDiff = false;
		farmingHarvestInProgress = false;
	}

	@Subscribe
	public void onNpcLootReceived(final NpcLootReceived npcLootReceived) {
		final NPC npc = npcLootReceived.getNpc();
		final Collection<ItemStack> items = npcLootReceived.getItems();

		final String npcName = npc.getName();
		lastNpcKilled = npcName;
		final int combat = npc.getCombatLevel();

		debugChat("NPC kill: " + npcName + " (lvl " + combat + ") - " + items.size() + " items");

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

		debugChat("Widget loaded: " + source.getDisplayName() + " (interfaceId=" + widgetLoaded.getGroupId() + ")");

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
			debugChat("Pickpocket detected: " + pickpocketTarget);

			// Use the pre-change snapshot that was captured on the last inventory change.
			// referenceInventorySnapshot is maintained continuously in onItemContainerChanged
			// so it always reflects the state *before* the pickpocket loot arrives.
		}

		// Check for clue scroll completion
		final Matcher clueMatcher = CLUE_SCROLL_PATTERN.matcher(message);
		if (clueMatcher.find()) {
			String type = clueMatcher.group(2);
			String eventName = "Clue Scroll (" + type.substring(0, 1).toUpperCase() + type.substring(1) + ")";

			// Defer clue reward processing to the next game tick — the container
			// may not be populated yet when the chat message fires
			clientThread.invokeLater(() -> {
				@SuppressWarnings("deprecation")
				int containerId = net.runelite.api.InventoryID.BARROWS_REWARD.getId();
				final ItemContainer container = client.getItemContainer(containerId);
				if (container != null) {
					lastNpcKilled = eventName;
					TrackableItemDrop clueReward = new TrackableItemDrop(eventName, 0);
					for (Item item : container.getItems()) {
						if (item.getId() > -1 && item.getQuantity() > 0) {
							TrackableDroppedItem droppedItem = buildTrackableItem(item.getId(), item.getQuantity());
							clueReward.addLootToDrop(droppedItem);
						}
					}
					if (!clueReward.getDroppedItems().isEmpty()) {
						processNewDrop(clueReward);
					}
				}
			});
		}

		// Chat-triggered inventory-diff loot sources
		final int regionId = client.getLocalPlayer() != null
				? client.getLocalPlayer().getWorldLocation().getRegionID() : -1;

		if (regionId == WINTERTODT_REGION && message.contains(WINTERTODT_LOOT_PREFIX)) {
			triggerLootDiff("Wintertodt");
		} else if (regionId == TEMPOROSS_REGION && message.contains(TEMPOROSS_LOOT_PREFIX)) {
			triggerLootDiff("Tempoross");
		} else if (regionId == GUARDIANS_OF_THE_RIFT_REGION && message.contains(GUARDIANS_OF_THE_RIFT_LOOT_PREFIX)) {
			triggerLootDiff("Guardians of the Rift");
		} else if (message.equals(HERBIBOAR_MESSAGE)) {
			triggerLootDiff("Herbiboar");
		} else if (message.equals(CHEST_LOOTED_MESSAGE) || message.equals(OTHER_CHEST_LOOTED_MESSAGE)) {
			triggerLootDiff("Chest");
		} else if (LARRAN_CHEST_PATTERN.matcher(message).matches()) {
			triggerLootDiff("Larran's Chest");
		} else if (BIRDHOUSE_PATTERN.matcher(message).matches()) {
			triggerLootDiff("Bird House");
		}

		// Farming harvest detection
		final Matcher farmingMatcher = FARMING_HARVEST_PATTERN.matcher(message);
		if (farmingMatcher.matches() && !farmingHarvestInProgress && !isInsideChambers()) {
			String patchType = farmingMatcher.group(1);
			farmingHarvestInProgress = true;
			farmingStartedFromXp = false;
			farmingPatchType = patchType;
			farmingPreHarvestSnapshot = getPlayerInventorySnapshot();
			debugChat("Farming harvest started: " + patchType);
		}

		// Cactus patch picking — uses a different message than standard harvesting
		if (message.equals(CACTUS_PICK_MESSAGE) && !farmingHarvestInProgress) {
			farmingHarvestInProgress = true;
			farmingStartedFromXp = false;
			farmingPatchType = "cactus patch";
			farmingPreHarvestSnapshot = getPlayerInventorySnapshot();
			debugChat("Farming harvest started: cactus patch");
		}

		// Fruit tree / bush picking — "You pick a coconut.", "You pick a banana.", etc.
		final Matcher pickMatcher = FARMING_PICK_PATTERN.matcher(message);
		if (pickMatcher.matches() && !farmingHarvestInProgress) {
			String pickedItem = pickMatcher.group(1);
			farmingHarvestInProgress = true;
			farmingStartedFromXp = false;
			farmingPatchType = pickedItem + " tree";
			farmingPreHarvestSnapshot = getPlayerInventorySnapshot();
			debugChat("Farming pick started: " + farmingPatchType + " (picked: " + pickedItem + ")");
		}
	}

	/**
	 * Triggers an inventory diff for chat-based loot sources.
	 * Takes a snapshot now; the diff is processed on the next ItemContainerChanged.
	 */
	private void triggerLootDiff(String eventName) {
		awaitingLootDiff = true;
		pendingLootEventName = eventName;
		preLootInventorySnapshot = getPlayerInventorySnapshot();
		debugChat("Loot trigger: " + eventName + " (awaiting inventory change)");
	}

	private static final int INVENTORY_CONTAINER_ID = 93; // Standard player inventory container ID

	private Multiset<Integer> getPlayerInventorySnapshot() {
		Multiset<Integer> multiset = HashMultiset.create();
		final ItemContainer itemContainer = client.getItemContainer(INVENTORY_CONTAINER_ID);
		if (itemContainer != null)
		{
			Arrays.stream(itemContainer.getItems())
					.forEach(item -> {
						int id = item.getId();
						// Normalize noted items to their unnoted form so diffs aggregate correctly.
						// In OSRS, noted item IDs are unnoted + 1, and ItemComposition.getNote()
						// returns the linked note template ID (799) for noted items.
						ItemComposition comp = itemManager.getItemComposition(id);
						if (comp.getNote() != -1) {
							id = comp.getLinkedNoteId();
						}
						multiset.add(id, item.getQuantity());
					});
		}

		return multiset;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		// If the change has occurred in the player's inventory
		if (event.getContainerId() == INVENTORY_CONTAINER_ID) {
			lastInventoryChangeTick = client.getTickCount();

			// Always capture the current inventory state for reference tracking
			Multiset<Integer> currentSnapshot = getPlayerInventorySnapshot();

			// pickpocketHasOccurred is set to true as a result of a certain chat message being detected
			// in onChatMessage
			if (pickpocketHasOccurred) {
				// Set to false to signify that the pickpocketing event has been processed
				pickpocketHasOccurred = false;

				// If we don't have a reference snapshot yet, just update and skip processing
				if (referenceInventorySnapshot != null) {
					// Create a difference between the post-change and pre-change inventory
					Multiset<Integer> newItems = compareInventorySnapshot(currentSnapshot, referenceInventorySnapshot);

					// If there's a difference, process the pickpocket loot
					if (!newItems.isEmpty()) {
						// Generate a RuneLite List<ItemStack> object from the difference
						final List<ItemStack> itemStacks = newItems.entrySet().stream()
								.map(e -> new ItemStack(e.getElement(), e.getCount()))
								.collect(Collectors.toList());

						// Create a new itemDrop object
						TrackableItemDrop itemDrop = new TrackableItemDrop(lastPickpocketTarget, 0);

						// Look up the average coin value for this NPC's pickpocket
						int coinValuePerPouch = PICKPOCKET_COIN_VALUES.getOrDefault(lastPickpocketTarget, 1);

						for (ItemStack itemStack : itemStacks) {
							int itemId = itemStack.getId();
							int itemQuantity = itemStack.getQuantity() > 0 ? itemStack.getQuantity() : 1;

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
					}
				}
			} else if (awaitingLootDiff) {
				// Process chat-triggered loot diff
				awaitingLootDiff = false;

				Multiset<Integer> newItems = compareInventorySnapshot(currentSnapshot, preLootInventorySnapshot);

				if (!newItems.isEmpty()) {
					lastNpcKilled = pendingLootEventName;
					TrackableItemDrop lootDrop = new TrackableItemDrop(pendingLootEventName, 0);

					for (Multiset.Entry<Integer> entry : newItems.entrySet()) {
						int itemId = entry.getElement();
						int quantity = entry.getCount();
						// Exclude clockwork from bird house loot (it's returned, not earned)
						if (itemId == CLOCKWORK_ITEM_ID && "Bird House".equals(pendingLootEventName)) {
							continue;
						}
						if (itemId > -1 && quantity > 0) {
							TrackableDroppedItem droppedItem = buildTrackableItem(itemId, quantity);
							lootDrop.addLootToDrop(droppedItem);
						}
					}

					if (!lootDrop.getDroppedItems().isEmpty()) {
						processNewDrop(lootDrop);
					}
				}
			} else if (awaitingBirdNestDiff) {
				// Process bird nest search loot diff
				awaitingBirdNestDiff = false;

				Multiset<Integer> newItems = compareInventorySnapshot(currentSnapshot, preLootInventorySnapshot);

				if (!newItems.isEmpty()) {
					lastNpcKilled = BIRD_NEST_EVENT;
					TrackableItemDrop nestDrop = new TrackableItemDrop(BIRD_NEST_EVENT, 0);

					for (Multiset.Entry<Integer> entry : newItems.entrySet()) {
						int itemId = entry.getElement();
						int quantity = entry.getCount();
						if (itemId > -1 && quantity > 0) {
							TrackableDroppedItem droppedItem = buildTrackableItem(itemId, quantity);
							nestDrop.addLootToDrop(droppedItem);
						}
					}

					if (!nestDrop.getDroppedItems().isEmpty()) {
						processNewDrop(nestDrop);
					}
				}
			}

			// Always update reference snapshots after every inventory change
			// On the first change after login, previous will be null — set it to the current
			// snapshot so that the very next change has a valid baseline to diff against.
			if (referenceInventorySnapshot == null) {
				previousReferenceInventorySnapshot = currentSnapshot;
				debugChat("First inventory load — initializing both snapshots");
			} else {
				previousReferenceInventorySnapshot = referenceInventorySnapshot;
			}
			referenceInventorySnapshot = currentSnapshot;
			debugChat("Snapshot updated. prev=" + (previousReferenceInventorySnapshot != null ? previousReferenceInventorySnapshot.size() : "null") 
					+ " ref=" + (referenceInventorySnapshot != null ? referenceInventorySnapshot.size() : "null")
					+ " tick=" + client.getTickCount());

			// Check if farming XP fired on this same tick but before this inventory change
			// (flowers: StatChanged fires before ItemContainerChanged)
			int currentTick = client.getTickCount();
			if (!farmingHarvestInProgress && lastFarmingXpTick == currentTick && previousReferenceInventorySnapshot != null) {
				farmingHarvestInProgress = true;
				farmingPatchType = "farming patch";
				farmingStartedFromXp = true;
				farmingPreHarvestSnapshot = HashMultiset.create(previousReferenceInventorySnapshot);
				debugChat("Farming harvest started from inventory change (XP was on same tick)");

				// Start the debounce timer
				synchronized (saveLock) {
					if (farmingDebounceTimer != null && !farmingDebounceTimer.isDone()) {
						farmingDebounceTimer.cancel(false);
					}
					farmingDebounceTimer = debounceExecutor.schedule(
							this::completeFarmingHarvest,
							FARMING_DEBOUNCE_TICKS_MS,
							TimeUnit.MILLISECONDS
					);
				}
			}
		}
	}

	private Multiset<Integer> compareInventorySnapshot(Multiset<Integer> multiset1, Multiset<Integer> multiset2) {
		return Multisets.difference(multiset1, multiset2);
	}

	/**
	 * Returns true if the player is currently inside the Chambers of Xeric raid.
	 */
	private boolean isInsideChambers() {
		return client.getVarbitValue(IN_RAID_VARBIT) == 1;
	}

	@Subscribe
	public void onStatChanged(StatChanged event) {
		if (event.getSkill() != Skill.FARMING) {
			return;
		}

		// Skip farming tracking entirely inside Chambers of Xeric
		if (isInsideChambers()) {
			return;
		}

		debugChat("Farming XP event: total=" + event.getXp() + ", harvestInProgress=" + farmingHarvestInProgress);

		if (!farmingHarvestInProgress) {
			// No chat trigger fired (e.g., allotments, flowers have no harvest message).
			// Record that farming XP fired on this tick. ItemContainerChanged may fire
			// before or after this event on the same tick, so we check both directions:
			// - If inventory already changed this tick, start tracking now
			// - If not, record the tick and let ItemContainerChanged start tracking
			int currentTick = client.getTickCount();
			lastFarmingXpTick = currentTick;

			if (lastInventoryChangeTick == currentTick && previousReferenceInventorySnapshot != null) {
				// Inventory change already happened this tick (e.g., allotments where
				// ItemContainerChanged fires before StatChanged)
				farmingHarvestInProgress = true;
				farmingPatchType = "farming patch";
				farmingStartedFromXp = true;
				farmingPreHarvestSnapshot = HashMultiset.create(previousReferenceInventorySnapshot);
				debugChat("Farming harvest auto-started from XP (inventory already changed this tick)");
			} else {
				// Inventory change hasn't happened yet this tick (e.g., flowers where
				// StatChanged fires before ItemContainerChanged). Let onItemContainerChanged handle it.
				debugChat("Farming XP recorded on tick " + currentTick + ", awaiting same-tick inventory change");
				return;
			}
		}

		// Each XP drop resets the debounce timer — harvest is still in progress
		synchronized (saveLock) {
			if (farmingDebounceTimer != null && !farmingDebounceTimer.isDone()) {
				farmingDebounceTimer.cancel(false);
				debugChat("Farming debounce timer reset");
			}

			// Detect level-up: if the level increased, use a longer debounce ONCE
			// to bridge the level-up animation pause. Subsequent XP ticks revert to normal.
			long debounceMs = FARMING_DEBOUNCE_TICKS_MS;
			int currentLevel = event.getLevel();
			if (lastKnownFarmingLevel > 0 && currentLevel > lastKnownFarmingLevel) {
				debounceMs = FARMING_LEVELUP_DEBOUNCE_MS;
				debugChat("Farming level-up detected (" + lastKnownFarmingLevel + " -> " + currentLevel + "), using extended debounce");
			}
			lastKnownFarmingLevel = currentLevel;

			farmingDebounceTimer = debounceExecutor.schedule(
					this::completeFarmingHarvest,
					debounceMs,
					TimeUnit.MILLISECONDS
			);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		// Track bird nest searching: player right-clicks nest → "Search"
		if (event.getMenuOption() != null && event.getMenuOption().equals("Search")
				&& BIRD_NEST_IDS.contains(event.getItemId())) {
			// Snapshot inventory before the search so we can diff after
			preLootInventorySnapshot = getPlayerInventorySnapshot();
			awaitingBirdNestDiff = true;
			pendingLootEventName = BIRD_NEST_EVENT;
			debugChat("Bird nest search detected, awaiting inventory diff");
		}
	}

	/**
	 * Called when farming XP stops arriving after a harvest started.
	 * Diffs the inventory and creates a drop record for the harvested produce.
	 */
	private void completeFarmingHarvest() {
		if (!farmingHarvestInProgress) {
			return;
		}
		farmingHarvestInProgress = false;
		farmingStartedFromXp = false;

		debugChat("Farming debounce fired — completing harvest for: " + farmingPatchType);

		// Must read inventory on the client thread
		clientThread.invokeLater(() -> {
			Multiset<Integer> currentInventory = getPlayerInventorySnapshot();
			Multiset<Integer> newItems = compareInventorySnapshot(currentInventory, farmingPreHarvestSnapshot);

			debugChat("Farming inventory diff: " + newItems.entrySet().size() + " distinct items gained");

			if (newItems.isEmpty()) {
				debugChat("Farming harvest completed but no new items detected");
				return;
			}

			// Filter out excluded items (e.g., weeds from raking patches)
			Multiset<Integer> filteredItems = HashMultiset.create();
			for (Multiset.Entry<Integer> entry : newItems.entrySet()) {
				if (!FARMING_EXCLUDED_ITEM_IDS.contains(entry.getElement())) {
					filteredItems.add(entry.getElement(), entry.getCount());
				}
			}

			if (filteredItems.isEmpty()) {
				debugChat("Farming harvest contained only excluded items (weeds) — skipping");
				return;
			}

			String sourceName = WordUtils.capitalize(farmingPatchType);
			lastNpcKilled = sourceName;

			TrackableItemDrop harvestDrop = new TrackableItemDrop(sourceName, 0);

			for (Multiset.Entry<Integer> entry : filteredItems.entrySet()) {
				int itemId = entry.getElement();
				int quantity = entry.getCount();
				if (itemId > -1 && quantity > 0) {
					TrackableDroppedItem droppedItem = buildTrackableItem(itemId, quantity);
					harvestDrop.addLootToDrop(droppedItem);
					debugChat("  Harvested: " + droppedItem.getItemName() + " x" + quantity);
				}
			}

			if (!harvestDrop.getDroppedItems().isEmpty()) {
				debugChat("Farming harvest recorded: " + sourceName + " - " + harvestDrop.getDroppedItems().size() + " item types, value=" + harvestDrop.getTotalDropGeValue());
				processNewDrop(harvestDrop);
			}
		});
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
		synchronized (listViewDropArray) {
			while (listViewDropArray.size() > maxDrops) {
				listViewDropArray.remove(0);
				trimmed = true;
			}
		}

		int maxTrips = config.maxTrips();
		synchronized (trips) {
			while (trips.size() > maxTrips) {
				trips.remove(0);
			}
		}

		// If trimmed, rebuild aggregates from scratch and refresh UI
		if (trimmed) {
			synchronized (npcLootAggregates) {
				npcLootAggregates.clear();
				synchronized (listViewDropArray) {
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

		// Debounced persistence — save at most once every SAVE_DEBOUNCE_MS
		scheduleDebouncedSave();
	}

	private void updateGroupedViewUI() {
		NpcLootAggregate aggregate = getNpcAggregate(lastNpcKilled);
		if (aggregate == null) {
			log.debug("No aggregate found for NPC: {}", lastNpcKilled);
			return;
		}
		ArrayList<LootAggregation> lootAggregation = aggregate.aggregateNpcDrops();
		SwingUtilities.invokeLater(() -> panel.addLootBox(aggregate, lootAggregation));
	}

	private void updateCurrentTripUi() {
		if (getActiveTrip() != null) {
			Trip aTrip = getActiveTrip();
			// Don't update trip UI while paused
			if (aTrip.isPaused()) {
				return;
			}
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

	public List<TrackableItemDrop> getListViewDropArray() {
		synchronized (listViewDropArray) {
			return new ArrayList<>(listViewDropArray);
		}
	}

	public void addDropToTripAggregates(TrackableItemDrop itemDrop) {
		if (getActiveTrip() != null) {
			Trip trip = getActiveTrip();
			// Don't record drops while the trip is paused
			if (trip.isPaused()) {
				return;
			}
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

		synchronized (npcLootAggregates) {
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
		}

		getItemAggregations(npcName);
	}


	public Trip getActiveTrip() {
		synchronized (trips) {
			for (Trip trip : trips) {
				if (trip.getTripStatus()) {
					return trip;
				}
			}
		}
		return null;
	}

	public boolean checkForActiveTrip() {
		synchronized (trips) {
			for (Trip trip : trips) {
				if (trip.getTripStatus()) {
					return true;
				}
			}
		}
		return false;
	}

	public void initTrip(String tripName) {
		if (getActiveTrip() != null) {
			getActiveTrip().setStatus(false);
		}

		trips.add(new Trip(tripName, this));

		// Persist trip state change (debounced)
		scheduleDebouncedTripSave();
	}

	public int getNumberOfTrips() {
		return trips.size();
	}

	/**
	 * Returns the next trip number based on the highest existing trip ID.
	 */
	public int getNextTripNumber() {
		int maxId = 0;
		synchronized (trips) {
			for (Trip trip : trips) {
				maxId = Math.max(maxId, trip.getTripId());
			}
		}
		return Math.max(maxId, trips.size()) + 1;
	}

	public void getItemAggregations(String npcName) {
		if (!log.isDebugEnabled()) {
			return;
		}

		ArrayList<LootAggregation> lootAggregation = null;

		synchronized (npcLootAggregates) {
			for (NpcLootAggregate npcAggregate : npcLootAggregates) {
				if (npcAggregate.getNpcName().equals(npcName)) {
					lootAggregation = npcAggregate.getNpcItemAggregations();
				}
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
		synchronized (npcLootAggregates) {
			for (NpcLootAggregate npcAggregate : npcLootAggregates) {
				if (npcAggregate.getNpcName().equals(npcName)) {
					return npcAggregate;
				}
			}
		}
		return null;
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
				List<TrackableItemDrop> dropsCopy = getListViewDropArray();
				for (TrackableItemDrop itemDrop : dropsCopy) {
					panel.addLootBox(itemDrop);
				}
				break;

			case GROUPED:
				synchronized (npcLootAggregates) {
					for (NpcLootAggregate npcAggregate : npcLootAggregates) {
						String npcName = npcAggregate.getNpcName();
						ArrayList<LootAggregation> npcsLootAggregation = npcAggregate.getNpcItemAggregations();
						if (npcsLootAggregation != null) {
							final NpcLootAggregate aggRef = npcAggregate;
							final ArrayList<LootAggregation> aggList = npcsLootAggregation;
							SwingUtilities.invokeLater(() -> panel.addLootBox(aggRef, aggList));
						}
					}
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

	public List<Trip> getTrips() {
		synchronized (trips) {
			return new ArrayList<>(trips);
		}
	}

	public void removeTrip(String tripName) {
		synchronized (trips) {
			for (int i = 0; i < trips.size(); i++) {
				if (trips.get(i).getTripName().equals(tripName)) {
					int tripId = trips.get(i).getTripId();
					trips.remove(i);
					panel.removeTrip(tripId);
					break;
				}
			}
		}
		// Save immediately — trip deletion is destructive and must not be lost
		List<Trip> tripsCopy;
		synchronized (trips) {
			tripsCopy = new ArrayList<>(trips);
		}
		storageService.saveTripsSync(tripsCopy);
	}

	public void onTripStatusChanged() {
		scheduleDebouncedTripSave();
	}

	public void onDropCollapseChanged() {
		scheduleDebouncedSave();
	}

	public void onGroupedCollapseChanged() {
		storageService.saveCollapsedNpcs(panel.getCollapsedNpcs());
	}

	public ItemManager getItemManager() {
		return itemManager;
	}

	public boolean isSpriteDisplayMode() {
		return config.spriteDisplayMode();
	}

	/**
	 * Sends a debug message to game chat when debug mode is enabled.
	 */
	private void debugChat(String message) {
		if (config.debugMode()) {
			String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.GAMEMESSAGE)
					.runeLiteFormattedMessage("[Trip Tracker " + timestamp + "] " + message)
					.build());
		}
	}

	public void showTripComparison(int preSelectedTripId) {
		SwingUtilities.invokeLater(() -> panel.showComparisonView(preSelectedTripId));
	}

	/**
	 * Clears all persisted and in-memory loot data (drops, trips, aggregates).
	 */
	public void clearAllData() {
		synchronized (listViewDropArray) {
			listViewDropArray.clear();
		}
		synchronized (npcLootAggregates) {
			npcLootAggregates.clear();
		}
		synchronized (trips) {
			trips.clear();
		}

		storageService.saveDrops(new ArrayList<>());
		storageService.saveTrips(new ArrayList<>());
		storageService.saveCollapsedNpcs(new HashSet<>());

		SwingUtilities.invokeLater(() -> panel.rebuildAfterClear());
	}

	/**
	 * Schedules a debounced save for both drops and trips.
	 * If a save is already pending, it is cancelled and rescheduled.
	 */
	private void scheduleDebouncedSave() {
		synchronized (saveLock) {
			if (pendingDropSave != null && !pendingDropSave.isDone()) {
				pendingDropSave.cancel(false);
			}
			if (pendingTripSave != null && !pendingTripSave.isDone()) {
				pendingTripSave.cancel(false);
			}
			pendingDropSave = debounceExecutor.schedule(() -> {
				List<TrackableItemDrop> dropsCopy;
				synchronized (listViewDropArray) {
					dropsCopy = new ArrayList<>(listViewDropArray);
				}
				storageService.saveDrops(dropsCopy);
			}, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);

			pendingTripSave = debounceExecutor.schedule(() -> {
				List<Trip> tripsCopy;
				synchronized (trips) {
					tripsCopy = new ArrayList<>(trips);
				}
				storageService.saveTrips(tripsCopy);
			}, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Schedules a debounced save for trips only.
	 */
	private void scheduleDebouncedTripSave() {
		synchronized (saveLock) {
			if (pendingTripSave != null && !pendingTripSave.isDone()) {
				pendingTripSave.cancel(false);
			}
			pendingTripSave = debounceExecutor.schedule(() -> {
				List<Trip> tripsCopy;
				synchronized (trips) {
					tripsCopy = new ArrayList<>(trips);
				}
				storageService.saveTrips(tripsCopy);
			}, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		}
	}

	// NPC names used as farming sources (capitalized form of farmingPatchType values)
	private static final Set<String> FARMING_SOURCE_NAMES = new HashSet<>(Arrays.asList(
			"Herb Patch", "Cactus Patch", "Farming Patch"
	));

	/**
	 * Strips FARMING_EXCLUDED_ITEM_IDS from drops whose source is a farming patch.
	 * This cleans up legacy persisted data that was recorded before the exclusion filter existed.
	 * Drops from non-farming sources (e.g., Zalcano dropping crystal teleport seeds) are left untouched.
	 */
	private void stripFarmingExcludedItems(TrackableItemDrop drop) {
		String source = drop.getDropNpcName();
		if (source == null) {
			return;
		}
		// Only strip from farming sources — also match tree sources like "Coconut Tree"
		boolean isFarmingSource = FARMING_SOURCE_NAMES.contains(source) || source.endsWith(" Tree");
		if (!isFarmingSource) {
			return;
		}
		drop.getDroppedItems().removeIf(item -> FARMING_EXCLUDED_ITEM_IDS.contains(item.getItemId()));
	}

	/**
	 * Strips FARMING_EXCLUDED_ITEM_IDS from an NPC aggregate if it belongs to a farming source.
	 * Used during trip restoration to clean up legacy persisted data.
	 */
	public void stripFarmingExcludedItemsFromAggregate(NpcLootAggregate aggregate) {
		String source = aggregate.getNpcName();
		if (source == null) {
			return;
		}
		boolean isFarmingSource = FARMING_SOURCE_NAMES.contains(source) || source.endsWith(" Tree");
		if (!isFarmingSource) {
			return;
		}
		aggregate.getDroppedItems().removeIf(item -> FARMING_EXCLUDED_ITEM_IDS.contains(item.getItemId()));
	}
}
