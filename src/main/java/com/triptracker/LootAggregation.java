package com.triptracker;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

public class LootAggregation implements Comparable<LootAggregation> {
    int itemId;
    int quantity;
    String itemName;
    long itemPrice;
    long haPrice;
    ItemManager itemManager;
    ItemComposition itemComposition;

    LootAggregation(int itemId, int quantity, ItemManager itemManager) {
        this.itemId = itemId;
        this.itemManager = itemManager;
        this.itemComposition = itemManager.getItemComposition(itemId);
        this.itemPrice = itemManager.getItemPrice(itemId);
        this.itemName = itemComposition.getMembersName();
        this.haPrice = itemComposition.getHaPrice();

        if (quantity <= 0) {
            this.quantity = 1;
        } else {
            this.quantity = quantity;
        }
    }

    public long getTotalHaPrice() {
        return haPrice * quantity;
    }

    public long getTotalGePrice() {
        return itemPrice * quantity;
    }

    public String getItemName() {
        return itemName;
    }

    public void updateItemAggregation(int quantity) {
        this.quantity += quantity;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getItemId() {
        return itemId;
    }

    public boolean matches(int itemId) {
        return this.itemId == itemId;
    }

    @Override
    public int compareTo(LootAggregation otherItem) {
        return Long.compare(otherItem.getTotalGePrice(), this.getTotalGePrice());
    }
}
