package com.triptracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Handles reading and writing trip data to disk.
 * Data is stored as JSON in ~/.runelite/trip-tracker/
 * Writes are performed asynchronously on a background thread to avoid blocking the game thread.
 *
 * File naming convention:
 * - v0 (legacy): trips.json, drops.json (bare arrays, no version field)
 * - v1+: trips.v1.json, drops.v1.json (object envelope with "version" field)
 *
 * On load, the highest available version file is used. Old files are never deleted,
 * serving as backups if a user needs to roll back.
 */
@Slf4j
public class TripStorageService {

    private static final String PLUGIN_DIR_NAME = "trip-tracker";
    private static final String COLLAPSED_NPCS_FILE_NAME = "collapsed-npcs.json";

    // Current schema version — increment when making structural changes to the persisted format
    static final int CURRENT_VERSION = 1;

    // Base names (without extension) for versioned files
    private static final String TRIPS_BASE = "trips";
    private static final String DROPS_BASE = "drops";

    private final Gson gson;
    private final File baseDir; // Root plugin directory (~/.runelite/trip-tracker/)
    private File pluginDir;     // Active data directory (baseDir or baseDir/{accountHash}/)
    private final ExecutorService writeExecutor;

    public TripStorageService() {
        this(new File(RuneLite.RUNELITE_DIR, PLUGIN_DIR_NAME));
    }

    public TripStorageService(File pluginDir) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.baseDir = pluginDir;
        this.pluginDir = pluginDir;
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "trip-tracker-persistence");
            t.setDaemon(true);
            return t;
        });

        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }
    }

    // --- Account switching ---

    /**
     * Switches the active data directory to a character-specific subdirectory.
     * If legacy files exist in the root dir but the account subdir is empty,
     * copies them into the account subdir (one-time migration).
     *
     * @param accountHash the account's unique hash from client.getAccountHash()
     */
    public void switchAccount(long accountHash) {
        File accountDir = new File(baseDir, String.valueOf(accountHash));
        if (!accountDir.exists()) {
            accountDir.mkdirs();
        }

        // One-time migration: if the account dir has no data files but the base dir does,
        // copy them over (first login with character-specific tracking enabled)
        migrateIfNeeded(accountDir);

        this.pluginDir = accountDir;
        log.debug("Switched storage to account directory: {}", accountDir.getAbsolutePath());
    }

    /**
     * Returns the currently active data directory.
     */
    public File getActiveDirectory() {
        return pluginDir;
    }

    /**
     * Copies legacy data files from the base directory to the account directory
     * if the account directory has no versioned data files yet.
     */
    private void migrateIfNeeded(File accountDir) {
        // Check if the account dir already has data (any version of drops or trips)
        boolean hasDrops = false;
        boolean hasTrips = false;
        for (int v = CURRENT_VERSION; v >= 0; v--) {
            if (new File(accountDir, versionedFileName(DROPS_BASE, v)).exists()) {
                hasDrops = true;
            }
            if (new File(accountDir, versionedFileName(TRIPS_BASE, v)).exists()) {
                hasTrips = true;
            }
        }

        if (hasDrops || hasTrips) {
            // Account already has data — no migration needed
            return;
        }

        // Check if legacy files exist in the base directory
        String[] filesToMigrate = {
                versionedFileName(DROPS_BASE, CURRENT_VERSION),
                versionedFileName(DROPS_BASE, 0),
                versionedFileName(TRIPS_BASE, CURRENT_VERSION),
                versionedFileName(TRIPS_BASE, 0),
                COLLAPSED_NPCS_FILE_NAME,
                SESSION_FILE_NAME
        };

        boolean migrated = false;
        for (String fileName : filesToMigrate) {
            File source = new File(baseDir, fileName);
            if (source.exists()) {
                File dest = new File(accountDir, fileName);
                try {
                    Files.copy(source.toPath(), dest.toPath());
                    migrated = true;
                    log.debug("Migrated {} to account directory", fileName);
                } catch (IOException e) {
                    log.warn("Failed to migrate {} to account directory", fileName, e);
                }
            }
        }

        if (migrated) {
            log.info("Migrated existing tracking data to character-specific directory: {}", accountDir.getName());
        }
    }

    // --- File naming helpers ---

    /**
     * Returns the filename for a given base and version.
     * v0 (legacy) uses "base.json", v1+ uses "base.v{N}.json".
     */
    private static String versionedFileName(String base, int version) {
        if (version == 0) {
            return base + ".json";
        }
        return base + ".v" + version + ".json";
    }

    /**
     * Finds the highest version file that exists on disk for the given base name.
     * Scans from CURRENT_VERSION down to 0. Returns null if no file exists.
     */
    private String findHighestVersionFile(String base) {
        for (int v = CURRENT_VERSION; v >= 0; v--) {
            String fileName = versionedFileName(base, v);
            File file = new File(pluginDir, fileName);
            if (file.exists()) {
                return fileName;
            }
        }
        return null;
    }

    // --- Trips ---

    /**
     * Save trip data to disk asynchronously.
     */
    public void saveTrips(List<Trip> trips) {
        List<TripRecord> records = new ArrayList<>();
        for (Trip trip : trips) {
            records.add(TripRecord.fromTrip(trip));
        }

        writeExecutor.submit(() -> {
            JsonObject envelope = new JsonObject();
            envelope.addProperty("version", CURRENT_VERSION);
            envelope.add("trips", gson.toJsonTree(records));
            writeFile(versionedFileName(TRIPS_BASE, CURRENT_VERSION), gson.toJson(envelope));
        });
    }

    /**
     * Save trip data to disk synchronously (for use during shutdown).
     */
    public void saveTripsSync(List<Trip> trips) {
        List<TripRecord> records = new ArrayList<>();
        for (Trip trip : trips) {
            records.add(TripRecord.fromTrip(trip));
        }

        JsonObject envelope = new JsonObject();
        envelope.addProperty("version", CURRENT_VERSION);
        envelope.add("trips", gson.toJsonTree(records));
        writeFile(versionedFileName(TRIPS_BASE, CURRENT_VERSION), gson.toJson(envelope));
    }

    /**
     * Load trip data from disk.
     * Scans from highest version down to legacy. Handles bare arrays (v0) and versioned envelopes (v1+).
     */
    public List<TripRecord> loadTrips() {
        String fileName = findHighestVersionFile(TRIPS_BASE);
        if (fileName == null) {
            return new ArrayList<>();
        }

        String json = readFile(fileName);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            JsonElement root = gson.fromJson(json, JsonElement.class);
            JsonArray tripsArray;

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                int version = obj.has("version") ? obj.get("version").getAsInt() : 0;
                log.debug("Loading {} (version {})", fileName, version);
                // Future migrations: if (version < 2) { migrateTripsV1toV2(obj); }
                tripsArray = obj.has("trips") ? obj.getAsJsonArray("trips") : new JsonArray();
            } else if (root.isJsonArray()) {
                log.debug("Loading {} (legacy format, no version)", fileName);
                tripsArray = root.getAsJsonArray();
            } else {
                log.warn("Unexpected format in {}, starting fresh", fileName);
                return new ArrayList<>();
            }

            Type listType = new TypeToken<ArrayList<TripRecord>>() {}.getType();
            List<TripRecord> records = gson.fromJson(tripsArray, listType);
            return records != null ? records : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to parse trip data from {}, starting fresh", fileName, e);
            return new ArrayList<>();
        }
    }

    // --- Drops ---

    /**
     * Save the list-view drop history to disk asynchronously.
     */
    public void saveDrops(List<TrackableItemDrop> drops) {
        List<DropRecord> records = new ArrayList<>();
        for (TrackableItemDrop drop : drops) {
            records.add(DropRecord.fromDrop(drop));
        }

        writeExecutor.submit(() -> {
            JsonObject envelope = new JsonObject();
            envelope.addProperty("version", CURRENT_VERSION);
            envelope.add("drops", gson.toJsonTree(records));
            writeFile(versionedFileName(DROPS_BASE, CURRENT_VERSION), gson.toJson(envelope));
        });
    }

    /**
     * Save the list-view drop history to disk synchronously (for use during shutdown).
     */
    public void saveDropsSync(List<TrackableItemDrop> drops) {
        List<DropRecord> records = new ArrayList<>();
        for (TrackableItemDrop drop : drops) {
            records.add(DropRecord.fromDrop(drop));
        }

        JsonObject envelope = new JsonObject();
        envelope.addProperty("version", CURRENT_VERSION);
        envelope.add("drops", gson.toJsonTree(records));
        writeFile(versionedFileName(DROPS_BASE, CURRENT_VERSION), gson.toJson(envelope));
    }

    /**
     * Load the list-view drop history from disk.
     * Scans from highest version down to legacy. Handles bare arrays (v0) and versioned envelopes (v1+).
     */
    public List<DropRecord> loadDrops() {
        String fileName = findHighestVersionFile(DROPS_BASE);
        if (fileName == null) {
            return new ArrayList<>();
        }

        String json = readFile(fileName);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            JsonElement root = gson.fromJson(json, JsonElement.class);
            JsonArray dropsArray;

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                int version = obj.has("version") ? obj.get("version").getAsInt() : 0;
                log.debug("Loading {} (version {})", fileName, version);
                // Future migrations: if (version < 2) { migrateDropsV1toV2(obj); }
                dropsArray = obj.has("drops") ? obj.getAsJsonArray("drops") : new JsonArray();
            } else if (root.isJsonArray()) {
                log.debug("Loading {} (legacy format, no version)", fileName);
                dropsArray = root.getAsJsonArray();
            } else {
                log.warn("Unexpected format in {}, starting fresh", fileName);
                return new ArrayList<>();
            }

            Type listType = new TypeToken<ArrayList<DropRecord>>() {}.getType();
            List<DropRecord> records = gson.fromJson(dropsArray, listType);
            return records != null ? records : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to parse drop data from {}, starting fresh", fileName, e);
            return new ArrayList<>();
        }
    }

    // --- Session timestamp ---

    private static final String SESSION_FILE_NAME = "last-session.txt";

    /**
     * Persist the epoch timestamp of the last logout or shutdown.
     * Written synchronously since it's called during shutdown and logout.
     */
    public void saveLastSessionEpoch(long epochMs) {
        writeFile(SESSION_FILE_NAME, String.valueOf(epochMs));
    }

    /**
     * Load the last session epoch. Returns 0 if not found.
     */
    public long loadLastSessionEpoch() {
        String content = readFile(SESSION_FILE_NAME);
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(content.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse last session epoch, returning 0");
            return 0;
        }
    }

    // --- Collapsed NPCs ---

    /**
     * Save the set of collapsed NPC names (for grouped view) to disk asynchronously.
     */
    public void saveCollapsedNpcs(Set<String> collapsedNpcs) {
        Set<String> snapshot = new HashSet<>(collapsedNpcs);
        writeExecutor.submit(() -> {
            String json = gson.toJson(snapshot);
            writeFile(COLLAPSED_NPCS_FILE_NAME, json);
        });
    }

    /**
     * Load the set of collapsed NPC names from disk.
     */
    public Set<String> loadCollapsedNpcs() {
        String json = readFile(COLLAPSED_NPCS_FILE_NAME);
        if (json == null || json.isEmpty()) {
            return new HashSet<>();
        }

        try {
            Type setType = new TypeToken<HashSet<String>>() {}.getType();
            Set<String> result = gson.fromJson(json, setType);
            return result != null ? result : new HashSet<>();
        } catch (Exception e) {
            log.warn("Failed to parse collapsed NPC data from disk, starting fresh", e);
            return new HashSet<>();
        }
    }

    // --- Lifecycle ---

    /**
     * Shutdown the write executor, waiting for pending writes to complete.
     */
    public void shutdown() {
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Persistence executor did not terminate in time");
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // --- File I/O ---

    private void writeFile(String fileName, String content) {
        try {
            Path filePath = new File(pluginDir, fileName).toPath();
            Files.writeString(filePath, content);
            log.debug("Saved data to {}", filePath);
        } catch (IOException e) {
            log.error("Failed to write {}", fileName, e);
        }
    }

    private String readFile(String fileName) {
        File file = new File(pluginDir, fileName);
        if (!file.exists()) {
            return null;
        }

        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            log.error("Failed to read {}", fileName, e);
            return null;
        }
    }
}
