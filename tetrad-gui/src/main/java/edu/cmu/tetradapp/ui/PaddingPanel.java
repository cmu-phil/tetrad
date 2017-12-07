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

import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

/**
 * This a wrapper panel that puts gaps around the wrapped panel.
 *
 * Nov 22, 2017 11:35:16 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class PaddingPanel extends JPanel {

    private static final long serialVersionUID = 6075091842307611079L;

    private final JComponent innerComponent;
    private final JLayeredPane layeredPane;

    public PaddingPanel(JComponent innerComponent) {
        this.innerComponent = (innerComponent == null) ? new JPanel() : innerComponent;
        this.layeredPane = new JLayeredPane();

        initComponents();
    }

    private void initComponents() {
        layeredPane.setLayer(innerComponent, JLayeredPane.DEFAULT_LAYER);

        GroupLayout layeredPaneLayout = new GroupLayout(layeredPane);
        layeredPane.setLayout(layeredPaneLayout);
        layeredPaneLayout.setHorizontalGroup(layeredPaneLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layeredPaneLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(innerComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
        );
        layeredPaneLayout.setVerticalGroup(layeredPaneLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layeredPaneLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(innerComponent, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
        );

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(layeredPane, GroupLayout.Alignment.TRAILING)
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(layeredPane, GroupLayout.Alignment.TRAILING)
        );
    }

}
