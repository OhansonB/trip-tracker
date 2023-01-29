package com.triptracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class EnhancedLootTrackerTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(EnhancedLootTrackerPlugin.class);
		RuneLite.main(args);
	}
}