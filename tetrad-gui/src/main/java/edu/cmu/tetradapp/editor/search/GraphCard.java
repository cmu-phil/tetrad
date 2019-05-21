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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.editor.EdgeTypeTable;
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
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.border.EmptyBorder;

/**
 *
 * Apr 15, 2019 4:49:15 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class GraphCard extends JPanel {

    private static final long serialVersionUID = -7654484444146823298L;

    private final GeneralAlgorithmRunner algorithmRunner;

    public GraphCard(GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;

        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(800, 506));
    }

    public void refresh() {
        removeAll();

        setBorder(BorderFactory.createTitledBorder(algorithmRunner.getAlgorithm().getDescription()));

        Graph graph = algorithmRunner.getGraph();
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createGraphPanel(graph), createEdgeTypeTable(graph));
        splitPane.setDividerLocation(406);
        add(new PaddingPanel(splitPane), BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    private EdgeTypeTable createEdgeTypeTable(Graph graph) {
        EdgeTypeTable edgeTypeTable = new EdgeTypeTable();
        edgeTypeTable.setPreferredSize(new Dimension(825, 100));
        edgeTypeTable.update(graph);

        return edgeTypeTable;
    }

    private JPanel createGraphPanel(Graph graph) {
        GraphWorkbench graphWorkbench = new GraphWorkbench(graph);
        graphWorkbench.enableEditing(false);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(825, 406));
        mainPanel.add(new JScrollPane(graphWorkbench), BorderLayout.CENTER);
        mainPanel.add(createInstructionBox(), BorderLayout.SOUTH);

        return mainPanel;
    }

    private Box createInstructionBox() {
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

        Box instruction = Box.createHorizontalBox();
        instruction.add(label);
        instruction.add(Box.createHorizontalStrut(5));
        instruction.add(infoBtn);

        Box instructionBox = Box.createVerticalBox();
        instructionBox.add(Box.createVerticalStrut(5));
        instructionBox.add(instruction);
        instructionBox.add(Box.createVerticalStrut(5));

        return instructionBox;
    }

}
