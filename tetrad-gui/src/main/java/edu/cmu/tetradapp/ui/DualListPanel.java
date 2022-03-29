/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetradapp.ui;

import edu.cmu.tetrad.graph.Node;

import javax.swing.*;
import javax.swing.LayoutStyle.ComponentPlacement;
import java.awt.*;

/**
 * Nov 21, 2017 2:13:40 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DualListPanel extends JPanel {

    private static final long serialVersionUID = -5301381955599984479L;

    private JPanel buttonPanel;
    private JPanel buttonParentPanel;
    private JButton moveToSource;
    private JButton moveToselector;
    private JList<Node> selectedList;
    private JScrollPane selectedScrollPane;
    private JList<Node> sourceList;
    private JScrollPane unselectedScrollPane;

    public DualListPanel() {
        initComponents();
    }

    private void initComponents() {
        final GridBagConstraints gridBagConstraints;

        this.sourceList = new JList<>();
        this.selectedList = new JList<>();

        this.unselectedScrollPane = new JScrollPane(this.sourceList);
        this.selectedScrollPane = new JScrollPane(this.selectedList);

        this.buttonParentPanel = new JPanel();
        this.buttonPanel = new JPanel();
        this.moveToselector = new JButton();
        this.moveToSource = new JButton();

        setOpaque(false);

        this.unselectedScrollPane.setBorder(BorderFactory.createTitledBorder("Not selected"));

        this.unselectedScrollPane.setViewportView(this.sourceList);

        this.selectedScrollPane.setBorder(BorderFactory.createTitledBorder("Selected"));

        this.selectedScrollPane.setViewportView(this.selectedList);

        this.buttonParentPanel.setOpaque(false);
        this.buttonParentPanel.setLayout(new GridBagLayout());

        this.buttonPanel.setOpaque(false);

        this.moveToselector.setText(">");
        this.moveToselector.setMaximumSize(new Dimension(64, 25));
        this.moveToselector.setMinimumSize(new Dimension(64, 25));
        this.moveToselector.setPreferredSize(new Dimension(64, 25));

        this.moveToSource.setText("<");
        this.moveToSource.setMaximumSize(new Dimension(64, 25));
        this.moveToSource.setMinimumSize(new Dimension(64, 25));
        this.moveToSource.setPreferredSize(new Dimension(64, 25));

        final GroupLayout buttonPanelLayout = new GroupLayout(this.buttonPanel);
        this.buttonPanel.setLayout(buttonPanelLayout);
        buttonPanelLayout.setHorizontalGroup(
                buttonPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(buttonPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(buttonPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(moveToselector, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(moveToSource, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                .addContainerGap())
        );
        buttonPanelLayout.setVerticalGroup(
                buttonPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(buttonPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(moveToselector, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(ComponentPlacement.RELATED)
                                .addComponent(moveToSource, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())
        );

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        buttonParentPanel.add(buttonPanel, gridBagConstraints);

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(unselectedScrollPane, GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
                                .addPreferredGap(ComponentPlacement.RELATED)
                                .addComponent(buttonParentPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(ComponentPlacement.RELATED)
                                .addComponent(selectedScrollPane, GroupLayout.DEFAULT_SIZE, 144, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(this.unselectedScrollPane)
                        .addComponent(this.buttonParentPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(this.selectedScrollPane, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );

        this.unselectedScrollPane.getAccessibleContext().setAccessibleName("Unselected");
        this.unselectedScrollPane.getAccessibleContext().setAccessibleDescription("");
    }

    public JButton getMoveToSource() {
        return this.moveToSource;
    }

    public JButton getMoveToselector() {
        return this.moveToselector;
    }

    public JList<Node> getSelectedList() {
        return this.selectedList;
    }

    public JScrollPane getSelectedScrollPane() {
        return this.selectedScrollPane;
    }

    public JList<Node> getSourceList() {
        return this.sourceList;
    }

    public JScrollPane getUnselectedScrollPane() {
        return this.unselectedScrollPane;
    }

}
