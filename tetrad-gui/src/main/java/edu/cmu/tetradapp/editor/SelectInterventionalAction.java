/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
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

    private static final long serialVersionUID = -1981559602783726423L;

    /**
     * The desktop containing the target session editor.
     */
    private GraphWorkbench workbench;

    /**
     * Creates a new copy subsession action for the given desktop and
     * clipboard.
     * @param workbench
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
     * @param e
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        workbench.deselectAll();

        for (Component comp : workbench.getComponents()) {
            if (comp instanceof DisplayNode) {
                Node node = ((DisplayNode) comp).getModelNode();
                // Only interventional nodes has the `interventioanl` flag as true
                if (node.getNodeType() == NodeType.MEASURED && node.isInterventional()) {
                    workbench.selectNode(node);
                }
            }
        }
    }

    /**
     * Required by the AbstractAction interface; does nothing.
     */
    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
    
}
