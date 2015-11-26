///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class CloseSessionAction extends AbstractAction {

    private boolean saved;

    /**
     * Creates a new close session action for the given desktop.
     */
    public CloseSessionAction() {
        super("Close Session");
    }

    /**
     * Closes the frontmost session of this action's desktop.
     */
    public void actionPerformed(ActionEvent e) {

        // Get the frontmost SessionWrapper.
        SessionEditorIndirectRef sessionEditorRef =
                DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench graph = sessionEditor.getSessionWorkbench();
        SessionWrapper sessionWrapper = graph.getSessionWrapper();

        if (sessionWrapper.isSessionChanged()) {
            String name = sessionWrapper.getName();

            // check to make sure user wants to evaporate this window...
            String msg =
                    "Do you want to save the changes you made to " + name + "?";
            int response = JOptionPane.showConfirmDialog(
                    JOptionUtils.centeringComp(), msg, "Fair Warning",
                    JOptionPane.YES_NO_CANCEL_OPTION);

            if (response == JOptionPane.YES_OPTION) {
                SaveSessionAction saveSessionAction = new SaveSessionAction();
                saveSessionAction.actionPerformed(e);
                this.saved = saveSessionAction.isSaved();
            }
            else if (response == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }

        DesktopController.getInstance().closeFrontmostSession();
    }

    /**
     * Prints out a string representation of this object.
     */
    public String toString() {
        return "Close session action.";
    }

    public boolean isSaved() {
        return saved;
    }
}





