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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * Jan 8, 2019 11:26:23 AM
 *
 * @author Zhou Yuan zhy19@pitt.edu
 * @version $Id: $Id
 */
public class SelectInterventionalAction extends AbstractAction implements ClipboardOwner {

    private static final long serialVersionUID = -1981559602783726423L;

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * Creates a new copy subsession action for the given desktop and clipboard.
     *
     * @param workbench a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public SelectInterventionalAction(GraphWorkbench workbench) {
        super("Highlight Interventional Nodes");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Copies a parentally closed selection of session nodes in the frontmost session editor to the clipboard.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        this.workbench.deselectAll();

        for (Component comp : this.workbench.getComponents()) {
            if (comp instanceof DisplayNode) {
                Node node = ((DisplayNode) comp).getModelNode();
                if (node.getNodeVariableType() == NodeVariableType.INTERVENTION_STATUS || node.getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE) {
                    this.workbench.selectNode(node);
                }
            }

            if (comp instanceof DisplayEdge) {
                Edge edge = ((DisplayEdge) comp).getModelEdge();
                if ((edge.getNode1().getNodeVariableType() == NodeVariableType.INTERVENTION_STATUS || edge.getNode1().getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE)
                    || (edge.getNode2().getNodeVariableType() == NodeVariableType.INTERVENTION_STATUS || edge.getNode2().getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE)) {
                    this.workbench.selectEdge(edge);
                }
            }
        }

    }

    /**
     * {@inheritDoc}
     * <p>
     * Required by the AbstractAction interface; does nothing.
     */
    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }

}

