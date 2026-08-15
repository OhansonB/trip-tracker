package com.triptracker;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class TrackableItemDrop {
    private final ArrayList<TrackableDroppedItem> droppedItems;
    private final long dropTimeDate;
    private final String npcName;
    private final int npcCombatLevel;
    private long totalDropGeValue;
    private long totalDropHaValue;
    private boolean collapsed;

    TrackableItemDrop(String npcName, int npcCombatLevel) {
        this.npcName = npcName;
        this.npcCombatLevel = npcCombatLevel;

        droppedItems = new ArrayList<>();
        dropTimeDate = System.currentTimeMillis();
        totalDropGeValue = 0;
        totalDropHaValue = 0;
    }

    /**
     * Constructor for restoring a drop from persisted data with a known timestamp.
     */
    TrackableItemDrop(String npcName, int npcCombatLevel, long dropTimeDate) {
        this.npcName = npcName;
        this.npcCombatLevel = npcCombatLevel;

        droppedItems = new ArrayList<>();
        this.dropTimeDate = dropTimeDate;
        totalDropGeValue = 0;
        totalDropHaValue = 0;
    }

    void addLootToDrop(TrackableDroppedItem itemToAdd) {
        droppedItems.add(itemToAdd);
        totalDropGeValue += itemToAdd.getTotalGePrice();
        totalDropHaValue += itemToAdd.getTotalHaPrice();
    }

    String getDateFromLong(long EpochTimeMillis) {
        Date date = new Date(EpochTimeMillis);
        Format format = new SimpleDateFormat("HH:mm:ss 'on' MMM d yyyy");
        return format.format(date);
    }

    long getDropTimeDate() {
        return dropTimeDate;
    }

    String getDropNpcName() {
        return npcName;
    }

    int getDropNpcLevel() {
        return npcCombatLevel;
    }

    ArrayList<TrackableDroppedItem> getDroppedItems() {
        return droppedItems;
    }

    long getTotalDropGeValue() {
        return totalDropGeValue;
    }

    long getTotalDropHaValue() {
        return totalDropHaValue;
    }

    boolean isCollapsed() {
        return collapsed;
    }

    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }
}
