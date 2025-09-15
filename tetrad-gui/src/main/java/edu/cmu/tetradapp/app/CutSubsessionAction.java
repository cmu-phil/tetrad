///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

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






