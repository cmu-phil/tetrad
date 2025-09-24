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
import edu.cmu.tetrad.graph.NodeType;
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
 * The SelectMeasuredNodesAction class highlights all measured nodes and edges in a GraphWorkbench instance.
 */
public class SelectMeasuredNodesAction extends AbstractAction implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * Highlights all measured nodes and edges in the workbench.
     *
     * @param workbench the GraphWorkbench containing the target session editor (must not be null)
     */
    public SelectMeasuredNodesAction(GraphWorkbench workbench) {
        super("Highlight Measured Nodes");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * Selects all measured nodes and edges in the workbench. This method is called when an action occurs.
     *
     * @param e the action event
     */
    public void actionPerformed(ActionEvent e) {
        this.workbench.deselectAll();

        for (Component comp : this.workbench.getComponents()) {
            if (comp instanceof DisplayNode) {
                Node node = ((DisplayNode) comp).getModelNode();
                if (node.getNodeType() == NodeType.MEASURED) {
                    this.workbench.selectNode(node);
                }
            }
        }

        for (Component comp : this.workbench.getComponents()) {
            if (comp instanceof DisplayEdge) {
                Edge edge = ((DisplayEdge) comp).getModelEdge();

                if (edge.getNode1().getNodeType() == NodeType.MEASURED
                    && edge.getNode2().getNodeType() == NodeType.MEASURED) {
                    this.workbench.selectEdge(edge);
                }
            }
        }
    }

    /**
     * This method is called when ownership of the clipboard contents is lost.
     *
     * @param clipboard the clipboard that lost ownership (not null)
     * @param contents  the contents that were lost (not null)
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}




