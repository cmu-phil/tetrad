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
import edu.cmu.tetradapp.model.EditorUtils;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.TetradMetadata;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;

/**
 * Saves a session from a file.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class SaveSessionAsAction extends AbstractAction {
    private boolean saved = false;

    public SaveSessionAsAction() {
        super("Save Session As...");
    }

    /**
     * Performs the action of saving a session to a file.
     */
    public void actionPerformed(ActionEvent e) {

        // Get the frontmost SessionWrapper.
        SessionEditorIndirectRef sessionEditorRef =
                DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench workbench = sessionEditor.getSessionWorkbench();
        SessionWrapper sessionWrapper = workbench.getSessionWrapper();
        TetradMetadata metadata = new TetradMetadata();

        // Select the file to save this to.
        File file = EditorUtils.getSaveFile(sessionEditor.getName(), "tet",
                JOptionUtils.centeringComp(), true, "Save Session As...");

        if (file == null) {
            this.saved = false;
            return;
        }

        if ((DesktopController.getInstance().existsSessionByName(
                file.getName()) &&
                !(sessionWrapper.getName().equals(file.getName())))) {
            this.saved = false;
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Another session by that name is currently open. Please " +
                            "\nclose that session first.");
            return;
        }

        sessionWrapper.setName(file.getName());
        sessionEditor.setName(file.getName());

        // Save it.
        try {
            FileOutputStream out = new FileOutputStream(file);
            ObjectOutputStream objOut = new ObjectOutputStream(out);
            objOut.writeObject(metadata);
            objOut.writeObject(sessionWrapper);
            out.close();

            FileInputStream in = new FileInputStream(file);
            ObjectInputStream objIn = new ObjectInputStream(in);
            objIn.readObject();

            sessionWrapper.setSessionChanged(false);
            sessionWrapper.setNewSession(false);
            this.saved = true;
        }
        catch (Exception e2) {
            this.saved = false;
            e2.printStackTrace();
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "An error occurred while attempting to save the session.");
        }

        DesktopController.getInstance().putMetadata(sessionWrapper, metadata);
        sessionEditor.firePropertyChange("name", null, file.getName());
    }

    public boolean isSaved() {
        return saved;
    }
}





