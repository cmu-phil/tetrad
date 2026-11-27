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
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Mar 19, 2023 1:45:50 AM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 * @version $Id: $Id
 */
public class EnsembleMenu extends JMenu {

    /**
     * The ensemble currently shown, shared across workbenches so a freshly built menu (one is constructed per
     * right-click popup) can reflect the last selection. The median member graph is the default initial display
     * as of 2026-8-13, so this starts at Median.
     */
    public static ResamplingEdgeEnsemble resamplingEdgeEnsemble = ResamplingEdgeEnsemble.Median;

    /**
     * The workbench graph.
     */
    private final GraphWorkbench graphWorkbench;

    /**
     * The checkable menu items, one per ensemble, kept so the checked state can be re-synced when a selection is
     * cancelled (e.g., the Threshold dialog is dismissed) or unavailable (no stored median graph).
     */
    private final Map<ResamplingEdgeEnsemble, JRadioButtonMenuItem> items = new EnumMap<>(ResamplingEdgeEnsemble.class);

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

    /**
     * Returns whether the graph is the result of resampling, judged by whether its first edge carries edge type
     * probabilities.
     *
     * @param graph the graph to check; may be null.
     * @return true if the graph is non-null, has edges, and its first edge has edge type probabilities.
     */
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

    /**
     * Returns whether the two graphs share nodes, judged by whether their first nodes are the same object.
     *
     * @param graph1 the first graph; may be null.
     * @param graph2 the second graph; may be null.
     * @return true if both graphs are non-null, have nodes, and have the identical first node.
     */
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
        // Checkable items in a button group so the current selection is visible in the menu; Median first,
        // since the median member graph is the default initial display. Added 2026-8-13.
        ButtonGroup group = new ButtonGroup();

        for (ResamplingEdgeEnsemble ensemble : new ResamplingEdgeEnsemble[]{
                ResamplingEdgeEnsemble.Median,
                ResamplingEdgeEnsemble.Highest,
                ResamplingEdgeEnsemble.Majority,
                ResamplingEdgeEnsemble.Preserved,
                ResamplingEdgeEnsemble.Threshold,
                ResamplingEdgeEnsemble.Endpointwise}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(ensemble.name());
            items.put(ensemble, item);
            group.add(item);
            add(item);
        }

        syncSelection();

        // Only the result of a search run with resampling carries a sampling graph to choose a display from. For
        // any other graph every choice used to fail with an exception that reached only the console.
        if (!(graphWorkbench.getGraph() instanceof EdgeListGraph graph)
            || graph.getAncillaryGraph("samplingGraph") == null) {
            setEnabled(false);
            setToolTipText("Available for the result of a search run with resampling.");
        }

        items.get(ResamplingEdgeEnsemble.Median).addActionListener(action -> showMedian());
        items.get(ResamplingEdgeEnsemble.Highest).addActionListener(action -> showComposite(ResamplingEdgeEnsemble.Highest));
        items.get(ResamplingEdgeEnsemble.Majority).addActionListener(action -> showComposite(ResamplingEdgeEnsemble.Majority));
        items.get(ResamplingEdgeEnsemble.Preserved).addActionListener(action -> showComposite(ResamplingEdgeEnsemble.Preserved));
        items.get(ResamplingEdgeEnsemble.Threshold).addActionListener(action -> showThreshold());
        items.get(ResamplingEdgeEnsemble.Endpointwise).addActionListener(action -> showEndpointwise());
    }

    /**
     * Checks the item for the ensemble currently shown; used at construction and to revert the checked state when a
     * selection is cancelled or unavailable.
     */
    private void syncSelection() {
        JRadioButtonMenuItem item = items.get(resamplingEdgeEnsemble);
        if (item != null) {
            item.setSelected(true);
        }
    }

    private Graph getSamplingGraph() {
        Graph workbenchGraph = graphWorkbench.getGraph();
        Graph samplingGraph = ((EdgeListGraph) workbenchGraph).getAncillaryGraph("samplingGraph");

        if (samplingGraph == null) {
            throw new IllegalStateException("Cannot find sampling graph");
        }

        return samplingGraph;
    }

    private void showMedian() {
        Graph samplingGraph = getSamplingGraph();

        // The median member graph cannot be recomputed from the composite sampling graph alone (the member
        // graphs are needed), so it is computed at search time and stored as an ancillary graph on the
        // sampling graph; see AbstractBootstrapAlgorithm.search. Added 2026-8-13.
        Graph medianGraph = ((EdgeListGraph) samplingGraph).getAncillaryGraph("medianGraph");

        if (medianGraph == null) {
            JOptionPane.showMessageDialog(graphWorkbench,
                    "No median member graph is stored with this search result. The Median display is\n"
                    + "available for bootstrap searches run after this option was introduced; please\n"
                    + "re-run the search.",
                    "Median", JOptionPane.INFORMATION_MESSAGE);
            syncSelection();
            return;
        }

        ((EdgeListGraph) medianGraph).setAncillaryGraph("samplingGraph", samplingGraph);

        resamplingEdgeEnsemble = ResamplingEdgeEnsemble.Median;
        syncSelection();

        graphWorkbench.setGraph(GraphUtils.fixDirections(medianGraph));
    }

    private void showComposite(ResamplingEdgeEnsemble ensemble) {
        Graph samplingGraph = getSamplingGraph();

        Graph displayGraph = GraphSampling.createDisplayGraph(samplingGraph, ensemble);
        ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", samplingGraph);

        resamplingEdgeEnsemble = ensemble;
        syncSelection();

        graphWorkbench.setGraph(GraphUtils.fixDirections(displayGraph));
    }

    private void showThreshold() {
        Graph samplingGraph = getSamplingGraph();

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
                // Cancelled: keep the previously shown ensemble checked.
                syncSelection();
                return;
            }
        }

        Graph displayGraph = GraphSampling.createDisplayGraph(samplingGraph,
                ResamplingEdgeEnsemble.Threshold);
        ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", samplingGraph);

        resamplingEdgeEnsemble = ResamplingEdgeEnsemble.Threshold;
        syncSelection();

        graphWorkbench.setGraph(GraphUtils.fixDirections(displayGraph));
    }

    /**
     * Shows the Endpointwise composite: the adjacency and each endpoint mark are decided separately, with a
     * threshold for each that the user sets here. The thresholds are remembered in the preferences. Added
     * 2026-11-27.
     */
    private void showEndpointwise() {
        Graph samplingGraph = getSamplingGraph();
        Preferences prefs = Preferences.userRoot();

        JTextField adjacencyField = new JTextField(String.valueOf(prefs.getDouble(
                GraphSampling.ENDPOINTWISE_ADJACENCY_THRESHOLD_PREF,
                GraphSampling.ENDPOINTWISE_ADJACENCY_THRESHOLD_DEFAULT)), 6);
        JTextField markField = new JTextField(String.valueOf(prefs.getDouble(
                GraphSampling.ENDPOINTWISE_MARK_THRESHOLD_PREF,
                GraphSampling.ENDPOINTWISE_MARK_THRESHOLD_DEFAULT)), 6);

        JPanel fields = new JPanel(new GridLayout(2, 2, 8, 6));
        fields.add(new JLabel("Edge threshold (0 to 1):"));
        fields.add(adjacencyField);
        fields.add(new JLabel("Mark threshold (above 0.5, up to 1):"));
        fields.add(markField);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html>An edge is kept if it is present in at least the edge threshold's fraction"
                             + " of the resamples.<br>An arrowhead or tail is kept if it sits at that endpoint in"
                             + " at least the mark threshold's<br>fraction of all the resamples. Any other endpoint"
                             + " of a kept edge is shown as a circle,<br>meaning not settled by the resamples."
                             + "<br><br>The result is a summary, mark by mark. It is generally not a legal graph of"
                             + " the<br>search's type.</html>"), BorderLayout.NORTH);
        panel.add(fields, BorderLayout.CENTER);

        Graph displayGraph;

        while (true) {
            int response = JOptionPane.showConfirmDialog(graphWorkbench, panel, "Endpointwise",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (response != JOptionPane.OK_OPTION) {
                // Cancelled: keep the previously shown ensemble checked.
                syncSelection();
                return;
            }

            try {
                double adjacencyThreshold = Double.parseDouble(adjacencyField.getText().trim());
                double markThreshold = Double.parseDouble(markField.getText().trim());

                // Range checks are the library's; an out-of-range value comes back as IllegalArgumentException.
                displayGraph = GraphSampling.createEndpointwiseDisplayGraph(samplingGraph, adjacencyThreshold,
                        markThreshold);

                prefs.putDouble(GraphSampling.ENDPOINTWISE_ADJACENCY_THRESHOLD_PREF, adjacencyThreshold);
                prefs.putDouble(GraphSampling.ENDPOINTWISE_MARK_THRESHOLD_PREF, markThreshold);
                break;
            } catch (IllegalArgumentException e) {
                // NumberFormatException is an IllegalArgumentException, so this covers unparseable text too.
                JOptionPane.showMessageDialog(graphWorkbench,
                        "The edge threshold must be a number from 0 to 1, and the mark threshold a number\n"
                        + "above 0.5 and at most 1.",
                        "Endpointwise", JOptionPane.WARNING_MESSAGE);
            }
        }

        ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", samplingGraph);

        resamplingEdgeEnsemble = ResamplingEdgeEnsemble.Endpointwise;
        syncSelection();

        graphWorkbench.setGraph(GraphUtils.fixDirections(displayGraph));
    }

}
