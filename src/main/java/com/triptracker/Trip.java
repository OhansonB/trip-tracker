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
    private final JButton tripInfoButton = new JButton();
    private static final ImageIcon STOP_TRIP_TRACKER_ICON;
    private static final ImageIcon STOP_TRIP_TRACKER_ICON_HOVER;
    private static final ImageIcon DELETE_TRIP_TRACKER_ICON;
    private static final ImageIcon DELETE_TRIP_TRACKER_ICON_HOVER;
    private static final ImageIcon TRIP_INFO_ICON;
    private static final ImageIcon TRIP_INFO_ICON_HOVER;
    private final JLabel statusLabel = new JLabel();
    final String tripName;
    ArrayList<NpcLootAggregate> npcAggregations = new ArrayList<>();
    private JPanel innerRightPanel;
    private JPanel lootPanel;
    private final EnhancedLootTrackerPlugin parentPlugin;
    boolean tripActive;

    static {
        // Trip control icons
        final BufferedImage stopIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/stop_trip_icon.png");
        final BufferedImage deleteIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/delete_trip_icon.png");
        final BufferedImage infoIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/info_icon.png");

        STOP_TRIP_TRACKER_ICON = new ImageIcon(stopIcon);
        STOP_TRIP_TRACKER_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(stopIcon, -180));

        DELETE_TRIP_TRACKER_ICON = new ImageIcon(deleteIcon);
        DELETE_TRIP_TRACKER_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(deleteIcon, -180));

        TRIP_INFO_ICON = new ImageIcon(infoIcon);
        TRIP_INFO_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(infoIcon, -180));
    }

    Trip(String tripName, EnhancedLootTrackerPlugin parentPlugin) {
        this.tripName = tripName;
        this.parentPlugin = parentPlugin;

        this.tripActive = true;

        statusLabel.setBorder(new EmptyBorder(5,0,0,0));
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
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
        boolean tripContainsNpc = false;

        for (NpcLootAggregate npcAggregate : npcAggregations) {
            if (npcAggregate.getNpcName().equals(npcName)) {
                tripContainsNpc = true;
                break;
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
        outerPanel.setBorder(new EmptyBorder(0,0,0,0));
        outerPanel.setLayout(new BorderLayout());
        outerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        final JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new GridLayout(0, 2));
        innerPanel.setPreferredSize(new Dimension(230, 35));
        outerPanel.add(innerPanel, BorderLayout.PAGE_START);

        JPanel innerLeftPanel = new JPanel();
        innerLeftPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        innerLeftPanel.setLayout(new FlowLayout(FlowLayout.LEADING));
        innerPanel.add(innerLeftPanel);

        innerRightPanel = new JPanel();
        innerRightPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        innerRightPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        innerPanel.add(innerRightPanel);

        // This label summaries the trip name
        JLabel summaryPanelTitle = new JLabel(tripName);
        summaryPanelTitle.setFont(FontManager.getRunescapeBoldFont());
        summaryPanelTitle.setForeground(Color.LIGHT_GRAY);
        summaryPanelTitle.setBorder(new EmptyBorder(5,0,0,0));

        String infoLabelText = "<html>First line<br>Second line</html>";
        SwingUtil.removeButtonDecorations(tripInfoButton);
        tripInfoButton.setIcon(TRIP_INFO_ICON);
        tripInfoButton.setRolloverIcon(TRIP_INFO_ICON_HOVER);
        tripInfoButton.setToolTipText(infoLabelText);
        tripInfoButton.setPreferredSize(new Dimension(15,25));
        tripInfoButton.setBorder(new EmptyBorder(0,0,0,2));

        innerLeftPanel.add(summaryPanelTitle);
        innerLeftPanel.add(statusLabel);
        innerRightPanel.add(tripInfoButton);

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
            deleteTripButton.setPreferredSize(new Dimension(25,25));
            deleteTripButton.setBorder(new EmptyBorder(0,0,0,10));



            if (deleteTripButton.getActionListeners().length == 0) {
                deleteTripButton.addActionListener(e -> deleteTrip());
            }

            innerRightPanel.add(deleteTripButton);

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
        stopTripButton.setPreferredSize(new Dimension(25,25));
        stopTripButton.setBorder(new EmptyBorder(0,0,0,10));

        if (stopTripButton.getActionListeners().length == 0) {
            stopTripButton.addActionListener(e -> stopTrip());
        }

        innerRightPanel.add(stopTripButton);

        statusLabel.setText("(active)");
    }

    public boolean getTripStatus() {
        return tripActive;
    }

    public void setStatus(boolean status) {
        tripActive = status;

        if (!tripActive) {
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

    public JPanel getLootPanel() {
        return lootPanel;
    }
}
