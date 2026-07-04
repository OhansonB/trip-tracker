package com.triptracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("triptracker")
public interface EnhancedLootTrackerConfig extends Config {
	@ConfigItem(
			position = 1,
			keyName = "showLootInChat",
			name = "Show loot in chat",
			description = "Show a message in chat summarising loot dropped from monsters"
	)
	default boolean showLootInChat()
	{
		return true;
	}

	@ConfigItem(
			position = 2,
			keyName = "debugMode",
			name = "Debug mode",
			description = "Show detection events in game chat for troubleshooting"
	)
	default boolean debugMode()
	{
		return false;
	}

	@ConfigItem(
			position = 2,
			keyName = "maxDrops",
			name = "Max drops to keep (10-10000)",
			description = "Maximum number of individual drop events to persist (oldest are removed first)"
	)
	@Range(min = 10, max = 10000)
	default int maxDrops()
	{
		return 5000;
	}

	@ConfigItem(
			position = 3,
			keyName = "maxTrips",
			name = "Max trips to keep (5-200)",
			description = "Maximum number of trips to persist (oldest are removed first)"
	)
	@Range(min = 5, max = 200)
	default int maxTrips()
	{
		return 50;
	}
}
