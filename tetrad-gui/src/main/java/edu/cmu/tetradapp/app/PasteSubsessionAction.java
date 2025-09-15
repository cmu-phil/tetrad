package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.model.EditorUtils;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.InternalClipboard;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Copies a parentally closed selection of session nodes in the frontmost session editor to the clipboard.
 *
 * @author josephramsey
 */
final class PasteSubsessionAction extends AbstractAction
        implements ClipboardOwner {

    /**
     * Constucts an action for loading the session in the given '.tet' file into the desktop.
     */
    public PasteSubsessionAction() {
        super("Paste");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Copies a parentally closed selection of session nodes in the frontmost session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        Transferable transferable = InternalClipboard.getInstance()
                .getContents(null);

        if (!(transferable instanceof SubsessionSelection selection)) {
            return;
        }

        DataFlavor flavor = new DataFlavor(SubsessionSelection.class,
                "Subsession Selection");

        try {
            List modelList = (List) selection.getTransferData(flavor);

            if (modelList != null) {
                SessionEditorIndirectRef sessionEditorRef =
                        DesktopController.getInstance().getFrontmostSessionEditor();
                SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
                Point point = EditorUtils.getTopLeftPoint(modelList);
                int numPastes = selection.getNumPastes();
                point.translate(50 * numPastes, 50 * numPastes);

//                Point point = sessionEditor.getSessionWorkbench().getCurrentMouseLocation();
//
                sessionEditor.pasteSubsession(modelList, point);
            }
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





