package com.triptracker;

import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

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

    TrackableItemDrop(String npcName, int npcCombatLevel) {
        this.npcName = npcName;
        this.npcCombatLevel = npcCombatLevel;

        droppedItems = new ArrayList<>();
        dropTimeDate = System.currentTimeMillis();
        totalDropGeValue = 0;
        totalDropHaValue = 0;
    }

    void addLootToDrop(TrackableDroppedItem itemToAdd) {
        droppedItems.add(itemToAdd);
        totalDropGeValue += itemToAdd.getTotalGePrice();
        totalDropHaValue += itemToAdd.getTotalHaPrice();
    }

    String describeTrackableDrop(ChatMessageManager chatMessageManager, EnhancedLootTrackerConfig pluginConfig) {
        StringBuilder dropDescription = new StringBuilder();
        dropDescription.append("Drop with value ").append(totalDropGeValue).append(" gp ");
        dropDescription.append("received at ").append(getDateFromLong(dropTimeDate)).append(" ");
        dropDescription.append("from ").append(npcName).append(" ");

        if (pluginConfig.showLootInChat()) {
            chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .runeLiteFormattedMessage(dropDescription.toString())
                    .build());
        }

        dropDescription.append("with combat level ").append(npcCombatLevel).append(". ");
        dropDescription.append("Items dropped: \r\n");

        for (final TrackableDroppedItem item: droppedItems) {
            dropDescription.append(item.describeTrackableDroppedItem()).append("\r\n");
        }

        return dropDescription.toString();
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
}
