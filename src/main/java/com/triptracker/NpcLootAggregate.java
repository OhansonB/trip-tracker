package com.triptracker;

import net.runelite.client.game.ItemManager;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class NpcLootAggregate {
    final String npcName;
    ArrayList<TrackableDroppedItem> droppedItems;
    final ItemManager itemManager;
    int numberOfKills;
    String lastKillTime;
    ArrayList<LootAggregation> lootAggregations;
    boolean collapsed;

    // O(1) lookup map keyed by item ID for efficient aggregation
    private final LinkedHashMap<Integer, LootAggregation> aggregationMap = new LinkedHashMap<>();

    // Bird nest item IDs — these have different IDs but same display name "Bird nest"
    // and should be aggregated together by name rather than by ID
    private static final Set<Integer> BIRD_NEST_IDS = new HashSet<>(Arrays.asList(
            5070, 5071, 5072,  // egg nests (blue, green, red)
            5073,              // seed nest
            5074,              // ring nest
            5075,              // empty nest
            22798,             // clue nest (beginner)
            22800,             // clue nest (easy)
            22802,             // clue nest (medium)
            22804,             // clue nest (hard)
            22806              // clue nest (elite)
    ));

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

            // Bird nests: aggregate by name since different nest types share the display name
            if (BIRD_NEST_IDS.contains(itemId)) {
                String itemName = item.getItemName();
                LootAggregation existingByName = findAggregationByName(itemName);
                if (existingByName != null) {
                    existingByName.updateItemAggregation(item.getQuantity());
                } else {
                    LootAggregation newAgg = new LootAggregation(itemId, item.getQuantity(), itemManager);
                    aggregationMap.put(itemId, newAgg);
                }
            } else {
                LootAggregation existing = aggregationMap.get(itemId);
                if (existing != null) {
                    existing.updateItemAggregation(item.getQuantity());
                } else {
                    LootAggregation newAgg = new LootAggregation(itemId, item.getQuantity(), itemManager);
                    aggregationMap.put(itemId, newAgg);
                }
            }
        }

        // Debug: log aggregation map state
        StringBuilder sb = new StringBuilder("AggMap for " + npcName + ": ");
        for (Map.Entry<Integer, LootAggregation> e : aggregationMap.entrySet()) {
            sb.append(e.getKey()).append("=").append(e.getValue().getItemName())
              .append("x").append(e.getValue().getQuantity()).append(", ");
        }
        System.out.println(sb.toString());

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
            if (BIRD_NEST_IDS.contains(itemId)) {
                LootAggregation existingByName = findAggregationByName(item.getItemName());
                if (existingByName != null) {
                    existingByName.updateItemAggregation(item.getQuantity());
                } else {
                    aggregationMap.put(itemId, new LootAggregation(itemId, item.getQuantity(), itemManager));
                }
            } else {
                LootAggregation existing = aggregationMap.get(itemId);
                if (existing != null) {
                    existing.updateItemAggregation(item.getQuantity());
                } else {
                    aggregationMap.put(itemId, new LootAggregation(itemId, item.getQuantity(), itemManager));
                }
            }
        }
        this.lootAggregations = new ArrayList<>(aggregationMap.values());
    }

    private ArrayList<LootAggregation> rebuildAggregations() {
        aggregationMap.clear();
        for (TrackableDroppedItem item : droppedItems) {
            int itemId = item.getItemId();
            if (BIRD_NEST_IDS.contains(itemId)) {
                LootAggregation existingByName = findAggregationByName(item.getItemName());
                if (existingByName != null) {
                    existingByName.updateItemAggregation(item.getQuantity());
                } else {
                    aggregationMap.put(itemId, new LootAggregation(itemId, item.getQuantity(), itemManager));
                }
            } else {
                LootAggregation existing = aggregationMap.get(itemId);
                if (existing != null) {
                    existing.updateItemAggregation(item.getQuantity());
                } else {
                    aggregationMap.put(itemId, new LootAggregation(itemId, item.getQuantity(), itemManager));
                }
            }
        }
        this.lootAggregations = new ArrayList<>(aggregationMap.values());
        return lootAggregations;
    }

    /**
     * Finds an existing aggregation entry by item name. Used for bird nests where
     * multiple item IDs share the same display name and should be merged.
     */
    private LootAggregation findAggregationByName(String itemName) {
        for (LootAggregation agg : aggregationMap.values()) {
            if (agg.getItemName().equals(itemName)) {
                return agg;
            }
        }
        return null;
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
