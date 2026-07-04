# Chest/Event Loot Tracking — Implementation Plan

## Status: IMPLEMENTED

All phases complete and merged to master.

## Overview

Track loot received from reward chests, minigames, and events — not just NPC kills and pickpockets.

## Implementation Summary

### Widget-based (direct container read via `onWidgetLoaded`)

These sources fire a `WidgetLoaded` event when their reward screen opens. We read items directly from the associated `ItemContainer`.

| Source | Interface ID | Container (InventoryID) | Status |
|---|---|---|---|
| Barrows | 155 | BARROWS_REWARD | ✅ Done |
| Chambers of Xeric | 539 | CHAMBERS_OF_XERIC_CHEST | ✅ Done |
| Theatre of Blood | 23 | THEATRE_OF_BLOOD_CHEST | ✅ Done |
| Tombs of Amascut | 771 | TOA_REWARD_CHEST | ✅ Done |
| Fishing Trawler | 367 | FISHING_TRAWLER_REWARD | ✅ Done |
| Drift Net | 607 | DRIFT_NET_FISHING_REWARD | ✅ Done |
| Lunar Chest | 868 | LUNAR_CHEST | ✅ Done |
| Fortis Colosseum | 864 | FORTIS_COLOSSEUM_REWARD_CHEST | ✅ Done |
| Kingdom of Miscellania | 616 | KINGDOM_OF_MISCELLANIA | ✅ Done |

Duplicate prevention: Raids and Colosseum use a `chestLooted` flag that resets on scene load (`GameStateChanged` → `LOADING`).

### Chat + container read

| Source | Chat Pattern | Container | Status |
|---|---|---|---|
| Clue Scrolls | "You have completed X (type) Treasure Trails." | BARROWS_REWARD (shared) | ✅ Done |

### Chat + inventory diff

These sources are detected via a chat message, then an inventory diff captures what items were gained.

| Source | Chat Trigger | Status |
|---|---|---|
| Wintertodt | "You found some loot: " (region 6461) | ✅ Done |
| Tempoross | "You found some loot: " (region 12588) | ✅ Done |
| Guardians of the Rift | "You found some loot: " (region 14484) | ✅ Done |
| Herbiboar | "You harvest herbs from the herbiboar..." | ✅ Done |
| Bird Houses | "You dismantle and discard the trap..." | ✅ Done |
| Larran's Chest | "You have opened Larran's (big|small) chest" | ✅ Done |
| Generic chests | "You find some treasure in the chest!" | ✅ Done |
| Generic chests | "You steal some loot from the chest." | ✅ Done |

### Not tracked (by design)

| Source | Reason |
|---|---|
| Farming herbs | Skilling output, not loot. No detection event. |
| Herb sack bypass | Items go directly to container without ground/inventory. |
| Seed box bypass | Same as herb sack. |
| Fishing catches | Skilling output. |
| Ore mined | Skilling output. |

## Technical Reference

### Key source: RuneLite's LootTrackerPlugin.java

Located at: `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`

Key findings from their source:
- Uses `client.getItemContainer(InventoryID.X.getId())` to read reward containers
- Clue scrolls share the same container as Barrows (`TRAIL_REWARDINV` / `BARROWS_REWARD`)
- Raids use a `chestLooted` boolean to prevent duplicate tracking on re-opens
- Chat-triggered loot uses inventory diffing via `onInvChange()` callbacks
- `InterfaceID` constants are in `net.runelite.api.gameval.InterfaceID` (non-deprecated) or `net.runelite.api.widgets.InterfaceID` (deprecated, same values)

### Container IDs

We use the deprecated `net.runelite.api.InventoryID` enum which provides `getId()` for the raw numeric values. This produces deprecation warnings but is functionally correct — the `gameval` package uses the same underlying IDs.

## Future Considerations

- **Wilderness Loot Chest** — Uses multiple PVP loot key containers. More complex than a single container read. Could be added later.
- **Shade Chests** — Triggered by menu click on specific objects. Would need `MenuOptionClicked` subscription.
- **Seed Pack / Bird Nests** — Triggered by opening items. Would need `MenuOptionClicked` with item ID checks.
- **Soul Wars Spoils** — Opened item, inventory diff.
- **Caskets** — Opened item, inventory diff.

These are lower-priority sources that could be added incrementally via the same patterns already established.
