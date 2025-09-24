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
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.TetradMetadata;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

/**
 * Saves a session from a file.
 *
 * @author josephramsey
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class SaveSessionAction extends AbstractAction {

    @Serial
    private static final long serialVersionUID = -1812370698394158108L;

    /**
     * Constant <code>saved=false</code>
     */
    public static boolean saved = false;

    /**
     * <p>Constructor for SaveSessionAction.</p>
     */
    public SaveSessionAction() {
        super("Save Session");
    }

    /**
     * {@inheritDoc}
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

        Path outputFile = Paths.get(
                Preferences.userRoot().get("sessionSaveLocation", Preferences.userRoot().absolutePath()),
                sessionWrapper.getName());

        if (Files.notExists(outputFile) || sessionWrapper.isNewSession()) {
            SaveSessionAsAction saveSessionAsAction = new SaveSessionAsAction();
            saveSessionAsAction.actionPerformed(e);
            saved = SaveSessionAsAction.saved;

            return;
        }

        if (Files.exists(outputFile)) {
            int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                    "File already exists. Overwrite?", "Save", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.NO_OPTION) {
                SaveSessionAsAction saveSessionAsAction = new SaveSessionAsAction();
                saveSessionAsAction.actionPerformed(e);
                saved = SaveSessionAsAction.saved;

                return;
            }
        }

        class MyWatchedProceess extends WatchedProcess {

            @Override
            public void watch() {
                try (ObjectOutputStream objOut = new ObjectOutputStream(Files.newOutputStream(outputFile))) {
                    sessionWrapper.setNewSession(false);
                    objOut.writeObject(metadata);
                    objOut.writeObject(sessionWrapper);
                } catch (NotSerializableException exception) {
                    exception.printStackTrace(System.err);
                    JOptionPane.showMessageDialog(
                            JOptionUtils.centeringComp(),
                            "An error occurred while attempting to save the session. The session could not be saved.");
                } catch (IOException exception) {
                    exception.printStackTrace(System.err);
                    JOptionPane.showMessageDialog(
                            JOptionUtils.centeringComp(),
                            String.format(
                                    "An error occurred while attempting to save the session as %s.",
                                    outputFile.toAbsolutePath()));
                }

                sessionWrapper.setSessionChanged(false);
                DesktopController.getInstance().putMetadata(sessionWrapper, metadata);
            }
        }

        new MyWatchedProceess();
    }

    /**
     * Finds the next available filename in the format "untitled1.tet", "untitled2.tet", etc.
     *
     * @param directory the directory to search in
     * @param baseName  the base filename (e.g., "untitled")
     * @return the next available Path object
     */
    private Path getNextUntitledFileName(Path directory, String baseName) {
        int counter = 1;
        Path newFileName;
        do {
            newFileName = directory.resolve(baseName + counter + ".tet");
            counter++;
        } while (Files.exists(newFileName));
        return newFileName;
    }
}

