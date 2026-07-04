package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class EnhancedLootTrackerPanel extends PluginPanel {
    private EnhancedLootTrackerPlugin parentPlugin;
    private JPanel lootBoxPanel;
    private final int DEFAULT_TRACKING_MODE = 0;
    protected int selectedTrackingMode = DEFAULT_TRACKING_MODE;
    private static final ImageIcon GROUPED_MODE_ICON;
    private static final ImageIcon GROUPED_MODE_ICON_HOVER;
    private static final ImageIcon GROUPED_MODE_ICON_UNSELECTED;
    private static final ImageIcon LIST_MODE_ICON;
    private static final ImageIcon LIST_MODE_ICON_HOVER;
    private static final ImageIcon LIST_MODE_ICON_UNSELECTED;
    private static final ImageIcon TRIP_MODE_ICON;
    private static final ImageIcon TRIP_MODE_ICON_HOVER;
    private static final ImageIcon TRIP_MODE_ICON_UNSELECTED;
    private static final ImageIcon ADD_TRIP_TRACKER_ICON;
    private static final ImageIcon ADD_TRIP_TRACKER_ICON_HOVER;
    private final JRadioButton groupedModeButton = new JRadioButton();
    private final JRadioButton listModeButton = new JRadioButton();
    private final JRadioButton tripModeButton = new JRadioButton();
    private final JButton addTripButton = new JButton();
    private final LinkedHashMap<String, JPanel> groupedLootBoxPanels = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, TripPanel> tripsMap = new LinkedHashMap<>();
    private LinkedHashMap<String, LootTrackingPanelBox> activeTripLootPanels = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>> tripPanelBoxes = new LinkedHashMap<>();

    static {
        // Tracker mode control icons
        final BufferedImage groupedIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/grouped_icon.png");
        final BufferedImage listIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/list_icon.png");
        final BufferedImage timerIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/timer_icon.png");
        final BufferedImage addTripTrackerIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/add_trip_icon.png");

        GROUPED_MODE_ICON = new ImageIcon(groupedIcon);
        GROUPED_MODE_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(groupedIcon, -180));
        GROUPED_MODE_ICON_UNSELECTED = new ImageIcon(ImageUtil.alphaOffset(groupedIcon, -200));

        LIST_MODE_ICON = new ImageIcon(listIcon);
        LIST_MODE_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(listIcon, -180));
        LIST_MODE_ICON_UNSELECTED = new ImageIcon(ImageUtil.alphaOffset(listIcon, -200));

        TRIP_MODE_ICON = new ImageIcon(timerIcon);
        TRIP_MODE_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(timerIcon, -180));
        TRIP_MODE_ICON_UNSELECTED = new ImageIcon(ImageUtil.alphaOffset(timerIcon, -200));

        ADD_TRIP_TRACKER_ICON = new ImageIcon(addTripTrackerIcon);
        ADD_TRIP_TRACKER_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(addTripTrackerIcon, -180));
    }

    EnhancedLootTrackerPanel() {
        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Create layout panel for wrapping
        JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        add(layoutPanel, BorderLayout.NORTH);

        layoutPanel.add(buildTrackingModeControls());
        layoutPanel.add(buildLootBoxPanel());

        // Footer with clear button
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildTrackingModeControls() {
        final JPanel trackingModeControlPanel = new JPanel();

        trackingModeControlPanel.setLayout(new BorderLayout());
        trackingModeControlPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        trackingModeControlPanel.setPreferredSize(new Dimension(0, 40));
        trackingModeControlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        final JPanel modeControlsPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        modeControlsPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);

        // List mode button with label
        JPanel listPanel = buildModeButton(listModeButton, LIST_MODE_ICON_UNSELECTED,
                LIST_MODE_ICON_HOVER, LIST_MODE_ICON, "List", 0);
        JPanel groupedPanel = buildModeButton(groupedModeButton, GROUPED_MODE_ICON_UNSELECTED,
                GROUPED_MODE_ICON_HOVER, GROUPED_MODE_ICON, "Grouped", 1);
        JPanel tripPanel = buildModeButton(tripModeButton, TRIP_MODE_ICON_UNSELECTED,
                TRIP_MODE_ICON_HOVER, TRIP_MODE_ICON, "Trips", 2);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(listModeButton);
        buttonGroup.add(groupedModeButton);
        buttonGroup.add(tripModeButton);

        listModeButton.setSelected(true);

        modeControlsPanel.add(listPanel);
        modeControlsPanel.add(groupedPanel);
        modeControlsPanel.add(tripPanel);

        trackingModeControlPanel.add(modeControlsPanel, BorderLayout.CENTER);

        return trackingModeControlPanel;
    }

    private JPanel buildModeButton(JRadioButton button, ImageIcon icon, ImageIcon hoverIcon,
                                   ImageIcon selectedIcon, String label, int modeId) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);

        SwingUtil.removeButtonDecorations(button);
        button.setIcon(icon);
        button.setRolloverIcon(hoverIcon);
        button.setSelectedIcon(selectedIcon);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.addActionListener(e -> changeTrackingMode(modeId));

        JLabel textLabel = new JLabel(label, SwingConstants.CENTER);
        textLabel.setFont(FontManager.getRunescapeSmallFont());
        textLabel.setForeground(Color.GRAY);

        panel.add(button, BorderLayout.CENTER);
        panel.add(textLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildLootBoxPanel() {
        lootBoxPanel = new JPanel();
        lootBoxPanel.setLayout(new BoxLayout(lootBoxPanel, BoxLayout.Y_AXIS));

        return lootBoxPanel;
    }

    private JPanel buildTripTrackerControls() {
        JPanel outerPanel = new JPanel();
        outerPanel.setLayout(new BorderLayout());
        outerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        outerPanel.setBorder(new EmptyBorder(2, 0, 5, 0));

        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BorderLayout());
        innerPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        innerPanel.setPreferredSize(new Dimension(0, 30));
        innerPanel.setBorder(new EmptyBorder(5, 69, 5, 5));
        outerPanel.add(innerPanel);

        JLabel titleLabel = new JLabel();
        titleLabel.setText("TRIP TRACKERS");
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(Color.LIGHT_GRAY);
        innerPanel.add(titleLabel, BorderLayout.CENTER);

        SwingUtil.removeButtonDecorations(addTripButton);
        addTripButton.setIcon(ADD_TRIP_TRACKER_ICON);
        addTripButton.setRolloverIcon(ADD_TRIP_TRACKER_ICON_HOVER);
        addTripButton.setToolTipText("Click to add a new trip tracker");

        if (addTripButton.getActionListeners().length == 0) {
            addTripButton.addActionListener(e -> createNewTrip());
        }

        innerPanel.add(addTripButton, BorderLayout.EAST);

        return outerPanel;
    }

    // This method is used to build the loot panels from scratch, using stored data. This method is called for example
    // when switching between view modes, and eventually when re-building the loot tracker from scratch between
    // sessions using persisted data.
    private void rebuildLootPanel() {
        // Remove all components from lootBoxPanel
        SwingUtil.fastRemoveAll(lootBoxPanel);
        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();

        if (selectedTrackingMode == 2) {
            lootBoxPanel.add(buildTripTrackerControls());

            if (tripPanelBoxes.isEmpty()) {
                // Empty state for trip view
                lootBoxPanel.add(buildEmptyStateLabel("No trips yet \u2014 click + to start one."));
            } else {
                // tripPanels is a map of trip IDs to trip panels associated with that trip
                tripPanelBoxes.forEach((tripId, aValue) -> {
                    TripPanel tripPanel = tripsMap.get(tripId);
                    if (tripPanel != null) {
                        lootBoxPanel.add(tripPanel.buildHeaderPanel(), 1);
                        lootBoxPanel.revalidate();
                        lootBoxPanel.repaint();
                        tripPanelBoxes.get(tripId).forEach((bKey, bValue) -> {
                            LootTrackingPanelBox panelBox = tripPanelBoxes.get(tripId).get(bKey);
                            JPanel panel = panelBox.buildPanelBox();
                            panel.setName(bKey);
                            tripPanel.addLootPanel(panel);
                        });
                    }
                });
            }

        } else if (selectedTrackingMode == 1) {
            // Grouped view
            if (parentPlugin.getListViewDropArray().isEmpty()) {
                lootBoxPanel.add(buildEmptyStateLabel("No drops tracked yet. Kill something to get started!"));
            } else {
                lootBoxPanel.add(buildSubtitleLabel("Sorted by most recent kill"));
                parentPlugin.rebuildLootPanel();
            }
        } else {
            // List view
            if (parentPlugin.getListViewDropArray().isEmpty()) {
                lootBoxPanel.add(buildEmptyStateLabel("No drops tracked yet. Kill something to get started!"));
            } else {
                parentPlugin.rebuildLootPanel();
            }
        }
    }

    private JPanel buildEmptyStateLabel(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(20, 10, 20, 10));

        JLabel label = new JLabel("<html><center>" + text + "</center></html>", SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.GRAY);
        panel.add(label, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildSubtitleLabel(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(4, 0, 2, 0));

        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.GRAY);
        panel.add(label, BorderLayout.CENTER);

        return panel;
    }

    // This method is used for adding a loot box when in list view mode
    public void addLootBox(TrackableItemDrop itemDrop) {
        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(itemDrop);
        lootBoxPanel.add(newDropBox.buildPanelBox(),0);
        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
    }

    public void addLootBox(NpcLootAggregate npcLootAggregate, ArrayList<LootAggregation> lootAggregation, int tripId) {
        String npcName = npcLootAggregate.getNpcName();
        int numberOfKills = npcLootAggregate.getNumberOfKills();
        String lastKillTime = npcLootAggregate.getLastKillTime();

        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(lootAggregation, npcName, numberOfKills, lastKillTime);
        JPanel newLootPanel = newDropBox.buildPanelBox();
        newLootPanel.setName(npcName);

        TripPanel activeTripPanel = tripsMap.get(tripId);

        // Always look up the loot panels map by trip ID to avoid stale reference bugs
        LinkedHashMap<String, LootTrackingPanelBox> tripLootPanels = tripPanelBoxes.get(tripId);
        if (tripLootPanels == null) {
            tripLootPanels = new LinkedHashMap<>();
            tripPanelBoxes.put(tripId, tripLootPanels);
        }

        if (tripLootPanels.containsKey(npcName)) {
            if (activeTripPanel != null) {
                JPanel tripLootPanel = activeTripPanel.getLootPanel();
                Component[] componentList = tripLootPanel.getComponents();
                for (Component c : componentList) {
                    if (c.getName() != null && c.getName().equals(npcName)) {
                        tripLootPanel.remove(c);
                    }
                }
            }

            tripLootPanels.remove(npcName);
            tripLootPanels.put(npcName, newDropBox);
        } else {
            tripLootPanels.put(npcName, newDropBox);
        }

        // Keep activeTripLootPanels in sync for the current active trip
        if (tripPanelBoxes.containsKey(tripId)) {
            activeTripLootPanels = tripLootPanels;
        }

        if (selectedTrackingMode == 2 && activeTripPanel != null) {
            activeTripPanel.addLootPanel(newLootPanel);
        }
    }

    // This method is used for adding a loot box when in grouped view mode
    public void addLootBox(NpcLootAggregate npcLootAggregate, ArrayList<LootAggregation> lootAggregation) {
        String npcName = npcLootAggregate.getNpcName();
        int numberOfKills = npcLootAggregate.getNumberOfKills();
        String lastKillTime = npcLootAggregate.getLastKillTime();

        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(lootAggregation, npcName, numberOfKills, lastKillTime);
        JPanel newLootPanel = newDropBox.buildPanelBox();

        if (groupedLootBoxPanels.containsKey(npcName)) {
            lootBoxPanel.remove(groupedLootBoxPanels.get(npcName));
            groupedLootBoxPanels.remove(npcName);
            groupedLootBoxPanels.put(npcName, newLootPanel);

        } else {
            groupedLootBoxPanels.put(npcName, newLootPanel);
        }

        if (selectedTrackingMode == 1) {
            lootBoxPanel.add(newLootPanel, 0);
            lootBoxPanel.revalidate();
            lootBoxPanel.repaint();
        }
    }

    private void changeTrackingMode(int newTrackingModeType) {
        if (newTrackingModeType != selectedTrackingMode) {
            selectedTrackingMode = newTrackingModeType;
            rebuildLootPanel();
        }
    }

    public void setParentPlugin(EnhancedLootTrackerPlugin parentPlugin) {
        this.parentPlugin = parentPlugin;
    }

    public int getSelectedTrackingMode() { return selectedTrackingMode; }

    /**
     * Called after persisted data has been loaded to refresh the current view.
     */
    public void rebuildAfterLoad() {
        // Register restored trips so the trip view knows about them
        for (Trip trip : parentPlugin.getTrips()) {
            int id = trip.getTripId();
            if (!tripsMap.containsKey(id)) {
                TripPanel tripPanel = new TripPanel(trip);
                tripsMap.put(id, tripPanel);

                // Build loot panel boxes from the trip's restored NPC aggregates
                LinkedHashMap<String, LootTrackingPanelBox> lootPanels = new LinkedHashMap<>();
                for (NpcLootAggregate aggregate : trip.getTripAggregates()) {
                    String npcName = aggregate.getNpcName();
                    int kills = aggregate.getNumberOfKills();
                    String lastKill = aggregate.getLastKillTime();
                    ArrayList<LootAggregation> aggregations = aggregate.getNpcItemAggregations();

                    if (aggregations != null) {
                        LootTrackingPanelBox panelBox = new LootTrackingPanelBox(aggregations, npcName, kills, lastKill);
                        lootPanels.put(npcName, panelBox);
                    }
                }
                tripPanelBoxes.put(id, lootPanels);
            }
        }

        rebuildLootPanel();
    }

    private void createNewTrip() {
        boolean isActiveTrip = parentPlugin.checkForActiveTrip();

        if (!isActiveTrip) {
            String tripName = "TRIP " + parentPlugin.getNextTripNumber();
            parentPlugin.initTrip(tripName);

            Trip activeTrip = parentPlugin.getActiveTrip();
            TripPanel tripPanel = new TripPanel(activeTrip);
            tripsMap.put(activeTrip.getTripId(), tripPanel);

            activeTripLootPanels = new LinkedHashMap<>();
            tripPanelBoxes.put(activeTrip.getTripId(), activeTripLootPanels);

            // Rebuild to remove empty state and show the new trip
            rebuildLootPanel();

        } else {
            int selectedOption = JOptionPane.showConfirmDialog(null,
                    "You can only have a single active trip. Do you want to cancel the current trip and start a new one?",
                    "Warning!",
                    JOptionPane.YES_NO_OPTION);

            switch (selectedOption) {
                case JOptionPane.YES_OPTION:
                    Trip currentTrip = parentPlugin.getActiveTrip();
                    currentTrip.setStatus(false);
                    TripPanel activePanel = tripsMap.get(currentTrip.getTripId());
                    if (activePanel != null) {
                        activePanel.setStatus(false);
                    }
                    parentPlugin.onTripStatusChanged();
                    createNewTrip();
                    break;
                case JOptionPane.NO_OPTION:
                    break;
            }
        }
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        footer.setBorder(new EmptyBorder(10, 0, 0, 0));

        JButton clearButton = new JButton("Clear all data");
        clearButton.setFont(FontManager.getRunescapeSmallFont());
        clearButton.setForeground(Color.WHITE);
        clearButton.setBackground(new Color(120, 30, 30));
        clearButton.setOpaque(true);
        clearButton.setBorder(new EmptyBorder(5, 10, 5, 10));
        clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearButton.setToolTipText("Delete all tracked drops and trips");
        clearButton.setFocusPainted(false);
        clearButton.addActionListener(e -> confirmClearAllData());
        footer.add(clearButton, BorderLayout.CENTER);

        return footer;
    }

    public void removeTrip(int tripId) {
        tripsMap.remove(tripId);
        tripPanelBoxes.remove(tripId);
        rebuildLootPanel();
    }

    private void confirmClearAllData() {
        int selectedOption = JOptionPane.showConfirmDialog(null,
                "This will permanently delete all tracked drops and trips. Are you sure?",
                "Clear All Data",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (selectedOption == JOptionPane.YES_OPTION) {
            parentPlugin.clearAllData();
        }
    }

    /**
     * Switches the panel to show the trip comparison sub-view.
     */
    public void showComparisonView(int preSelectedTripId) {
        removeAll();

        TripComparisonPanel comparisonPanel = new TripComparisonPanel(
                parentPlugin.getTrips(),
                preSelectedTripId,
                this::hideComparisonView
        );

        setLayout(new BorderLayout());
        add(comparisonPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Returns from the comparison view to the normal trip panel.
     */
    private void hideComparisonView() {
        removeAll();

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        add(layoutPanel, BorderLayout.NORTH);

        layoutPanel.add(buildTrackingModeControls());
        layoutPanel.add(buildLootBoxPanel());
        add(buildFooter(), BorderLayout.SOUTH);

        // Switch to trip view and rebuild
        selectedTrackingMode = 2;
        tripModeButton.setSelected(true);
        rebuildLootPanel();

        revalidate();
        repaint();
    }

    /**
     * Called after all data has been cleared to reset the panel state.
     */
    public void rebuildAfterClear() {
        tripsMap.clear();
        tripPanelBoxes.clear();
        groupedLootBoxPanels.clear();
        activeTripLootPanels.clear();
        rebuildLootPanel();
    }
}
