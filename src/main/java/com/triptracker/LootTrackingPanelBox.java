package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

public class LootTrackingPanelBox extends JPanel {
    private final TrackableItemDrop itemDrop;

    LootTrackingPanelBox(TrackableItemDrop itemDrop) {
        this.itemDrop = itemDrop;
        buildPanelBox();
    }

    JPanel buildPanelBox() {
        // Contains all of the other panels that constitute the loot box
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
        final JLabel summaryPanelTitle = new JLabel();
        summaryPanelTitle.setFont(FontManager.getRunescapeSmallFont());
        summaryPanelTitle.setForeground(Color.ORANGE);
        summaryPanelTitle.setText(itemDrop.getDropNpcName() + " (lvl " + itemDrop.getDropNpcLevel() + ")");
        innerSummaryPanel.add(summaryPanelTitle, BorderLayout.WEST);

        // This label summaries the drop value
        final JLabel dropValueLabel = new JLabel();
        dropValueLabel.setFont(FontManager.getRunescapeSmallFont());
        dropValueLabel.setForeground(Color.ORANGE);
        dropValueLabel.setText(itemDrop.getTotalDropGeValue() + "gp");
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
        final JLabel dropTimeDateLabel = new JLabel();
        dropTimeDateLabel.setFont(FontManager.getRunescapeSmallFont());
        dropTimeDateLabel.setForeground(Color.LIGHT_GRAY);
        dropTimeDateLabel.setText(itemDrop.getDateFromLong(itemDrop.getDropTimeDate()));
        dropTimeDateLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        dropDatePanel.add(dropTimeDateLabel, BorderLayout.WEST);

        // This panel contains the grid that shows item drop detail (item sprite and quantity)
        final JPanel droppedItemsPanel = new JPanel();
        droppedItemsPanel.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        droppedItemsPanel.setLayout(new GridLayout(0, 2, 2, 2));

        ArrayList<TrackableDroppedItem> droppedItems = itemDrop.getDroppedItems();

        for (final TrackableDroppedItem item: droppedItems) {
            JLabel droppedItemNameLabel = new JLabel();
            droppedItemNameLabel.setText(item.getItemName() + " x" + item.getQuantity());
            droppedItemNameLabel.setFont(FontManager.getRunescapeSmallFont());
            droppedItemNameLabel.setForeground(Color.LIGHT_GRAY);
            droppedItemNameLabel.setBorder(new EmptyBorder(2, 5, 0, 5));
            droppedItemsPanel.add(droppedItemNameLabel, BorderLayout.WEST);

            JLabel droppedItemValueLabel = new JLabel();
            droppedItemValueLabel.setText(item.getTotalGePrice() + "gp");
            droppedItemValueLabel.setFont(FontManager.getRunescapeSmallFont());
            droppedItemValueLabel.setForeground(Color.LIGHT_GRAY);
            droppedItemValueLabel.setBorder(new EmptyBorder(2, 5, 0, 5));
            droppedItemValueLabel.setHorizontalAlignment(JLabel.RIGHT);
            droppedItemsPanel.add(droppedItemValueLabel);
        }

        dropDetailPanel.add(droppedItemsPanel, BorderLayout.SOUTH);

        return outerPanel;
    }

    void toggleCollapseDetailPanel() {

    }
}
