package com.triptracker;

import com.google.common.collect.ImmutableMap;
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
import java.util.Map;
import java.util.Set;

public class EnhancedLootTrackerPanel extends PluginPanel {
    private JPanel layoutPanel;
    private JPanel lootBoxPanel;
    private static final ImageIcon GROUPED_MODE_ICON;
    private static final ImageIcon GROUPED_MODE_ICON_HOVER;
    private static final ImageIcon GROUPED_MODE_ICON_UNSELECTED;
    private static final ImageIcon LIST_MODE_ICON;
    private static final ImageIcon LIST_MODE_ICON_HOVER;
    private static final ImageIcon LIST_MODE_ICON_UNSELECTED;
    private static final ImageIcon TRIP_MODE_ICON;
    private static final ImageIcon TRIP_MODE_ICON_HOVER;
    private static final ImageIcon TRIP_MODE_ICON_UNSELECTED;

    private final JRadioButton groupedModeButton = new JRadioButton();
    private final JRadioButton listModeButton = new JRadioButton();
    private final JRadioButton tripModeButton = new JRadioButton();
    private final int DEFAULT_TRACKING_MODE = 0;
    protected int selectedTrackingMode = DEFAULT_TRACKING_MODE;
    private EnhancedLootTrackerPlugin parentPlugin;
    private LinkedHashMap<String, JPanel> mapOfNpcsToGroupLootBoxes = new LinkedHashMap<>();


    static {
        // Tracker mode control icons
        final BufferedImage groupedIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/grouped_icon.png");
        final BufferedImage listIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/list_icon.png");
        final BufferedImage timerIcon = ImageUtil.loadImageResource(EnhancedLootTrackerPlugin.class, "/timer_icon.png");

        GROUPED_MODE_ICON = new ImageIcon(groupedIcon);
        GROUPED_MODE_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(groupedIcon, -180));
        GROUPED_MODE_ICON_UNSELECTED = new ImageIcon(ImageUtil.alphaOffset(groupedIcon, -200));

        LIST_MODE_ICON = new ImageIcon(listIcon);
        LIST_MODE_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(listIcon, -180));
        LIST_MODE_ICON_UNSELECTED = new ImageIcon(ImageUtil.alphaOffset(listIcon, -200));

        TRIP_MODE_ICON = new ImageIcon(timerIcon);
        TRIP_MODE_ICON_HOVER = new ImageIcon(ImageUtil.alphaOffset(timerIcon, -180));
        TRIP_MODE_ICON_UNSELECTED = new ImageIcon(ImageUtil.alphaOffset(timerIcon, -200));
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

        if (mapOfNpcsToGroupLootBoxes.containsKey(npcName)) {
            lootBoxPanel.remove(mapOfNpcsToGroupLootBoxes.get(npcName));
            mapOfNpcsToGroupLootBoxes.replace(npcName, groupedLootBox);


        } else if (!mapOfNpcsToGroupLootBoxes.containsKey(npcName)) {
            mapOfNpcsToGroupLootBoxes.put(npcName, groupedLootBox);
        }

        lootBoxPanel.add(groupedLootBox,0);
        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
    }

    private void changeTrackingMode(int newTrackingModeType) {
        if (newTrackingModeType != selectedTrackingMode) {
            selectedTrackingMode = newTrackingModeType;
            rebuildLootPanel();
        }
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
                System.out.println("Switching to trip view mode");
                break;
            default:
                System.out.println("You have tried to switch to a view mode that is not supported");
                break;
        }
    }

    public void setParentPlugin(EnhancedLootTrackerPlugin parentPlugin) {
        this.parentPlugin = parentPlugin;
    }

    public int getSelectedTrackingMode() { return selectedTrackingMode; }
}
