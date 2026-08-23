package com.triptracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("triptracker")
public interface EnhancedLootTrackerConfig extends Config {

	// --- Sections ---

	@ConfigSection(
			name = "Display",
			description = "How loot is displayed in the panel",
			position = 0
	)
	String displaySection = "display";

	@ConfigSection(
			name = "Persistence",
			description = "How many drops and trips are saved to disk",
			position = 1
	)
	String persistenceSection = "persistence";

	@ConfigSection(
			name = "Debug",
			description = "Developer and troubleshooting options",
			position = 2,
			closedByDefault = true
	)
	String debugSection = "debug";

	@ConfigSection(
			name = "Exclusions",
			description = "Hide specific items or NPCs from all views (data is still tracked)",
			position = 3,
			closedByDefault = true
	)
	String exclusionsSection = "exclusions";

	// --- Display ---

	@ConfigItem(
			position = 0,
			keyName = "showLootInChat",
			name = "Show loot in chat",
			description = "Show a message in chat summarising loot dropped from monsters",
			section = displaySection
	)
	default boolean showLootInChat()
	{
		return true;
	}

	@ConfigItem(
			position = 1,
			keyName = "spriteDisplayMode",
			name = "Show items as sprites",
			description = "Display items as sprite icons with quantity overlays instead of text list",
			section = displaySection
	)
	default boolean spriteDisplayMode()
	{
		return false;
	}

	// --- Persistence ---

	@ConfigItem(
			position = 0,
			keyName = "maxDrops",
			name = "Max drops to keep",
			description = "Maximum number of individual drop events to persist. Oldest are removed first. Range: 10–10,000.",
			section = persistenceSection
	)
	@Range(min = 10, max = 10000)
	default int maxDrops()
	{
		return 5000;
	}

	@ConfigItem(
			position = 1,
			keyName = "maxTrips",
			name = "Max trips to keep",
			description = "Maximum number of trips to persist. Oldest are removed first. Range: 5–200.",
			section = persistenceSection
	)
	@Range(min = 5, max = 200)
	default int maxTrips()
	{
		return 50;
	}

	@ConfigItem(
			position = 2,
			keyName = "tripInactivityTimeout",
			name = "Trip inactivity timeout (minutes)",
			description = "Minutes of inactivity (logged out / client closed) before an active trip is auto-stopped. 0 = stop immediately on logout. Paused trips are exempt. Range: 0–14,400 (10 days).",
			section = persistenceSection
	)
	@Range(min = 0, max = 14400)
	default int tripInactivityTimeout()
	{
		return 180;
	}

	// --- Debug ---

	@ConfigItem(
			position = 0,
			keyName = "debugMode",
			name = "Debug mode",
			description = "Prints timestamped detection events (loot diffs, farming harvests, inventory snapshots) to game chat for troubleshooting",
			section = debugSection
	)
	default boolean debugMode()
	{
		return false;
	}

	// --- Exclusions ---

	@ConfigItem(
			position = 0,
			keyName = "excludedItems",
			name = "Excluded items",
			description = "Comma-separated list of item names to hide from all views (e.g. Bones, Ashes). Case-insensitive.",
			section = exclusionsSection
	)
	default String excludedItems()
	{
		return "";
	}

	@ConfigItem(
			position = 1,
			keyName = "excludedNpcs",
			name = "Excluded NPCs",
			description = "Comma-separated list of NPC names to hide from all views (e.g. Man, Woman). Case-insensitive.",
			section = exclusionsSection
	)
	default String excludedNpcs()
	{
		return "";
	}
}
