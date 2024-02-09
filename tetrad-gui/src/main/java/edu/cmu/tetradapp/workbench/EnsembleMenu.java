///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
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

    private void initComponents() {
        JMenuItem highestEnsemble = new JMenuItem(ResamplingEdgeEnsemble.Highest.name());
        JMenuItem majorityEnsemble = new JMenuItem(ResamplingEdgeEnsemble.Majority.name());
        JMenuItem preservedEnsemble = new JMenuItem(ResamplingEdgeEnsemble.Preserved.name());
        JMenuItem thresholdEnsemble = new JMenuItem(ResamplingEdgeEnsemble.Threshold.name());

        highestEnsemble.addActionListener(action -> {
            Graph workbenchGraph = graphWorkbench.getGraph();
            if (isSamplingGraph(workbenchGraph)) {
                Graph samplingGraph = graphWorkbench.getSamplingGraph();

                // replace original sampling graph if it's a different sampling graph
                if (!isSameGraph(samplingGraph, workbenchGraph)) {
                    samplingGraph = workbenchGraph;
                    graphWorkbench.setSamplingGraph(samplingGraph);
                }

                graphWorkbench.setGraph(
                        GraphSampling.createDisplayGraph(samplingGraph,
                                ResamplingEdgeEnsemble.Highest));
            } else {
                graphWorkbench.setSamplingGraph(null);
            }
        });
        majorityEnsemble.addActionListener(action -> {
            Graph workbenchGraph = graphWorkbench.getGraph();
            if (isSamplingGraph(workbenchGraph)) {
                Graph samplingGraph = graphWorkbench.getSamplingGraph();

                // replace original sampling graph if it's a different sampling graph
                if (!isSameGraph(samplingGraph, workbenchGraph)) {
                    samplingGraph = workbenchGraph;
                    graphWorkbench.setSamplingGraph(samplingGraph);
                }

                graphWorkbench.setGraph(
                        GraphSampling.createDisplayGraph(samplingGraph,
                                ResamplingEdgeEnsemble.Majority));
            } else {
                graphWorkbench.setSamplingGraph(null);
            }
        });
        preservedEnsemble.addActionListener(action -> {
            Graph workbenchGraph = graphWorkbench.getGraph();
            if (isSamplingGraph(workbenchGraph)) {
                Graph samplingGraph = graphWorkbench.getSamplingGraph();

                // replace original sampling graph if it's a different sampling graph
                if (!isSameGraph(samplingGraph, workbenchGraph)) {
                    samplingGraph = workbenchGraph;
                    graphWorkbench.setSamplingGraph(samplingGraph);
                }

                graphWorkbench.setGraph(
                        GraphSampling.createDisplayGraph(samplingGraph,
                                ResamplingEdgeEnsemble.Preserved));
            } else {
                graphWorkbench.setSamplingGraph(null);
            }
        });
        thresholdEnsemble.addActionListener(action -> {
            Graph workbenchGraph = graphWorkbench.getGraph();
            if (isSamplingGraph(workbenchGraph)) {
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

                Graph samplingGraph = graphWorkbench.getSamplingGraph();

                // replace the original sampling graph if it's a different sampling graph
                if (!isSameGraph(samplingGraph, workbenchGraph)) {
                    samplingGraph = workbenchGraph;
                    graphWorkbench.setSamplingGraph(samplingGraph);
                }

                graphWorkbench.setGraph(
                        GraphSampling.createDisplayGraph(samplingGraph,
                                ResamplingEdgeEnsemble.Threshold));
            } else {
                graphWorkbench.setSamplingGraph(null);
            }
        });

        add(highestEnsemble);
        add(majorityEnsemble);
        add(preservedEnsemble);
        add(thresholdEnsemble);
    }

    private boolean isSamplingGraph(Graph graph) {
        if (graph == null) {
            return false;
        } else if (graph.getEdges().isEmpty()) {
            return false;
        } else {
            Edge edge = graph.getEdges().iterator().next();

            return (edge.getEdgeTypeProbabilities() == null)
                    ? false
                    : !edge.getEdgeTypeProbabilities().isEmpty();
        }
    }

    private boolean isSameGraph(Graph graph1, Graph graph2) {
        if (graph1 == null || graph2 == null) {
            return false;
        }

        List<Node> graph1Nodes = graph1.getNodes();
        List<Node> graph2Nodes = graph2.getNodes();
        if (graph1Nodes.isEmpty() || graph2Nodes.isEmpty()) {
            return false;
        }

        return graph1Nodes.get(0) == graph2Nodes.get(0);
    }

}
