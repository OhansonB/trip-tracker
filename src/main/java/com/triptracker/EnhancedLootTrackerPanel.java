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

public class EnhancedLootTrackerPanel extends PluginPanel {
    private static final String MAIN_PANEL_TITLE = "Loot tracker";
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
        //listModeButton.addActionListener(e -> changeGrouping(false));

        SwingUtil.removeButtonDecorations(groupedModeButton);
        groupedModeButton.setIcon(GROUPED_MODE_ICON_UNSELECTED);
        groupedModeButton.setRolloverIcon(GROUPED_MODE_ICON_HOVER);
        groupedModeButton.setSelectedIcon(GROUPED_MODE_ICON);
        groupedModeButton.setToolTipText("Show loot drops grouped by NPC name");
        //groupedModeButton.addActionListener(e -> changeGrouping(false));

        SwingUtil.removeButtonDecorations(tripModeButton);
        tripModeButton.setIcon(TRIP_MODE_ICON_UNSELECTED);
        tripModeButton.setRolloverIcon(TRIP_MODE_ICON_HOVER);
        tripModeButton.setSelectedIcon(TRIP_MODE_ICON);
        tripModeButton.setToolTipText("Show controls for creating loot trips");
        //tripModeButton.addActionListener(e -> changeGrouping(false));

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(listModeButton);
        buttonGroup.add(groupedModeButton);
        buttonGroup.add(tripModeButton);
        listModeButton.setSelected(true);

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
    public void addLootBox(TrackableItemDrop itemDrop) {
        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(itemDrop);
        lootBoxPanel.add(newDropBox.buildPanelBox(),0);
        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
    }
}
