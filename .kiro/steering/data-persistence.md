---
inclusion: fileMatch
fileMatchPattern: "**/DropRecord.java,**/TripRecord.java,**/TripStorageService.java,**/Trip.java,**/TrackableItemDrop.java,**/TrackableDroppedItem.java,**/NpcLootAggregate.java"
---

# Data Persistence Rules

These rules apply when modifying any files that affect the persisted data format in `~/.runelite/trip-tracker/`.

## Critical: No Breaking Changes

Users have persisted data that must remain loadable after plugin updates. Breaking their data is unacceptable.

## Schema Versioning Checklist

**Before making any change to a Record class or the data model, ask:**

1. Does this change alter the shape of the JSON that gets written to disk?
   - Adding a new field → **safe** (Gson defaults missing fields to null/0/false). No version bump needed.
   - Renaming a field → **breaking**. Requires version bump + migration.
   - Changing a field's type → **breaking**. Requires version bump + migration.
   - Removing a field → **safe for deserialization** (Gson ignores extras), but don't remove from the class.
   - Restructuring nested objects → **breaking**. Requires version bump + migration.

2. If a version bump is needed:
   - Increment `CURRENT_VERSION` in `TripStorageService`
   - Write a `migrateDropsVxToVy(JsonObject)` and/or `migrateTripsVxToVy(JsonObject)` method that transforms the JSON in-place
   - Add the migration call in `loadDrops()` / `loadTrips()`: `if (version < N) { migrateVxToVy(obj); }`
   - The old file (e.g., `drops.v1.json`) is preserved untouched — the new version writes to `drops.v2.json`
   - Write a test that loads the OLD format JSON and verifies it deserializes correctly with the new code

3. File naming convention:
   - v0 (legacy): `drops.json`, `trips.json` (bare arrays)
   - v1+: `drops.v1.json`, `trips.v1.json` (object envelope with `"version"` field)
   - Load scans from `CURRENT_VERSION` down to 0, uses first file found
   - Old files are never deleted — they serve as backups

## Rules

1. **Never rename a serialized field.** If a field name changes, the old data won't deserialize correctly. Add the new field alongside the old one and handle both.

2. **Never change a field's type.** Changing `int` to `String` or `long` to `int` will cause Gson parse failures. Add a new field with the new type instead.

3. **Never remove a field from a Record class.** Gson silently ignores extra fields in JSON, so old fields in the file won't cause errors even if unused. Leave them in the class.

4. **Always add new fields with defaults.** When Gson deserializes and a field is missing from the JSON, it defaults to `0`/`null`/`false`. Ensure your code handles these defaults gracefully (null checks, fallback values).

5. **Bump the schema version for structural changes.** Increment `CURRENT_VERSION` in `TripStorageService` and write migration logic.

6. **Write migration functions for structural changes.** If you restructure the data (e.g., moving nested objects, splitting a field into multiple), write a `migrateVxToVy()` method in `TripStorageService` that transforms the old format before deserializing.

7. **Test round-trip serialization.** Any change to Record classes should have a test that:
   - Creates a record with the OLD format (simulating existing user data)
   - Loads it with the NEW code
   - Verifies all fields are populated correctly

## Examples

### Safe: Adding a new field
```java
// OLD
public class DropRecord {
    String npcName;
    int npcCombatLevel;
}

// NEW — safe, old data deserializes fine (lootSource will be null). No version bump needed.
public class DropRecord {
    String npcName;
    int npcCombatLevel;
    String lootSource; // New field — null for old data, handle gracefully
}
```

### Unsafe: Renaming a field (requires version bump + migration)
```java
// This BREAKS old data — "npcName" in JSON won't map to "sourceName"
public class DropRecord {
    String sourceName; // BAD without migration
}
```

### Migration example
```java
// In TripStorageService — bump CURRENT_VERSION to 2, add migration:
private void migrateDropsV1toV2(JsonObject root) {
    JsonArray drops = root.getAsJsonArray("drops");
    for (JsonElement el : drops) {
        JsonObject drop = el.getAsJsonObject();
        // Rename "npcName" → "sourceName"
        if (drop.has("npcName")) {
            drop.addProperty("sourceName", drop.get("npcName").getAsString());
            drop.remove("npcName");
        }
    }
    root.addProperty("version", 2);
}

// In loadDrops():
if (version < 2) { migrateDropsV1toV2(obj); }
```
