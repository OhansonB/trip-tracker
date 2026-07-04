package com.triptracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Handles reading and writing trip data to disk.
 * Data is stored as JSON in ~/.runelite/trip-tracker/
 * Writes are performed asynchronously on a background thread to avoid blocking the game thread.
 */
@Slf4j
public class TripStorageService {

    private static final String PLUGIN_DIR_NAME = "trip-tracker";
    private static final String TRIPS_FILE_NAME = "trips.json";
    private static final String DROPS_FILE_NAME = "drops.json";

    private final Gson gson;
    private final File pluginDir;
    private final ExecutorService writeExecutor;

    public TripStorageService() {
        this(new File(RuneLite.RUNELITE_DIR, PLUGIN_DIR_NAME));
    }

    public TripStorageService(File pluginDir) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
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

    /**
     * Save trip data to disk asynchronously.
     */
    public void saveTrips(List<Trip> trips) {
        // Take a snapshot of the data to avoid concurrent modification
        List<TripRecord> records = new ArrayList<>();
        for (Trip trip : trips) {
            records.add(TripRecord.fromTrip(trip));
        }

        writeExecutor.submit(() -> {
            String json = gson.toJson(records);
            writeFile(TRIPS_FILE_NAME, json);
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

        String json = gson.toJson(records);
        writeFile(TRIPS_FILE_NAME, json);
    }

    /**
     * Load trip data from disk.
     */
    public List<TripRecord> loadTrips() {
        String json = readFile(TRIPS_FILE_NAME);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Type listType = new TypeToken<ArrayList<TripRecord>>() {}.getType();
            List<TripRecord> records = gson.fromJson(json, listType);
            return records != null ? records : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to parse trip data from disk, starting fresh", e);
            return new ArrayList<>();
        }
    }

    /**
     * Save the list-view drop history to disk asynchronously.
     */
    public void saveDrops(List<TrackableItemDrop> drops) {
        // Take a snapshot of the data to avoid concurrent modification
        List<DropRecord> records = new ArrayList<>();
        for (TrackableItemDrop drop : drops) {
            records.add(DropRecord.fromDrop(drop));
        }

        writeExecutor.submit(() -> {
            String json = gson.toJson(records);
            writeFile(DROPS_FILE_NAME, json);
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

        String json = gson.toJson(records);
        writeFile(DROPS_FILE_NAME, json);
    }

    /**
     * Load the list-view drop history from disk.
     */
    public List<DropRecord> loadDrops() {
        String json = readFile(DROPS_FILE_NAME);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Type listType = new TypeToken<ArrayList<DropRecord>>() {}.getType();
            List<DropRecord> records = gson.fromJson(json, listType);
            return records != null ? records : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to parse drop data from disk, starting fresh", e);
            return new ArrayList<>();
        }
    }

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
