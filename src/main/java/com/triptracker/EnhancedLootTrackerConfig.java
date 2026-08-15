package com.triptracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("triptracker")
public interface EnhancedLootTrackerConfig extends Config {
	@ConfigSection(
			name = "Keyboard Shortcuts",
			description = "Keyboard shortcuts available in the Trip Tracker panel",
			position = 0,
			closedByDefault = true
	)
	String keyboardShortcutsSection = "keyboardShortcuts";

	@ConfigItem(
			position = 0,
			keyName = "shortcutsInfo",
			name = "Available shortcuts",
			description = "<html>"
					+ "<b>Tab</b> — Move focus between panels and buttons<br>"
					+ "<b>Enter / Space</b> — Toggle collapse on focused trip or loot panel<br>"
					+ "<b>Shift+F10</b> — Open context menu on focused trip header<br>"
					+ "<b>Enter / Space</b> — Activate focused button (Add Trip, Clear All Data)"
					+ "</html>",
			section = keyboardShortcutsSection
	)
	default String shortcutsInfo()
	{
		return "Tab, Enter/Space, Shift+F10";
	}

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
			position = 3,
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
			position = 4,
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
