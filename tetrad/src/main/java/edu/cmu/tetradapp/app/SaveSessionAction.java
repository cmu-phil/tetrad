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

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.prefs.Preferences;

/**
 * Saves a session from a file.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class  SaveSessionAction extends AbstractAction {
    private boolean saved = false;

    public SaveSessionAction() {
        super("Save Session");
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
        TetradMetadata metadata = new TetradMetadata();

        File file = new File(Preferences.userRoot().get("fileSaveLocation",
                Preferences.userRoot().absolutePath()),
                sessionWrapper.getName());

        if (!file.exists() || sessionWrapper.isNewSession()) {
            SaveSessionAsAction saveSessionAsAction = new SaveSessionAsAction();
            saveSessionAsAction.actionPerformed(e);
            this.saved = saveSessionAsAction.isSaved();
            return;
        }

        if (file.exists()) {
            int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                    "File already exists. Overwrite?", "Save", JOptionPane.YES_NO_OPTION);

            if (ret == JOptionPane.NO_OPTION) {
                SaveSessionAsAction saveSessionAsAction = new SaveSessionAsAction();
                saveSessionAsAction.actionPerformed(e);
                this.saved = saveSessionAsAction.isSaved();
                return;
            }
        }

                                                
        // Save it.
        try {
            FileOutputStream out = new FileOutputStream(file);              
            ObjectOutputStream objOut = new ObjectOutputStream(out);

            sessionWrapper.setNewSession(false);
            objOut.writeObject(metadata);
            objOut.writeObject(sessionWrapper);
            out.close();
//            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                    "Session saved.");

            FileInputStream in = new FileInputStream(file);
            ObjectInputStream objIn = new ObjectInputStream(in);
            objIn.readObject();
        }
        catch (Exception e2) {
            e2.printStackTrace();
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "An error occurred while attempting to save the session as " +
                            file.getAbsolutePath() + ".");
        }

        sessionWrapper.setSessionChanged(false);
        DesktopController.getInstance().putMetadata(sessionWrapper, metadata);
    }

    public boolean isSaved() {
        return saved;
    }
}






