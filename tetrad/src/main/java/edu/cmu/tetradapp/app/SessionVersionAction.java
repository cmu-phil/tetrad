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
import edu.cmu.tetradapp.model.TetradMetadata;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;
import edu.cmu.tetradapp.util.TetradMetadataIndirectRef;
import edu.cmu.tetrad.util.Version;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;

/**
 * Saves a session from a file.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class SessionVersionAction extends AbstractAction {

    public SessionVersionAction() {
        super("Session Version");
    }

    /**
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {

        // Get the frontmost SessionWrapper.
        SessionEditorIndirectRef sessionEditorRef =
                DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench workbench = sessionEditor.getSessionWorkbench();
        SessionWrapper sessionWrapper = workbench.getSessionWrapper();
        TetradMetadataIndirectRef metadataRef =
                DesktopController.getInstance().getTetradMetadata(
                        sessionWrapper);
        TetradMetadata metadata = (TetradMetadata) metadataRef;

        StringBuilder buf = new StringBuilder();

        if (metadata == null) {
            buf.append(
                    "This session has not yet been saved or loaded. The model\n");
            buf.append("version you are working in is ");
            buf.append(Version.currentViewableVersion());
            buf.append(".");
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    buf.toString());
            return;
        }

        SimpleDateFormat df = new SimpleDateFormat("MMM dd, yyyy");

        buf.append("Version information for \"");
        buf.append(sessionWrapper.getName());
        buf.append("\":\n\n");
        buf.append("Last saved using Tetrad ");
        buf.append(metadata.getVersion());
        buf.append(" (");
        buf.append(df.format(metadata.getDate()));
        buf.append(").\n");
        buf.append("You are running Tetrad ");
        buf.append(Version.currentViewableVersion());
        buf.append(".");
        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                buf.toString());
    }
}





