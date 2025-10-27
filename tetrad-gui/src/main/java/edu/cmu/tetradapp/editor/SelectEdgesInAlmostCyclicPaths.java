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

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Selects all directed edges in the given display graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SelectEdgesInAlmostCyclicPaths extends AbstractAction implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * Creates a new copy subsession action for the given desktop and clipboard.
     *
     * @param workbench the given workbench.
     */
    public SelectEdgesInAlmostCyclicPaths(GraphWorkbench workbench) {
        super("Highlight Almost Cyclic Paths");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Selects all directed edges in the given display graph.
     */
    public void actionPerformed(ActionEvent e) {
        this.workbench.deselectAll();
        Graph graph = this.workbench.getGraph();

        if (graph == null) {
            JOptionPane.showMessageDialog(this.workbench, "No graph to check for almost cyclic paths.");
            return;
        }

        // Make a list of the bidirected edges in the graph.
        java.util.List<Edge> bidirectedEdges = new ArrayList<>();

        for (Edge edge : graph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                bidirectedEdges.add(edge);
            }
        }

        java.util.Set<Edge> almostCyclicEdges = new HashSet<>();

        for (Edge edge : bidirectedEdges) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            {
                java.util.Set<java.util.List<Node>> directedPaths = graph.paths().directedPaths(x, y, 1000);

                for (java.util.List<Node> path : directedPaths) {
                    for (int i = 0; i < path.size() - 1; i++) {
                        Node node1 = path.get(i);
                        Node node2 = path.get(i + 1);

                        Edge _edge = graph.getEdge(node1, node2);
                        almostCyclicEdges.add(_edge);
                        almostCyclicEdges.add(edge);
                    }
                }
            }

            {
                java.util.Set<java.util.List<Node>> directedPaths = graph.paths().directedPaths(y, x, 1000);

                for (java.util.List<Node> path : directedPaths) {
                    for (int i = 0; i < path.size() - 1; i++) {
                        Node node1 = path.get(i);
                        Node node2 = path.get(i + 1);

                        Edge _edge = graph.getEdge(node1, node2);
                        almostCyclicEdges.add(_edge);
                        almostCyclicEdges.add(edge);
                    }
                }
            }
        }

        for (Edge edge : almostCyclicEdges) {
            this.workbench.selectEdge(edge);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}




