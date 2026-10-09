/// ////////////////////////////////////////////////////////////////////////////
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

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;

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

        // Each session remembers the file it was loaded from or last saved to. This path used to be reconstructed
        // from the single global "sessionSaveLocation" preference plus the session name, so with several sessions
        // open from different directories, whichever was touched last repointed the preference and Save wrote
        // every session into that one directory -- or offered to overwrite an unrelated file of the same name
        // there. A session that has not touched disk in this run, or whose file has since vanished, goes to Save
        // As.
        File outputFile = sessionWrapper.getSessionFile();

        if (outputFile == null || sessionWrapper.isNewSession() || Files.notExists(outputFile.toPath())) {
            SaveSessionAsAction saveSessionAsAction = new SaveSessionAsAction();
            saveSessionAsAction.actionPerformed(e);
            saved = SaveSessionAsAction.saved;

            return;
        }

        // Saving to the session's own file is a plain overwrite, as in any editor. The confirm dialog that used to
        // sit here existed only because the path was a guess.
        try (ObjectOutputStream objOut = new ObjectOutputStream(Files.newOutputStream(outputFile.toPath()))) {
            sessionWrapper.setNewSession(false);
            objOut.writeObject(metadata);
            objOut.writeObject(sessionWrapper);
        } catch (NotSerializableException exception) {
            saved = false;
            exception.printStackTrace(System.err);
            JOptionPane.showMessageDialog(
                    JOptionUtils.centeringComp(),
                    "An error occurred while attempting to save the session. The session could not be saved.");
            return;
        } catch (IOException exception) {
            saved = false;
            exception.printStackTrace(System.err);
            JOptionPane.showMessageDialog(
                    JOptionUtils.centeringComp(),
                    String.format(
                            "An error occurred while attempting to save the session as %s.",
                            outputFile.getAbsolutePath()));
            return;
        }

        // Only a successful save marks the session unchanged; marking it unchanged on a failed save would let a
        // close or quit silently discard the changes afterwards.
        saved = true;
        sessionWrapper.setSessionChanged(false);
        DesktopController.getInstance().putMetadata(sessionWrapper, metadata);
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

