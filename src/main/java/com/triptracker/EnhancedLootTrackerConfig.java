package com.triptracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("example")
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
}
