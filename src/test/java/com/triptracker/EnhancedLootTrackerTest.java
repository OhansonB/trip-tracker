package com.triptracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

import java.util.Arrays;
import java.util.stream.Stream;

public class EnhancedLootTrackerTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(EnhancedLootTrackerPlugin.class);

		// Launch the from-source dev client with RuneLite's developer mode enabled so
		// Dev Tools (Var Inspector, region overlays, etc.) is available for debugging.
		// Only inject the flag if the caller hasn't already supplied it.
		final String[] launchArgs =
				Arrays.stream(args).anyMatch("--developer-mode"::equals)
						? args
						: Stream.concat(Arrays.stream(args), Stream.of("--developer-mode"))
								.toArray(String[]::new);

		RuneLite.main(launchArgs);
	}
}