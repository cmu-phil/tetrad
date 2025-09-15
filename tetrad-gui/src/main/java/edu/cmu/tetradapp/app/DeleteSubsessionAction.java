package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * Deletes a selection of session nodes from the frontmost session editor.
 *
 * @author josephramsey
 */
final class DeleteSubsessionAction extends AbstractAction
        implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private TetradDesktop desktop;

    /**
     * Creates a new delete subsession action for the given desktop and clipboard.
     */
    public DeleteSubsessionAction() {
        super("Delete");
    }

    /**
     * Invoked when an action occurs. Displays a confirmation dialog and deletes selected nodes in the session editor if
     * the user chooses to delete.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        SessionEditor sessionEditor = this.desktop.getFrontmostSessionEditor();
        SessionEditorWorkbench graph = sessionEditor.getSessionWorkbench();

        int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                "Delete nodes?", "Confirm", JOptionPane.OK_CANCEL_OPTION);

        if (ret == JOptionPane.OK_OPTION) {
            graph.deleteSelectedObjects();
        }
    }

    /**
     * Notifies that the specified clipboard is no longer owned by the owner.
     *
     * @param clipboard the clipboard that is no longer owned
     * @param contents  the contents which this owner had placed on the clipboard
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}





