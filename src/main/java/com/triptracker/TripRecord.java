package com.triptracker;

import net.runelite.client.game.ItemManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A serializable record of a trip.
 * This is the JSON-friendly representation of Trip + its associated drops.
 */
public class TripRecord {
    String tripName;
    boolean tripActive;
    long tripStartTimeEpoch;
    String tripStartTime;
    String tripEndTime;
    long tripEndTimeEpoch;
    int tripKills;
    long tripValue;
    int tripId;
    List<NpcAggregateRecord> npcAggregates;

    public static TripRecord fromTrip(Trip trip) {
        TripRecord record = new TripRecord();
        record.tripName = trip.getTripName();
        record.tripActive = trip.getTripStatus();
        record.tripStartTimeEpoch = trip.getTripStartTimeEpoch();
        record.tripStartTime = trip.getTripStartTime();
        record.tripEndTime = trip.getTripEndTime();
        record.tripEndTimeEpoch = trip.getTripEndTimeEpoch();
        record.tripKills = trip.getTripKills();
        record.tripValue = trip.getTripValue();
        record.tripId = trip.getTripId();
        record.npcAggregates = new ArrayList<>();

        for (NpcLootAggregate aggregate : trip.getTripAggregates()) {
            NpcAggregateRecord aggRecord = new NpcAggregateRecord();
            aggRecord.npcName = aggregate.getNpcName();
            aggRecord.numberOfKills = aggregate.getNumberOfKills();
            aggRecord.lastKillTime = aggregate.getLastKillTime();
            aggRecord.items = new ArrayList<>();

            for (TrackableDroppedItem item : aggregate.getDroppedItems()) {
                DropRecord.ItemRecord itemRecord = new DropRecord.ItemRecord();
                itemRecord.itemId = item.getItemId();
                itemRecord.itemName = item.getItemName();
                itemRecord.quantity = item.getQuantity();
                itemRecord.gePrice = (int) (item.getTotalGePrice() / Math.max(item.getQuantity(), 1));
                itemRecord.haPrice = (int) (item.getTotalHaPrice() / Math.max(item.getQuantity(), 1));
                aggRecord.items.add(itemRecord);
            }

            record.npcAggregates.add(aggRecord);
        }

        return record;
    }

    /**
     * Reconstruct a Trip from this record.
     */
    public Trip toTrip(EnhancedLootTrackerPlugin plugin, ItemManager itemManager) {
        Trip trip = new Trip(tripName, plugin, tripActive, tripStartTimeEpoch,
                tripStartTime, tripEndTime, tripEndTimeEpoch, tripKills, tripValue, tripId);

        if (npcAggregates == null) {
            return trip;
        }

        for (NpcAggregateRecord aggRecord : npcAggregates) {
            if (aggRecord == null || aggRecord.npcName == null) {
                continue;
            }

            NpcLootAggregate aggregate = new NpcLootAggregate(aggRecord.npcName, itemManager);

            // Restore items into the aggregate
            if (aggRecord.items != null) {
                for (DropRecord.ItemRecord itemRecord : aggRecord.items) {
                    if (itemRecord == null) {
                        continue;
                    }
                    TrackableDroppedItem item = new TrackableDroppedItem(
                            itemRecord.itemId, itemRecord.itemName,
                            itemRecord.quantity, itemRecord.gePrice, itemRecord.haPrice);
                    aggregate.getDroppedItems().add(item);
                }
            }

            // Restore kill count and last kill time directly
            aggregate.numberOfKills = aggRecord.numberOfKills;
            aggregate.lastKillTime = aggRecord.lastKillTime;
            // Rebuild the aggregation map from the restored items
            aggregate.rebuildAggregationMap();

            trip.addNpcAggregateToTrip(aggregate);
        }

        return trip;
    }

    /**
     * Record for a single NPC's aggregated loot within a trip.
     */
    public static class NpcAggregateRecord {
        String npcName;
        int numberOfKills;
        String lastKillTime;
        List<DropRecord.ItemRecord> items;
    }
}
