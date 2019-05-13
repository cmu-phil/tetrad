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
package edu.cmu.tetradapp.editor.search;

import edu.cmu.tetrad.annotation.Algorithm;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.editor.EdgeTypeTable;
import edu.cmu.tetradapp.editor.GeneralAlgorithmEditor;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.Box;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 *
 * Apr 15, 2019 4:49:15 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class GraphCard extends JPanel {

    private static final long serialVersionUID = 8349434413076189088L;

    private final JButton backBtn = new JButton("<   Set Parameters");

    private final JLabel title = new JLabel();
    private final JPanel graphContainer = new JPanel(new BorderLayout(0, 5));

    private JSplitPane mainPanel = new JSplitPane();

    private final GeneralAlgorithmRunner algorithmRunner;

    private final EdgeTypeTable edgeTypeTable;

    public GraphCard(GeneralAlgorithmEditor algorithmEditor, GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;
        this.edgeTypeTable = new EdgeTypeTable();

        initComponents(algorithmEditor);
    }

    private void initComponents(GeneralAlgorithmEditor algorithmEditor) {
        Dimension buttonSize = new Dimension(268, 25);
        backBtn.setMinimumSize(buttonSize);
        backBtn.setMaximumSize(buttonSize);
        backBtn.addActionListener((e) -> {
            firePropertyChange("graphBack", null, null);
        });

        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setVerticalAlignment(SwingConstants.CENTER);

        graphContainer.add(title, BorderLayout.NORTH);
        graphContainer.add(mainPanel, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(new PaddingPanel(graphContainer), BorderLayout.CENTER);
        add(new SouthPanel(), BorderLayout.SOUTH);

        addPropertyChangeListener(algorithmEditor);
    }

    /**
     * Resulting graph with bootstrap table - Zhou
     *
     * @param graph
     * @return
     */
    private JSplitPane createSearchResultPane(Graph graph) {
        // topBox contains the graphEditorScroll and the instructionBox underneath
        Box topBox = Box.createVerticalBox();
        topBox.setPreferredSize(new Dimension(820, 400));

        GraphWorkbench graphWorkbench = new GraphWorkbench(graph);
        graphWorkbench.enableEditing(false);

        // topBox graph editor
        JScrollPane graphEditorScroll = new JScrollPane();
        graphEditorScroll.setPreferredSize(new Dimension(820, 420));
        graphEditorScroll.setViewportView(graphWorkbench);

        // Instruction with info button
        Box instructionBox = Box.createHorizontalBox();
        instructionBox.setMaximumSize(new Dimension(820, 40));

        JLabel label = new JLabel("More information on graph edge types");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Info button added by Zhou to show edge types
        JButton infoBtn = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        infoBtn.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Clock info button to show edge types instructions - Zhou
        infoBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Initialize helpSet
                String helpHS = "/resources/javahelp/TetradHelp.hs";

                try {
                    URL url = this.getClass().getResource(helpHS);
                    HelpSet helpSet = new HelpSet(null, url);

                    helpSet.setHomeID("graph_edge_types");
                    HelpBroker broker = helpSet.createHelpBroker();
                    ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                    listener.actionPerformed(e);
                } catch (Exception ee) {
                    System.out.println("HelpSet " + ee.getMessage());
                    System.out.println("HelpSet " + helpHS + " not found");
                    throw new IllegalArgumentException();
                }
            }
        });

        instructionBox.add(label);
        instructionBox.add(Box.createHorizontalStrut(2));
        instructionBox.add(infoBtn);

        // Add to topBox
        topBox.add(graphEditorScroll);
        topBox.add(instructionBox);

        edgeTypeTable.setPreferredSize(new Dimension(820, 150));

        // Use JSplitPane to allow resize the bottom box - Zhou
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topBox, edgeTypeTable);
        splitPane.setDividerLocation((int) (splitPane.getPreferredSize().getHeight() - 150));

        edgeTypeTable.update(graph);

        return splitPane;
    }

    public void refresh() {
        title.setText("Algorithm: " + algorithmRunner.getAlgorithm().getClass().getAnnotation(Algorithm.class).name());

        graphContainer.remove(mainPanel);

        mainPanel = createSearchResultPane(algorithmRunner.getGraph());

        graphContainer.add(mainPanel, BorderLayout.CENTER);
        graphContainer.revalidate();
        graphContainer.repaint();
    }

    private class SouthPanel extends JPanel {

        private static final long serialVersionUID = -6938352710872851817L;

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
                                    .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            );
            layout.setVerticalGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(backBtn)
                                    .addContainerGap())
            );

            this.setLayout(layout);
        }
    }

}
