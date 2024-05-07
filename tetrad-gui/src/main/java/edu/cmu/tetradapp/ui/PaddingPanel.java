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

import javax.swing.*;
import java.io.Serial;

/**
 * This a wrapper panel that puts gaps around the wrapped panel.
 * <p>
 * Nov 22, 2017 11:35:16 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class PaddingPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 6075091842307611079L;

    /**
     * Inner component.
     */
    private final JComponent innerComponent;

    /**
     * Layered pane.
     */
    private final JLayeredPane layeredPane;

    /**
     * <p>Constructor for PaddingPanel.</p>
     *
     * @param innerComponent a {@link javax.swing.JComponent} object
     */
    public PaddingPanel(JComponent innerComponent) {
        this.innerComponent = (innerComponent == null) ? new JPanel() : innerComponent;
        this.layeredPane = new JLayeredPane();

        initComponents();
    }

    private void initComponents() {
        this.layeredPane.setLayer(this.innerComponent, JLayeredPane.DEFAULT_LAYER);

        GroupLayout layeredPaneLayout = new GroupLayout(this.layeredPane);
        this.layeredPane.setLayout(layeredPaneLayout);
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
        setLayout(layout);
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
