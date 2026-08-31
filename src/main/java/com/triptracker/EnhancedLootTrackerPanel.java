package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

public class EnhancedLootTrackerPanel extends PluginPanel {
    private static final Color FOCUS_COLOR = new Color(0x5E, 0x9E, 0xD6);

    private EnhancedLootTrackerPlugin parentPlugin;
    private JPanel lootBoxPanel;
    private JPanel layoutPanel;
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
    private final LinkedHashMap<String, LootTrackingPanelBox> groupedPanelBoxes = new LinkedHashMap<>();
    private final ArrayList<LootTrackingPanelBox> listViewPanelBoxes = new ArrayList<>();
    private final LinkedHashMap<Integer, TripPanel> tripsMap = new LinkedHashMap<>();
    private LinkedHashMap<String, LootTrackingPanelBox> activeTripLootPanels = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, LinkedHashMap<String, LootTrackingPanelBox>> tripPanelBoxes = new LinkedHashMap<>();
    private Set<String> collapsedNpcs = new HashSet<>();

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

    private String filterText = "";
    private String tripFilterText = "";
    private JTextField filterField;
    private JPanel filterPanel;
    private JTextField tripFilterField;
    private boolean showHidden = false;

    EnhancedLootTrackerPanel() {
        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Create layout panel for wrapping
        layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        add(layoutPanel, BorderLayout.NORTH);

        layoutPanel.add(buildTrackingModeControls());
        layoutPanel.add(buildFilterPanel());
        layoutPanel.add(buildLootBoxPanel());

        // Footer with clear button
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildFilterPanel() {
        filterPanel = new JPanel(new BorderLayout());
        filterPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        filterPanel.setBorder(new EmptyBorder(4, 0, 4, 0));

        // Collapse/Expand all buttons on the left
        JPanel collapsePanel = new JPanel(new GridLayout(1, 3, 0, 0));
        collapsePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton collapseAllButton = new JButton("\u2212"); // minus sign
        collapseAllButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        collapseAllButton.setPreferredSize(new Dimension(22, 22));
        collapseAllButton.setToolTipText("Collapse all");
        collapseAllButton.getAccessibleContext().setAccessibleName("Collapse all");
        collapseAllButton.setContentAreaFilled(false);
        collapseAllButton.setBorderPainted(false);
        collapseAllButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        collapseAllButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        collapseAllButton.addActionListener(e -> setAllCollapsed(true));

        JButton expandAllButton = new JButton("+");
        expandAllButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        expandAllButton.setPreferredSize(new Dimension(22, 22));
        expandAllButton.setToolTipText("Expand all");
        expandAllButton.getAccessibleContext().setAccessibleName("Expand all");
        expandAllButton.setContentAreaFilled(false);
        expandAllButton.setBorderPainted(false);
        expandAllButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        expandAllButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        expandAllButton.addActionListener(e -> setAllCollapsed(false));

        JButton showHiddenButton = new JButton("\u25CB"); // ○ (empty circle = hidden items not shown)
        showHiddenButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        showHiddenButton.setPreferredSize(new Dimension(22, 22));
        showHiddenButton.setToolTipText("Show hidden items/NPCs");
        showHiddenButton.getAccessibleContext().setAccessibleName("Toggle show hidden items");
        showHiddenButton.setContentAreaFilled(false);
        showHiddenButton.setBorderPainted(false);
        showHiddenButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        showHiddenButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        showHiddenButton.addActionListener(e -> {
            showHidden = !showHidden;
            showHiddenButton.setText(showHidden ? "\u25CF" : "\u25CB");
            showHiddenButton.setForeground(showHidden ? Color.GREEN : ColorScheme.LIGHT_GRAY_COLOR);
            showHiddenButton.setToolTipText(showHidden ? "Hide excluded items/NPCs" : "Show hidden items/NPCs");
            rebuildAfterLoad();
        });

        collapsePanel.add(showHiddenButton);
        collapsePanel.add(collapseAllButton);
        collapsePanel.add(expandAllButton);

        filterField = new JTextField();
        filterField.setFont(FontManager.getRunescapeSmallFont());
        filterField.setToolTipText("Filter by NPC name");
        filterField.putClientProperty("JTextField.placeholderText", "Type to filter...");
        filterField.getAccessibleContext().setAccessibleName("Filter drops by NPC name");
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onFilterChanged(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onFilterChanged(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onFilterChanged(); }
        });

        // Clear button (X) on the right side
        JButton clearButton = new JButton("\u2715");
        clearButton.setFont(FontManager.getRunescapeSmallFont());
        clearButton.setPreferredSize(new Dimension(20, 20));
        clearButton.setToolTipText("Clear filter");
        clearButton.setContentAreaFilled(false);
        clearButton.setBorderPainted(false);
        clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearButton.addActionListener(e -> {
            filterField.setText("");
        });

        filterPanel.add(collapsePanel, BorderLayout.WEST);
        filterPanel.add(filterField, BorderLayout.CENTER);
        filterPanel.add(clearButton, BorderLayout.EAST);

        return filterPanel;
    }

    private void onFilterChanged() {
        filterText = filterField.getText().trim().toLowerCase();
        // Rebuild loot entries without triggering intermediate paints
        lootBoxPanel.setIgnoreRepaint(true);
        rebuildLootPanel();
        lootBoxPanel.setIgnoreRepaint(false);
        filterField.requestFocusInWindow();
    }

    /**
     * Builds an inline filter field for trip view, placed below the TRIP TRACKERS header.
     * Uses a separate tripFilterText state from the main filter.
     */
    private JPanel buildTripFilterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(0, 0, 4, 0));

        // Collapse/Expand all buttons on the left
        JPanel collapsePanel = new JPanel(new GridLayout(1, 3, 0, 0));
        collapsePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton collapseAllButton = new JButton("\u2212");
        collapseAllButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        collapseAllButton.setPreferredSize(new Dimension(22, 22));
        collapseAllButton.setToolTipText("Collapse all trips");
        collapseAllButton.getAccessibleContext().setAccessibleName("Collapse all trips");
        collapseAllButton.setContentAreaFilled(false);
        collapseAllButton.setBorderPainted(false);
        collapseAllButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        collapseAllButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        collapseAllButton.addActionListener(e -> setAllCollapsed(true));

        JButton expandAllButton = new JButton("+");
        expandAllButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        expandAllButton.setPreferredSize(new Dimension(22, 22));
        expandAllButton.setToolTipText("Expand all trips");
        expandAllButton.getAccessibleContext().setAccessibleName("Expand all trips");
        expandAllButton.setContentAreaFilled(false);
        expandAllButton.setBorderPainted(false);
        expandAllButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        expandAllButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        expandAllButton.addActionListener(e -> setAllCollapsed(false));

        JButton tripShowHiddenButton = new JButton(showHidden ? "\u25CF" : "\u25CB");
        tripShowHiddenButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        tripShowHiddenButton.setPreferredSize(new Dimension(22, 22));
        tripShowHiddenButton.setToolTipText(showHidden ? "Hide excluded items/NPCs" : "Show hidden items/NPCs");
        tripShowHiddenButton.getAccessibleContext().setAccessibleName("Toggle show hidden items");
        tripShowHiddenButton.setContentAreaFilled(false);
        tripShowHiddenButton.setBorderPainted(false);
        tripShowHiddenButton.setForeground(showHidden ? Color.GREEN : ColorScheme.LIGHT_GRAY_COLOR);
        tripShowHiddenButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tripShowHiddenButton.addActionListener(e -> {
            showHidden = !showHidden;
            tripShowHiddenButton.setText(showHidden ? "\u25CF" : "\u25CB");
            tripShowHiddenButton.setForeground(showHidden ? Color.GREEN : ColorScheme.LIGHT_GRAY_COLOR);
            tripShowHiddenButton.setToolTipText(showHidden ? "Hide excluded items/NPCs" : "Show hidden items/NPCs");
            rebuildAfterLoad();
        });

        collapsePanel.add(tripShowHiddenButton);
        collapsePanel.add(collapseAllButton);
        collapsePanel.add(expandAllButton);

        if (tripFilterField == null) {
            tripFilterField = new JTextField();
            tripFilterField.setFont(FontManager.getRunescapeSmallFont());
            tripFilterField.setToolTipText("Filter by trip name");
            tripFilterField.putClientProperty("JTextField.placeholderText", "Type to filter...");
            tripFilterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) { onTripFilterChanged(); }
                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) { onTripFilterChanged(); }
                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) { onTripFilterChanged(); }
            });
        }

        JButton clearButton = new JButton("\u2715");
        clearButton.setFont(FontManager.getRunescapeSmallFont());
        clearButton.setPreferredSize(new Dimension(20, 20));
        clearButton.setToolTipText("Clear filter");
        clearButton.setContentAreaFilled(false);
        clearButton.setBorderPainted(false);
        clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearButton.addActionListener(e -> tripFilterField.setText(""));

        panel.add(collapsePanel, BorderLayout.WEST);
        panel.add(tripFilterField, BorderLayout.CENTER);
        panel.add(clearButton, BorderLayout.EAST);

        return panel;
    }

    private void onTripFilterChanged() {
        tripFilterText = tripFilterField.getText().trim().toLowerCase();
        // Only rebuild the trip entries, not the entire panel (avoids flash)
        rebuildTripEntries();
        tripFilterField.requestFocusInWindow();
    }

    /**
     * Rebuilds only the trip entries below the header and filter, without touching the
     * trip controls or filter field. Avoids the flash from a full panel rebuild.
     */
    private void rebuildTripEntries() {
        // Remove everything after the first 2 components (trip controls + filter)
        while (lootBoxPanel.getComponentCount() > 2) {
            lootBoxPanel.remove(2);
        }

        if (tripPanelBoxes.isEmpty()) {
            lootBoxPanel.add(buildEmptyStateLabel("No trips yet \u2014 click + to start one."));
        } else {
            // Insert at position 2 (after controls + filter) so each trip pushes previous ones down
            // This results in newest-first ordering since tripPanelBoxes is insertion-ordered (oldest first)
            tripPanelBoxes.forEach((tripId, aValue) -> {
                TripPanel tripPanel = tripsMap.get(tripId);
                if (tripPanel != null) {
                    if (!tripFilterText.isEmpty() && !tripPanel.getTrip().getTripName().toLowerCase().contains(tripFilterText)) {
                        return;
                    }
                    lootBoxPanel.add(tripPanel.buildHeaderPanel(), 2);
                    tripPanelBoxes.get(tripId).forEach((bKey, bValue) -> {
                        // Skip excluded NPCs (unless showing hidden)
                        if (!showHidden && parentPlugin.isNpcExcluded(bKey)) {
                            return;
                        }
                        LootTrackingPanelBox panelBox = tripPanelBoxes.get(tripId).get(bKey);
                        JPanel panel = panelBox.buildPanelBox();
                        panel.setName(bKey);
                        tripPanel.addLootPanel(panel);
                    });
                }
            });
        }

        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
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
        button.setToolTipText(label + " view");
        button.getAccessibleContext().setAccessibleName(label + " view");
        button.setFocusable(true);
        button.addActionListener(e -> {
            // Manually deselect others since we're not using ButtonGroup
            listModeButton.setSelected(false);
            groupedModeButton.setSelected(false);
            tripModeButton.setSelected(false);
            button.setSelected(true);
            changeTrackingMode(modeId);
        });
        // Focus indicator on the wrapper panel since FlatLaf overrides button border rendering
        // Only show when focus is gained via keyboard (not mouse click)
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                panel.putClientProperty("focusedByMouse", Boolean.TRUE);
            }
        });
        button.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (Boolean.TRUE.equals(panel.getClientProperty("focusedByMouse"))) {
                    panel.putClientProperty("focusedByMouse", null);
                    return;
                }
                panel.setBorder(new LineBorder(FOCUS_COLOR, 2));
                panel.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                panel.setBorder(null);
                panel.repaint();
            }
        });
        // Allow Enter to activate
        button.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "activate");
        button.getActionMap().put("activate", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                button.doClick();
            }
        });

        JLabel textLabel = new JLabel(label, SwingConstants.CENTER);
        textLabel.setFont(FontManager.getRunescapeSmallFont());
        textLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        textLabel.setLabelFor(button);

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
        innerPanel.setBorder(new EmptyBorder(5, 60, 5, 12));
        outerPanel.add(innerPanel);

        JLabel titleLabel = new JLabel();
        titleLabel.setText("TRIP TRACKERS");
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(Color.WHITE);
        innerPanel.add(titleLabel, BorderLayout.CENTER);

        SwingUtil.removeButtonDecorations(addTripButton);
        addTripButton.setIcon(ADD_TRIP_TRACKER_ICON);
        addTripButton.setRolloverIcon(ADD_TRIP_TRACKER_ICON_HOVER);
        addTripButton.setToolTipText("Add a new trip tracker");
        addTripButton.getAccessibleContext().setAccessibleName("Add new trip");
        addTripButton.setPreferredSize(new Dimension(20, 20));
        addTripButton.setFocusable(true);
        // Only show focus border on keyboard navigation, not mouse click
        addTripButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                addTripButton.putClientProperty("focusedByMouse", Boolean.TRUE);
            }
        });
        addTripButton.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (Boolean.TRUE.equals(addTripButton.getClientProperty("focusedByMouse"))) {
                    addTripButton.putClientProperty("focusedByMouse", null);
                    return;
                }
                addTripButton.setBorder(new LineBorder(FOCUS_COLOR, 2));
            }

            @Override
            public void focusLost(FocusEvent e) {
                addTripButton.setBorder(null);
            }
        });

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
        listViewPanelBoxes.clear();

        // Show/hide the main filter panel based on mode (trip view uses its own inline filter)
        if (filterPanel != null) {
            filterPanel.setVisible(selectedTrackingMode != 2);
        }

        if (selectedTrackingMode == 2) {
            lootBoxPanel.add(buildTripTrackerControls());

            // Trip filter sits directly below the header
            JPanel tripFilter = buildTripFilterPanel();
            lootBoxPanel.add(tripFilter);

            // Delegate trip entry rendering to shared method
            rebuildTripEntries();

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

        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
    }

    private JPanel buildEmptyStateLabel(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setName("emptyState");
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(20, 10, 20, 10));

        JLabel label = new JLabel("<html><center>" + text + "</center></html>", SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        panel.add(label, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildSubtitleLabel(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(4, 0, 2, 0));

        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        panel.add(label, BorderLayout.CENTER);

        return panel;
    }

    // This method is used for adding a loot box when in list view mode
    public void addLootBox(TrackableItemDrop itemDrop) {
        // Apply NPC name filter
        if (!filterText.isEmpty() && !itemDrop.getDropNpcName().toLowerCase().contains(filterText)) {
            return;
        }
        // Apply NPC exclusion filter (unless showing hidden)
        if (!showHidden && parentPlugin.isNpcExcluded(itemDrop.getDropNpcName())) {
            return;
        }
        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(itemDrop, parentPlugin.getItemManager(), parentPlugin.isSpriteDisplayMode());
        newDropBox.setOnCollapseChanged(() -> parentPlugin.onDropCollapseChanged());
        newDropBox.setParentPlugin(parentPlugin);
        if (!showHidden) {
            newDropBox.setExcludedItems(parentPlugin.getExcludedItems());
        }
        listViewPanelBoxes.add(0, newDropBox);
        lootBoxPanel.add(newDropBox.buildPanelBox(),0);
        // Only repaint if the panel is already attached (skip during off-screen rebuild)
        if (lootBoxPanel.getParent() != null) {
            lootBoxPanel.revalidate();
            lootBoxPanel.repaint();
        }
    }

    public void addLootBox(NpcLootAggregate npcLootAggregate, ArrayList<LootAggregation> lootAggregation, int tripId) {
        String npcName = npcLootAggregate.getNpcName();
        int numberOfKills = npcLootAggregate.getNumberOfKills();
        String lastKillTime = npcLootAggregate.getLastKillTime();

        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(lootAggregation, npcName, numberOfKills, lastKillTime, npcLootAggregate.collapsed, () -> {
            npcLootAggregate.collapsed = !npcLootAggregate.collapsed;
            parentPlugin.onTripStatusChanged();
        }, parentPlugin.getItemManager(), parentPlugin.isSpriteDisplayMode());
        newDropBox.setParentPlugin(parentPlugin);
        if (!showHidden) {
            newDropBox.setExcludedItems(parentPlugin.getExcludedItems());
        }
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

        // Apply NPC exclusion filter (unless showing hidden)
        if (!showHidden && parentPlugin.isNpcExcluded(npcName)) {
            return;
        }
        int numberOfKills = npcLootAggregate.getNumberOfKills();
        String lastKillTime = npcLootAggregate.getLastKillTime();

        boolean isCollapsed = collapsedNpcs.contains(npcName);
        LootTrackingPanelBox newDropBox = new LootTrackingPanelBox(lootAggregation, npcName, numberOfKills, lastKillTime, isCollapsed, () -> {
            // Toggle the NPC name in the collapsed set
            if (collapsedNpcs.contains(npcName)) {
                collapsedNpcs.remove(npcName);
            } else {
                collapsedNpcs.add(npcName);
            }
            parentPlugin.onGroupedCollapseChanged();
        }, parentPlugin.getItemManager(), parentPlugin.isSpriteDisplayMode());
        newDropBox.setParentPlugin(parentPlugin);
        if (!showHidden) {
            newDropBox.setExcludedItems(parentPlugin.getExcludedItems());
        }
        JPanel newLootPanel = newDropBox.buildPanelBox();

        if (groupedLootBoxPanels.containsKey(npcName)) {
            lootBoxPanel.remove(groupedLootBoxPanels.get(npcName));
            groupedLootBoxPanels.remove(npcName);
            groupedLootBoxPanels.put(npcName, newLootPanel);
            groupedPanelBoxes.remove(npcName);
            groupedPanelBoxes.put(npcName, newDropBox);

        } else {
            groupedLootBoxPanels.put(npcName, newLootPanel);
            groupedPanelBoxes.put(npcName, newDropBox);
        }

        if (selectedTrackingMode == 1) {
            // Apply NPC name filter
            if (!filterText.isEmpty() && !npcName.toLowerCase().contains(filterText)) {
                return;
            }
            lootBoxPanel.add(newLootPanel, 0);
            // Only repaint if the panel is already attached (skip during off-screen rebuild)
            if (lootBoxPanel.getParent() != null) {
                lootBoxPanel.revalidate();
                lootBoxPanel.repaint();
            }
        }
    }

    private void changeTrackingMode(int newTrackingModeType) {
        if (newTrackingModeType != selectedTrackingMode) {
            selectedTrackingMode = newTrackingModeType;
            rebuildLootPanel();
        }
    }

    /**
     * Collapse or expand all items in the current view.
     * - List view: toggles collapsed state on each TrackableItemDrop.
     * - Grouped view: adds/removes all NPC names from the collapsedNpcs set.
     * - Trip view: toggles collapsed state on each Trip (not NPC aggregates within).
     */
    private void setAllCollapsed(boolean collapsed) {
        switch (selectedTrackingMode) {
            case 0: // List view — toggle each box in-place (no rebuild)
                for (TrackableItemDrop drop : parentPlugin.getListViewDropArray()) {
                    drop.setCollapsed(collapsed);
                }
                for (LootTrackingPanelBox box : listViewPanelBoxes) {
                    box.setCollapsedState(collapsed);
                }
                parentPlugin.onDropCollapseChanged();
                break;

            case 1: // Grouped view — toggle each box in-place (no rebuild)
                if (collapsed) {
                    collapsedNpcs.addAll(groupedLootBoxPanels.keySet());
                } else {
                    // Only un-collapse visible NPCs, leave hidden NPCs' state intact
                    collapsedNpcs.removeAll(groupedLootBoxPanels.keySet());
                }
                for (LootTrackingPanelBox box : groupedPanelBoxes.values()) {
                    box.setCollapsedState(collapsed);
                }
                parentPlugin.onGroupedCollapseChanged();
                break;

            case 2: // Trip view — collapse/expand trip headers directly (no rebuild)
                for (TripPanel tripPanel : tripsMap.values()) {
                    tripPanel.setCollapsedState(collapsed);
                }
                parentPlugin.onTripStatusChanged();
                break;
        }
    }

    public void setParentPlugin(EnhancedLootTrackerPlugin parentPlugin) {
        this.parentPlugin = parentPlugin;
    }

    public void setCollapsedNpcs(Set<String> collapsedNpcs) {
        this.collapsedNpcs = collapsedNpcs != null ? collapsedNpcs : new HashSet<>();
    }

    public Set<String> getCollapsedNpcs() {
        return collapsedNpcs;
    }

    public int getSelectedTrackingMode() { return selectedTrackingMode; }

    /**
     * Called after persisted data has been loaded to refresh the current view.
     */
    public void rebuildAfterLoad() {
        // Build a fresh loot box panel off-screen to avoid intermediate repaints
        JPanel newLootBoxPanel = new JPanel();
        newLootBoxPanel.setLayout(new BoxLayout(newLootBoxPanel, BoxLayout.Y_AXIS));

        // Swap the reference so all rebuild methods populate the new panel
        JPanel oldPanel = lootBoxPanel;
        lootBoxPanel = newLootBoxPanel;

        // Clear existing trip panel state to rebuild with current exclusion/display settings
        tripsMap.clear();
        tripPanelBoxes.clear();
        listViewPanelBoxes.clear();
        groupedLootBoxPanels.clear();
        groupedPanelBoxes.clear();

        // Preserve collapsed NPC state — the set may be indirectly cleared during rebuild
        Set<String> savedCollapsedNpcs = new HashSet<>(collapsedNpcs);

        // Register restored trips so the trip view knows about them
        for (Trip trip : parentPlugin.getTrips()) {
            int id = trip.getTripId();
            TripPanel tripPanel = new TripPanel(trip);
            if (!showHidden) {
                tripPanel.setExcludedItems(parentPlugin.getExcludedItems());
                tripPanel.setExcludedNpcs(parentPlugin.getExcludedNpcs());
            }
            tripsMap.put(id, tripPanel);

            // Build loot panel boxes from the trip's restored NPC aggregates
            LinkedHashMap<String, LootTrackingPanelBox> lootPanels = new LinkedHashMap<>();
            for (NpcLootAggregate aggregate : trip.getTripAggregates()) {
                String npcName = aggregate.getNpcName();
                int kills = aggregate.getNumberOfKills();
                String lastKill = aggregate.getLastKillTime();
                ArrayList<LootAggregation> aggregations = aggregate.getNpcItemAggregations();

                if (aggregations != null) {
                    LootTrackingPanelBox panelBox = new LootTrackingPanelBox(aggregations, npcName, kills, lastKill, aggregate.collapsed, () -> {
                        aggregate.collapsed = !aggregate.collapsed;
                        parentPlugin.onTripStatusChanged();
                    }, parentPlugin.getItemManager(), parentPlugin.isSpriteDisplayMode());
                    panelBox.setParentPlugin(parentPlugin);
                    if (!showHidden) {
                        panelBox.setExcludedItems(parentPlugin.getExcludedItems());
                    }
                    lootPanels.put(npcName, panelBox);
                }
            }
            tripPanelBoxes.put(id, lootPanels);
        }

        // Restore collapsed NPC state before rebuilding
        collapsedNpcs = savedCollapsedNpcs;

        rebuildLootPanel();

        // Swap the old panel for the new one in a single operation
        int index = layoutPanel.getComponentZOrder(oldPanel);
        layoutPanel.remove(oldPanel);
        layoutPanel.add(lootBoxPanel, index);
        layoutPanel.revalidate();
        layoutPanel.repaint();
    }

    private void createNewTrip() {
        boolean isActiveTrip = parentPlugin.checkForActiveTrip();

        if (!isActiveTrip) {
            String tripName = "TRIP " + parentPlugin.getNextTripNumber();
            parentPlugin.initTrip(tripName);

            Trip activeTrip = parentPlugin.getActiveTrip();
            TripPanel tripPanel = new TripPanel(activeTrip);
            if (!showHidden) {
                tripPanel.setExcludedItems(parentPlugin.getExcludedItems());
                tripPanel.setExcludedNpcs(parentPlugin.getExcludedNpcs());
            }
            tripsMap.put(activeTrip.getTripId(), tripPanel);

            activeTripLootPanels = new LinkedHashMap<>();
            tripPanelBoxes.put(activeTrip.getTripId(), activeTripLootPanels);

            // Remove empty state if present, then add the new trip header without a full rebuild
            if (lootBoxPanel.getComponentCount() > 2) {
                Component third = lootBoxPanel.getComponent(2);
                if (third instanceof JPanel && "emptyState".equals(third.getName())) {
                    lootBoxPanel.remove(third);
                }
            }
            lootBoxPanel.add(tripPanel.buildHeaderPanel(), 2);
            lootBoxPanel.revalidate();
            lootBoxPanel.repaint();

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
        clearButton.addFocusListener(new FocusAdapter() {
            final Border defaultBorder = new EmptyBorder(5, 10, 5, 10);
            final Border focusBorder = new CompoundBorder(
                    new LineBorder(FOCUS_COLOR, 2),
                    new EmptyBorder(3, 8, 3, 8)
            );

            @Override
            public void focusGained(FocusEvent e) {
                clearButton.setBorder(focusBorder);
            }

            @Override
            public void focusLost(FocusEvent e) {
                clearButton.setBorder(defaultBorder);
            }
        });
        clearButton.addActionListener(e -> confirmClearAllData());
        footer.add(clearButton, BorderLayout.CENTER);

        return footer;
    }

    public void removeTrip(int tripId) {
        tripsMap.remove(tripId);
        tripPanelBoxes.remove(tripId);
        lootBoxPanel.setVisible(false);
        rebuildTripEntries();
        lootBoxPanel.setVisible(true);
        // Move focus to the + button to prevent accidental focus on "Clear all data"
        addTripButton.requestFocusInWindow();
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
                this::hideComparisonView,
                parentPlugin
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

        layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        add(layoutPanel, BorderLayout.NORTH);

        layoutPanel.add(buildTrackingModeControls());
        layoutPanel.add(buildFilterPanel());
        layoutPanel.add(buildLootBoxPanel());
        add(buildFooter(), BorderLayout.SOUTH);

        // Switch to trip view and rebuild
        selectedTrackingMode = 2;
        listModeButton.setSelected(false);
        groupedModeButton.setSelected(false);
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
        collapsedNpcs.clear();
        rebuildLootPanel();
    }
}
