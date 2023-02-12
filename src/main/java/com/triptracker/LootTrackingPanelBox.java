package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;

public class LootTrackingPanelBox extends JPanel {
    private TrackableItemDrop itemDrop;
    private int boxType;
    private LinkedHashMap<String, Object> droppedItemsSummary = new LinkedHashMap<>();
    private int numberOfKills;
    private String npcName;
    private long totalGeValue;
    private long lastKillTime;


    // This constructor is used when creating a loot box panel containing a single drop (e.g., in list view)
    LootTrackingPanelBox(TrackableItemDrop itemDrop) {
        this.itemDrop = itemDrop;
        this.boxType = 0;
    }

    // This constructor is used when creating a loot box panel containing multiple drops (e.g., in grouped and trip
    // mode)
    LootTrackingPanelBox(LinkedHashMap<String, Object> droppedItemsSummary, String npcName) {
        this.droppedItemsSummary = droppedItemsSummary;
        this.npcName = npcName;
        this.numberOfKills = (int) droppedItemsSummary.get("numberOfDrops");
        this.totalGeValue = (long) droppedItemsSummary.get("totalGeValue");
        this.lastKillTime = (long) droppedItemsSummary.get("lastKillTime");

        this.boxType = 1;
    }

    JPanel buildPanelBox() {
        final JLabel summaryPanelTitle = new JLabel();
        final JLabel dropValueLabel = new JLabel();
        final JLabel dropTimeDateLabel = new JLabel();

        // This panel contains the grid that shows item drop detail
        final JPanel droppedItemsPanel = new JPanel();
        droppedItemsPanel.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);

        switch (boxType) {
            case 0:
                droppedItemsPanel.setLayout(new GridLayout(0, 2, 2, 2));
                summaryPanelTitle.setText(itemDrop.getDropNpcName() + " (lvl " + itemDrop.getDropNpcLevel() + ")");
                dropValueLabel.setText(itemDrop.getTotalDropGeValue() + "gp");
                dropTimeDateLabel.setText(itemDrop.getDateFromLong(itemDrop.getDropTimeDate()));

                ArrayList<TrackableDroppedItem> droppedItems = itemDrop.getDroppedItems();
                Collections.sort(droppedItems);

                for (final TrackableDroppedItem item: droppedItems) {
                    JLabel droppedItemNameLabel = new JLabel();
                    droppedItemNameLabel.setText(item.getItemName() + " x" + item.getQuantity());
                    droppedItemNameLabel.setFont(FontManager.getRunescapeSmallFont());
                    droppedItemNameLabel.setForeground(Color.LIGHT_GRAY);
                    droppedItemNameLabel.setBorder(new EmptyBorder(2, 5, 4, 5));
                    droppedItemsPanel.add(droppedItemNameLabel, BorderLayout.WEST);

                    JLabel droppedItemValueLabel = new JLabel();
                    droppedItemValueLabel.setText(item.getTotalGePrice() + "gp");
                    droppedItemValueLabel.setFont(FontManager.getRunescapeSmallFont());
                    droppedItemValueLabel.setForeground(Color.LIGHT_GRAY);
                    droppedItemValueLabel.setBorder(new EmptyBorder(2, 5, 4, 5));
                    droppedItemValueLabel.setHorizontalAlignment(JLabel.RIGHT);
                    droppedItemsPanel.add(droppedItemValueLabel);
                }
                break;
            case 1:
                droppedItemsPanel.setLayout(new GridLayout(0, 1, 2, 2));
                System.out.println(droppedItemsSummary);

                summaryPanelTitle.setText(npcName + " x" + numberOfKills);
                dropValueLabel.setText(totalGeValue + "gp");

                Date date = new Date(lastKillTime);
                Format format = new SimpleDateFormat("HH:mm:ss 'on' MMM d YYYY");
                dropTimeDateLabel.setText("Last kill at: " + format.format(date));

                Set<String> mapKeys = droppedItemsSummary.keySet();

                for (String key : mapKeys) {
                    if (key != "totalGeValue" && key != "numberOfDrops" && key != "lastKillTime") {
                        JLabel droppedItemNameLabel = new JLabel();
                        droppedItemNameLabel.setText(key + " x" + droppedItemsSummary.get(key));
                        droppedItemNameLabel.setFont(FontManager.getRunescapeSmallFont());
                        droppedItemNameLabel.setForeground(Color.LIGHT_GRAY);
                        droppedItemNameLabel.setBorder(new EmptyBorder(2, 5, 4, 5));
                        droppedItemsPanel.add(droppedItemNameLabel, BorderLayout.WEST);
                    }
                }
                break;
            default:
                break;
        }


        // Contains all the other panels that constitute the loot box
        final JPanel outerPanel = new JPanel();
        outerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        outerPanel.setLayout(new BorderLayout());
        outerPanel.setBorder(new EmptyBorder(6, 0, 0, 0));

        // This panel provides the summary information (npc name, drop value, etc.)
        final JPanel innerSummaryPanel = new JPanel();
        innerSummaryPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        innerSummaryPanel.setLayout(new BorderLayout());
        innerSummaryPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
        outerPanel.add(innerSummaryPanel, BorderLayout.NORTH);

        // This label summaries the npc name and level
        summaryPanelTitle.setFont(FontManager.getRunescapeSmallFont());
        summaryPanelTitle.setForeground(Color.ORANGE);
        innerSummaryPanel.add(summaryPanelTitle, BorderLayout.WEST);

        // This label summaries the drop value

        dropValueLabel.setFont(FontManager.getRunescapeSmallFont());
        dropValueLabel.setForeground(Color.ORANGE);
        innerSummaryPanel.add(dropValueLabel, BorderLayout.EAST);

        // This panel sits under the summary panel and is a parent panel for all other panels showing drop detail
        // such as drop date and dropped items
        final JPanel dropDetailPanel = new JPanel();
        dropDetailPanel.setBackground(ColorScheme.GRAND_EXCHANGE_LIMIT);
        dropDetailPanel.setLayout(new BorderLayout());
        outerPanel.add(dropDetailPanel);

        // This panel contains the label that shows the date and time of the drop
        final JPanel dropDatePanel = new JPanel();
        dropDatePanel.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        dropDatePanel.setLayout(new BorderLayout());
        dropDetailPanel.add(dropDatePanel);

        // This label shows the time and date of the drop
        dropTimeDateLabel.setFont(FontManager.getRunescapeSmallFont());
        dropTimeDateLabel.setForeground(Color.LIGHT_GRAY);
        dropTimeDateLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        dropDatePanel.add(dropTimeDateLabel, BorderLayout.WEST);

        dropDetailPanel.add(droppedItemsPanel, BorderLayout.SOUTH);

        return outerPanel;
    }
}
