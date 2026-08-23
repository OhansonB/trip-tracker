package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Swing UI panel for displaying and controlling a Trip.
 * Separated from the Trip data model for clean architecture.
 */
public class TripPanel {
    private static final Color FOCUS_COLOR = new Color(0x5E, 0x9E, 0xD6);
    private static final Border DEFAULT_HEADER_BORDER = new EmptyBorder(5, 7, 5, 7);
    private static final Border FOCUS_HEADER_BORDER = new CompoundBorder(
            new LineBorder(FOCUS_COLOR, 2),
            new EmptyBorder(3, 5, 3, 5)
    );

    private final Trip trip;
    private final JLabel statusLabel = new JLabel();
    private JLabel statsLabel;
    private JLabel summaryPanelTitle;
    private JPanel lootPanel;
    private JPanel headerPanel;
    private Timer statsTimer;
    private Set<String> excludedItems = new HashSet<>();
    private Set<String> excludedNpcs = new HashSet<>();

    public TripPanel(Trip trip) {
        this.trip = trip;
        statusLabel.setBorder(new EmptyBorder(2, 0, 0, 0));
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        if (trip.getTripStatus()) {
            if (trip.isPaused()) {
                statusLabel.setText("(paused)");
                statusLabel.setForeground(Color.YELLOW);
            } else {
                statusLabel.setText("(active)");
                statusLabel.setForeground(Color.GREEN);
            }
        }
    }

    public JPanel buildHeaderPanel() {
        final JPanel outerPanel = new JPanel();
        outerPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
        outerPanel.setLayout(new BorderLayout());
        outerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        headerPanel = new JPanel();
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
        summaryPanelTitle.setForeground(Color.WHITE);
        summaryPanelTitle.setBorder(new EmptyBorder(0, 0, 3, 0));
        contentPanel.add(summaryPanelTitle);

        // Inline stats: kills | value | gp/hr | duration
        String statsText = getAdjustedKills() + " kills \u2022 " +
                FormatUtil.shortenNumber(getAdjustedTripValue()) + " gp \u2022 " +
                FormatUtil.shortenNumber(getAdjustedGpPerHour()) + " gp/hr \u2022 " +
                trip.calculateTripDuration();
        statsLabel = new JLabel(statsText);
        statsLabel.setFont(FontManager.getRunescapeSmallFont());
        statsLabel.setForeground(Color.WHITE);
        contentPanel.add(statsLabel);

        contentPanel.add(statusLabel);
        headerPanel.add(contentPanel, BorderLayout.CENTER);

        // Right-click context menu on the entire header
        headerPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                if (evt.isPopupTrigger()) {
                    showContextMenu(evt.getX(), evt.getY());
                }
            }

            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (evt.isPopupTrigger()) {
                    showContextMenu(evt.getX(), evt.getY());
                }
            }

            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    toggleCollapse();
                }
            }
        });
        headerPanel.setToolTipText("Right-click for options, click to collapse");

        // Make header keyboard-accessible
        headerPanel.setFocusable(true);
        headerPanel.getAccessibleContext().setAccessibleName(trip.getTripName() + " trip panel");
        headerPanel.getAccessibleContext().setAccessibleDescription(
                "Collapsible trip panel. Press Enter or Space to toggle, Shift+F10 for options.");
        headerPanel.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                headerPanel.setBorder(FOCUS_HEADER_BORDER);
                headerPanel.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                headerPanel.setBorder(DEFAULT_HEADER_BORDER);
                headerPanel.repaint();
            }
        });

        // Keyboard listener for collapse/expand and context menu
        headerPanel.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER
                        || e.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE) {
                    toggleCollapse();
                    e.consume();
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_F10 && e.isShiftDown()) {
                    e.consume();
                    showContextMenu(0, headerPanel.getHeight());
                }
            }
        });
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (trip.getTripStatus() && !trip.isPaused()) {
            startStatsTimer();
        }

        lootPanel = new JPanel();
        lootPanel.setLayout(new BoxLayout(lootPanel, BoxLayout.Y_AXIS));
        outerPanel.add(lootPanel);

        // Restore persisted collapse state
        if (trip.isCollapsed()) {
            lootPanel.setVisible(false);
            summaryPanelTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            statsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }

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

        JMenuItem compareItem = new JMenuItem("Compare...");
        compareItem.addActionListener(e -> trip.getParentPlugin().showTripComparison(trip.getTripId()));
        menu.add(compareItem);

        if (trip.getTripStatus()) {
            if (trip.isPaused()) {
                JMenuItem resumeItem = new JMenuItem("Resume trip");
                resumeItem.addActionListener(e -> resumeTrip());
                menu.add(resumeItem);
            } else {
                JMenuItem pauseItem = new JMenuItem("Pause trip");
                pauseItem.addActionListener(e -> pauseTrip());
                menu.add(pauseItem);
            }

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

    private void showContextMenu(int x, int y) {
        JPopupMenu menu = buildContextMenu();
        menu.show(headerPanel, x, y);
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
                statusLabel.setText("");
                trip.getParentPlugin().onTripStatusChanged();
            }
        }
    }

    public void pauseTrip() {
        if (trip.getTripStatus() && !trip.isPaused()) {
            trip.pause();
            stopStatsTimer();
            statusLabel.setText("(paused)");
            statusLabel.setForeground(Color.YELLOW);
            updateStats();
            trip.getParentPlugin().onTripStatusChanged();
        }
    }

    public void resumeTrip() {
        if (trip.getTripStatus() && trip.isPaused()) {
            trip.resume();
            startStatsTimer();
            statusLabel.setText("(active)");
            statusLabel.setForeground(Color.GREEN);
            updateStats();
            trip.getParentPlugin().onTripStatusChanged();
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
            statusLabel.setText("");
        } else {
            if (trip.isPaused()) {
                statusLabel.setText("(paused)");
                statusLabel.setForeground(Color.YELLOW);
            } else {
                statusLabel.setText("(active)");
                statusLabel.setForeground(Color.GREEN);
                startStatsTimer();
            }
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
            String statsText = getAdjustedKills() + " kills \u2022 " +
                    FormatUtil.shortenNumber(getAdjustedTripValue()) + " gp \u2022 " +
                    FormatUtil.shortenNumber(getAdjustedGpPerHour()) + " gp/hr \u2022 " +
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

    private void toggleCollapse() {
        if (lootPanel.isVisible()) {
            lootPanel.setVisible(false);
            summaryPanelTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            statsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            trip.setCollapsed(true);
        } else {
            lootPanel.setVisible(true);
            summaryPanelTitle.setForeground(Color.WHITE);
            statsLabel.setForeground(Color.WHITE);
            trip.setCollapsed(false);
        }
        trip.getParentPlugin().onTripStatusChanged();
    }

    public Trip getTrip() {
        return trip;
    }

    /**
     * Programmatically set the collapsed state without triggering a full panel rebuild.
     */
    public void setCollapsedState(boolean collapsed) {
        if (collapsed) {
            lootPanel.setVisible(false);
            summaryPanelTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            statsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        } else {
            lootPanel.setVisible(true);
            summaryPanelTitle.setForeground(Color.WHITE);
            statsLabel.setForeground(Color.WHITE);
        }
        trip.setCollapsed(collapsed);
    }

    public void setExcludedItems(Set<String> excludedItems) {
        this.excludedItems = excludedItems != null ? excludedItems : new HashSet<>();
    }

    public void setExcludedNpcs(Set<String> excludedNpcs) {
        this.excludedNpcs = excludedNpcs != null ? excludedNpcs : new HashSet<>();
    }

    private long getAdjustedTripValue() {
        if (excludedItems.isEmpty() && excludedNpcs.isEmpty()) {
            return trip.getTripValue();
        }
        long excluded = 0;
        for (NpcLootAggregate agg : trip.getTripAggregates()) {
            if (excludedNpcs.contains(agg.getNpcName().toLowerCase().trim())) {
                for (TrackableDroppedItem item : agg.getDroppedItems()) {
                    excluded += item.getTotalGePrice();
                }
            } else {
                for (TrackableDroppedItem item : agg.getDroppedItems()) {
                    if (excludedItems.contains(item.getItemName().toLowerCase().trim())) {
                        excluded += item.getTotalGePrice();
                    }
                }
            }
        }
        return trip.getTripValue() - excluded;
    }

    private int getAdjustedKills() {
        if (excludedNpcs.isEmpty()) {
            return trip.getTripKills();
        }
        int excluded = 0;
        for (NpcLootAggregate agg : trip.getTripAggregates()) {
            if (excludedNpcs.contains(agg.getNpcName().toLowerCase().trim())) {
                excluded += agg.getNumberOfKills();
            }
        }
        return trip.getTripKills() - excluded;
    }

    private long getAdjustedGpPerHour() {
        long adjustedValue = getAdjustedTripValue();
        long durationSeconds = trip.getDurationSeconds();
        if (durationSeconds <= 0) {
            return 0;
        }
        return (adjustedValue * 3600L) / durationSeconds;
    }
}
