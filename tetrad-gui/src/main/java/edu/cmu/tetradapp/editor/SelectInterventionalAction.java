/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import java.awt.Component;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class SelectInterventionalAction extends AbstractAction implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private GraphWorkbench workbench;

    /**
     * Creates a new copy subsession action for the given desktop and
     * clipboard.
     */
    public SelectInterventionalAction(GraphWorkbench workbench) {
        super("Highlight Interventional Nodes");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * Copies a parentally closed selection of session nodes in the frontmost
     * session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        workbench.deselectAll();

        for (Component comp : workbench.getComponents()) {
            if (comp instanceof DisplayNode) {
                Node node = ((DisplayNode) comp).getModelNode();
                if (node.getNodeType() == NodeType.INTERVENTIONAL) {
                    workbench.selectNode(node);
                }
            }
        }

        for (Component comp : workbench.getComponents()) {
            if (comp instanceof DisplayEdge) {
                Edge edge = ((DisplayEdge) comp).getModelEdge();

                if (edge.getNode1().getNodeType() == NodeType.INTERVENTIONAL
                        && edge.getNode2().getNodeType() == NodeType.INTERVENTIONAL) {
                    workbench.selectEdge(edge);
                }
            }
        }
    }

    /**
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
    
}
