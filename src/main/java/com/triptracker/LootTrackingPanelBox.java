package com.triptracker;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public class LootTrackingPanelBox extends JPanel {
    private static final int ITEMS_PER_ROW = 5;

    private TrackableItemDrop itemDrop;
    private final int boxType;
    private Set<String> excludedItems = new HashSet<>();
    private EnhancedLootTrackerPlugin parentPlugin;
    private int numberOfKills;
    private String npcName;
    private long totalGeValue;
    private String lastKillTimeFormatted;
    private ArrayList<LootAggregation> lootAggregations;
    private boolean initialCollapsed;
    private Runnable onCollapseChanged;
    private ItemManager itemManager;
    private boolean spriteMode;
    final JPanel dropDetailPanel = new JPanel();
    final JLabel summaryPanelTitle = new JLabel();
    final JLabel dropValueLabel = new JLabel();


    // This constructor is used when creating a loot box panel containing a single drop (e.g., in list view)
    LootTrackingPanelBox(TrackableItemDrop itemDrop) {
        this.itemDrop = itemDrop;
        this.boxType = 0;
        this.initialCollapsed = itemDrop.isCollapsed();
    }

    LootTrackingPanelBox(TrackableItemDrop itemDrop, ItemManager itemManager, boolean spriteMode) {
        this.itemDrop = itemDrop;
        this.boxType = 0;
        this.initialCollapsed = itemDrop.isCollapsed();
        this.itemManager = itemManager;
        this.spriteMode = spriteMode;
    }

    LootTrackingPanelBox(ArrayList<LootAggregation> lootAggregation, String npcName, int numberOfKills, String lastKillTime) {
        this.lootAggregations = lootAggregation;
        this.npcName = npcName;
        this.numberOfKills = numberOfKills;
        this.lastKillTimeFormatted = lastKillTime;
        totalGeValue = lootAggregations.stream().mapToLong(LootAggregation::getTotalGePrice).sum();

        this.boxType = 1;
    }

    LootTrackingPanelBox(ArrayList<LootAggregation> lootAggregation, String npcName, int numberOfKills, String lastKillTime, ItemManager itemManager, boolean spriteMode) {
        this.lootAggregations = lootAggregation;
        this.npcName = npcName;
        this.numberOfKills = numberOfKills;
        this.lastKillTimeFormatted = lastKillTime;
        totalGeValue = lootAggregations.stream().mapToLong(LootAggregation::getTotalGePrice).sum();
        this.itemManager = itemManager;
        this.spriteMode = spriteMode;

        this.boxType = 1;
    }

    LootTrackingPanelBox(ArrayList<LootAggregation> lootAggregation, String npcName, int numberOfKills, String lastKillTime, boolean collapsed, Runnable onCollapseChanged) {
        this.lootAggregations = lootAggregation;
        this.npcName = npcName;
        this.numberOfKills = numberOfKills;
        this.lastKillTimeFormatted = lastKillTime;
        totalGeValue = lootAggregations.stream().mapToLong(LootAggregation::getTotalGePrice).sum();
        this.initialCollapsed = collapsed;
        this.onCollapseChanged = onCollapseChanged;

        this.boxType = 1;
    }

    LootTrackingPanelBox(ArrayList<LootAggregation> lootAggregation, String npcName, int numberOfKills, String lastKillTime, boolean collapsed, Runnable onCollapseChanged, ItemManager itemManager, boolean spriteMode) {
        this.lootAggregations = lootAggregation;
        this.npcName = npcName;
        this.numberOfKills = numberOfKills;
        this.lastKillTimeFormatted = lastKillTime;
        totalGeValue = lootAggregations.stream().mapToLong(LootAggregation::getTotalGePrice).sum();
        this.initialCollapsed = collapsed;
        this.onCollapseChanged = onCollapseChanged;
        this.itemManager = itemManager;
        this.spriteMode = spriteMode;

        this.boxType = 1;
    }

    JPanel buildPanelBox() {
        dropDetailPanel.setVisible(true);
        final JLabel dropTimeDateLabel = new JLabel();

        // This panel contains the grid that shows item drop detail
        final JPanel droppedItemsPanel = new JPanel();
        droppedItemsPanel.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        droppedItemsPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weightx = 1.0;

        switch (boxType) {
            case 0:
                summaryPanelTitle.setText(itemDrop.getDropNpcName() + " (lvl " + itemDrop.getDropNpcLevel() + ")");
                dropTimeDateLabel.setText(itemDrop.getDateFromLong(itemDrop.getDropTimeDate()));

                ArrayList<TrackableDroppedItem> droppedItems = itemDrop.getDroppedItems();
                Collections.sort(droppedItems);

                // Filter excluded items and calculate adjusted value
                long adjustedGeValue = itemDrop.getTotalDropGeValue();
                if (!excludedItems.isEmpty()) {
                    droppedItems = new ArrayList<>(droppedItems);
                    for (TrackableDroppedItem excluded : new ArrayList<>(droppedItems)) {
                        if (excludedItems.contains(excluded.getItemName().toLowerCase().trim())) {
                            adjustedGeValue -= excluded.getTotalGePrice();
                        }
                    }
                    droppedItems.removeIf(item -> excludedItems.contains(item.getItemName().toLowerCase().trim()));
                }
                dropValueLabel.setText(FormatUtil.shortenNumber(adjustedGeValue) + " gp");

                if (spriteMode && itemManager != null) {
                    buildSpriteGrid(droppedItems, droppedItemsPanel);
                } else {
                    for (final TrackableDroppedItem item: droppedItems) {
                        gbc.gridx = 0;
                        gbc.anchor = GridBagConstraints.LINE_START;

                        JLabel droppedItemNameLabel = new JLabel();
                        droppedItemNameLabel.setText(item.getItemName() + " x" + FormatUtil.shortenNumber(item.getQuantity()));
                        droppedItemNameLabel.setFont(FontManager.getRunescapeSmallFont());
                        droppedItemNameLabel.setForeground(Color.WHITE);
                        droppedItemNameLabel.setBorder(new EmptyBorder(2, 5, 4, 5));
                        final String itemNameForMenu = item.getItemName();
                        droppedItemNameLabel.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mousePressed(MouseEvent e) {
                                if (e.isPopupTrigger()) showItemContextMenu(e, droppedItemNameLabel, itemNameForMenu);
                            }
                            @Override
                            public void mouseReleased(MouseEvent e) {
                                if (e.isPopupTrigger()) showItemContextMenu(e, droppedItemNameLabel, itemNameForMenu);
                            }
                        });
                        droppedItemsPanel.add(droppedItemNameLabel, gbc);

                        gbc.gridx = 1;
                        gbc.anchor = GridBagConstraints.LINE_END;

                        JLabel droppedItemValueLabel = new JLabel();
                        droppedItemValueLabel.setText(FormatUtil.shortenNumber(item.getTotalGePrice()) + " gp");
                        droppedItemValueLabel.setFont(FontManager.getRunescapeSmallFont());
                        droppedItemValueLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                        droppedItemValueLabel.setBorder(new EmptyBorder(2, 5, 4, 5));
                        droppedItemValueLabel.setHorizontalAlignment(JLabel.RIGHT);
                        droppedItemsPanel.add(droppedItemValueLabel, gbc);

                        gbc.gridy++;
                    }
                }
                break;
            case 1:
                summaryPanelTitle.setText(npcName + " x" + numberOfKills);
                dropTimeDateLabel.setText("Last kill at: " + lastKillTimeFormatted);

                Collections.sort(lootAggregations);

                // Filter excluded items and calculate adjusted value
                ArrayList<LootAggregation> displayAggregations = lootAggregations;
                long adjustedGroupedValue = totalGeValue;
                if (!excludedItems.isEmpty()) {
                    displayAggregations = new ArrayList<>(lootAggregations);
                    for (LootAggregation agg : lootAggregations) {
                        if (excludedItems.contains(agg.getItemName().toLowerCase().trim())) {
                            adjustedGroupedValue -= agg.getTotalGePrice();
                        }
                    }
                    displayAggregations.removeIf(agg -> excludedItems.contains(agg.getItemName().toLowerCase().trim()));
                }
                dropValueLabel.setText(FormatUtil.shortenNumber(adjustedGroupedValue) + " gp");

                if (spriteMode && itemManager != null) {
                    buildAggregateSpriteGrid(displayAggregations, droppedItemsPanel);
                } else {
                    for (LootAggregation lootAggregation : displayAggregations) {
                        gbc.gridx = 0;
                        gbc.anchor = GridBagConstraints.LINE_START;

                        String itemName = lootAggregation.getItemName();
                        long itemQuantity = lootAggregation.getQuantity();
                        long totalValue = lootAggregation.getTotalGePrice();

                        JLabel droppedItemNameLabel = new JLabel(itemName + " x" + FormatUtil.shortenNumber(itemQuantity));
                        droppedItemNameLabel.setFont(FontManager.getRunescapeSmallFont());
                        droppedItemNameLabel.setForeground(Color.WHITE);
                        droppedItemNameLabel.setBorder(new EmptyBorder(2, 5, 4, 5));
                        final String aggItemNameForMenu = itemName;
                        droppedItemNameLabel.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mousePressed(MouseEvent e) {
                                if (e.isPopupTrigger()) showItemContextMenu(e, droppedItemNameLabel, aggItemNameForMenu);
                            }
                            @Override
                            public void mouseReleased(MouseEvent e) {
                                if (e.isPopupTrigger()) showItemContextMenu(e, droppedItemNameLabel, aggItemNameForMenu);
                            }
                        });
                        droppedItemsPanel.add(droppedItemNameLabel, gbc);

                        gbc.gridx = 1;
                        gbc.anchor = GridBagConstraints.LINE_END;


                        JLabel droppedItemValue = new JLabel(FormatUtil.shortenNumber(totalValue) + " gp", SwingConstants.RIGHT);
                        droppedItemValue.setFont(FontManager.getRunescapeSmallFont());
                        droppedItemValue.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                        droppedItemValue.setBorder(new EmptyBorder(2, 5, 4, 5));
                        droppedItemsPanel.add(droppedItemValue, gbc);
                        gbc.gridy++;
                    }
                }
                break;
            default:
                break;
        }

        // Contains all the other panels that constitute the loot box
        final JPanel outerPanel = new JPanel();
        outerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        outerPanel.setLayout(new BorderLayout());
        outerPanel.setBorder(new EmptyBorder(6, 0, 0, 0));

        // This panel provides the summary information (npc name, drop value, etc.)
        final JPanel innerSummaryPanel = new JPanel();
        innerSummaryPanel.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
        innerSummaryPanel.setLayout(new BorderLayout());
        innerSummaryPanel.setBorder(new EmptyBorder(7, 7, 7, 7));

        // Keyboard accessibility for collapse/expand
        innerSummaryPanel.setFocusable(true);
        String accessibleName = (boxType == 0)
                ? itemDrop.getDropNpcName() + " drop panel"
                : npcName + " loot panel";
        innerSummaryPanel.getAccessibleContext().setAccessibleName(accessibleName);
        innerSummaryPanel.getAccessibleContext().setAccessibleDescription(
                "Collapsible loot panel. Press Enter or Space to toggle.");
        innerSummaryPanel.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                innerSummaryPanel.setBorder(FOCUS_BORDER);
                innerSummaryPanel.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                innerSummaryPanel.setBorder(DEFAULT_BORDER);
                innerSummaryPanel.repaint();
            }
        });
        innerSummaryPanel.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER
                        || e.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE) {
                    toggleCollapse();
                }
            }
        });

        innerSummaryPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    toggleCollapse();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showNpcContextMenu(e, innerSummaryPanel);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showNpcContextMenu(e, innerSummaryPanel);
                }
            }
        });
        innerSummaryPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        outerPanel.add(innerSummaryPanel, BorderLayout.NORTH);

        // This label summaries the npc name and level
        summaryPanelTitle.setFont(FontManager.getRunescapeSmallFont());
        summaryPanelTitle.setForeground(Color.ORANGE);
        innerSummaryPanel.add(summaryPanelTitle, BorderLayout.WEST);

        // This label summaries the drop value
        dropValueLabel.setFont(FontManager.getRunescapeSmallFont());
        dropValueLabel.setForeground(Color.ORANGE);
        innerSummaryPanel.add(dropValueLabel, BorderLayout.EAST);

        // This panel sits under the summary panel and is a parent panel for all other panels showing drop detail
        // such as drop date and dropped items
        dropDetailPanel.setBackground(ColorScheme.GRAND_EXCHANGE_LIMIT);
        dropDetailPanel.setLayout(new BorderLayout());
        outerPanel.add(dropDetailPanel);

        // This panel contains the label that shows the date and time of the drop
        final JPanel dropDatePanel = new JPanel();
        dropDatePanel.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        dropDatePanel.setLayout(new BorderLayout());
        dropDetailPanel.add(dropDatePanel);

        // This label shows the time and date of the drop
        dropTimeDateLabel.setFont(FontManager.getRunescapeSmallFont());
        dropTimeDateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        dropTimeDateLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        dropDatePanel.add(dropTimeDateLabel, BorderLayout.WEST);

        dropDetailPanel.add(droppedItemsPanel, BorderLayout.SOUTH);

        // Restore persisted collapse state
        if (initialCollapsed) {
            dropDetailPanel.setVisible(false);
            summaryPanelTitle.setForeground(COLLAPSED_ORANGE);
            dropValueLabel.setForeground(COLLAPSED_ORANGE);
        }

        return outerPanel;
    }

    private void buildSpriteGrid(ArrayList<TrackableDroppedItem> items, JPanel container) {
        container.removeAll();
        int rowCount = ((items.size() % ITEMS_PER_ROW == 0) ? 0 : 1) + items.size() / ITEMS_PER_ROW;
        container.setLayout(new GridLayout(rowCount, ITEMS_PER_ROW, 1, 1));

        for (int i = 0; i < rowCount * ITEMS_PER_ROW; i++) {
            final JPanel slotContainer = new JPanel();
            slotContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            if (i < items.size()) {
                final TrackableDroppedItem item = items.get(i);
                final JLabel imageLabel = new JLabel();
                imageLabel.setVerticalAlignment(SwingConstants.CENTER);
                imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

                int quantity = (int) Math.min(item.getQuantity(), Integer.MAX_VALUE);
                AsyncBufferedImage itemImage = itemManager.getImage(item.getItemId(), quantity, quantity > 1);
                itemImage.addTo(imageLabel);

                imageLabel.setToolTipText(buildItemTooltip(item.getItemName(), item.getQuantity(), item.getTotalGePrice()));
                final String spriteItemName = item.getItemName();
                imageLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.isPopupTrigger()) showItemContextMenu(e, imageLabel, spriteItemName);
                    }
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (e.isPopupTrigger()) showItemContextMenu(e, imageLabel, spriteItemName);
                    }
                });
                slotContainer.add(imageLabel);
            }

            container.add(slotContainer);
        }
    }

    private void buildAggregateSpriteGrid(ArrayList<LootAggregation> items, JPanel container) {
        container.removeAll();
        int rowCount = ((items.size() % ITEMS_PER_ROW == 0) ? 0 : 1) + items.size() / ITEMS_PER_ROW;
        container.setLayout(new GridLayout(rowCount, ITEMS_PER_ROW, 1, 1));

        for (int i = 0; i < rowCount * ITEMS_PER_ROW; i++) {
            final JPanel slotContainer = new JPanel();
            slotContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            if (i < items.size()) {
                final LootAggregation item = items.get(i);
                final JLabel imageLabel = new JLabel();
                imageLabel.setVerticalAlignment(SwingConstants.CENTER);
                imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

                int quantity = (int) Math.min(item.getQuantity(), Integer.MAX_VALUE);
                AsyncBufferedImage itemImage = itemManager.getImage(item.getItemId(), quantity, quantity > 1);
                itemImage.addTo(imageLabel);

                imageLabel.setToolTipText(buildItemTooltip(item.getItemName(), item.getQuantity(), item.getTotalGePrice()));
                final String aggSpriteItemName = item.getItemName();
                imageLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.isPopupTrigger()) showItemContextMenu(e, imageLabel, aggSpriteItemName);
                    }
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (e.isPopupTrigger()) showItemContextMenu(e, imageLabel, aggSpriteItemName);
                    }
                });
                slotContainer.add(imageLabel);
            }

            container.add(slotContainer);
        }
    }

    private static String buildItemTooltip(String name, long quantity, long gePrice) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(name).append(" x ").append(QuantityFormatter.formatNumber(quantity));
        sb.append("<br>GE: ").append(QuantityFormatter.quantityToStackSize(gePrice));
        if (quantity > 1) {
            sb.append(" (").append(QuantityFormatter.quantityToStackSize(gePrice / quantity)).append(" ea)");
        }
        sb.append("</html>");
        return sb.toString();
    }

    private static final Color COLLAPSED_ORANGE = new Color(0xCC, 0x88, 0x33);
    private static final Color FOCUS_COLOR = new Color(0x5E, 0x9E, 0xD6);
    private static final Border DEFAULT_BORDER = new EmptyBorder(7, 7, 7, 7);
    private static final Border FOCUS_BORDER = new CompoundBorder(
            new LineBorder(FOCUS_COLOR, 2),
            new EmptyBorder(5, 5, 5, 5)
    );

    private void toggleCollapse() {
        if (dropDetailPanel.isVisible()) {
            dropDetailPanel.setVisible(false);
            summaryPanelTitle.setForeground(COLLAPSED_ORANGE);
            dropValueLabel.setForeground(COLLAPSED_ORANGE);
            updateCollapseState(true);
        } else {
            dropDetailPanel.setVisible(true);
            summaryPanelTitle.setForeground(Color.ORANGE);
            dropValueLabel.setForeground(Color.ORANGE);
            updateCollapseState(false);
        }
    }

    private void updateCollapseState(boolean collapsed) {
        this.initialCollapsed = collapsed;
        if (boxType == 0 && itemDrop != null) {
            itemDrop.setCollapsed(collapsed);
        }
        if (onCollapseChanged != null) {
            onCollapseChanged.run();
        }
    }

    void setOnCollapseChanged(Runnable onCollapseChanged) {
        this.onCollapseChanged = onCollapseChanged;
    }

    void setExcludedItems(Set<String> excludedItems) {
        this.excludedItems = excludedItems != null ? excludedItems : new HashSet<>();
    }

    void setParentPlugin(EnhancedLootTrackerPlugin plugin) {
        this.parentPlugin = plugin;
    }

    /**
     * Programmatically set the collapsed state without triggering a full panel rebuild.
     */
    void setCollapsedState(boolean collapsed) {
        this.initialCollapsed = collapsed;
        if (collapsed) {
            dropDetailPanel.setVisible(false);
            summaryPanelTitle.setForeground(COLLAPSED_ORANGE);
            dropValueLabel.setForeground(COLLAPSED_ORANGE);
        } else {
            dropDetailPanel.setVisible(true);
            summaryPanelTitle.setForeground(Color.ORANGE);
            dropValueLabel.setForeground(Color.ORANGE);
        }
        // Don't fire onCollapseChanged — this is a programmatic update, not a user toggle.
        // The caller is responsible for updating any external state (collapsedNpcs, persistence).
        if (boxType == 0 && itemDrop != null) {
            itemDrop.setCollapsed(collapsed);
        }
    }

    private void showNpcContextMenu(MouseEvent e, JPanel source) {
        if (parentPlugin == null) {
            return;
        }
        String npcNameToHide = (boxType == 0) ? itemDrop.getDropNpcName() : npcName;
        if (npcNameToHide == null || npcNameToHide.isEmpty()) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        if (parentPlugin.isNpcExcluded(npcNameToHide)) {
            JMenuItem unhideNpc = new JMenuItem("Unhide \"" + npcNameToHide + "\"");
            unhideNpc.addActionListener(ev -> parentPlugin.removeExcludedNpc(npcNameToHide));
            menu.add(unhideNpc);
        } else {
            JMenuItem hideNpc = new JMenuItem("Hide \"" + npcNameToHide + "\"");
            hideNpc.addActionListener(ev -> parentPlugin.addExcludedNpc(npcNameToHide));
            menu.add(hideNpc);
        }
        menu.show(source, e.getX(), e.getY());
    }

    private void showItemContextMenu(MouseEvent e, JComponent source, String itemName) {
        if (parentPlugin == null || itemName == null || itemName.isEmpty()) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        if (parentPlugin.isItemExcluded(itemName)) {
            JMenuItem unhideItem = new JMenuItem("Unhide \"" + itemName + "\"");
            unhideItem.addActionListener(ev -> parentPlugin.removeExcludedItem(itemName));
            menu.add(unhideItem);
        } else {
            JMenuItem hideItem = new JMenuItem("Hide \"" + itemName + "\"");
            hideItem.addActionListener(ev -> parentPlugin.addExcludedItem(itemName));
            menu.add(hideItem);
        }
        menu.show(source, e.getX(), e.getY());
    }
}
