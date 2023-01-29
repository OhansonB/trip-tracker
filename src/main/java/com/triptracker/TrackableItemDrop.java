package com.triptracker;

import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemStack;

import java.text.Format;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class TrackableItemDrop {
    private ArrayList<TrackableDroppedItem> droppedItems;
    private long dropTimeDate;
    private String npcName;
    private int npcCombatLevel;
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
        String dropDescription = "";
        dropDescription += "Drop with value " + totalDropGeValue + " gp ";
        dropDescription += "received at " + getDateFromLong(dropTimeDate) + " ";
        dropDescription += "from " + npcName + " ";

        if (pluginConfig.showLootInChat()) {
            chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .runeLiteFormattedMessage(dropDescription)
                    .build());
        }

        dropDescription += "with combat level " + npcCombatLevel + ". ";
        dropDescription += "Items dropped: \r\n";

        for (final TrackableDroppedItem item: droppedItems) {
            dropDescription += item.describeTrackableDroppedItem() + "\r\n";
        }

        return dropDescription;
    }

    String getDateFromLong(long EpochTimeMillis) {
        Date date = new Date(EpochTimeMillis);
        Format format = new SimpleDateFormat("HH:mm:ss 'on' MMM d YYYY");
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
