package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.InternalClipboard;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Copies a selection of session nodes in the frontmost session editor, to the clipboard.
 *
 * @author josephramsey
 */
final class CopySubsessionAction extends AbstractAction
        implements ClipboardOwner {

    /**
     * Creates a new copy subsession action for the given desktop and clipboard.
     */
    public CopySubsessionAction() {
        super("Copy");
    }

    /**
     * Processes the action event by copying a selection of session nodes in the frontmost session editor to the
     * clipboard.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        SessionEditorIndirectRef sessionEditorRef =
                DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        List modelNodes = sessionEditor.getSelectedModelComponents();
        SubsessionSelection selection = new SubsessionSelection(modelNodes);
        InternalClipboard.getInstance().setContents(selection, this);
    }

    /**
     * Notifies the owner that ownership of the clipboard contents has been lost.
     *
     * @param clipboard the clipboard that is no longer owned
     * @param contents  the contents which this owner had placed on the clipboard
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}





