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
import java.util.Set;

public class EnhancedLootTrackerPanel extends PluginPanel {
    private EnhancedLootTrackerPlugin parentPlugin;
    private JPanel layoutPanel;
    private JPanel lootBoxPanel;
    private final int DEFAULT_TRACKING_MODE = 0;
    protected int selectedTrackingMode = DEFAULT_TRACKING_MODE;
    private String activeTripName;
    private boolean tripActive = false;
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
    private static final ImageIcon STOP_TRIP_TRACKER_ICON;
    private static final ImageIcon STOP_TRIP_TRACKER_ICON_HOVER;
    private final JRadioButton groupedModeButton = new JRadioButton();
    private final JRadioButton listModeButton = new JRadioButton();
    private final JRadioButton tripModeButton = new JRadioButton();
    private final JButton addTripButton = new JButton();
    private final JButton stopTripButton = new JButton();
    private LinkedHashMap<String, JPanel> groupedLootBoxPanels = new LinkedHashMap<>();
    private LinkedHashMap<String, LinkedHashMap<String, Object>> tripsMap = new LinkedHashMap<>();
    private LinkedHashMap<String, Object> tripLootSummaries;
    private JPanel activeTripLootPanel;
    private LinkedHashMap<String, JPanel> activeTripLootPanels;

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

        // Trip control icons
        final BufferedImage stopIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/stop_trip_icon.png");

        STOP_TRIP_TRACKER_ICON = new ImageIcon(stopIcon);
        STOP_TRIP_TRACKER_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(stopIcon, -180));
    }

    EnhancedLootTrackerPanel() {
        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Create layout panel for wrapping
        layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        add(layoutPanel, BorderLayout.NORTH);

        layoutPanel.add(buildTrackingModeControls());
        layoutPanel.add(buildLootBoxPanel());
    }

    private JPanel buildTrackingModeControls() {
        final JPanel trackingModeControlPanel = new JPanel();

        trackingModeControlPanel.setLayout(new BorderLayout());
        trackingModeControlPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        trackingModeControlPanel.setPreferredSize(new Dimension(0, 30));
        trackingModeControlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        final JPanel modeControlsPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        modeControlsPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);

        SwingUtil.removeButtonDecorations(listModeButton);
        listModeButton.setIcon(LIST_MODE_ICON_UNSELECTED);
        listModeButton.setRolloverIcon(LIST_MODE_ICON_HOVER);
        listModeButton.setSelectedIcon(LIST_MODE_ICON);
        listModeButton.setToolTipText("Show each loot drop separately");
        listModeButton.addActionListener(e -> changeTrackingMode(0));

        SwingUtil.removeButtonDecorations(groupedModeButton);
        groupedModeButton.setIcon(GROUPED_MODE_ICON_UNSELECTED);
        groupedModeButton.setRolloverIcon(GROUPED_MODE_ICON_HOVER);
        groupedModeButton.setSelectedIcon(GROUPED_MODE_ICON);
        groupedModeButton.setToolTipText("Show loot drops grouped by NPC name");
        groupedModeButton.addActionListener(e -> changeTrackingMode(1));

        SwingUtil.removeButtonDecorations(tripModeButton);
        tripModeButton.setIcon(TRIP_MODE_ICON_UNSELECTED);
        tripModeButton.setRolloverIcon(TRIP_MODE_ICON_HOVER);
        tripModeButton.setSelectedIcon(TRIP_MODE_ICON);
        tripModeButton.setToolTipText("Show controls for creating loot trips");
        tripModeButton.addActionListener(e -> changeTrackingMode(2));

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(listModeButton);
        buttonGroup.add(groupedModeButton);
        buttonGroup.add(tripModeButton);

        switch (DEFAULT_TRACKING_MODE) {
            case 0:
                listModeButton.setSelected(true);
                break;
            case 1:
                groupedModeButton.setSelected(true);
                break;
            case 2:
                tripModeButton.setSelected(true);
                break;
            default:
                break;
        }

        modeControlsPanel.add(listModeButton);
        modeControlsPanel.add(groupedModeButton);
        modeControlsPanel.add(tripModeButton);

        trackingModeControlPanel.add(modeControlsPanel, BorderLayout.EAST);

        return trackingModeControlPanel;
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
        innerPanel.setBorder(new EmptyBorder(5, 25, 5, 5));
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

        // case 0 = list view; case 1 = grouped by NPC; case 2 = trip mode.
        switch (selectedTrackingMode) {
            case 0:
                ArrayList<TrackableItemDrop> listViewDrops = parentPlugin.getListViewDropArray();
                for (TrackableItemDrop itemDrop : listViewDrops) {
                    addLootBox(itemDrop);
                }

                break;
            case 1:
                LinkedHashMap<String, LinkedHashMap<String, Object>> aggregatedDropsByNpc = parentPlugin.getDropQuantitiesByTypeOfNpc();
                Set<String> keys = aggregatedDropsByNpc.keySet();

                for (String key : keys) {
                    addLootBox(aggregatedDropsByNpc.get(key), key);
                }

                break;
            case 2:
                lootBoxPanel.add(buildTripTrackerControls());

                Set<String> tripsMapKeys = tripsMap.keySet();
                for (String a_key : tripsMapKeys) {
                    buildTripHeaderPanel(a_key);

                    Set<String> npcsKilledInTrip = tripsMap.get(a_key).keySet();

                    for (String b_key : npcsKilledInTrip) {
                        LinkedHashMap<String, Object> dropSummary = (LinkedHashMap<String, Object>) tripsMap.get(a_key).get(b_key);
                        String npcName = b_key;
                        String tripToUpdate = a_key;

                        addLootBox(dropSummary, npcName, tripToUpdate, true);
                    }
                }

                break;
            default:
                System.out.println("You have tried to switch to a view mode that is not supported");
                break;
        }
    }

    // This method is used for adding a loot box when in list view mode
    public void addLootBox(TrackableItemDrop itemDrop) {
        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(itemDrop);
        lootBoxPanel.add(newDropBox.buildPanelBox(),0);
        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
    }

    // This method is used for adding a loot box when in grouped view mode
    public void addLootBox(LinkedHashMap<String, Object> npcDropsSummary, String npcName) {
        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(npcDropsSummary, npcName);
        JPanel groupedLootBox = newDropBox.buildPanelBox();

        if (groupedLootBoxPanels.containsKey(npcName)) {
            lootBoxPanel.remove(groupedLootBoxPanels.get(npcName));
            groupedLootBoxPanels.replace(npcName, groupedLootBox);

        } else if (!groupedLootBoxPanels.containsKey(npcName)) {
            groupedLootBoxPanels.put(npcName, groupedLootBox);
        }

        lootBoxPanel.add(groupedLootBox,0);
        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
    }

    // This method is used for adding a loot box when an active trip is active
    public void addLootBox(LinkedHashMap<String, Object> npcDropsSummary, String npcName, String tripToUpdate, Boolean rebuilding) {
        if (tripActive) {
            LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(npcDropsSummary, npcName);
            JPanel newLootPanel = newDropBox.buildPanelBox();

            if (!rebuilding) {
                updateTripMaps(npcDropsSummary, npcName, tripToUpdate);
            }

            activeTripLootPanels.put(npcName, newLootPanel);
            activeTripLootPanel.add(newLootPanel, 0);
            activeTripLootPanel.revalidate();
            activeTripLootPanel.repaint();
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

    private void createNewTrip() {
        tripLootSummaries = new LinkedHashMap<>();

        if (!tripActive) {
            int tripNumber = tripsMap.size() + 1;
            String tripName = "TRIP " + tripNumber;

            toggleTripStatus(tripName);
            buildTripHeaderPanel(tripName);

            activeTripLootPanels = new LinkedHashMap<>();
            tripsMap.put(tripName, tripLootSummaries);

            parentPlugin.clearTripMap();

        } else {
            int selectedOption = JOptionPane.showConfirmDialog(null,
                    "You can only have a single active trip. Do you want to cancel the current trip and start a new one?",
                    "Warning!",
                    JOptionPane.YES_NO_OPTION);

            switch (selectedOption) {
                case JOptionPane.YES_OPTION:
                    toggleTripStatus(activeTripName);
                    activeTripLootPanels.clear();
                    createNewTrip();

                    break;
                case JOptionPane.NO_OPTION:
                    break;
            }
        }
    }

    private void toggleTripStatus(String tripName) {
        if (!tripActive) {
            tripActive = true;
            activeTripName = tripName;
        } else {
            tripActive = false;
            activeTripName = null;
        }
    }

    public String getActiveTripName() { return activeTripName; }

    public void buildTripHeaderPanel(String tripName) {
        final JPanel outerPanel = new JPanel();
        outerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        outerPanel.setLayout(new BorderLayout());
        outerPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

        final JPanel innerSummaryPanel = new JPanel();
        innerSummaryPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        innerSummaryPanel.setLayout(new BorderLayout());
        innerSummaryPanel.setBorder(new EmptyBorder(7, 10, 7, 7));
        innerSummaryPanel.setPreferredSize(new Dimension(0, 30));
        //innerSummaryPanel.setBorder(new EmptyBorder(5, 25, 5, 5));
        outerPanel.add(innerSummaryPanel, BorderLayout.NORTH);

        // This label summaries the npc name and level
        JLabel summaryPanelTitle = new JLabel(tripName);
        summaryPanelTitle.setFont(FontManager.getRunescapeBoldFont());
        summaryPanelTitle.setForeground(Color.LIGHT_GRAY);
        innerSummaryPanel.add(summaryPanelTitle, BorderLayout.WEST);

        activeTripLootPanel = new JPanel();
        activeTripLootPanel.setLayout(new BoxLayout(activeTripLootPanel, BoxLayout.Y_AXIS));
        outerPanel.add(activeTripLootPanel);

        SwingUtil.removeButtonDecorations(stopTripButton);
        stopTripButton.setIcon(STOP_TRIP_TRACKER_ICON);
        stopTripButton.setRolloverIcon(STOP_TRIP_TRACKER_ICON_HOVER);
        stopTripButton.setToolTipText("Click to end the trip");
        stopTripButton.setBorder(null);

        if (stopTripButton.getActionListeners().length == 0) {
            stopTripButton.addActionListener(e -> stopTrip());
        }

        innerSummaryPanel.add(stopTripButton, BorderLayout.EAST);
        stopTripButton.setVisible(true);

        lootBoxPanel.add(outerPanel,1);
        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
    }

    public void updateTripMaps(LinkedHashMap<String, Object> npcDropsSummary, String npcName, String tripToUpdate) {
        if (tripLootSummaries.containsKey(npcName)) {
            tripLootSummaries.replace(npcName, npcDropsSummary);
        } else {
            tripLootSummaries.put(npcName, npcDropsSummary);
        }

        if (tripsMap.get(tripToUpdate).containsKey(npcName)) {
            if (activeTripLootPanels.containsKey(npcName)) {
                activeTripLootPanel.remove(activeTripLootPanels.get(npcName));
            }
            tripsMap.replace(tripToUpdate, tripLootSummaries);
        } else {
            tripsMap.put(tripToUpdate, tripLootSummaries);
        }
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
                    activeTripName = null;
                    stopTripButton.setVisible(false);
                    break;
                case JOptionPane.NO_OPTION:
                    break;
            }
        }
    }
}
