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
    private final String tripName;
    private final ArrayList<NpcLootAggregate> npcAggregations = new ArrayList<>();
    private final EnhancedLootTrackerPlugin parentPlugin;
    private boolean tripActive;
    private final String tripStartTime;
    private final long tripStartTimeEpoch;
    private String tripEndTime;
    private long tripEndTimeEpoch;
    private int tripKills;
    private long tripValue;

    Trip(String tripName, EnhancedLootTrackerPlugin parentPlugin) {
        this.parentPlugin = parentPlugin;
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
         long tripEndTimeEpoch, int tripKills, long tripValue) {
        this.parentPlugin = parentPlugin;
        this.tripName = tripName;
        this.tripActive = tripActive;
        this.tripStartTimeEpoch = tripStartTimeEpoch;
        this.tripStartTime = tripStartTime;
        this.tripEndTime = tripEndTime;
        this.tripEndTimeEpoch = tripEndTimeEpoch;
        this.tripKills = tripKills;
        this.tripValue = tripValue;
    }

    public void addNpcAggregateToTrip(NpcLootAggregate npcLootAggregate) {
        if (contains(npcLootAggregate.getNpcName())) {
            removeNpcAggregate(npcLootAggregate.getNpcName());
        }
        npcAggregations.add(npcLootAggregate);
    }

    public ArrayList<NpcLootAggregate> getTripAggregates() {
        return npcAggregations;
    }

    public boolean contains(String npcName) {
        for (NpcLootAggregate npcAggregate : npcAggregations) {
            if (npcAggregate.getNpcName().equals(npcName)) {
                return true;
            }
        }
        return false;
    }

    public void removeNpcAggregate(String npcName) {
        npcAggregations.removeIf(agg -> agg.getNpcName().equals(npcName));
    }

    public boolean matches(String tripName) {
        return this.tripName.equals(tripName);
    }

    // Accessors

    public String getTripName() {
        return tripName;
    }

    public boolean getTripStatus() {
        return tripActive;
    }

    public void setStatus(boolean status) {
        this.tripActive = status;
        if (!tripActive) {
            this.tripEndTimeEpoch = System.currentTimeMillis();
            this.tripEndTime = formatTime(tripEndTimeEpoch);
        }
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
        long tripDurationSeconds = (endTime - tripStartTimeEpoch) / 1000;

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

    public static String formatTime(long epochMillis) {
        Date date = new Date(epochMillis);
        Format format = new SimpleDateFormat("HH:mm:ss 'on' MMM d yyyy");
        return format.format(date);
    }
}
