package com.triptracker;

import net.runelite.api.InventoryID;

/**
 * Defines reward chest/event sources that can be tracked.
 * Each source maps an interface group ID (widget) to an item container ID and display name.
 */
@SuppressWarnings("deprecation")
public enum RewardSource {
    BARROWS(155, InventoryID.BARROWS_REWARD.getId(), "Barrows"),
    CHAMBERS_OF_XERIC(539, InventoryID.CHAMBERS_OF_XERIC_CHEST.getId(), "Chambers of Xeric"),
    THEATRE_OF_BLOOD(23, InventoryID.THEATRE_OF_BLOOD_CHEST.getId(), "Theatre of Blood"),
    TOMBS_OF_AMASCUT(771, InventoryID.TOA_REWARD_CHEST.getId(), "Tombs of Amascut"),
    FISHING_TRAWLER(367, InventoryID.FISHING_TRAWLER_REWARD.getId(), "Fishing Trawler"),
    DRIFT_NET(607, InventoryID.DRIFT_NET_FISHING_REWARD.getId(), "Drift Net"),
    LUNAR_CHEST(868, InventoryID.LUNAR_CHEST.getId(), "Lunar Chest"),
    FORTIS_COLOSSEUM(864, InventoryID.FORTIS_COLOSSEUM_REWARD_CHEST.getId(), "Fortis Colosseum"),
    KINGDOM_OF_MISCELLANIA(616, InventoryID.KINGDOM_OF_MISCELLANIA.getId(), "Kingdom of Miscellania");

    private final int interfaceGroupId;
    private final int containerID;
    private final String displayName;

    RewardSource(int interfaceGroupId, int containerID, String displayName) {
        this.interfaceGroupId = interfaceGroupId;
        this.containerID = containerID;
        this.displayName = displayName;
    }

    public int getInterfaceGroupId() {
        return interfaceGroupId;
    }

    public int getContainerID() {
        return containerID;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Find a RewardSource by its interface group ID.
     * Returns null if no match.
     */
    public static RewardSource fromInterfaceId(int groupId) {
        for (RewardSource source : values()) {
            if (source.interfaceGroupId == groupId) {
                return source;
            }
        }
        return null;
    }
}
