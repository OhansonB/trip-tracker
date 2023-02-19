package com.triptracker;

import java.text.MessageFormat;
import java.util.ArrayList;

public class TrackableDroppedItem implements Comparable<TrackableDroppedItem> {

    private final int itemId;
    private final String itemName;
    private int quantity;
    private final int gePrice;
    private final int haPrice;

    TrackableDroppedItem(int itemId, String itemName, int quantity, int gePrice, int haPrice) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.gePrice = gePrice;
        this.haPrice = haPrice;
    }

    long getTotalGePrice() {
        return (long) gePrice * quantity;
    }

    long getTotalHaPrice() {
        return (long) haPrice * quantity;
    }

    String describeTrackableDroppedItem() {
        String dropDescription = MessageFormat.format("Item Id: {0}, itemName: {1}, quantity: {2}, gePrice: {3}, haPrice: {4}", itemId, itemName, quantity, getTotalGePrice(), getTotalHaPrice());
        return dropDescription;
    }

    String getItemName() { return itemName; }

    int getQuantity() { return quantity; }

    int getItemId() { return itemId; }

    @Override
    public int compareTo(TrackableDroppedItem otherItem) {
        return Long.compare(otherItem.getTotalGePrice(), this.getTotalGePrice());
    }

    public boolean containedIn(ArrayList<LootAggregation> aggregatedItemList) {
        boolean recordFound = false;
        for (LootAggregation aggregation : aggregatedItemList) {
            if (aggregation.getItemId() == this.getItemId())
                recordFound = true;
        }
        return recordFound;
    }
}
