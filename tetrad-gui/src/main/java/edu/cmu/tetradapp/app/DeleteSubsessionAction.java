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






