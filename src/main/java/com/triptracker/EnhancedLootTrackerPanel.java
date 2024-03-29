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
    private final JRadioButton groupedModeButton = new JRadioButton();
    private final JRadioButton listModeButton = new JRadioButton();
    private final JRadioButton tripModeButton = new JRadioButton();
    private final JButton addTripButton = new JButton();
    private final LinkedHashMap<String, JPanel> groupedLootBoxPanels = new LinkedHashMap<>();
    private final LinkedHashMap<String, Trip> tripsMap = new LinkedHashMap<>();
    private LinkedHashMap<String, LootTrackingPanelBox> activeTripLootPanels = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, LootTrackingPanelBox>> tripPanelBoxes = new LinkedHashMap<>();

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

            // tripPanels is a map of trip names to trip panels associated with that trip
            tripPanelBoxes.forEach((aKey, aValue) -> {

                // Build a trip header with the panel name
                for (Trip trip : parentPlugin.getTrips()) {
                    if (trip.matches(aKey)) {
                        lootBoxPanel.add(trip.buildHeaderPanel(),1);
                        lootBoxPanel.revalidate();
                        lootBoxPanel.repaint();
                        // Get the trip panels map associated with the given trip and iterate over them
                        tripPanelBoxes.get(aKey).forEach((bKey, bValue) -> {

                            LootTrackingPanelBox panelBox = tripPanelBoxes.get(aKey).get(bKey);
                            JPanel panel = panelBox.buildPanelBox();
                            panel.setName(bKey);
                            trip.addLootPanel(panel);

                        });
                    }
                }
            });

        } else {
            parentPlugin.rebuildLootPanel();
        }
    }

    // This method is used for adding a loot box when in list view mode
    public void addLootBox(TrackableItemDrop itemDrop) {
        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(itemDrop);
        lootBoxPanel.add(newDropBox.buildPanelBox(),0);
        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
    }

    public void addLootBox(NpcLootAggregate npcLootAggregate, ArrayList<LootAggregation> lootAggregation, String activeTripName) {
        String npcName = npcLootAggregate.getNpcName();
        int numberOfKills = npcLootAggregate.getNumberOfKills();
        String lastKillTime = npcLootAggregate.getLastKillTime();

        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(lootAggregation, npcName, numberOfKills, lastKillTime);
        JPanel newLootPanel = newDropBox.buildPanelBox();
        newLootPanel.setName(npcName);

        if (activeTripLootPanels.containsKey(npcName)) {
            JPanel activeTripLootPanel = parentPlugin.getActiveTrip().getLootPanel();
            Component[] componentList = activeTripLootPanel.getComponents();
            for(Component c : componentList){
                if(c.getName().equals(npcName)){
                    activeTripLootPanel.remove(c);
                }
            }

            activeTripLootPanels.remove(npcName);
            activeTripLootPanels.put(npcName, newDropBox);
        } else {
            activeTripLootPanels.put(npcName, newDropBox);
        }

        if (tripPanelBoxes.containsKey(activeTripName)) {
            tripPanelBoxes.remove(activeTripName);
            tripPanelBoxes.put(activeTripName, activeTripLootPanels);
        } else {
            tripPanelBoxes.put(activeTripName, activeTripLootPanels);
        }

        if (selectedTrackingMode == 2) {
            parentPlugin.getActiveTrip().addLootPanel(newLootPanel);
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

    private void createNewTrip() {
        String tripName;
        boolean isActiveTrip = parentPlugin.checkForActiveTrip();

        if (!isActiveTrip) {
            if (parentPlugin.getNumberOfTrips() != 0) {
                int tripNumber = parentPlugin.getNumberOfTrips() + 1;
                tripName = "TRIP " + tripNumber;
                parentPlugin.initTrip(tripName);
            } else {
                tripName = "TRIP 1";
                parentPlugin.initTrip(tripName);
            }

            toggleTripStatus(tripName);

            lootBoxPanel.add(parentPlugin.getActiveTrip().buildHeaderPanel(),1);
            lootBoxPanel.revalidate();
            lootBoxPanel.repaint();

            activeTripLootPanels = new LinkedHashMap<>();
            tripPanelBoxes.put(tripName, activeTripLootPanels);
            tripsMap.put(tripName, parentPlugin.getActiveTrip());

        } else {
            int selectedOption = JOptionPane.showConfirmDialog(null,
                    "You can only have a single active trip. Do you want to cancel the current trip and start a new one?",
                    "Warning!",
                    JOptionPane.YES_NO_OPTION);

            switch (selectedOption) {
                case JOptionPane.YES_OPTION:
                    toggleTripStatus(activeTripName);
                    parentPlugin.getActiveTrip().setStatus(false);
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

    public void removeTrip(String tripName) {
        tripsMap.remove(tripName);
        rebuildLootPanel();
    }
}
