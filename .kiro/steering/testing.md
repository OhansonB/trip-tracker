# Testing Standards

## Before Every Commit

1. **Review existing tests** — check if any existing tests are broken by the changes. Fix them before committing.
2. **Add new tests** — if new behaviour has been introduced, write tests that cover it. Match the patterns in existing test files (JUnit 4, Mockito, reflection for private fields).
3. **Run the full suite** — execute `./gradlew test` and confirm all tests pass before committing. Do not commit with failing tests.

## Test Structure

- Tests live in `src/test/java/com/triptracker/`
- Use reflection (`setField`, `getField`) to access plugin internals since RuneLite DI is not available in unit tests
- Mock RuneLite dependencies: `Client`, `ItemManager`, `ItemComposition`, `ClientThread`, etc.
- For `ClientThread.invokeLater()`, use a mock that executes the Runnable immediately
- For debounce/scheduled tasks, use a real `ScheduledExecutorService` in tests

## What to Test

- **Event handlers** — verify that game events (chat messages, XP changes, inventory changes) trigger the correct internal state changes
- **Data flow** — verify that detected loot creates correct `TrackableItemDrop` entries with accurate items, quantities, and source names
- **Exclusions/filters** — verify that excluded items (weeds, clockwork) are not present in recorded drops
- **Edge cases** — fresh login state, teleporting, repeated events, empty diffs
- **Aggregation** — verify that grouped/trip views merge items correctly (noted → unnoted normalization, bird nest merging)

## What NOT to Test in Unit Tests

- Swing UI rendering (panel layout, button clicks)
- File I/O (persistence is mocked via `TripStorageService`)
- Network calls or RuneLite API internals
