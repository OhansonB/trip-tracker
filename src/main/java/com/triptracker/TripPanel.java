package com.triptracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;

public class TripPanel extends JPanel {
    private JPanel outerPanel;
    private JPanel lootBoxPanel;
    private String tripName;

    TripPanel(String tripName) {
        this.tripName = tripName;

        buildTripTitlePanel();
    }

    public void addLootBox(LinkedHashMap<String, Object> npcDropsSummary, String npcName) {
        lootBoxPanel.add(new LootTrackingPanelBox(npcDropsSummary, npcName));
        lootBoxPanel.revalidate();
        lootBoxPanel.repaint();
    }

    public JPanel getTripPanel() {
        return outerPanel;
    }

    private void buildTripTitlePanel() {
        outerPanel = new JPanel();
        outerPanel.setLayout(new BorderLayout());
        outerPanel.setBackground(ColorScheme.BRAND_ORANGE);
        outerPanel.setPreferredSize(new Dimension(0, 30));
        outerPanel.setBorder(new EmptyBorder(2, 0, 2, 0));

        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BorderLayout());
        innerPanel.setBackground(ColorScheme.GRAND_EXCHANGE_LIMIT);
        innerPanel.setPreferredSize(new Dimension(0, 10));
        innerPanel.setBorder(new EmptyBorder(5, 10, 5, 5));
        outerPanel.add(innerPanel);

        JLabel titleLabel = new JLabel();
        titleLabel.setText(this.tripName);
        titleLabel.setFont(FontManager.getRunescapeFont());
        titleLabel.setForeground(Color.LIGHT_GRAY);

        innerPanel.add(titleLabel, BorderLayout.WEST);
        innerPanel.repaint();
        innerPanel.revalidate();


        lootBoxPanel = new JPanel();
        lootBoxPanel.setLayout(new BoxLayout(lootBoxPanel, BoxLayout.Y_AXIS));
        outerPanel.add(lootBoxPanel);

    }
}
