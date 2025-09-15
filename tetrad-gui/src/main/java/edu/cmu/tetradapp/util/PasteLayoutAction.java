package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.Graph;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * Pastes a layout into a LayoutEditable gadget, which lays out the graph in that gadget according to the stored graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PasteLayoutAction extends AbstractAction
        implements ClipboardOwner {
    /**
     * The LayoutEditable containing the target session editor.
     */
    private final LayoutEditable layoutEditable;

    /**
     * Constucts an action for loading the session in the given '.tet' file into the layoutEditable.
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public PasteLayoutAction(LayoutEditable layoutEditable) {
        super("Paste Layout");

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
        Transferable transferable = InternalClipboard.getLayoutInstance()
                .getContents(null);

        if (!(transferable instanceof LayoutSelection selection)) {
            return;
        }

        DataFlavor flavor = new DataFlavor(LayoutSelection.class, "Layout");

        try {
            Graph layoutGraph = (Graph) selection.getTransferData(flavor);
            this.layoutEditable.layoutByGraph(layoutGraph);
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Notifies this object that it is no longer the owner of the contents of the clipboard.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}





