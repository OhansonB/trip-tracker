package com.triptracker;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Pure data model representing a loot tracking trip.
 * Contains no UI logic — see TripPanel for the Swing representation.
 */
public class Trip {
    private final int tripId;
    private String tripName;
    private final ArrayList<NpcLootAggregate> npcAggregations = new ArrayList<>();
    private final EnhancedLootTrackerPlugin parentPlugin;
    private boolean tripActive;
    private boolean tripPaused;
    private boolean collapsed;
    private final String tripStartTime;
    private final long tripStartTimeEpoch;
    private String tripEndTime;
    private long tripEndTimeEpoch;
    private int tripKills;
    private long tripValue;
    private long pausedDurationMs; // accumulated time spent paused (subtracted from duration)
    private long pausedAtEpoch;    // when the trip was paused (0 if not paused)

    Trip(String tripName, EnhancedLootTrackerPlugin parentPlugin) {
        this.parentPlugin = parentPlugin;
        this.tripId = parentPlugin.getNextTripNumber();
        this.tripName = tripName;
        this.tripActive = true;
        this.tripStartTimeEpoch = System.currentTimeMillis();
        this.tripStartTime = formatTime(tripStartTimeEpoch);
        this.tripEndTime = "n/a";
        this.tripKills = 0;
        this.tripValue = 0;
    }

    /**
     * Constructor for restoring a trip from persisted data.
     */
    Trip(String tripName, EnhancedLootTrackerPlugin parentPlugin, boolean tripActive,
         long tripStartTimeEpoch, String tripStartTime, String tripEndTime,
         long tripEndTimeEpoch, int tripKills, long tripValue, int tripId, boolean collapsed,
         boolean tripPaused, long pausedDurationMs, long pausedAtEpoch) {
        this.parentPlugin = parentPlugin;
        this.tripId = tripId;
        this.tripName = tripName;
        this.tripActive = tripActive;
        this.tripPaused = tripPaused;
        this.collapsed = collapsed;
        this.tripStartTimeEpoch = tripStartTimeEpoch;
        this.tripStartTime = tripStartTime;
        this.tripEndTime = tripEndTime;
        this.tripEndTimeEpoch = tripEndTimeEpoch;
        this.tripKills = tripKills;
        this.tripValue = tripValue;
        this.pausedDurationMs = pausedDurationMs;
        this.pausedAtEpoch = pausedAtEpoch;
    }

    public void addNpcAggregateToTrip(NpcLootAggregate npcLootAggregate) {
        synchronized (npcAggregations) {
            if (contains(npcLootAggregate.getNpcName())) {
                removeNpcAggregate(npcLootAggregate.getNpcName());
            }
            npcAggregations.add(npcLootAggregate);
        }
    }

    public ArrayList<NpcLootAggregate> getTripAggregates() {
        synchronized (npcAggregations) {
            return new ArrayList<>(npcAggregations);
        }
    }

    public boolean contains(String npcName) {
        synchronized (npcAggregations) {
            for (NpcLootAggregate npcAggregate : npcAggregations) {
                if (npcAggregate.getNpcName().equals(npcName)) {
                    return true;
                }
            }
            return false;
        }
    }

    public void removeNpcAggregate(String npcName) {
        synchronized (npcAggregations) {
            npcAggregations.removeIf(agg -> agg.getNpcName().equals(npcName));
        }
    }

    public boolean matches(String tripName) {
        return this.tripName.equals(tripName);
    }

    // Accessors

    public String getTripName() {
        return tripName;
    }

    public void setTripName(String tripName) {
        this.tripName = tripName;
    }

    public int getTripId() {
        return tripId;
    }

    public boolean getTripStatus() {
        return tripActive;
    }

    public void setStatus(boolean status) {
        this.tripActive = status;
        if (!tripActive) {
            this.tripPaused = false;
            this.tripEndTimeEpoch = System.currentTimeMillis();
            this.tripEndTime = formatTime(tripEndTimeEpoch);
            // If stopped while paused, finalize the paused duration
            if (pausedAtEpoch > 0) {
                pausedDurationMs += System.currentTimeMillis() - pausedAtEpoch;
                pausedAtEpoch = 0;
            }
        }
    }

    public boolean isPaused() {
        return tripPaused;
    }

    public void pause() {
        if (tripActive && !tripPaused) {
            tripPaused = true;
            pausedAtEpoch = System.currentTimeMillis();
        }
    }

    public void resume() {
        if (tripActive && tripPaused) {
            tripPaused = false;
            if (pausedAtEpoch > 0) {
                pausedDurationMs += System.currentTimeMillis() - pausedAtEpoch;
                pausedAtEpoch = 0;
            }
        }
    }

    public long getPausedDurationMs() {
        return pausedDurationMs;
    }

    public long getPausedAtEpoch() {
        return pausedAtEpoch;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public int getTripKills() {
        return tripKills;
    }

    public void incrementKills() {
        tripKills++;
    }

    public long getTripValue() {
        return tripValue;
    }

    public void addValue(long value) {
        tripValue += value;
    }

    public String getTripStartTime() {
        return tripStartTime;
    }

    public long getTripStartTimeEpoch() {
        return tripStartTimeEpoch;
    }

    public String getTripEndTime() {
        return tripEndTime;
    }

    public long getTripEndTimeEpoch() {
        return tripEndTimeEpoch;
    }

    public EnhancedLootTrackerPlugin getParentPlugin() {
        return parentPlugin;
    }

    public String calculateTripDuration() {
        long endTime = tripActive ? System.currentTimeMillis() : tripEndTimeEpoch;
        long totalPausedMs = pausedDurationMs;
        // If currently paused, add the ongoing pause duration
        if (tripPaused && pausedAtEpoch > 0) {
            totalPausedMs += System.currentTimeMillis() - pausedAtEpoch;
        }
        long tripDurationSeconds = Math.max(0, (endTime - tripStartTimeEpoch - totalPausedMs)) / 1000;

        long days = tripDurationSeconds / (24 * 3600);
        long hours = (tripDurationSeconds % (24 * 3600)) / 3600;
        long minutes = (tripDurationSeconds % 3600) / 60;
        long remainingSeconds = tripDurationSeconds % 60;

        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m ");
        if (remainingSeconds > 0) result.append(remainingSeconds).append("s");
        else result.append("0s");

        return result.toString().trim();
    }

    /**
     * Calculates GP earned per hour based on trip value and active duration (excluding paused time).
     * Returns 0 if the trip has been active for less than 1 second.
     */
    public long getGpPerHour() {
        long endTime = tripActive ? System.currentTimeMillis() : tripEndTimeEpoch;
        long totalPausedMs = pausedDurationMs;
        if (tripPaused && pausedAtEpoch > 0) {
            totalPausedMs += System.currentTimeMillis() - pausedAtEpoch;
        }
        long durationMs = endTime - tripStartTimeEpoch - totalPausedMs;
        if (durationMs <= 0) {
            return 0;
        }
        return (tripValue * 3600000L) / durationMs;
    }

    /**
     * Returns the trip active duration in seconds (excluding paused time).
     */
    public long getDurationSeconds() {
        long endTime = tripActive ? System.currentTimeMillis() : tripEndTimeEpoch;
        long totalPausedMs = pausedDurationMs;
        if (tripPaused && pausedAtEpoch > 0) {
            totalPausedMs += System.currentTimeMillis() - pausedAtEpoch;
        }
        return Math.max(0, (endTime - tripStartTimeEpoch - totalPausedMs)) / 1000;
    }

    /**
     * Calculates average GP earned per kill.
     * Returns 0 if no kills have been recorded.
     */
    public long getGpPerKill() {
        if (tripKills <= 0) {
            return 0;
        }
        return tripValue / tripKills;
    }

    public static String formatTime(long epochMillis) {
        Date date = new Date(epochMillis);
        Format format = new SimpleDateFormat("HH:mm:ss 'on' MMM d yyyy");
        return format.format(date);
    }
}
