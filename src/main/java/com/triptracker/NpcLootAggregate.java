package com.triptracker;

import net.runelite.client.game.ItemManager;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class NpcLootAggregate {
    final String npcName;
    ArrayList<TrackableDroppedItem> droppedItems;
    final ItemManager itemManager;
    int numberOfKills;
    String lastKillTime;
    ArrayList<LootAggregation> lootAggregations;

    // O(1) lookup map keyed by item ID for efficient aggregation
    private final LinkedHashMap<Integer, LootAggregation> aggregationMap = new LinkedHashMap<>();

    NpcLootAggregate(String npcName, ItemManager itemManager) {
        this.npcName = npcName;
        this.itemManager = itemManager;
        this.numberOfKills = 0;
        droppedItems = new ArrayList<>();
    }

    public void addDropToNpcAggregate(TrackableItemDrop itemDrop) {
        droppedItems.addAll(itemDrop.getDroppedItems());

        // Use the drop's timestamp for the last kill time
        Date date = new Date(itemDrop.getDropTimeDate());
        Format format = new SimpleDateFormat("HH:mm:ss 'on' MMM d yyyy");
        this.lastKillTime = format.format(date);

        numberOfKills++;

        // Incrementally update the aggregation map with new items (O(n) per drop, not O(n²))
        for (TrackableDroppedItem item : itemDrop.getDroppedItems()) {
            int itemId = item.getItemId();
            LootAggregation existing = aggregationMap.get(itemId);
            if (existing != null) {
                existing.updateItemAggregation(item.getQuantity());
            } else {
                LootAggregation newAgg = new LootAggregation(itemId, item.getQuantity(), itemManager);
                aggregationMap.put(itemId, newAgg);
            }
        }

        this.lootAggregations = new ArrayList<>(aggregationMap.values());
    }

    /**
     * Returns the aggregated drop list. Uses the pre-computed aggregation map for O(1) access.
     * This method is retained for backward compatibility but now simply returns the cached list.
     */
    public ArrayList<LootAggregation> aggregateNpcDrops() {
        if (lootAggregations != null) {
            return lootAggregations;
        }
        // Fallback: rebuild from scratch (only needed for freshly constructed aggregates with no drops)
        return rebuildAggregations();
    }

    /**
     * Rebuilds the aggregation map from the raw dropped items list.
     * Used after deserialization when items are added directly to droppedItems.
     */
    public void rebuildAggregationMap() {
        aggregationMap.clear();
        for (TrackableDroppedItem item : droppedItems) {
            int itemId = item.getItemId();
            LootAggregation existing = aggregationMap.get(itemId);
            if (existing != null) {
                existing.updateItemAggregation(item.getQuantity());
            } else {
                aggregationMap.put(itemId, new LootAggregation(itemId, item.getQuantity(), itemManager));
            }
        }
        this.lootAggregations = new ArrayList<>(aggregationMap.values());
    }

    private ArrayList<LootAggregation> rebuildAggregations() {
        aggregationMap.clear();
        for (TrackableDroppedItem item : droppedItems) {
            int itemId = item.getItemId();
            LootAggregation existing = aggregationMap.get(itemId);
            if (existing != null) {
                existing.updateItemAggregation(item.getQuantity());
            } else {
                aggregationMap.put(itemId, new LootAggregation(itemId, item.getQuantity(), itemManager));
            }
        }
        this.lootAggregations = new ArrayList<>(aggregationMap.values());
        return lootAggregations;
    }

    public String getNpcName() {
        return npcName;
    }

    public int getNumberOfKills() { return numberOfKills; }

    public String getLastKillTime() { return lastKillTime; }

    public ArrayList<LootAggregation> getNpcItemAggregations() {
        return lootAggregations;
    }

    public ArrayList<TrackableDroppedItem> getDroppedItems() {
        return droppedItems;
    }
}
