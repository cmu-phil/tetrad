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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.Set;

/**
 * Jul 23, 2018 4:05:07 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @version $Id: $Id
 */
public class HideShowNoConnectionNodesAction extends AbstractAction implements ClipboardOwner {

    private static final long serialVersionUID = 1843073951524699538L;

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * <p>Constructor for HideShowNoConnectionNodesAction.</p>
     *
     * @param workbench a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public HideShowNoConnectionNodesAction(GraphWorkbench workbench) {
        super("Hide/Show nodes with no connections");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Graph graph = this.workbench.getGraph();
        for (Component comp : this.workbench.getComponents()) {
            if (comp instanceof DisplayNode) {
                Node node = ((DisplayNode) comp).getModelNode();
                Set<Edge> edges = graph.getEdges(node);
                if (edges == null || edges.isEmpty()) {
                    comp.setVisible(!comp.isVisible());
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {

    }

}

