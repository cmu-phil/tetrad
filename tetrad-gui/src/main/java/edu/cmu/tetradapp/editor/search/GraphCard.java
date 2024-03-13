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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.editor.*;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serial;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

/**
 * Apr 15, 2019 4:49:15 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class GraphCard extends JPanel {
    @Serial
    private static final long serialVersionUID = -7654484444146823298L;

    /**
     * The algorithm runner.
     */
    private final GeneralAlgorithmRunner algorithmRunner;

    /**
     * The workbench.
     */
    private GraphWorkbench workbench;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for GraphCard.</p>
     *
     * @param algorithmRunner a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     */
    public GraphCard(GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;
        this.knowledge = algorithmRunner.getKnowledge();
        initComponents();
    }

    public static boolean isLatentVariableAlgorithm(Algorithm algorithm) {
        if (algorithm == null) {
            throw new NullPointerException("Algorithm must not be null.");
        }

        Annotation annotation = algorithm.getClass().getAnnotationsByType(edu.cmu.tetrad.annotation.Algorithm.class)[0];
        try {
            Method method = annotation.annotationType().getDeclaredMethod("algoType");
            AlgType ret = (AlgType) method.invoke(annotation);

            if (ret == AlgType.allow_latent_common_causes) {
                return true;
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("Error in getting algorithm type from annotation", e);
        }

        return false;
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(50, 406));
    }

    /**
     * <p>refresh.</p>
     */
    public void refresh() {
        removeAll();

        setBorder(BorderFactory.createTitledBorder(this.algorithmRunner.getAlgorithm().getDescription()));

        Graph graph = this.algorithmRunner.getGraph();

        PaddingPanel graphPanel = new PaddingPanel(createGraphPanel(graph));
        EdgeTypeTable edgePanel = createEdgeTypeTable(graph);

        JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.RIGHT);
        tabbedPane.addTab("Graph", graphPanel);
        tabbedPane.addTab("Edges", edgePanel);
        tabbedPane.addChangeListener(event -> {
            // update edgetype table with new graph
            if (tabbedPane.getSelectedComponent() == edgePanel) {
                Graph workbenchGraph = this.workbench.getGraph();
                Graph edgePanelGraph = edgePanel.getGraph();
                if (edgePanelGraph != workbenchGraph) {
                    edgePanel.update(workbenchGraph);
                }
            }
        });
        add(tabbedPane, BorderLayout.CENTER);

        add(menuBar(), BorderLayout.NORTH);

        revalidate();
        repaint();
    }

    JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new SaveComponentImage(this.workbench, "Save Graph Image..."));

        menuBar.add(file);

        JMenu graph = new JMenu("Graph");

        graph.add(new GraphPropertiesAction(this.workbench));
        graph.add(new PathsAction(this.workbench));
        graph.add(new UnderliningsAction(this.workbench));

        graph.add(new JMenuItem(new SelectDirectedAction(this.workbench)));
        graph.add(new JMenuItem(new SelectBidirectedAction(this.workbench)));
        graph.add(new JMenuItem(new SelectUndirectedAction(this.workbench)));
        graph.add(new JMenuItem(new SelectTrianglesAction(this.workbench)));
        graph.add(new JMenuItem(new SelectLatentsAction(this.workbench)));
        graph.add(new PagColorer(this.workbench));

        menuBar.add(graph);

        return menuBar;
    }

    private EdgeTypeTable createEdgeTypeTable(Graph graph) {
        EdgeTypeTable edgeTypeTable = new EdgeTypeTable();
        edgeTypeTable.setPreferredSize(new Dimension(825, 100));
        edgeTypeTable.update(graph);

        return edgeTypeTable;
    }

    private JPanel createGraphPanel(Graph graph) {
        GraphWorkbench graphWorkbench = new GraphWorkbench(graph);
        graphWorkbench.setKnowledge(knowledge);
        graphWorkbench.enableEditing(false);

        this.workbench = graphWorkbench;

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(825, 406));
        mainPanel.add(new JScrollPane(graphWorkbench), BorderLayout.CENTER);

        if (isLatentVariableAlgorithm(this.algorithmRunner.getAlgorithm())) {
            mainPanel.add(createLatentVariableInstructionBox(), BorderLayout.SOUTH);
        }

        return mainPanel;
    }

    private Box createLatentVariableInstructionBox() {
        JLabel label = new JLabel("More information on FCI graph edge types");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Info button added by Zhou to show edge types
        JButton infoBtn = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        infoBtn.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Clock info button to show edge types instructions - Zhou
        infoBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Initialize helpSet
                final String helpHS = "/docs/javahelp/TetradHelp.hs";

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
