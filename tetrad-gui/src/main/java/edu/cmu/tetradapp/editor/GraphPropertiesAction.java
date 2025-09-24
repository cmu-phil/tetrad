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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;

/**
 * Puts up a panel showing some graph properties, e.g., number of nodes and edges in the graph, etc.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphPropertiesAction extends AbstractAction implements ClipboardOwner {

    /**
     * The workbench for the graph.
     */
    private GraphWorkbench workbench;

    /**
     * Creates a new copy subsession action for the given LayoutEditable and clipboard.
     *
     * @param workbench a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public GraphPropertiesAction(GraphWorkbench workbench) {
        super("Graph Properties");
        this.workbench = workbench;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Copies a parentally closed selection of session nodes in the frontmost session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        Box b = Box.createVerticalBox();

        int numLatents = 0;
        for (Node node : getGraph().getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                numLatents++;
            }
        }

        int maxIndegree = 0;
        for (Node node : getGraph().getNodes()) {
            int indegree = getGraph().getNodesInTo(node, Endpoint.ARROW).size();

            if (indegree > maxIndegree) {
                maxIndegree = indegree;
            }
        }

        int maxOutdegree = 0;
        for (Node node : getGraph().getNodes()) {
            int outdegree = getGraph().getNodesOutTo(node, Endpoint.ARROW).size();

            if (outdegree > maxOutdegree) {
                maxOutdegree = outdegree;
            }
        }

        java.util.List<Node> nodes = getGraph().getNodes();

        int numTwoCycles = 0;
        int numDirectedEdges = 0;
        int numBidirectedEdges = 0;
        int numUndirectedEdges = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i; j < nodes.size(); j++) {
                Node n1 = nodes.get(i);
                Node n2 = nodes.get(j);

                if (getGraph().getDirectedEdge(n1, n2) != null && getGraph().getDirectedEdge(n2, n1) != null) {
                    numTwoCycles++;
                } else if (getGraph().getEdges(n1, n2).size() == 1) {
                    if (getGraph().getEdge(n1, n2).isDirected()) {
                        numDirectedEdges++;
                    } else if (Edges.isBidirectedEdge(getGraph().getEdge(n1, n2))) {
                        numBidirectedEdges++;
                    } else if (Edges.isUndirectedEdge(getGraph().getEdge(n1, n2))) {
                        numUndirectedEdges++;
                    }
                }
            }
        }

//        for (Edge edge : getGraph().getEdges()) {
//            if (Edges.isDirectedEdge(edge)) {
//                if (getGraph().containsEdge(edge) && getGraph().containsEdge(edge.reverse())) {
//                    numTwoCycles++;
//                } else {
//                    numDirectedEdges++;
//                }
//            }
//            else if (Edges.isBidirectedEdge(edge)) numBidirectedEdges++;
//            else if (Edges.isUndirectedEdge(edge)) numUndirectedEdges++;
//        }

        boolean cyclic = getGraph().paths().existsDirectedCycle();

        int numAdjacencies = 0;

        for (
                int i = 0; i < nodes.size(); i++) {
            for (int j = i; j < nodes.size(); j++) {
                if (getGraph().isAdjacentTo(nodes.get(i), nodes.get(j))) {
                    numAdjacencies++;
                }
            }
        }

        int numEdges = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i; j < nodes.size(); j++) {
                numEdges += getGraph().getEdges(nodes.get(i), nodes.get(j)).size();
            }
        }

        JTextArea textArea = new JTextArea();
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(400, 300));

        textArea.append("\nNumber of nodes: " + getGraph().getNumNodes());
        textArea.append("\nNumber of latents: " + numLatents);
        textArea.append("\nNumber of edges: " + numEdges);
        textArea.append("\nNumber of adjacencies: " + numAdjacencies);
        textArea.append("\nNumber of two-cycles: " + numTwoCycles);
        textArea.append("\nNumber of directed edges not in two cycles: " + numDirectedEdges);
        textArea.append("\nNumber of bidirected edges: " + numBidirectedEdges);
        textArea.append("\nNumber of undirected edges: " + numUndirectedEdges);
        textArea.append("\nMax degree: " +

                        getGraph().

                                getDegree());
        textArea.append("\nMax indegree: " + maxIndegree);
        textArea.append("\nMax outdegree: " + maxOutdegree);

        //        int numEdges = getGraph().getNumEdges();
        int numVars = getGraph().getNumNodes();
        double avgDegree = 2 * numAdjacencies / ((double) (numVars));
        double density = avgDegree / (numVars - 1);

        textArea.append("\nAverage degree: " + NumberFormat.getInstance().format(avgDegree));
        textArea.append("\nDensity: " + NumberFormat.getInstance().format(density));

        textArea.append("\nNumber of latents: " + numLatents);
        textArea.append("\n" + (cyclic ? "Cyclic" : "Acyclic"));

        Map<String, Object> values = getGraph().getAllAttributes();
        NumberFormat nf = new DecimalFormat("0.00");

        for (String key : values.keySet()) {
            Object value = values.get(key);

            String _value;
            if (value instanceof Double) _value = nf.format(value);
            else _value = value.toString();

            textArea.append("\n" + key + ": " + _value);
        }

        Box b2 = Box.createHorizontalBox();
        b2.add(scroll);
        textArea.setCaretPosition(0);
        b.add(b2);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b);

        EditorWindow window = new EditorWindow(panel,
                "Graph Properties", "Close", false, workbench);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }


    /**
     * <p>setGraph.</p>
     *
     * @param graph     a {@link edu.cmu.tetrad.graph.Graph} object
     * @param workbench a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public void setGraph(Graph graph, GraphWorkbench workbench) {
        workbench.setGraph(graph);
        this.workbench = workbench;
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.workbench.getGraph();
    }
}



