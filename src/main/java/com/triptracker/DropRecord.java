package com.triptracker;

import java.util.ArrayList;
import java.util.List;

/**
 * A serializable record of a single loot drop event.
 * This is the JSON-friendly representation of TrackableItemDrop.
 */
public class DropRecord {
    String npcName;
    int npcCombatLevel;
    long dropTime;
    boolean collapsed;
    List<ItemRecord> items;

    public static DropRecord fromDrop(TrackableItemDrop drop) {
        DropRecord record = new DropRecord();
        record.npcName = drop.getDropNpcName();
        record.npcCombatLevel = drop.getDropNpcLevel();
        record.dropTime = drop.getDropTimeDate();
        record.collapsed = drop.isCollapsed();
        record.items = new ArrayList<>();

        for (TrackableDroppedItem item : drop.getDroppedItems()) {
            ItemRecord itemRecord = new ItemRecord();
            itemRecord.itemId = item.getItemId();
            itemRecord.itemName = item.getItemName();
            itemRecord.quantity = item.getQuantity();
            itemRecord.gePrice = (int) (item.getTotalGePrice() / Math.max(item.getQuantity(), 1));
            itemRecord.haPrice = (int) (item.getTotalHaPrice() / Math.max(item.getQuantity(), 1));
            record.items.add(itemRecord);
        }

        return record;
    }

    public TrackableItemDrop toDrop() {
        TrackableItemDrop drop = new TrackableItemDrop(npcName, npcCombatLevel, dropTime);
        for (ItemRecord item : items) {
            TrackableDroppedItem droppedItem = new TrackableDroppedItem(
                    item.itemId, item.itemName, item.quantity, item.gePrice, item.haPrice);
            drop.addLootToDrop(droppedItem);
        }
        drop.setCollapsed(collapsed);
        return drop;
    }

    public static class ItemRecord {
        int itemId;
        String itemName;
        long quantity;
        int gePrice;
        int haPrice;
    }
}
