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

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.model.EditorUtils;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.TetradMetadata;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.nio.file.Files;
import java.util.prefs.Preferences;

/**
 * Saves a session from a file.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SaveSessionAsAction extends AbstractAction {

    @Serial
    private static final long serialVersionUID = 2798487128341621686L;

    /**
     * Constant <code>saved=false</code>
     */
    public static boolean saved = false;

    /**
     * <p>Constructor for SaveSessionAsAction.</p>
     */
    public SaveSessionAsAction() {
        super("Save Session As...");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs the action of saving a session to a file.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        // Get the frontmost SessionWrapper.
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench workbench = sessionEditor.getSessionWorkbench();
        SessionWrapper sessionWrapper = workbench.getSessionWrapper();
        TetradMetadata metadata = new TetradMetadata();

        // Select the file to save this to.
        String sessionSaveLocation
                = Preferences.userRoot().get("sessionSaveLocation", "");
        File file = EditorUtils.getSaveFileWithPath(sessionEditor.getName(), "tet",
                JOptionUtils.centeringComp(), true, "Save Session As...", sessionSaveLocation);

        if (file == null) {
            saved = false;
            return;
        }

        if ((DesktopController.getInstance().existsSessionByName(
                file.getName())
             && !(sessionWrapper.getName().equals(file.getName())))) {
            saved = false;
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Another session by that name is currently open. Please "
                    + "\nclose that session first.");
            return;
        }

        sessionWrapper.setName(file.getName());
        sessionEditor.setName(file.getName());

        class MyWatchedProcess extends WatchedProcess {

            @Override
            public void watch() {
                try (ObjectOutputStream objOut = new ObjectOutputStream(Files.newOutputStream(file.toPath()))) {
                    saved = false;
                    objOut.writeObject(metadata);
                    objOut.writeObject(sessionWrapper);

                    sessionWrapper.setSessionChanged(false);
                    sessionWrapper.setNewSession(false);
                    saved = true;
                } catch (IOException exception) {
                    exception.printStackTrace(System.err);

                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "An error occurred while attempting to save the session.");
                    saved = false;
                }

                DesktopController.getInstance().putMetadata(sessionWrapper, metadata);
                sessionEditor.firePropertyChange("name", null, file.getName());
            }
        }

        new MyWatchedProcess();
    }
}
