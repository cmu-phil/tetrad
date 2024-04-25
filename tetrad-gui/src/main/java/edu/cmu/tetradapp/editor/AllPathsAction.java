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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Puts up a panel showing some graph properties, e.g., number of nodes and edges in the graph, etc.
 *
 * @author josephramsey
 */
class AllPathsAction extends AbstractAction implements ClipboardOwner {
    private final GraphWorkbench workbench;

    /**
     * Creates a new copy subsession action for the given LayoutEditable and clipboard.
     *
     * @param workbench a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public AllPathsAction(GraphWorkbench workbench) {
        super("All Paths");
        this.workbench = workbench;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Copies a parentally closed selection of session nodes in the frontmost session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        Box b = Box.createVerticalBox();
        Graph graph = this.workbench.getGraph();

        JTextArea textArea = new JTextArea();
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(600, 600));

        textArea.append("All Paths:");

        Box b2 = Box.createHorizontalBox();
        b2.add(scroll);
        textArea.setCaretPosition(0);
        b.add(b2);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b);

        EditorWindow window = new EditorWindow(panel,
                "All Paths", "Close", false, this.workbench);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);

        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                for (int i = 0; i < graph.getNodes().size(); i++) {
                    for (int j = 0; j < graph.getNodes().size(); j++) {

                        Node node1 = graph.getNodes().get(i);
                        Node node2 = graph.getNodes().get(j);

                        addTreks(node1, node2, graph, textArea);
                    }
                }
            }
        }

        new MyWatchedProcess();
    }

    private void addTreks(Node node1, Node node2, Graph graph, JTextArea textArea) {
        List<List<Node>> treks = graph.paths().allPaths(node1, node2, 8);

        if (treks.isEmpty()) {
            return;
        }

        textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

        for (int k = 0; k < treks.size(); k++) {
            textArea.append("\nPath " + k + ": ");
            List<Node> trek = treks.get(k);

            textArea.append(trek.get(0).toString());

            for (int m = 1; m < trek.size(); m++) {
                Node n0 = trek.get(m - 1);
                Node n1 = trek.get(m);

                Edge edge = graph.getEdge(n0, n1);

                Endpoint endpoint0 = edge.getProximalEndpoint(n0);
                Endpoint endpoint1 = edge.getProximalEndpoint(n1);

                textArea.append(endpoint0 == Endpoint.ARROW ? "<" : "-");
                textArea.append("-");
                textArea.append(endpoint1 == Endpoint.ARROW ? ">" : "-");

                textArea.append(n1.toString());
            }
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



