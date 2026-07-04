---
inclusion: fileMatch
fileMatchPattern: "**/DropRecord.java,**/TripRecord.java,**/TripStorageService.java,**/Trip.java,**/TrackableItemDrop.java,**/TrackableDroppedItem.java,**/NpcLootAggregate.java"
---

# Data Persistence Rules

These rules apply when modifying any files that affect the persisted data format in `~/.runelite/trip-tracker/drops.json` or `trips.json`.

## Critical: No Breaking Changes

Users have persisted data that must remain loadable after plugin updates. Breaking their data is unacceptable.

## Rules

1. **Never rename a serialized field.** If a field name changes, the old data won't deserialize correctly. Add the new field alongside the old one and handle both.

2. **Never change a field's type.** Changing `int` to `String` or `long` to `int` will cause Gson parse failures. Add a new field with the new type instead.

3. **Never remove a field from a Record class.** Gson silently ignores extra fields in JSON, so old fields in the file won't cause errors even if unused. Leave them in the class.

4. **Always add new fields with defaults.** When Gson deserializes and a field is missing from the JSON, it defaults to `0`/`null`/`false`. Ensure your code handles these defaults gracefully (null checks, fallback values).

5. **Add a schema version.** If not already present, add a `"version": N` field to the root of each JSON file. Increment it when making structural changes.

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

// NEW — safe, old data deserializes fine (lootSource will be null)
public class DropRecord {
    String npcName;
    int npcCombatLevel;
    String lootSource; // New field — null for old data, handle gracefully
}
```

### Unsafe: Renaming a field
```java
// This BREAKS old data — "npcName" in JSON won't map to "sourceName"
public class DropRecord {
    String sourceName; // BAD: was "npcName"
}
```

### Migration example
```java
public List<DropRecord> loadDrops() {
    JsonObject root = parseFile("drops.json");
    int version = root.has("version") ? root.get("version").getAsInt() : 0;
    
    if (version < 1) {
        migrateDropsV0toV1(root);
    }
    
    // Deserialize current format
    return gson.fromJson(root.get("drops"), listType);
}
```
