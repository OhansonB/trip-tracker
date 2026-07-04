# Trip Tracker

A RuneLite plugin that enhances loot tracking with trip scoping, GP/hour stats, and trip comparison. Track your drops by grinding session and see exactly what you earned, how fast, and how it compares to previous runs.

## Features

### Three View Modes

- **List View** — Every drop shown individually with NPC name, combat level, items, and GE values
- **Grouped View** — Loot aggregated by NPC across all kills, sorted by most recent
- **Trip View** — Scope your loot to named trips for per-session tracking

### Trip Management

- Create trips to track loot for a specific grinding session
- Live stats: kills, total GP, GP/hour, and duration (updates in real-time)
- Right-click trips to rename, stop, compare, or delete
- Click trip headers to collapse/expand
- Data persists across client restarts

### Trip Comparison

- Right-click any trip and select "Compare..." to open the comparison view
- Select/deselect multiple trips with checkboxes
- Table shows kills, time, value, GP/hr, and GP/kill side by side
- Export comparison to CSV or JSON (copied to clipboard)

### Loot Sources Tracked

**NPC & Player Kills**
- All NPC kills via RuneLite's NpcLootReceived event
- PvP kills via PlayerLootReceived

**Pickpocketing**
- Detects pickpocket loot via inventory diffing
- Coin pouches shown with estimated GP value per NPC
- Supports all pickpocketable NPCs

**Raid & Boss Rewards**
- Chambers of Xeric (CoX)
- Theatre of Blood (ToB)
- Tombs of Amascut (ToA)
- Barrows chest
- Fortis Colosseum
- Lunar Chest (Moons of Peril)

**Minigames & Events**
- Clue Scroll rewards (all tiers: Beginner through Master)
- Wintertodt supply crates
- Tempoross reward pool
- Guardians of the Rift
- Fishing Trawler
- Drift Net Fishing
- Kingdom of Miscellania

**Skilling & Chests**
- Herbiboar herb harvests
- Bird house dismantling
- Larran's Chest (big and small)
- Generic treasure chests

### Persistence

- All drops and trips saved to `~/.runelite/trip-tracker/`
- Asynchronous writes — no gameplay impact
- Survives client restarts and crashes (saves after every drop)
- Configurable retention limits (default: 5000 drops, 50 trips)

### Configuration

- **Max drops to keep** (10–10,000) — oldest drops pruned automatically
- **Max trips to keep** (5–200) — oldest trips pruned automatically
- **Show loot in chat** — posts a GP summary to game chat on each drop
- **Clear all data** — button in panel footer with confirmation dialog

### UI

- Labeled mode tabs (List, Grouped, Trips)
- Empty state messages when no data exists
- Collapsible drop panels and trip panels
- "Sorted by most recent kill" indicator in grouped view
- Red "Clear all data" button with confirmation
- Values shortened for readability (10k, 1.5m, 2.1b)

## Known Limitations

- Items going directly to the **herb sack** or **seed box** (without touching the ground or inventory) may not be tracked. For accurate tracking during trips, consider closing these containers.
- Coin pouch values are **estimates** based on average GP per pickpocket for each NPC.
- Chest loot tracked via inventory diff may occasionally miss items if other inventory changes happen on the same game tick.

## Installation

Search for **Trip Tracker** in the RuneLite Plugin Hub.

## Building

Requires JDK 11.

```
./gradlew build
```

## Running (development)

```
./gradlew run
```

This launches the full RuneLite client with the plugin loaded.

## Testing

```
./gradlew test
```

58 tests covering loot detection, persistence, trip management, and data formatting.

## Feedback

Found a bug or have a feature request? [Open an issue](https://github.com/OhansonB/trip-tracker/issues).
