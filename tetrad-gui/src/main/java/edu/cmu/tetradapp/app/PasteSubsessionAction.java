///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

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





