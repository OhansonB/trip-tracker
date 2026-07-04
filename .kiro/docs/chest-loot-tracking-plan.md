# Chest/Event Loot Tracking — Implementation Plan

## Overview

Add tracking for loot received from reward chests, minigames, and events — not just NPC kills and pickpockets. This covers Barrows, Raids (CoX, ToB, ToA), Clue Scrolls, and several minigames.

## Current State

The plugin currently tracks loot from:
- NPC kills (`NpcLootReceived` event)
- Player kills (`PlayerLootReceived` event)
- Pickpocketing (chat message + inventory diff)

## Target Sources

| Source | Interface ID | Display Name | Testable? |
|---|---|---|---|
| Barrows | 155 | Barrows | Medium (requires quest progress) |
| Chambers of Xeric (CoX) | 539 | Chambers of Xeric | Hard (requires team/gear) |
| Theatre of Blood (ToB) | 23 | Theatre of Blood | Hard |
| Tombs of Amascut (ToA) | 771 | Tombs of Amascut | Hard |
| Clue Scroll Reward | 73 | Clue Scroll | Easy (any clue) |
| Fortis Colosseum | 864 | Fortis Colosseum | Hard |
| Lunar Chest (Moons of Peril) | 868 | Lunar Chest | Hard |
| Drift Net Fishing | 607 | Drift Net Fishing | Medium |
| Fishing Trawler | 367 | Fishing Trawler | Medium |
| Wilderness Loot Chest | 742 | Wilderness Loot Chest | Medium (requires wildy PvP) |

## Technical Approach

### Option A: WidgetLoaded + Widget Item Reading

**How it works:**
1. Subscribe to `WidgetLoaded` event
2. When a reward interface opens (e.g., groupId == 155 for Barrows), read the item widgets from the container child widget
3. Each reward interface has a specific child widget that contains the item grid (this varies per interface)
4. Extract item IDs and quantities from widget children

**Pros:** Direct access to reward items as shown to the player
**Cons:** Need to know the exact child widget index for each interface; fragile if Jagex changes widget layouts

### Option B: Inventory Diff on Widget Open

**How it works:**
1. Subscribe to `WidgetLoaded` for reward interfaces
2. When a reward widget opens, take a snapshot of the player's inventory before/after
3. Use the diff to determine what items were received

**Pros:** Works regardless of widget layout changes; same pattern as pickpocket detection
**Cons:** Won't catch items that overflow inventory (dropped on ground); timing issues if other inventory changes happen simultaneously

### Option C: ItemContainerChanged with Known Container IDs

**How it works:**
1. Subscribe to `ItemContainerChanged`
2. When a known reward container ID changes, read the full container contents
3. Map container IDs to source names

**Pros:** Most reliable; exact items as the game server knows them
**Cons:** Need to discover the exact container ID for each reward source; these aren't documented in the public API

### Recommended: Hybrid (Option A primary, Option B fallback)

Use Option A (widget item reading) as the primary approach. For each reward source, we need to identify:
1. The interface group ID (known — see table above)
2. The child widget index that holds the item container within that interface (needs research)

If widget reading fails for a source, fall back to Option B (inventory diff).

## Implementation Steps

### Phase 1: Infrastructure
- [ ] Create `RewardSource` enum with interface IDs and display names
- [ ] Create `RewardLootHandler` class that subscribes to `WidgetLoaded`
- [ ] Add a method to read items from a reward widget's item container child
- [ ] Wire `RewardLootHandler` into the plugin to call `processNewDrop()`

### Phase 2: First Source (Clue Scrolls)
- [ ] Implement clue scroll reward reading (interface 73)
- [ ] Clue rewards use a well-known widget structure — good starting point
- [ ] Test in-game with any clue scroll
- [ ] Verify items appear in list view, grouped view, and trips

### Phase 3: Barrows
- [ ] Implement Barrows reward reading (interface 155)
- [ ] Barrows rewards are shown in a grid in the reward widget
- [ ] Test by completing a Barrows run

### Phase 4: Raids
- [ ] Chambers of Xeric (interface 539)
- [ ] Theatre of Blood (interface 23)
- [ ] Tombs of Amascut (interface 771)
- [ ] These are harder to test; may need community testers

### Phase 5: Remaining Sources
- [ ] Fortis Colosseum (interface 864)
- [ ] Lunar Chest (interface 868)
- [ ] Drift Net Fishing (interface 607)
- [ ] Fishing Trawler (interface 367)
- [ ] Wilderness Loot Chest (interface 742)

## Research Needed

### Findings from RuneLite's LootTrackerPlugin Source

The `onWidgetLoaded` handler in RuneLite's loot tracker uses **`ItemContainer` reading** (Option C from our plan), not widget child parsing. Each reward source maps to a specific `InventoryID` constant.

Here's the exact mapping from their source:

| Source | Interface ID | InventoryID Constant | Display Name |
|---|---|---|---|
| Barrows | `InterfaceID.BARROWS_REWARD` | `InventoryID.TRAIL_REWARDINV` | "Barrows" |
| Chambers of Xeric | `InterfaceID.RAIDS_REWARDS` | `InventoryID.RAIDS_REWARDS` | "Chambers of Xeric" |
| Theatre of Blood | `InterfaceID.TOB_CHESTS` | `InventoryID.TOB_CHESTS` | "Theatre of Blood" |
| Tombs of Amascut | `InterfaceID.TOA_CHESTS` | `InventoryID.TOA_CHESTS` | "Tombs of Amascut" |
| Kingdom of Miscellania | `InterfaceID.MISC_COLLECTION` | `InventoryID.MISC_RESOURCES_COLLECTED` | "Kingdom of Miscellania" |
| Fishing Trawler | `InterfaceID.TRAWLER_REWARD` | `InventoryID.TRAWLER_REWARDINV` | "Fishing Trawler" |
| Drift Net Fishing | `InterfaceID.FOSSIL_DRIFTNET` | `InventoryID.MACRO_CERTER` | "Drift Net" |
| Lunar Chest | `InterfaceID.PMOON_REWARD` | `InventoryID.PMOON_REWARDINV` | "Lunar Chest" |
| Fortis Colosseum | `InterfaceID.COLOSSEUM_REWARD_CHEST_2` | `InventoryID.COLOSSEUM_REWARDS` | "Fortis Colosseum" |
| Wilderness Loot Chest | `InterfaceID.WILDY_LOOT_CHEST` | Multiple `DEADMAN_LOOT_INV` containers | "Loot Chest" |

**Important notes from the source:**

1. **Clue Scrolls are NOT in `onWidgetLoaded`** — they're detected via a **chat message pattern** (`"You have completed X (type) Treasure Trails."`) and then read from `InventoryID.TRAIL_REWARDINV` (same container as Barrows).

2. **Duplicate prevention** — Raids (CoX, ToB, ToA) use a `chestLooted` boolean flag that prevents re-reading when the player opens the chest multiple times. The flag resets on scene load.

3. **Region checks** — ToB checks `inTobChestRegion()` to ensure the widget is actually a ToB reward and not something else.

4. **Chat-based loot** — Many sources (chests opened with keys, Wintertodt, Tempoross, Guardians of the Rift, herbiboar, seed packs, etc.) use an **inventory diff** approach triggered by specific chat messages. They call `onInvChange()` which snapshots the inventory and diffs it on the next `ItemContainerChanged`.

5. **The `InterfaceID` they import is `net.runelite.api.gameval.InterfaceID`** — this is the non-deprecated version (different from the deprecated `net.runelite.api.widgets.InterfaceID` we found earlier). The constants may have different names but should have the same numeric values.

6. **`InventoryID` they use is also `net.runelite.api.gameval.InventoryID`** — the non-deprecated replacement. We need the raw numeric values of these constants to use in our plugin (since the deprecated enum we previously used won't have all of them).

### Approach Revision

Based on this research, the recommended approach is:

**For reward chests (Barrows, Raids, etc.):**
- Subscribe to `WidgetLoaded`
- Check `groupId` against known interface IDs
- Read items from `client.getItemContainer(containerID)`
- Use a `chestLooted` flag for raids to prevent duplicates

**For chat-triggered loot (Clue Scrolls, chests, minigames):**
- Subscribe to `ChatMessage`
- Match against known patterns (clue completion, chest opened, etc.)
- Take an inventory snapshot, then diff on next `ItemContainerChanged`

**The biggest challenge:** We need the raw numeric values of `InventoryID.TRAIL_REWARDINV`, `InventoryID.RAIDS_REWARDS`, etc. These are in the `gameval` package which is only available at runtime (not in the API javadocs). We'll need to either:
- Look up the values from the RuneLite source/constants page
- Use the deprecated `InventoryID` enum values where available
- Or reference them by the deprecated constant values we already know

## Risk Mitigation

- **Widget layout changes:** Jagex occasionally updates interfaces. Version the widget indices and document them clearly so they can be updated easily.
- **Duplicate tracking:** If the Loot Tracker plugin is also enabled, both plugins will process the same loot. This is fine — they maintain separate state.
- **Re-opening reward interface:** Some reward chests (CoX) can be opened multiple times. We need to track whether we've already recorded loot for a given instance (e.g., use a flag that resets when the widget closes).
- **Testing without access:** For raid content, we may need to rely on the RuneLite source code as ground truth and community feedback for validation.

## Config

Add a config option:
```java
@ConfigItem(
    position = 5,
    keyName = "trackChestLoot",
    name = "Track chest/event loot",
    description = "Track loot from Barrows, Raids, Clue Scrolls, and other reward chests"
)
default boolean trackChestLoot() {
    return true;
}
```

## Display

Chest loot appears in the plugin the same way as NPC kills:
- **List view:** "Barrows (lvl 0)" with the items listed below
- **Grouped view:** "Barrows x5" aggregated across multiple runs
- **Trips:** Included in the active trip's loot and value

The `npcCombatLevel` field will be 0 for event-based loot since there's no specific NPC. The "NPC name" becomes the event name (e.g., "Barrows", "Clue Scroll (Hard)").

## Next Steps

1. Examine RuneLite's LootTrackerPlugin source on GitHub for exact widget/container handling
2. Start with Phase 1 + Phase 2 (infrastructure + clue scrolls)
3. Test clue scroll reward tracking in-game
4. Iterate through remaining sources
