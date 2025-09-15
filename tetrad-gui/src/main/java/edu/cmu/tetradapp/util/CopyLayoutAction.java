package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.Graph;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * Copies the getModel graph in a given container, storing it so that another container can be laid out the same way.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CopyLayoutAction extends AbstractAction implements ClipboardOwner {

    /**
     * The LayoutEditable containing the target session editor.
     */
    private final LayoutEditable layoutEditable;

    /**
     * Creates a new copy subsession action for the given LayoutEditable and clipboard.
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public CopyLayoutAction(LayoutEditable layoutEditable) {
        super("Copy Layout");

        if (layoutEditable == null) {
            throw new NullPointerException();
        }

        this.layoutEditable = layoutEditable;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Copies a parentally closed selection of session nodes in the frontmost session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        Graph layoutGraph = this.layoutEditable.getGraph();
        LayoutSelection selection = new LayoutSelection(layoutGraph);
        InternalClipboard.getLayoutInstance().setContents(selection, this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}





