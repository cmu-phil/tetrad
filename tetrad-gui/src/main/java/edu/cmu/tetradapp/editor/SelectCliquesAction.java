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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.Set;

/**
 * An action to highlight edges in node cliques in the GraphWorkbench of a certain minimum size (input by the user).
 */
public class SelectCliquesAction extends AbstractAction implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * Constructs a new SelectCliquesAction.
     *
     * @param workbench the GraphWorkbench to highlight the cliques in
     * @throws NullPointerException if the workbench is null
     */
    public SelectCliquesAction(GraphWorkbench workbench) {
        super("Highlight Maximal Cliques");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * Performs the action of highlighting all edges in cliques in the given display graph. Inputs the minimum size of the
     * cliques to highlight by popping up a dialog box.
     *
     * @param e the {@link ActionEvent} object
     */
    public void actionPerformed(ActionEvent e) {
        this.workbench.deselectAll();

        final Graph graph = this.workbench.getGraph();

        String s = JOptionPane.showInputDialog("Enter the minimum size of the (maximal) clique: ");

        int minSize;

        while (true) {
            if (s == null) {
                return;
            }

            try {
                minSize = Integer.parseInt(s);

                if (minSize < 2) {
                    JOptionPane.showMessageDialog(this.workbench, "Cliques must have at least 2 nodes");
                } else {
                    break;
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this.workbench, "Please enter a valid integer.");
                s = JOptionPane.showInputDialog("Enter the minimum size of the (maximal) clique: ");
            }
        }

        Set<Set<Node>> cliques = GraphUtils.maximalCliques(graph, graph.getNodes());

        for (Set<Node> clique : cliques) {
            if (clique.size() < minSize) {
                continue;
            }

            for (Node n1 : clique) {
                for (Node n2 : clique) {
                    if (n1 == n2) {
                        continue;
                    }

                    if (graph.isAdjacentTo(n1, n2)) {
                        this.workbench.selectEdge(graph.getEdge(n1, n2));
                    }
                }
            }
        }
    }

    /**
     * Invoked when ownership of the clipboard contents is lost.
     *
     * @param clipboard the clipboard that lost the ownership
     * @param contents  the transferred contents that were previously on the clipboard
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}




