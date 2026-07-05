# Trip Tracker — Feature Backlog

## To Do

### 1. Item sprite display mode
Configurable option to display items as sprite icons overlaid with quantity (like RuneLite's native Loot Tracker) rather than the current text list. Uses `ItemManager.getImage(itemId, quantity, stackable)` to render item sprites in a grid. Should be a toggle in config so users can switch between text and sprite mode.

### 2. Schema version for persisted data
Add a `"version": N` field to the root of `drops.json` and `trips.json`. Load logic should check version and run migration functions when the format changes. See `.kiro/steering/data-persistence.md` for the rules.

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

### 11. In Trip view, order within trip by value of NPC rather than by last killed

### 12. Export pretty print for e.g., Discord

### 13. Character-specific tracking

### 14. ~~Exclude Clockwork from drop when collecting bird boxes~~ ✅ Done

### 15. Farming: level-up mid-harvest causes partial tracking
When a player levels up mid-harvest (e.g., during herb picking), the level-up event likely triggers an `ItemContainerChanged` that disrupts the snapshot chain. Only herbs received after the level-up are tracked; those before it are lost. Needs investigation into what events fire during a level-up and how they interact with the farming debounce/snapshot logic.

### 16. Exclude herbs picked while in Chamber of Xeric

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

| # | Feature | Impact | Effort | Suggested Order |
|---|---------|--------|--------|-----------------|
| 2 | Schema version | Safety | Low | First (before any model changes) |
| 3 | Trip persistence | QoL | Medium | Second (common pain point) |
| 1 | Item sprites | Visual | High | Third (big UI uplift) |
| 6 | Item highlighting | Visual | Low | Fourth (quick win) |
| 4 | NPC filter | QoL | Low | Fifth (quick win) |
| 5 | Trip filter | QoL | Low | Sixth (quick win) |
| 8 | Trip stats page | Feature | Medium | Seventh |
| 7 | Damage/prayer | Feature | High | Last (new data model, new events) |
