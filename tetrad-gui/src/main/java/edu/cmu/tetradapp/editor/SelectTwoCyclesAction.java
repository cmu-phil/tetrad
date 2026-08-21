///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * Highlights all 2-cycles in the given display graph--that is, all pairs of nodes X, Y such that both of the directed
 * edges X -&gt; Y and Y -&gt; X are in the graph. Both directed edges of each 2-cycle are selected.
 *
 * <p>Note that 2-cycles are represented this way (as a pair of directed edges) only in directed cyclic graphs--e.g.,
 * true graphs from cyclic simulations or the outputs of algorithms like FASK that orient 2-cycles explicitly. In the
 * cyclic PAG output of CCD or BOSS-CCD, cycles are indicated by dotted underlinings of triples rather than by pairs of
 * directed edges, so for those graphs this action will select nothing.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SelectTwoCyclesAction extends AbstractAction implements ClipboardOwner {

    /**
     * The workbench containing the graph to be searched for 2-cycles.
     */
    private final GraphWorkbench workbench;

    /**
     * Creates a new action to highlight 2-cycles in the given workbench.
     *
     * @param workbench the given workbench.
     */
    public SelectTwoCyclesAction(GraphWorkbench workbench) {
        super("Highlight 2-Cycles");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Selects both directed edges of every 2-cycle in the given display graph.
     */
    public void actionPerformed(ActionEvent e) {
        this.workbench.deselectAll();
        Graph graph = this.workbench.getGraph();

        if (graph == null) {
            JOptionPane.showMessageDialog(this.workbench, "No graph to check for 2-cycles.");
            return;
        }

        for (Component comp : this.workbench.getComponents()) {
            if (comp instanceof DisplayEdge) {
                Edge edge = ((DisplayEdge) comp).getModelEdge();

                if (Edges.isDirectedEdge(edge)) {
                    Node x = Edges.getDirectedEdgeTail(edge);
                    Node y = Edges.getDirectedEdgeHead(edge);

                    if (graph.containsEdge(Edges.directedEdge(y, x))) {
                        this.workbench.selectEdge(edge);
                    }
                }
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
