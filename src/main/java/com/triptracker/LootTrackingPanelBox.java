package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.*;

public class LootTrackingPanelBox extends JPanel {
    private TrackableItemDrop itemDrop;
    private final int boxType;
    private int numberOfKills;
    private String npcName;
    private long totalGeValue;
    private String lastKillTimeFormatted;
    private ArrayList<LootAggregation> lootAggregations;
    final JPanel dropDetailPanel = new JPanel();
    final JLabel summaryPanelTitle = new JLabel();
    final JLabel dropValueLabel = new JLabel();


    // This constructor is used when creating a loot box panel containing a single drop (e.g., in list view)
    LootTrackingPanelBox(TrackableItemDrop itemDrop) {
        this.itemDrop = itemDrop;
        this.boxType = 0;
    }
    LootTrackingPanelBox(ArrayList<LootAggregation> lootAggregation, String npcName, int numberOfKills, String lastKillTime) {
        this.lootAggregations = lootAggregation;
        this.npcName = npcName;
        this.numberOfKills = numberOfKills;
        this.lastKillTimeFormatted = lastKillTime;
        totalGeValue = lootAggregations.stream().mapToLong(LootAggregation::getTotalGePrice).sum();

        this.boxType = 1;
    }

    JPanel buildPanelBox() {
        dropDetailPanel.setVisible(true);
        final JLabel dropTimeDateLabel = new JLabel();

        // This panel contains the grid that shows item drop detail
        final JPanel droppedItemsPanel = new JPanel();
        droppedItemsPanel.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        droppedItemsPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weightx = 1.0;

        switch (boxType) {
            case 0:
                summaryPanelTitle.setText(itemDrop.getDropNpcName() + " (lvl " + itemDrop.getDropNpcLevel() + ")");
                dropValueLabel.setText(shortenNumber(itemDrop.getTotalDropGeValue()) + " gp");
                dropTimeDateLabel.setText(itemDrop.getDateFromLong(itemDrop.getDropTimeDate()));

                ArrayList<TrackableDroppedItem> droppedItems = itemDrop.getDroppedItems();
                Collections.sort(droppedItems);

                for (final TrackableDroppedItem item: droppedItems) {
                    gbc.gridx = 0;
                    gbc.anchor = GridBagConstraints.LINE_START;

                    JLabel droppedItemNameLabel = new JLabel();
                    droppedItemNameLabel.setText(item.getItemName() + " x" + shortenNumber(item.getQuantity()));
                    droppedItemNameLabel.setFont(FontManager.getRunescapeSmallFont());
                    droppedItemNameLabel.setForeground(Color.LIGHT_GRAY);
                    droppedItemNameLabel.setBorder(new EmptyBorder(2, 5, 4, 5));
                    droppedItemsPanel.add(droppedItemNameLabel, gbc);

                    gbc.gridx = 1;
                    gbc.anchor = GridBagConstraints.LINE_END;

                    JLabel droppedItemValueLabel = new JLabel();
                    droppedItemValueLabel.setText(shortenNumber(item.getTotalGePrice()) + " gp");
                    droppedItemValueLabel.setFont(FontManager.getRunescapeSmallFont());
                    droppedItemValueLabel.setForeground(Color.LIGHT_GRAY);
                    droppedItemValueLabel.setBorder(new EmptyBorder(2, 5, 4, 5));
                    droppedItemValueLabel.setHorizontalAlignment(JLabel.RIGHT);
                    droppedItemsPanel.add(droppedItemValueLabel, gbc);

                    gbc.gridy++;
                }
                break;
            case 1:
                summaryPanelTitle.setText(npcName + " x" + numberOfKills);
                dropValueLabel.setText(shortenNumber(totalGeValue) + "gp");
                dropTimeDateLabel.setText("Last kill at: " + lastKillTimeFormatted);

                Collections.sort(lootAggregations);

                for (LootAggregation lootAggregation : lootAggregations) {
                    gbc.gridx = 0;
                    gbc.anchor = GridBagConstraints.LINE_START;

                    String itemName = lootAggregation.getItemName();
                    long itemQuantity = lootAggregation.getQuantity();
                    long totalValue = lootAggregation.getTotalGePrice();

                    JLabel droppedItemNameLabel = new JLabel(itemName + " x" + shortenNumber(itemQuantity));
                    droppedItemNameLabel.setFont(FontManager.getRunescapeSmallFont());
                    droppedItemNameLabel.setForeground(Color.LIGHT_GRAY);
                    droppedItemNameLabel.setBorder(new EmptyBorder(2, 5, 4, 5));
                    droppedItemsPanel.add(droppedItemNameLabel, gbc);

                    gbc.gridx = 1;
                    gbc.anchor = GridBagConstraints.LINE_END;


                    JLabel droppedItemValue = new JLabel(shortenNumber(totalValue) + " gp", SwingConstants.RIGHT);
                    droppedItemValue.setFont(FontManager.getRunescapeSmallFont());
                    droppedItemValue.setForeground(Color.LIGHT_GRAY);
                    droppedItemValue.setBorder(new EmptyBorder(2, 5, 4, 5));
                    droppedItemsPanel.add(droppedItemValue, gbc);
                    gbc.gridy++;
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
        innerSummaryPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    toggleCollapse();
                }
            }
        });
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

    public String shortenNumber(long numberToShorten) {
        String shortenedNumber = String.valueOf(numberToShorten);

        if (numberToShorten >= 10000 && numberToShorten <= 999999) {
            DecimalFormat df = new DecimalFormat("#.#");
            shortenedNumber = df.format(numberToShorten / 1000.0) + "k";
            
        } else if (numberToShorten >= 1000000 && numberToShorten <= 999999999) {
            DecimalFormat df = new DecimalFormat("#.##");
            shortenedNumber = df.format(numberToShorten / 1000000.00) + "m";

        } else if (numberToShorten >= 1000000000) {
            DecimalFormat df = new DecimalFormat("#.###");
            shortenedNumber = df.format(numberToShorten / 1000000000.000) + "b";
        }

        return shortenedNumber;
    }

    private void toggleCollapse() {
        if (dropDetailPanel.isVisible()) {
            dropDetailPanel.setVisible(false);
            summaryPanelTitle.setForeground(ColorScheme.BRAND_ORANGE_TRANSPARENT);
            dropValueLabel.setForeground(ColorScheme.BRAND_ORANGE_TRANSPARENT);

        } else {
            dropDetailPanel.setVisible(true);
            summaryPanelTitle.setForeground(Color.ORANGE);
            dropValueLabel.setForeground(Color.ORANGE);
        }
    }
}
