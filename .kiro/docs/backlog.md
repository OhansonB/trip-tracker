# Trip Tracker — Feature Backlog

## To Do

### 1. ~~Item sprite display mode~~ ✅ Done
Configurable toggle added to display items as sprite icons in a 5-column grid with quantity overlays (using `ItemManager.getImage`). Falls back to text list when disabled. Panel rebuilds live when the setting is toggled.

### 2. ~~Schema version for persisted data~~ ✅ Done
Added versioned file naming (`drops.v1.json`, `trips.v1.json`). Load scans from highest version down to legacy bare arrays. Old files preserved as backups. Steering file updated with migration checklist.

### 3. Trip persistence across client close
Do not automatically deactivate trips when the client shuts down. A trip may span a relog (e.g., DKs, slayer tasks). Instead:
- Keep trips active across restarts
- Add a configurable max inactive time (e.g., 30 minutes). If the client restarts and more than X minutes have passed since the last drop in the trip, auto-stop it.
- This prevents zombie trips that stay "active" for days after a player forgets about them.

### 4. NPC filter in list and grouped view
Search/filter box at the top of list view and grouped view. Typing filters drops to only show those matching the NPC name. Useful when the list is long and you want to find a specific monster.

### 5. Trip filter in trip view
Similar to #4 but for trips. Search by trip name to find specific trips when you have many.

### 6. Configurable item highlighting (text mode)
When in text-list mode, allow users to configure a GP threshold (e.g., 50k+). Items exceeding that value are highlighted in a different color (e.g., green or gold) to make valuable drops stand out at a glance.

### 7. Damage/prayer stats per trip
Track damage dealt, damage received, and prayer points used/restored during a trip. Would require subscribing to:
- `HitsplatApplied` event for damage dealt/received
- `StatChanged` event for prayer point changes
Display as additional trip stats alongside kills, value, GP/hour.

### 8. Detailed trip stats page
A drill-down view for each trip showing:
- Full breakdown by NPC (kills, value, GP/kill per NPC)
- Most/least valuable drops
- Drops per hour
- Item frequency table
- Timeline of kills (when during the trip each kill happened)
Accessed via right-click → "Details" on a trip header.


### 9. ~~Aggregate multiple drops of the same item in grouped/trip view~~ ✅ Done

Fixed by normalizing noted item IDs to unnoted form in inventory snapshots, and merging bird nest variants by display name.

### 10. Exclude certain items / monsters from drops and trips
**Behaviour:** This is a visibility filter, not a hard exclude. All data is still tracked and persisted, but hidden items/monsters are filtered from the UI.

- **Item exclusion:** Global "never show this item" list (e.g., ashes, bones). Configured via a text list in plugin settings. Items matching the list are hidden from all views but still saved to disk.
- **Monster exclusion:** Global "never show drops from this NPC" list. Drops from excluded NPCs are hidden from all views but still persisted.
- **Reversible:** If a user removes an item/monster from the exclusion list, previously hidden data reappears in the UI immediately (no data loss).
- **Show hidden toggle:** A global "Show hidden" button in the panel that temporarily reveals all excluded items/monsters, allowing users to see the full picture when needed.

### 11. In Trip view, order within trip by value of NPC rather than by last killed
Within a trip, NPC loot boxes should be sorted by total GP value contributed (highest value source at top), not by recency of kill. This makes it easy to see which source was most profitable during the trip at a glance.

### 12. Export pretty print for e.g., Discord
Extension of the existing trip comparison export (CSV/JSON). Adds a plain text export format suitable for pasting into Discord, Notepad, etc.

- **Scope:** Trip view only — exports the selected trips from the comparison view.
- **Format:** Plain text, monospace-friendly. No markdown or embed formatting needed.
- **Content per trip:** Trip name, kills, duration (formatted as e.g., "1h 30m 29s"), total value, GP/hr, GP/kill. No start/end timestamps.
- **Copied to clipboard** like the existing CSV/JSON exports.

### 13. Character-specific tracking
Separate data files per character so that e.g., an ironman and a main account don't contaminate each other's tracking history.

- **Storage:** Each character gets its own `drops.json` and `trips.json` in a character-specific subdirectory (e.g., `~/.runelite/trip-tracker/<character-id>/`).
- **Identification:** Ideally use an underlying account UUID if one is accessible via the RuneLite API or Jagex launcher integration. If no stable ID exists, fall back to player display name but handle name changes gracefully (e.g., store a mapping of name → ID, or prompt the user to merge when a name change is detected).
- **Switching:** On login, automatically load the correct character's data. No manual switching required.
- **Research needed:** Investigate whether `client.getAccountHash()` or similar provides a stable per-character identifier that survives name changes.

### 14. ~~Exclude Clockwork from drop when collecting bird boxes~~ ✅ Done

### 15. ~~Farming: level-up mid-harvest causes partial tracking~~ ⚠️ Fix applied, pending verification
Fixed by detecting farming level-ups during an active harvest and extending the debounce timer to 10 seconds to bridge the level-up animation pause. However, the root cause hasn't been conclusively confirmed — it may be a debounce timeout issue or a snapshot overwrite. Debug timestamps will clarify on next level-up.

### 16. ~~Disable farming tracking inside Chambers of Xeric (CoX)~~ ✅ Done
Farming tracking is skipped entirely when `client.getVarbitValue(5432) == 1` (player is inside CoX). Both chat triggers and XP fallback are gated.

### 17. ~~Exclude Clockwork from all bird house drops, not just trip view.~~ ✅ Done
Verified — clockwork is excluded at drop creation time in the inventory diff path, which feeds all views.

### 18. ~~When farming, exclude all item additions derived from snapshot except the herb in question.~~ ✅ Done
Added all Crystal Teleport Seed charge variants (IDs 6099-6103, 23959, 23968) to `FARMING_EXCLUDED_ITEM_IDS` so using a teleport crystal during the debounce window no longer pollutes the harvest diff.

### 19. Add collapse and expand all button on every view (list, group, trip)

### 20. ~~Add persistence to collapsed and expanded items~~ ✅ Done
Collapse state is now persisted for all views: trips via `trips.json`, list drops via `drops.json`, grouped NPCs via `collapsed-npcs.json`.

---

## Farming Tracking — Manual Test Plan

### Prerequisites
- Debug mode enabled in plugin config
- Clear all data before starting a fresh test run
- Note your inventory contents before each test

### Test 1: Herb Patch (Path A — chat trigger)
**Steps:**
1. Go to a herb patch with a fully grown herb
2. Click to harvest

**Expected:**
- Debug shows "Farming harvest started: herb patch"
- Debug shows "Farming debounce timer reset" for each subsequent XP tick
- Debug shows "Farming debounce fired — completing harvest for: herb patch"
- Correct herb name and quantity in list view
- Source named "Herb Patch"

**Variations:**
- [ ] First herb patch after fresh login
- [ ] Second herb patch after teleporting
- [ ] Harvest with full inventory (herbs noted at leprechaun mid-pick)
- [ ] Two herb patches in one session — grouped view shows single "Herb Patch" entry with merged quantities

### Test 2: Flower Patch (Path B — XP fallback, single-tick)
**Steps:**
1. Go to a flower patch with fully grown limpwurts
2. Click to harvest

**Expected:**
- Debug shows "Farming XP recorded on tick X, awaiting same-tick inventory change"
- Debug shows "Farming harvest started from inventory change (XP was on same tick)"
- Correct item name and quantity in list view
- Source named "Farming Patch"

**Variations:**
- [ ] First action after fresh login (no prior inventory changes)
- [ ] After teleporting to the patch location
- [ ] After multiple teleports in sequence
- [ ] With nearly full inventory (some items drop to floor — known limitation: only tracks what enters inventory)

### Test 3: Allotment Patch (Path B — XP fallback, multi-tick)
**Steps:**
1. Go to an allotment patch with fully grown crops (e.g., snape grass)
2. Click to harvest

**Expected:**
- Debug shows "Farming XP recorded on tick X" or "Farming harvest auto-started from XP (inventory already changed this tick)"
- Debounce timer resets on each subsequent XP tick
- Final diff shows correct crop and quantity
- Source named "Farming Patch"

**Variations:**
- [ ] Full harvest without interruption
- [ ] Harvest interrupted mid-way (walk away) — partial harvest should be recorded
- [ ] Two allotment patches in one session — grouped view merges quantities

### Test 4: Cactus Patch (Path A — specific chat message)
**Steps:**
1. Go to a cactus patch with spines available
2. Click to pick

**Expected:**
- Debug shows "Farming harvest started: cactus patch"
- Debounce timer resets on each spine picked
- Final diff shows "Cactus spine" with correct quantity
- Source named "Cactus Patch"

### Test 5: Fruit Tree — Coconut (Path A — pick pattern)
**Steps:**
1. Go to a palm tree with coconuts available
2. Click to pick

**Expected:**
- Debug shows "Farming pick started: coconut tree (picked: coconut)"
- Debounce timer resets on each coconut picked
- Final diff shows "Coconut" with correct quantity (up to 6)
- Source named "Coconut Tree"

**Variations:**
- [ ] After teleporting to the patch
- [ ] Two palm trees in one session — grouped view merges

### Test 6: Weeding (should NOT track)
**Steps:**
1. Go to a weedy farming patch
2. Rake the weeds

**Expected:**
- Farming XP fires, but weeds are filtered out
- Debug shows "Farming harvest contained only excluded items (weeds) — skipping"
- No drop recorded

### Test 7: Clearing dead patch (should NOT track)
**Steps:**
1. Go to a dead farming patch
2. Clear it with spade

**Expected:**
- Farming XP fires, but no inventory change on same tick (no items received)
- Debug shows "Farming XP recorded on tick X, awaiting same-tick inventory change"
- No drop recorded (ItemContainerChanged won't fire for clearing)

### Test 8: Planting seeds (should NOT track)
**Steps:**
1. Plant a seed in an empty patch

**Expected:**
- Farming XP fires (planting XP), but item was removed from inventory, not added
- Diff would show negative or zero — no drop recorded

### Test 9: Noting at leprechaun (should not cause duplicates)
**Steps:**
1. Harvest herbs from a patch
2. Use herbs on tool leprechaun to note them mid-harvest

**Expected:**
- Final diff uses normalized IDs (noted → unnoted)
- Single entry in grouped view, not duplicated noted + unnoted
- Quantity matches total herbs picked

### Test 10: Grouped view aggregation
**Steps:**
1. Harvest from multiple herb patches in one session
2. Switch to grouped view

**Expected:**
- Single "Herb Patch" entry with combined kill count
- Single line per herb type with total quantity
- No duplicate item lines (e.g., "Grimy torstol x11" appearing twice)
- Total value matches sum of all harvests

### Test 11: Trip view
**Steps:**
1. Start a trip before beginning a farm run
2. Harvest multiple patches of different types
3. Check trip view

**Expected:**
- Trip shows all farming sources (Herb Patch, Farming Patch, Coconut Tree, etc.)
- Kill count and value accumulate correctly
- GP/hour calculates based on trip duration

### Known Limitations
- Flower patch: if inventory is full, items that drop to the floor are not tracked (inventory diff limitation)
- Level-up mid-harvest may cause partial tracking (backlog item #15)
- First flower/allotment harvest after a real logout may be missed if it's the very first `ItemContainerChanged` event
- "Farming Patch" is a generic name for allotments/flowers — we don't distinguish which crop type without a chat message

## Priority Suggestion

| # | Feature | Impact | Effort | Suggested Order | Status |
|---|---------|--------|--------|-----------------|--------|
| 16 | Disable farming in CoX | Bug fix | Low | 1st — prevents bad data being tracked now | ✅ Done |
| 15 | Level-up mid-harvest fix | Bug fix | Low-Med | 2nd — known accuracy issue | ✅ Done |n
| 13 | Character-specific tracking | Data integrity | Medium | 4th — prevents cross-account contamination | |
| 3 | Trip persistence | QoL | Medium | 5th — common pain point | |
| 11 | Trip sort by value | QoL | Low | 6th — quick win, better readability | |
| 6 | Item highlighting | Visual | Low | 7th — quick win | |
| 4 | NPC filter | QoL | Low | 8th — quick win | |
| 5 | Trip filter | QoL | Low | 9th — quick win | |
| 12 | Discord export | QoL | Low | 10th — quick win, extends existing export | |
| 10 | Item/monster exclusion | QoL | Medium | 11th — UI + config work | |
| 1 | Item sprites | Visual | High | 12th — big UI uplift | |
| 8 | Trip stats page | Feature | Medium | 13th — nice to have | |
| 7 | Damage/prayer | Feature | High | Last — new data model, new events | |
