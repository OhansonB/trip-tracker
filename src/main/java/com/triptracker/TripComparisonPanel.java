package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A sub-view panel that shows a comparison table for selected trips.
 * Metrics are column headers, trips are rows.
 */
public class TripComparisonPanel extends JPanel {
    private static final Color FOCUS_COLOR = new Color(0x5E, 0x9E, 0xD6);

    private final List<Trip> allTrips;
    private final Set<Integer> selectedTripIds = new HashSet<>();
    private final JPanel tablePanel;
    private final JPanel checklistPanel;
    private final Runnable onBackAction;
    private final EnhancedLootTrackerPlugin plugin;

    public TripComparisonPanel(List<Trip> allTrips, int preSelectedTripId, Runnable onBackAction, EnhancedLootTrackerPlugin plugin) {
        this.allTrips = allTrips;
        this.onBackAction = onBackAction;
        this.plugin = plugin;
        this.selectedTripIds.add(preSelectedTripId);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        // Back button
        JPanel backPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        backPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        backPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        backPanel.setBorder(new EmptyBorder(0, 0, 8, 0));
        JButton backButton = new JButton("\u2190 Back to trips");
        backButton.setFont(FontManager.getRunescapeSmallFont());
        backButton.setForeground(Color.LIGHT_GRAY);
        backButton.setContentAreaFilled(false);
        backButton.setBorderPainted(false);
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backButton.getAccessibleContext().setAccessibleName("Back to trips");
        backButton.addActionListener(e -> onBackAction.run());
        addKeyboardFocusIndicator(backButton);
        backPanel.add(backButton);
        add(backPanel);

        // Checklist header + select all / deselect all
        JPanel checklistHeaderPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        checklistHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        checklistHeaderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        checklistHeaderPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel selectLabel = new JLabel("Select trips:");
        selectLabel.setFont(FontManager.getRunescapeSmallFont());
        selectLabel.setForeground(Color.LIGHT_GRAY);
        checklistHeaderPanel.add(selectLabel);

        JButton selectAllButton = new JButton("All");
        selectAllButton.setFont(FontManager.getRunescapeSmallFont());
        selectAllButton.setForeground(Color.LIGHT_GRAY);
        selectAllButton.setContentAreaFilled(false);
        selectAllButton.setBorderPainted(false);
        selectAllButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        selectAllButton.getAccessibleContext().setAccessibleName("Select all trips");
        selectAllButton.addActionListener(e -> {
            for (Trip trip : allTrips) {
                selectedTripIds.add(trip.getTripId());
            }
            buildChecklist();
            rebuildTable();
        });
        addKeyboardFocusIndicator(selectAllButton);
        checklistHeaderPanel.add(selectAllButton);

        JLabel separator = new JLabel("|");
        separator.setForeground(new Color(0xB0, 0xB0, 0xB0));
        checklistHeaderPanel.add(separator);

        JButton deselectAllButton = new JButton("None");
        deselectAllButton.setFont(FontManager.getRunescapeSmallFont());
        deselectAllButton.setForeground(Color.LIGHT_GRAY);
        deselectAllButton.setContentAreaFilled(false);
        deselectAllButton.setBorderPainted(false);
        deselectAllButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        deselectAllButton.getAccessibleContext().setAccessibleName("Deselect all trips");
        deselectAllButton.addActionListener(e -> {
            selectedTripIds.clear();
            buildChecklist();
            rebuildTable();
        });
        addKeyboardFocusIndicator(deselectAllButton);
        checklistHeaderPanel.add(deselectAllButton);

        add(checklistHeaderPanel);

        // Trip checkboxes
        checklistPanel = new JPanel();
        checklistPanel.setLayout(new BoxLayout(checklistPanel, BoxLayout.Y_AXIS));
        checklistPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        checklistPanel.setBorder(new EmptyBorder(0, 7, 10, 7));
        checklistPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buildChecklist();
        add(checklistPanel);

        // Export buttons
        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        exportPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        exportPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportPanel.setBorder(new EmptyBorder(0, 0, 12, 0));

        JLabel exportLabel = new JLabel("Export:");
        exportLabel.setFont(FontManager.getRunescapeSmallFont());
        exportLabel.setForeground(Color.LIGHT_GRAY);
        exportPanel.add(exportLabel);

        JButton exportCsvButton = new JButton("CSV");
        exportCsvButton.setFont(FontManager.getRunescapeSmallFont());
        exportCsvButton.setForeground(Color.LIGHT_GRAY);
        exportCsvButton.setContentAreaFilled(false);
        exportCsvButton.setBorderPainted(false);
        exportCsvButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exportCsvButton.getAccessibleContext().setAccessibleName("Export comparison as CSV");
        exportCsvButton.addActionListener(e -> exportCsv());
        addKeyboardFocusIndicator(exportCsvButton);
        exportPanel.add(exportCsvButton);

        JLabel sep1 = new JLabel("|");
        sep1.setForeground(new Color(0xB0, 0xB0, 0xB0));
        exportPanel.add(sep1);

        JButton exportJsonButton = new JButton("JSON");
        exportJsonButton.setFont(FontManager.getRunescapeSmallFont());
        exportJsonButton.setForeground(Color.LIGHT_GRAY);
        exportJsonButton.setContentAreaFilled(false);
        exportJsonButton.setBorderPainted(false);
        exportJsonButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exportJsonButton.getAccessibleContext().setAccessibleName("Export comparison as JSON");
        exportJsonButton.addActionListener(e -> exportJson());
        addKeyboardFocusIndicator(exportJsonButton);
        exportPanel.add(exportJsonButton);

        JLabel sep2 = new JLabel("|");
        sep2.setForeground(new Color(0xB0, 0xB0, 0xB0));
        exportPanel.add(sep2);

        JButton exportPrettyButton = new JButton("Pretty");
        exportPrettyButton.setFont(FontManager.getRunescapeSmallFont());
        exportPrettyButton.setForeground(Color.LIGHT_GRAY);
        exportPrettyButton.setContentAreaFilled(false);
        exportPrettyButton.setBorderPainted(false);
        exportPrettyButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exportPrettyButton.getAccessibleContext().setAccessibleName("Export comparison as plain text for Discord");
        exportPrettyButton.addActionListener(e -> exportPretty());
        addKeyboardFocusIndicator(exportPrettyButton);
        exportPanel.add(exportPrettyButton);

        add(exportPanel);

        // Comparison table
        tablePanel = new JPanel();
        tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.Y_AXIS));
        tablePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tablePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(tablePanel);

        rebuildTable();
    }

    private void buildChecklist() {
        checklistPanel.removeAll();
        for (Trip trip : allTrips) {
            JCheckBox checkBox = new JCheckBox(trip.getTripName());
            checkBox.setFont(FontManager.getRunescapeSmallFont());
            checkBox.setForeground(Color.LIGHT_GRAY);
            checkBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
            checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            checkBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, checkBox.getPreferredSize().height));
            checkBox.setSelected(selectedTripIds.contains(trip.getTripId()));
            checkBox.addActionListener(e -> {
                if (checkBox.isSelected()) {
                    selectedTripIds.add(trip.getTripId());
                } else {
                    selectedTripIds.remove(trip.getTripId());
                }
                rebuildTable();
            });
            addKeyboardFocusIndicator(checkBox);
            checklistPanel.add(checkBox);
        }
    }

    private void rebuildTable() {
        tablePanel.removeAll();

        List<Trip> selected = new ArrayList<>();
        for (Trip trip : allTrips) {
            if (selectedTripIds.contains(trip.getTripId())) {
                selected.add(trip);
            }
        }

        if (selected.size() < 2) {
            JLabel hint = new JLabel("Select at least 2 trips to compare.");
            hint.setFont(FontManager.getRunescapeSmallFont());
            hint.setForeground(new Color(0xB0, 0xB0, 0xB0));
            hint.setBorder(new EmptyBorder(10, 7, 10, 7));
            tablePanel.add(hint);
        } else {
            // Header row
            tablePanel.add(buildHeaderRow());

            // Data rows (one per trip)
            for (Trip trip : selected) {
                tablePanel.add(buildTripRow(trip));
            }
        }

        tablePanel.revalidate();
        tablePanel.repaint();
    }

    private JPanel buildHeaderRow() {
        JPanel row = new JPanel(new GridLayout(1, 5, 4, 0));
        row.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        row.setBorder(new EmptyBorder(6, 7, 6, 7));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

        String[] headers = {"Kills", "Time", "Value", "GP/hr", "GP/kill"};
        for (String header : headers) {
            JLabel label = new JLabel(header, SwingConstants.CENTER);
            label.setFont(FontManager.getRunescapeSmallFont());
            label.setForeground(Color.ORANGE);
            row.add(label);
        }

        return row;
    }

    private JPanel buildTripRow(Trip trip) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        container.setBorder(new EmptyBorder(5, 7, 5, 7));
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

        // Trip name
        JLabel nameLabel = new JLabel(trip.getTripName(), SwingConstants.CENTER);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        nameLabel.setForeground(new Color(0xE0, 0x6C, 0x5C));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.add(nameLabel);

        // Metrics row (respects exclusions)
        AdjustedTripStats stats = getAdjustedStats(trip);
        JPanel metricsRow = new JPanel(new GridLayout(1, 5, 4, 0));
        metricsRow.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        metricsRow.setBorder(new EmptyBorder(2, 0, 0, 0));

        metricsRow.add(buildMetricLabel(String.valueOf(stats.kills)));
        metricsRow.add(buildMetricLabel(trip.calculateTripDuration()));
        metricsRow.add(buildMetricLabel(FormatUtil.shortenNumber(stats.value)));
        metricsRow.add(buildMetricLabel(FormatUtil.shortenNumber(stats.gpPerHour)));
        metricsRow.add(buildMetricLabel(FormatUtil.shortenNumber(stats.gpPerKill)));

        container.add(metricsRow);
        return container;
    }

    private JLabel buildMetricLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.LIGHT_GRAY);
        return label;
    }

    private List<Trip> getSelectedTrips() {
        List<Trip> selected = new ArrayList<>();
        for (Trip trip : allTrips) {
            if (selectedTripIds.contains(trip.getTripId())) {
                selected.add(trip);
            }
        }
        return selected;
    }

    private void exportCsv() {
        List<Trip> selected = getSelectedTrips();
        if (selected.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Trip Name,Kills,Duration (s),Value,GP/hr,GP/kill,Start,End\n");
        for (Trip trip : selected) {
            AdjustedTripStats stats = getAdjustedStats(trip);
            sb.append(escapeCsv(trip.getTripName())).append(",");
            sb.append(stats.kills).append(",");
            sb.append(trip.getDurationSeconds()).append(",");
            sb.append(stats.value).append(",");
            sb.append(stats.gpPerHour).append(",");
            sb.append(stats.gpPerKill).append(",");
            sb.append(escapeCsv(formatIso(trip.getTripStartTimeEpoch()))).append(",");
            sb.append(escapeCsv(trip.getTripEndTimeEpoch() > 0 ? formatIso(trip.getTripEndTimeEpoch()) : "n/a")).append("\n");
        }

        copyToClipboard(sb.toString());
        JOptionPane.showMessageDialog(this, "CSV copied to clipboard!", "Export", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportJson() {
        List<Trip> selected = getSelectedTrips();
        if (selected.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < selected.size(); i++) {
            Trip trip = selected.get(i);
            AdjustedTripStats stats = getAdjustedStats(trip);
            sb.append("  {\n");
            sb.append("    \"name\": \"").append(trip.getTripName().replace("\"", "\\\"")).append("\",\n");
            sb.append("    \"kills\": ").append(stats.kills).append(",\n");
            sb.append("    \"durationSeconds\": ").append(trip.getDurationSeconds()).append(",\n");
            sb.append("    \"value\": ").append(stats.value).append(",\n");
            sb.append("    \"gpPerHour\": ").append(stats.gpPerHour).append(",\n");
            sb.append("    \"gpPerKill\": ").append(stats.gpPerKill).append(",\n");
            sb.append("    \"start\": \"").append(formatIso(trip.getTripStartTimeEpoch())).append("\",\n");
            sb.append("    \"end\": \"").append(trip.getTripEndTimeEpoch() > 0 ? formatIso(trip.getTripEndTimeEpoch()) : "n/a").append("\"\n");
            sb.append("  }");
            if (i < selected.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");

        copyToClipboard(sb.toString());
        JOptionPane.showMessageDialog(this, "JSON copied to clipboard!", "Export", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportPretty() {
        List<Trip> selected = getSelectedTrips();
        if (selected.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            Trip trip = selected.get(i);
            AdjustedTripStats stats = getAdjustedStats(trip);

            sb.append(trip.getTripName()).append("\n");
            sb.append("  Kills: ").append(stats.kills);
            sb.append(" | Duration: ").append(trip.calculateTripDuration()).append("\n");
            sb.append("  Value: ").append(FormatUtil.shortenNumber(stats.value)).append(" gp");
            sb.append(" | GP/hr: ").append(FormatUtil.shortenNumber(stats.gpPerHour));
            sb.append(" | GP/kill: ").append(FormatUtil.shortenNumber(stats.gpPerKill)).append("\n");

            if (i < selected.size() - 1) {
                sb.append("\n");
            }
        }

        copyToClipboard(sb.toString());
        JOptionPane.showMessageDialog(this, "Pretty text copied to clipboard!", "Export", JOptionPane.INFORMATION_MESSAGE);
    }

    private AdjustedTripStats getAdjustedStats(Trip trip) {
        Set<String> excludedItems = plugin.getExcludedItems();
        Set<String> excludedNpcs = plugin.getExcludedNpcs();

        long adjustedValue = trip.getTripValue();
        int adjustedKills = trip.getTripKills();

        for (NpcLootAggregate agg : trip.getTripAggregates()) {
            if (excludedNpcs.contains(agg.getNpcName().toLowerCase().trim())) {
                adjustedKills -= agg.getNumberOfKills();
                for (TrackableDroppedItem item : agg.getDroppedItems()) {
                    adjustedValue -= item.getTotalGePrice();
                }
            } else {
                for (TrackableDroppedItem item : agg.getDroppedItems()) {
                    if (excludedItems.contains(item.getItemName().toLowerCase().trim())) {
                        adjustedValue -= item.getTotalGePrice();
                    }
                }
            }
        }

        long durationSeconds = trip.getDurationSeconds();
        long gpPerHour = durationSeconds > 0 ? (adjustedValue * 3600L) / durationSeconds : 0;
        long gpPerKill = adjustedKills > 0 ? adjustedValue / adjustedKills : 0;

        return new AdjustedTripStats(adjustedKills, adjustedValue, gpPerHour, gpPerKill);
    }

    private static class AdjustedTripStats {
        final int kills;
        final long value;
        final long gpPerHour;
        final long gpPerKill;

        AdjustedTripStats(int kills, long value, long gpPerHour, long gpPerKill) {
            this.kills = kills;
            this.value = value;
            this.gpPerHour = gpPerHour;
            this.gpPerKill = gpPerKill;
        }
    }

    private void copyToClipboard(String text) {
        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String formatIso(long epochMillis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        return sdf.format(new java.util.Date(epochMillis));
    }

    private static final javax.swing.border.Border FOCUS_PLACEHOLDER_BORDER = new EmptyBorder(2, 2, 2, 2);
    private static final javax.swing.border.Border FOCUS_VISIBLE_BORDER = new LineBorder(FOCUS_COLOR, 2);

    /**
     * Adds a keyboard-only focus indicator (blue outline) to a component.
     * Only shows when focus is gained via keyboard, not mouse click.
     * Pre-allocates border space so layout doesn't shift.
     */
    private static void addKeyboardFocusIndicator(JComponent component) {
        // Pre-allocate the same insets so the component size stays constant
        if (component instanceof AbstractButton) {
            ((AbstractButton) component).setBorderPainted(true);
        }
        component.setBorder(FOCUS_PLACEHOLDER_BORDER);

        component.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                component.putClientProperty("focusedByMouse", Boolean.TRUE);
            }
        });
        component.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (Boolean.TRUE.equals(component.getClientProperty("focusedByMouse"))) {
                    component.putClientProperty("focusedByMouse", null);
                    return;
                }
                component.setBorder(FOCUS_VISIBLE_BORDER);
                component.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                component.setBorder(FOCUS_PLACEHOLDER_BORDER);
                component.repaint();
            }
        });
    }
}
