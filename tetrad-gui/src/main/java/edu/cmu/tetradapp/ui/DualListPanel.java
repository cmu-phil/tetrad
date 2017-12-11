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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.LayoutStyle;

/**
 *
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
        GridBagConstraints gridBagConstraints;

        sourceList = new JList<>();
        selectedList = new JList<>();

        unselectedScrollPane = new JScrollPane(sourceList);
        selectedScrollPane = new JScrollPane(selectedList);

        buttonParentPanel = new JPanel();
        buttonPanel = new JPanel();
        moveToselector = new JButton();
        moveToSource = new JButton();

        setOpaque(false);

        unselectedScrollPane.setBorder(BorderFactory.createTitledBorder("Unselected"));

        unselectedScrollPane.setViewportView(sourceList);

        selectedScrollPane.setBorder(BorderFactory.createTitledBorder("Selected"));

        selectedScrollPane.setViewportView(selectedList);

        buttonParentPanel.setOpaque(false);
        buttonParentPanel.setLayout(new GridBagLayout());

        buttonPanel.setOpaque(false);

        moveToselector.setText(">");
        moveToselector.setMaximumSize(new Dimension(64, 25));
        moveToselector.setMinimumSize(new Dimension(64, 25));
        moveToselector.setPreferredSize(new Dimension(64, 25));

        moveToSource.setText("<");
        moveToSource.setMaximumSize(new Dimension(64, 25));
        moveToSource.setMinimumSize(new Dimension(64, 25));
        moveToSource.setPreferredSize(new Dimension(64, 25));

        GroupLayout buttonPanelLayout = new GroupLayout(buttonPanel);
        buttonPanel.setLayout(buttonPanelLayout);
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
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(moveToSource, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())
        );

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        buttonParentPanel.add(buttonPanel, gridBagConstraints);

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(unselectedScrollPane, GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonParentPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(selectedScrollPane, GroupLayout.DEFAULT_SIZE, 144, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(unselectedScrollPane)
                        .addComponent(buttonParentPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(selectedScrollPane, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );

        unselectedScrollPane.getAccessibleContext().setAccessibleName("Unselected");
        unselectedScrollPane.getAccessibleContext().setAccessibleDescription("");
    }

    public JButton getMoveToSource() {
        return moveToSource;
    }

    public JButton getMoveToselector() {
        return moveToselector;
    }

    public JList<Node> getSelectedList() {
        return selectedList;
    }

    public JScrollPane getSelectedScrollPane() {
        return selectedScrollPane;
    }

    public JList<Node> getSourceList() {
        return sourceList;
    }

    public JScrollPane getUnselectedScrollPane() {
        return unselectedScrollPane;
    }

}
