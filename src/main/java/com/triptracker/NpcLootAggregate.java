package com.triptracker;

import net.runelite.client.game.ItemManager;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class NpcLootAggregate {
    final String npcName;
    ArrayList<TrackableDroppedItem> droppedItems;
    final ItemManager itemManager;
    int numberOfKills;
    String lastKillTime;
    ArrayList<LootAggregation> lootAggregations;

    NpcLootAggregate(String npcName, ItemManager itemManager) {
        this.npcName = npcName;
        this.itemManager = itemManager;
        this.numberOfKills = 0;
        droppedItems = new ArrayList<>();
    }

    public void addDropToNpcAggregate (TrackableItemDrop itemDrop) {
        for (TrackableDroppedItem item : itemDrop.getDroppedItems()) {
            droppedItems.add(item);
        }

        Date date = new Date(System.currentTimeMillis());
        Format format = new SimpleDateFormat("HH:mm:ss 'on' MMM d YYYY");
        this.lastKillTime = format.format(date);

        numberOfKills++;
        this.lootAggregations = aggregateNpcDrops();
    }

    public ArrayList<LootAggregation> aggregateNpcDrops() {
        System.out.println("Generating aggregation");
        // Create an empty list of ItemAggregation objects
        ArrayList<LootAggregation> aggregatedItems = new ArrayList<>();

        // Loop through each drop associated with this NPC
        for (TrackableDroppedItem item : droppedItems) {
            int droppedItemId = item.getItemId();
            int droppedItemQuantity = item.getQuantity();

            // Check if droppedItem is contained in the aggregatedItems list
            if (item.containedIn(aggregatedItems)) {
                // Find _which_ aggregatedItem it is in the list
                for (int i = 0; i < aggregatedItems.size(); i++) {
                    if (aggregatedItems.get(i).matches(item.getItemId())) {
                        aggregatedItems.get(i).updateItemAggregation(item.getQuantity());
                    }
                }

            } else {
                // The item is not in the array yet
                aggregatedItems.add(new LootAggregation(droppedItemId, droppedItemQuantity, itemManager));
            }
        }

        // This should be an array list where each unique item dropped for this NPC has an object with the quantity
        // of that item that has dropped
        return aggregatedItems;
    }

    public String getNpcName() {
        return npcName;
    }

    public int getNumberOfKills() { return numberOfKills; }

    public String getLastKillTime() { return lastKillTime; }

    public ArrayList<LootAggregation> getNpcItemAggregations() {
        return lootAggregations;
    }
}
