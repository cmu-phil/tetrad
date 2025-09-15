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
 * Copies a selection of session nodes in the frontmost session editor to the clipboard and deletes them from the
 * session editor.
 *
 * @author josephramsey
 */
final class CutSubsessionAction extends AbstractAction
        implements ClipboardOwner {

    /**
     * Constucts an action for loading the session in the given '.tet' file into the desktop.
     */
    public CutSubsessionAction() {
        super("Cut");
    }

    /**
     * This method handles the action performed when an event is triggered.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        SessionEditorIndirectRef sessionEditorRef =
                DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        List modelElements = sessionEditor.getSelectedModelComponents();
        SubsessionSelection selection = new SubsessionSelection(modelElements);
        InternalClipboard.getInstance().setContents(selection, this);
        SessionEditorWorkbench graph = sessionEditor.getSessionWorkbench();
        graph.deleteSelectedObjects();
    }

    /**
     * Notifies that ownership of the clipboard contents has been lost.
     *
     * @param clipboard the clipboard that is no longer owned
     * @param contents  the contents which this owner had placed on the clipboard
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}





