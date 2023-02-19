package com.triptracker;

import java.util.ArrayList;

public class Trip {
    final String tripName;
    ArrayList<NpcLootAggregate> npcAggregations = new ArrayList<>();

    Trip(String tripName) {
        this.tripName = tripName;
    }

    public void addNpcAggregateToTrip (NpcLootAggregate npcLootAggregate) {
        if (contains(npcLootAggregate.getNpcName())) {
            removeNpcAggregate(npcLootAggregate.getNpcName());
        }
        npcAggregations.add(npcLootAggregate);
    }

    public ArrayList<NpcLootAggregate> getTripAggregates() {
        return npcAggregations;
    }

    public boolean contains(String npcName) {
        Boolean tripContainsNpc = false;

        for (NpcLootAggregate npcAggregate : npcAggregations) {
            if (npcAggregate.getNpcName().equals(npcName)) {
                tripContainsNpc = true;
            }
        }
        return tripContainsNpc;
    }

    public void removeNpcAggregate(String npcName) {
        NpcLootAggregate npcAggregateToRemove = null;

        for (NpcLootAggregate npcAggregate : npcAggregations) {
            if (npcAggregate.getNpcName().equals(npcName)) {
                npcAggregateToRemove = npcAggregate;
                break;
            }
        }

        if (npcAggregateToRemove != null) {
            npcAggregations.remove(npcAggregateToRemove);
        } else {
            System.out.println("You have tried to remove an NPC aggregation for a trip where an aggregation does not exist");
        }
    }

    public boolean isActive(String tripName) {
        return this.tripName.equals(tripName);
    }

    public String getTripName() { return tripName; }
}
