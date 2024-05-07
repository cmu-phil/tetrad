/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

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
