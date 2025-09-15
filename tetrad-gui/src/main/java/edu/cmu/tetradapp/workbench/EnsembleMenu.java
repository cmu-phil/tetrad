///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.GraphSampling;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import javax.swing.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Mar 19, 2023 1:45:50 AM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 * @version $Id: $Id
 */
public class EnsembleMenu extends JMenu {

    public static ResamplingEdgeEnsemble resamplingEdgeEnsemble = ResamplingEdgeEnsemble.Majority;
    /**
     * The workbench graph.
     */
    private final GraphWorkbench graphWorkbench;

    /**
     * <p>Constructor for EnsembleMenu.</p>
     *
     * @param graphWorkbench a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public EnsembleMenu(GraphWorkbench graphWorkbench) {
        super("Ensemble Display");
        this.graphWorkbench = graphWorkbench;

        initComponents();
    }

    public static boolean isSamplingGraph(Graph graph) {
        if (graph == null) {
            return false;
        } else if (graph.getEdges().isEmpty()) {
            return false;
        } else {
            Edge edge = graph.getEdges().iterator().next();

            return edge.getEdgeTypeProbabilities() != null && !edge.getEdgeTypeProbabilities().isEmpty();
        }
    }

    public static boolean isSameGraph(Graph graph1, Graph graph2) {
        if (graph1 == null || graph2 == null) {
            return false;
        }

        List<Node> graph1Nodes = graph1.getNodes();
        List<Node> graph2Nodes = graph2.getNodes();
        if (graph1Nodes.isEmpty() || graph2Nodes.isEmpty()) {
            return false;
        }

        return graph1Nodes.getFirst() == graph2Nodes.getFirst();
    }

    private void initComponents() {
        JMenuItem highestEnsemble = new JMenuItem(ResamplingEdgeEnsemble.Highest.name());
        JMenuItem majorityEnsemble = new JMenuItem(ResamplingEdgeEnsemble.Majority.name());
        JMenuItem preservedEnsemble = new JMenuItem(ResamplingEdgeEnsemble.Preserved.name());
        JMenuItem thresholdEnsemble = new JMenuItem(ResamplingEdgeEnsemble.Threshold.name());

        highestEnsemble.addActionListener(action -> {
            Graph workbenchGraph = graphWorkbench.getGraph();
            Graph samplingGraph = ((EdgeListGraph) workbenchGraph).getAncillaryGraph("samplingGraph");

            if (samplingGraph == null) {
                throw new IllegalStateException("Cannot find sampling graph");
            }

            Graph displayGraph = GraphSampling.createDisplayGraph(samplingGraph,
                    ResamplingEdgeEnsemble.Highest);
            ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", samplingGraph);

            resamplingEdgeEnsemble = ResamplingEdgeEnsemble.Highest;

            displayGraph = GraphUtils.fixDirections(displayGraph);

            graphWorkbench.setGraph(displayGraph);
        });
        majorityEnsemble.addActionListener(action -> {
            Graph workbenchGraph = graphWorkbench.getGraph();
            Graph samplingGraph = ((EdgeListGraph) workbenchGraph).getAncillaryGraph("samplingGraph");

            if (samplingGraph == null) {
                throw new IllegalStateException("Cannot find sampling graph");
            }

            Graph displayGraph = GraphSampling.createDisplayGraph(samplingGraph,
                    ResamplingEdgeEnsemble.Majority);
            ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", samplingGraph);

            resamplingEdgeEnsemble = ResamplingEdgeEnsemble.Majority;

            displayGraph = GraphUtils.fixDirections(displayGraph);

            graphWorkbench.setGraph(displayGraph);
        });
        preservedEnsemble.addActionListener(action -> {
            Graph workbenchGraph = graphWorkbench.getGraph();
            Graph samplingGraph = ((EdgeListGraph) workbenchGraph).getAncillaryGraph("samplingGraph");

            if (samplingGraph == null) {
                throw new IllegalStateException("Cannot find sampling graph");
            }

            Graph displayGraph = GraphSampling.createDisplayGraph(samplingGraph,
                    ResamplingEdgeEnsemble.Preserved);
            ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", samplingGraph);

            resamplingEdgeEnsemble = ResamplingEdgeEnsemble.Preserved;

            displayGraph = GraphUtils.fixDirections(displayGraph);

            graphWorkbench.setGraph(displayGraph);
        });
        thresholdEnsemble.addActionListener(action -> {
            Graph workbenchGraph = graphWorkbench.getGraph();
            Graph samplingGraph = ((EdgeListGraph) workbenchGraph).getAncillaryGraph("samplingGraph");

            if (samplingGraph == null) {
                throw new IllegalStateException("Cannot find sampling graph");
            }

            while (true) {
                String response = JOptionPane.showInputDialog(graphWorkbench,
                        "Please enter a treshold between 0 and 1:",
                        "Threshold",
                        JOptionPane.QUESTION_MESSAGE);

                if (response != null) {
                    try {
                        double threshold = Double.parseDouble(response);

                        if (threshold < 0 || threshold > 1) {
                            throw new NumberFormatException();
                        }

                        Preferences.userRoot().putDouble("edge.ensemble.threshold", threshold);
                        break;
                    } catch (NumberFormatException e) {
                        // try again.
                    }
                } else {
                    return;
                }
            }

            Graph displayGraph = GraphSampling.createDisplayGraph(samplingGraph,
                    ResamplingEdgeEnsemble.Threshold);
            ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", samplingGraph);

            resamplingEdgeEnsemble = ResamplingEdgeEnsemble.Threshold;

            displayGraph = GraphUtils.fixDirections(displayGraph);

            graphWorkbench.setGraph(displayGraph);
        });

        add(highestEnsemble);
        add(majorityEnsemble);
        add(preservedEnsemble);
        add(thresholdEnsemble);
    }

}

