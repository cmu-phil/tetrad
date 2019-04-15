/*
 * Copyright (C) 2019 University of Pittsburgh.
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
package edu.cmu.tetradapp.editor.algorithm;

import edu.cmu.tetradapp.editor.AlgorithmParameterPanel;
import edu.cmu.tetradapp.editor.GeneralAlgorithmEditor;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 *
 * Apr 15, 2019 3:35:36 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ParameterCard extends JPanel {

    private static final long serialVersionUID = -5820689812610797211L;

    private final JButton backBtn = new JButton("<   Choose Algorithm");
    private final JButton forwardBtn = new JButton("Run Search & Generate Graph   >");

    private final AlgorithmParameterPanel paramPanel = new AlgorithmParameterPanel();

    private final GeneralAlgorithmRunner algorithmRunner;

    public ParameterCard(GeneralAlgorithmEditor algorithmEditor, GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;

        initComponents(algorithmEditor);
    }

    private void initComponents(GeneralAlgorithmEditor algorithmEditor) {
        Dimension buttonSize = new Dimension(268, 25);
        backBtn.setMinimumSize(buttonSize);
        backBtn.setMaximumSize(buttonSize);
        backBtn.addActionListener(e -> {
            firePropertyChange("paramBack", null, null);
        });

        forwardBtn.setMinimumSize(buttonSize);
        forwardBtn.setMaximumSize(buttonSize);
        forwardBtn.addActionListener(e -> {
            firePropertyChange("paramFwd", null, null);
        });

        setLayout(new BorderLayout());
        add(new JScrollPane(new PaddingPanel(paramPanel)), BorderLayout.CENTER);
        add(new SouthPanel(), BorderLayout.SOUTH);

        addPropertyChangeListener(algorithmEditor);
    }

    public void refresh() {
        this.paramPanel.addToPanel(algorithmRunner);
    }

    public void disableButtons() {
        backBtn.setEnabled(false);
        forwardBtn.setEnabled(false);
    }

    public void enableButtons() {
        backBtn.setEnabled(true);
        forwardBtn.setEnabled(true);
    }

    private final class SouthPanel extends JPanel {

        private static final long serialVersionUID = -4055772024145978761L;

        public SouthPanel() {
            initComponents();
        }

        private void initComponents() {
            GroupLayout layout = new GroupLayout(this);
            layout.setHorizontalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(backBtn)
                                    .addGap(18, 18, 18)
                                    .addComponent(forwardBtn)
                                    .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            );

            layout.linkSize(SwingConstants.HORIZONTAL, new java.awt.Component[]{backBtn, forwardBtn});
            layout.setVerticalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                            .addComponent(backBtn)
                                            .addComponent(forwardBtn))
                                    .addContainerGap())
            );

            this.setLayout(layout);
        }
    }

}
