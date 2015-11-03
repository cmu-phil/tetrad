///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import java.util.ArrayList;
import java.util.List;

/**
 * Puts up a panel showing some graph properties, e.g., number of nodes and
 * edges in the graph, etc.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class GraphPropertiesAction extends AbstractAction implements ClipboardOwner {
    private GraphWorkbench workbench;

    /**
     * Creates a new copy subsession action for the given LayoutEditable and
     * clipboard.
     */
    public GraphPropertiesAction(GraphWorkbench workbench) {
        super("Graph Properties");
        this.workbench = workbench;
    }

    /**
     * Copies a parentally closed selection of session nodes in the frontmost
     * session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        Box b = Box.createVerticalBox();
        Graph graph = workbench.getGraph();

        int numLatents = 0;
        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                numLatents++;
            }
        }

        int maxIndegree = 0;
        for (Node node : graph.getNodes()) {
            int indegree = graph.getNodesInTo(node, Endpoint.ARROW).size();

            if (indegree > maxIndegree) {
                maxIndegree = indegree;
            }
        }

        int maxOutdegree = 0;
        for (Node node : graph.getNodes()) {
            int outdegree = graph.getNodesOutTo(node, Endpoint.ARROW).size();

            if (outdegree > maxOutdegree) {
                maxOutdegree = outdegree;
            }
        }

        int numDirectedEdges = 0;
        int numBidirectedEdges = 0;
        int numUndirectedEdges = 0;

        for (Edge edge : graph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) numDirectedEdges++;
            else if (Edges.isBidirectedEdge(edge)) numBidirectedEdges++;
            else if (Edges.isUndirectedEdge(edge)) numUndirectedEdges++;
        }

        boolean cyclic = graph.existsDirectedCycle();
//        List<Node> cycle = GraphUtils.directedCycle(graph);

        JTextArea textArea = new JTextArea();
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(300, 300));

//        textArea.append("Graph Properties for " + workbench.getName());
        textArea.append("\nNumber of nodes: " + String.valueOf(graph.getNumNodes()));
        textArea.append("\nNumber of latents: " + String.valueOf(numLatents));
        textArea.append("\nNumber of edges: " + String.valueOf(graph.getNumEdges()));
        textArea.append("\nNumber of directed edges: " + String.valueOf(numDirectedEdges));
        textArea.append("\nNumber of bidirected edges: " + String.valueOf(numBidirectedEdges));
        textArea.append("\nNumber of undirected edges: " + String.valueOf(numUndirectedEdges));
        textArea.append("\nMax degree: " + String.valueOf(graph.getConnectivity()));
        textArea.append("\nMax indegree: " + String.valueOf(maxIndegree));
        textArea.append("\nMax outdegree: " + String.valueOf(maxOutdegree));
        textArea.append("\nNumber of latents: " + String.valueOf(numLatents));
        textArea.append("\n" + (cyclic ? "Cyclic": "Acyclic"));

//        if (cyclic) {
//            textArea.append("\nExample cycle: " + cycle.toString());
//        }


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

//        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), b,
//                "Graph Properties", JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }


}


