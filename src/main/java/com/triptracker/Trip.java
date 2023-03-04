package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class Trip {
    private final JButton stopTripButton = new JButton();
    private final JButton deleteTripButton = new JButton();
    private static final ImageIcon STOP_TRIP_TRACKER_ICON;
    private static final ImageIcon STOP_TRIP_TRACKER_ICON_HOVER;
    private static final ImageIcon DELETE_TRIP_TRACKER_ICON;
    private static final ImageIcon DELETE_TRIP_TRACKER_ICON_HOVER;
    private JLabel statusLabel = new JLabel();
    final String tripName;
    ArrayList<NpcLootAggregate> npcAggregations = new ArrayList<>();
    private JPanel innerSummaryPanel;
    private JPanel lootPanel;
    private EnhancedLootTrackerPlugin parentPlugin;
    boolean tripActive;

    static {
        // Trip control icons
        final BufferedImage stopIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/stop_trip_icon.png");
        final BufferedImage deleteIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/delete_trip_icon.png");

        STOP_TRIP_TRACKER_ICON = new ImageIcon(stopIcon);
        STOP_TRIP_TRACKER_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(stopIcon, -180));

        DELETE_TRIP_TRACKER_ICON = new ImageIcon(deleteIcon);
        DELETE_TRIP_TRACKER_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(deleteIcon, -180));
    }

    Trip(String tripName, EnhancedLootTrackerPlugin parentPlugin) {
        this.tripName = tripName;
        this.parentPlugin = parentPlugin;

        this.tripActive = true;

        statusLabel.setBorder(new EmptyBorder(0,10,0,0));
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

    public boolean matches(String tripName) {
        return this.tripName.equals(tripName);
    }

    public String getTripName() { return tripName; }

    public JPanel buildHeaderPanel() {
        final JPanel outerPanel = new JPanel();
        outerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        outerPanel.setLayout(new BorderLayout());
        outerPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

        innerSummaryPanel = new JPanel();
        innerSummaryPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        innerSummaryPanel.setLayout(new BorderLayout());
        innerSummaryPanel.setBorder(new EmptyBorder(7, 10, 7, 7));
        innerSummaryPanel.setPreferredSize(new Dimension(0, 30));
        outerPanel.add(innerSummaryPanel, BorderLayout.NORTH);

        // This label summaries the npc name and level
        JLabel summaryPanelTitle = new JLabel(tripName);
        summaryPanelTitle.setFont(FontManager.getRunescapeBoldFont());
        summaryPanelTitle.setForeground(Color.LIGHT_GRAY);
        innerSummaryPanel.add(summaryPanelTitle, BorderLayout.WEST);

        innerSummaryPanel.add(statusLabel);

        lootPanel = new JPanel();
        lootPanel.setLayout(new BoxLayout(lootPanel, BoxLayout.Y_AXIS));
        outerPanel.add(lootPanel);

        if (tripActive) {
            addStopButton();
        } else {
            addDeleteButton();
        }

        return outerPanel;
    }

    public void stopTrip() {
        if (tripActive) {
            int selectedOption = JOptionPane.showConfirmDialog(null,
                    "If you end this trip you will not be able to restart it. Are you sure?",
                    "Warning!",
                    JOptionPane.YES_NO_OPTION);

            switch (selectedOption) {
                case JOptionPane.YES_OPTION:
                    tripActive = false;
                    stopTripButton.setVisible(false);
                    addDeleteButton();

                    break;
                case JOptionPane.NO_OPTION:
                    break;
            }
        }
    }
    public void deleteTrip() {
        int selectedOption = JOptionPane.showConfirmDialog(null,
                "If you delete this trip you will permanently lose its data. Are you sure?",
                "Warning!",
                JOptionPane.YES_NO_OPTION);

        switch (selectedOption) {
            case JOptionPane.YES_OPTION:
                parentPlugin.removeTrip(this.tripName);
                break;
            case JOptionPane.NO_OPTION:
                System.out.println("Trip not deleted!");
                break;
        }
    }

    public void addDeleteButton() {
        if (!tripActive) {
            SwingUtil.removeButtonDecorations(deleteTripButton);
            deleteTripButton.setIcon(DELETE_TRIP_TRACKER_ICON);
            deleteTripButton.setRolloverIcon(DELETE_TRIP_TRACKER_ICON_HOVER);
            deleteTripButton.setToolTipText("Click to delete the trip");
            deleteTripButton.setBorder(null);

            if (deleteTripButton.getActionListeners().length == 0) {
                deleteTripButton.addActionListener(e -> deleteTrip());
            }

            innerSummaryPanel.add(deleteTripButton, BorderLayout.EAST);

            statusLabel.setText("(inactive)");
        } else {
            System.out.println("Trip must be inactive to add delete button");
        }
    }

    public void addStopButton() {
        SwingUtil.removeButtonDecorations(stopTripButton);
        stopTripButton.setIcon(STOP_TRIP_TRACKER_ICON);
        stopTripButton.setRolloverIcon(STOP_TRIP_TRACKER_ICON_HOVER);
        stopTripButton.setToolTipText("Click to end the trip");
        stopTripButton.setBorder(null);

        if (stopTripButton.getActionListeners().length == 0) {
            stopTripButton.addActionListener(e -> stopTrip());
        }

        innerSummaryPanel.add(stopTripButton, BorderLayout.EAST);

        statusLabel.setText("(active)");
    }

    public boolean getTripStatus() {
        return tripActive;
    }

    public void setStatus(boolean status) {
        tripActive = status;

        if (tripActive == false) {
            stopTripButton.setVisible(false);
            addDeleteButton();
        } else {
            deleteTripButton.setVisible(false);
            addStopButton();
        }
    }

    public void addLootPanel(JPanel lootPanel) {
        this.lootPanel.add(lootPanel, 0);
        this.lootPanel.revalidate();
        this.lootPanel.repaint();
    }
}
