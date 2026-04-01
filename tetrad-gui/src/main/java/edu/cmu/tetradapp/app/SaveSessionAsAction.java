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
//        String sessionSaveLocation
//                = Preferences.userRoot().get("sessionSaveLocation", "");
//        File file = EditorUtils.getSaveFileWithPath(sessionEditor.getName(), "tet",
//                JOptionUtils.centeringComp(), true, "Save Session As...", sessionSaveLocation);

        String sessionSaveLocation =
                Preferences.userRoot().get("sessionSaveLocation", "");

        // Get the next available filename before presenting the save dialog,
        // so the user is never defaulted into overwriting an existing session.
        String defaultName = "untitled";

        File file = EditorUtils.getSaveFileWithPath(
                defaultName, "tet",
                JOptionUtils.centeringComp(), false, "Save Session As...",
                sessionSaveLocation);

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

    /**
     * Given a desired file path, returns the first unused filename in the sequence.
     * For example, if "untitled1.tet" exists, tries "untitled2.tet", "untitled3.tet", etc.
     * If the given file does not exist, it is returned as-is.
     *
     * @param file the initially desired file
     * @return the first file in the sequence that does not yet exist on disk
     */
    public static File nextAvailableFile(File file) {
        if (!file.exists()) {
            return file;
        }

        String name = file.getName();
        String parent = file.getParent();
        String ext = "";
        String base = name;

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex >= 0) {
            ext = name.substring(dotIndex);       // e.g. ".tet"
            base = name.substring(0, dotIndex);   // e.g. "untitled1"
        }

        // Strip any trailing digits from base to get the stem, e.g. "untitled"
        String stem = base.replaceAll("\\d+$", "");

        // Find the starting index -- if base had digits, start after the highest used
        int index = 1;
        String digitsStr = base.substring(stem.length());
        if (!digitsStr.isEmpty()) {
            index = Integer.parseInt(digitsStr) + 1;
        }

        // Walk forward until we find an unused filename
        while (true) {
            File candidate = new File(parent, stem + index + ext);
            if (!candidate.exists()) {
                return candidate;
            }
            index++;
        }
    }
}

