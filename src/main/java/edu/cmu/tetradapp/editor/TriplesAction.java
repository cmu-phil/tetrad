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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.TripleClassifier;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Puts up a panel showing some graph properties, e.g., number of nodes and
 * edges in the graph, etc.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class TriplesAction extends AbstractAction implements ClipboardOwner {
    private GraphWorkbench workbench;
    private TripleClassifier classifier;

    /**
     * Creates a new copy subsession action for the given LayoutEditable and
     * clipboard.
     */
    public TriplesAction(GraphWorkbench workbench, TripleClassifier classifier) {
        super("Underlinings");
        this.workbench = workbench;
        this.classifier = classifier;
    }

    /**
     * Copies a parentally closed selection of session nodes in the frontmost
     * session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        Box b = Box.createVerticalBox();
        Graph graph = workbench.getGraph();

        JTextArea textArea = new JTextArea();
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(600, 600));

        textArea.append("Underlinings:");
        boolean allEmpty = true;
        List<Node> nodes = graph.getNodes();
        Collections.sort(nodes, new Comparator<Node>() {
            public int compare(Node node1, Node node2) {
                return node1.getName().compareTo(node2.getName());
            }
        });

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            List<String> types = classifier.getTriplesClassificationTypes();
            List<List<Triple>> triplesList = classifier.getTriplesLists(node);

            boolean existsClassification = false;

            for (List<Triple> list : triplesList) {
                if (!list.isEmpty()) {
                    existsClassification = true;
                }
            }

            if (!existsClassification) {
                continue;
            }

            textArea.append("\n\nNode " + node);

            for (int j = 0; j < types.size(); j++) {
                String type = types.get(j);
                List<Triple> triples = triplesList.get(j);

                if (!triples.isEmpty()) {
                    textArea.append("\n\n    ");
                    textArea.append(type + niceList(triples));
                }
            }

            allEmpty = false;
        }

        if (allEmpty) {
            textArea.append("\n\nAll triple classifications can be read from the graph.");
        }

        Box b2 = Box.createHorizontalBox();
        b2.add(scroll);
        textArea.setCaretPosition(0);
        b.add(b2);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b);

        EditorWindow window = new EditorWindow(panel,
                "Underlinings", "Close", false, workbench);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);

//        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), b,
//                "Graph Properties", JOptionPane.PLAIN_MESSAGE);
    }

    private String niceList(List<Triple> triples) {
        if (triples.isEmpty()) {
            return "--NONE--";
        }

        Graph graph = workbench.getGraph();
        StringBuilder buf = new StringBuilder();
        buf.append("\n\n        ");

        for (int i = 0; i < triples.size(); i++) {
            buf.append(pathFor(triples.get(i), graph));

            if (i < triples.size() - 1) {
                buf.append("\n        ");
            }
        }

        return buf.toString();
    }

    private String pathFor(Triple triple, Graph graph) {
        List<Node> path = asList(triple);
        return GraphUtils.pathString(path, graph);
    }


    private List<Node> asList(Triple triple) {
        List<Node> list = new LinkedList<Node>();
        list.add(triple.getX());
        list.add(triple.getY());
        list.add(triple.getZ());
        return list;
    }

    /**
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }


}



