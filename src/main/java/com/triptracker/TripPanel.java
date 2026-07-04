package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Swing UI panel for displaying and controlling a Trip.
 * Separated from the Trip data model for clean architecture.
 */
public class TripPanel {
    private final Trip trip;
    private final JButton stopTripButton = new JButton();
    private final JButton deleteTripButton = new JButton();
    private final JButton tripInfoButton = new JButton();
    private final JLabel statusLabel = new JLabel();
    private JPanel innerRightPanel;
    private JPanel lootPanel;

    private static final ImageIcon STOP_TRIP_TRACKER_ICON;
    private static final ImageIcon STOP_TRIP_TRACKER_ICON_HOVER;
    private static final ImageIcon DELETE_TRIP_TRACKER_ICON;
    private static final ImageIcon DELETE_TRIP_TRACKER_ICON_HOVER;
    private static final ImageIcon TRIP_INFO_ICON;
    private static final ImageIcon TRIP_INFO_ICON_HOVER;

    static {
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

    public TripPanel(Trip trip) {
        this.trip = trip;
        statusLabel.setBorder(new EmptyBorder(5, 0, 0, 0));
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
    }

    public JPanel buildHeaderPanel() {
        final JPanel outerPanel = new JPanel();
        outerPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
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

        JLabel summaryPanelTitle = new JLabel(trip.getTripName());
        summaryPanelTitle.setFont(FontManager.getRunescapeBoldFont());
        summaryPanelTitle.setForeground(Color.LIGHT_GRAY);
        summaryPanelTitle.setBorder(new EmptyBorder(5, 0, 0, 0));

        SwingUtil.removeButtonDecorations(tripInfoButton);
        tripInfoButton.setIcon(TRIP_INFO_ICON);
        tripInfoButton.setRolloverIcon(TRIP_INFO_ICON_HOVER);
        tripInfoButton.setToolTipText(buildTooltipText());
        tripInfoButton.setPreferredSize(new Dimension(15, 25));
        tripInfoButton.setBorder(new EmptyBorder(0, 0, 0, 2));
        tripInfoButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                tripInfoButton.setToolTipText(buildTooltipText());
            }
        });

        innerLeftPanel.add(summaryPanelTitle);
        innerLeftPanel.add(statusLabel);
        innerRightPanel.add(tripInfoButton);

        lootPanel = new JPanel();
        lootPanel.setLayout(new BoxLayout(lootPanel, BoxLayout.Y_AXIS));
        outerPanel.add(lootPanel);

        if (trip.getTripStatus()) {
            addStopButton();
        } else {
            addDeleteButton();
        }

        return outerPanel;
    }

    public void stopTrip() {
        if (trip.getTripStatus()) {
            int selectedOption = JOptionPane.showConfirmDialog(null,
                    "If you end this trip you will not be able to restart it. Are you sure?",
                    "Warning!",
                    JOptionPane.YES_NO_OPTION);

            if (selectedOption == JOptionPane.YES_OPTION) {
                stopTripButton.setVisible(false);
                trip.setStatus(false);
                addDeleteButton();
                trip.getParentPlugin().onTripStatusChanged();
            }
        }
    }

    public void deleteTrip() {
        int selectedOption = JOptionPane.showConfirmDialog(null,
                "If you delete this trip you will permanently lose its data. Are you sure?",
                "Warning!",
                JOptionPane.YES_NO_OPTION);

        if (selectedOption == JOptionPane.YES_OPTION) {
            trip.getParentPlugin().removeTrip(trip.getTripName());
        }
    }

    public void addDeleteButton() {
        if (!trip.getTripStatus()) {
            SwingUtil.removeButtonDecorations(deleteTripButton);
            deleteTripButton.setIcon(DELETE_TRIP_TRACKER_ICON);
            deleteTripButton.setRolloverIcon(DELETE_TRIP_TRACKER_ICON_HOVER);
            deleteTripButton.setToolTipText("Click to delete the trip");
            deleteTripButton.setPreferredSize(new Dimension(25, 25));
            deleteTripButton.setBorder(new EmptyBorder(0, 0, 0, 10));

            if (deleteTripButton.getActionListeners().length == 0) {
                deleteTripButton.addActionListener(e -> deleteTrip());
            }

            innerRightPanel.add(deleteTripButton);
            statusLabel.setText("(inactive)");
        }
    }

    public void addStopButton() {
        SwingUtil.removeButtonDecorations(stopTripButton);
        stopTripButton.setIcon(STOP_TRIP_TRACKER_ICON);
        stopTripButton.setRolloverIcon(STOP_TRIP_TRACKER_ICON_HOVER);
        stopTripButton.setToolTipText("Click to end the trip");
        stopTripButton.setPreferredSize(new Dimension(25, 25));
        stopTripButton.setBorder(new EmptyBorder(0, 0, 0, 10));

        if (stopTripButton.getActionListeners().length == 0) {
            stopTripButton.addActionListener(e -> stopTrip());
        }

        innerRightPanel.add(stopTripButton);
        statusLabel.setText("(active)");
    }

    public void setStatus(boolean status) {
        if (!status) {
            stopTripButton.setVisible(false);
            addDeleteButton();
        } else {
            deleteTripButton.setVisible(false);
            addStopButton();
        }
    }

    public void addLootPanel(JPanel panel) {
        this.lootPanel.add(panel, 0);
        this.lootPanel.revalidate();
        this.lootPanel.repaint();
    }

    public JPanel getLootPanel() {
        return lootPanel;
    }

    private String buildTooltipText() {
        String startTime = trip.getTripStartTime() != null ? trip.getTripStartTime() : "unknown";
        String endTime = trip.getTripEndTime() != null ? trip.getTripEndTime() : "n/a";
        return String.format(
                "<html>Trip started: %s<br>Trip ended: %s<br>Trip duration: %s<br>Trip kills: %d<br>Trip value: %s gp</html>",
                startTime,
                endTime,
                trip.calculateTripDuration(),
                trip.getTripKills(),
                FormatUtil.shortenNumber(trip.getTripValue()));
    }

    public Trip getTrip() {
        return trip;
    }
}
