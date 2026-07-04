package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Swing UI panel for displaying and controlling a Trip.
 * Separated from the Trip data model for clean architecture.
 */
public class TripPanel {
    private final Trip trip;
    private final JLabel statusLabel = new JLabel();
    private JLabel statsLabel;
    private JLabel summaryPanelTitle;
    private JPanel lootPanel;
    private Timer statsTimer;

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

        final JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BorderLayout());
        headerPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        headerPanel.setBorder(new EmptyBorder(5, 7, 5, 7));
        outerPanel.add(headerPanel, BorderLayout.PAGE_START);

        // Content panel: trip name + stats + status
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);

        summaryPanelTitle = new JLabel(trip.getTripName());
        summaryPanelTitle.setFont(FontManager.getRunescapeBoldFont());
        summaryPanelTitle.setForeground(Color.LIGHT_GRAY);
        contentPanel.add(summaryPanelTitle);

        // Inline stats: kills | value | gp/hr | duration
        String statsText = trip.getTripKills() + " kills \u2022 " +
                FormatUtil.shortenNumber(trip.getTripValue()) + " gp \u2022 " +
                FormatUtil.shortenNumber(trip.getGpPerHour()) + " gp/hr \u2022 " +
                trip.calculateTripDuration();
        statsLabel = new JLabel(statsText);
        statsLabel.setFont(FontManager.getRunescapeSmallFont());
        statsLabel.setForeground(Color.GRAY);
        contentPanel.add(statsLabel);

        contentPanel.add(statusLabel);
        headerPanel.add(contentPanel, BorderLayout.CENTER);

        // Right-click context menu on the entire header
        headerPanel.setComponentPopupMenu(buildContextMenu());
        headerPanel.setToolTipText("Right-click for options");

        if (trip.getTripStatus()) {
            startStatsTimer();
        }

        lootPanel = new JPanel();
        lootPanel.setLayout(new BoxLayout(lootPanel, BoxLayout.Y_AXIS));
        outerPanel.add(lootPanel);

        return outerPanel;
    }

    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem renameItem = new JMenuItem("Rename");
        renameItem.addActionListener(e -> {
            String newName = JOptionPane.showInputDialog(null,
                    "Enter new trip name:", trip.getTripName());
            if (newName != null && !newName.trim().isEmpty()) {
                trip.setTripName(newName.trim());
                summaryPanelTitle.setText(newName.trim());
                trip.getParentPlugin().onTripStatusChanged();
            }
        });
        menu.add(renameItem);

        if (trip.getTripStatus()) {
            JMenuItem stopItem = new JMenuItem("Stop trip");
            stopItem.addActionListener(e -> stopTrip());
            menu.add(stopItem);
        } else {
            JMenuItem deleteItem = new JMenuItem("Delete trip");
            deleteItem.addActionListener(e -> deleteTrip());
            menu.add(deleteItem);
        }

        return menu;
    }

    public void stopTrip() {
        if (trip.getTripStatus()) {
            int selectedOption = JOptionPane.showConfirmDialog(null,
                    "If you end this trip you will not be able to restart it. Are you sure?",
                    "Warning!",
                    JOptionPane.YES_NO_OPTION);

            if (selectedOption == JOptionPane.YES_OPTION) {
                trip.setStatus(false);
                stopStatsTimer();
                updateStats();
                statusLabel.setText("(inactive)");
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
            stopStatsTimer();
            trip.getParentPlugin().removeTrip(trip.getTripName());
        }
    }

    public void setStatus(boolean status) {
        if (!status) {
            stopStatsTimer();
            statusLabel.setText("(inactive)");
        } else {
            statusLabel.setText("(active)");
        }
    }

    public void addLootPanel(JPanel panel) {
        this.lootPanel.add(panel, 0);
        this.lootPanel.revalidate();
        this.lootPanel.repaint();
        updateStats();
    }

    /**
     * Refreshes the inline stats label with current trip data.
     */
    public void updateStats() {
        if (statsLabel != null) {
            String statsText = trip.getTripKills() + " kills \u2022 " +
                    FormatUtil.shortenNumber(trip.getTripValue()) + " gp \u2022 " +
                    FormatUtil.shortenNumber(trip.getGpPerHour()) + " gp/hr \u2022 " +
                    trip.calculateTripDuration();
            statsLabel.setText(statsText);
        }
    }

    private void startStatsTimer() {
        if (statsTimer == null) {
            statsTimer = new Timer(1000, e -> updateStats());
            statsTimer.start();
        }
    }

    private void stopStatsTimer() {
        if (statsTimer != null) {
            statsTimer.stop();
            statsTimer = null;
        }
    }

    public JPanel getLootPanel() {
        return lootPanel;
    }

    public Trip getTrip() {
        return trip;
    }
}
