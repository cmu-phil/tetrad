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
import edu.cmu.tetradapp.description.SessionDescriber;
import edu.cmu.tetradapp.description.SessionDescriptionWriter;
import edu.cmu.tetradapp.model.EditorUtils;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.Preferences;

/**
 * Saves a description of the frontmost session as a legible text file: its boxes, their connections and locations,
 * and the choices and non-default parameter settings made in each. The file holds no data sets and no results. It is
 * meant for keeping a record of an analysis and for pasting into documents and email. Saving a description does not
 * change the session and does not count as saving it.
 *
 * @author josephramsey
 * @author Claude
 * @see SessionDescriber
 */
final class SaveSessionDescriptionAction extends AbstractAction {

    /**
     * Constructs the action.
     */
    public SaveSessionDescriptionAction() {
        super("Save Session Description...");
    }

    /**
     * Returns the name to suggest for the description file: the session's name without its ".tet" ending, followed
     * by "-description".
     */
    private static String suggestedName(String sessionName) {
        String base = sessionName == null || sessionName.isBlank() ? "untitled" : sessionName.strip();

        if (base.toLowerCase().endsWith(".tet")) {
            base = base.substring(0, base.length() - 4);
        }

        return base + "-description";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Asks where to save the description of the frontmost session, and saves it there.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        SessionEditorIndirectRef sessionEditorRef = DesktopController.getInstance().getFrontmostSessionEditor();

        if (!(sessionEditorRef instanceof SessionEditor sessionEditor)) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "There is no session to describe.");
            return;
        }

        SessionWrapper sessionWrapper = sessionEditor.getSessionWorkbench().getSessionWrapper();

        String text;

        try {
            text = SessionDescriptionWriter.toText(new SessionDescriber().describe(sessionWrapper));
        } catch (RuntimeException exception) {
            exception.printStackTrace(System.err);
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "The session could not be described: " + exception);
            return;
        }

        // Open the chooser in the session's own directory when it has one, as Save Session As does.
        File sessionFile = sessionWrapper.getSessionFile();
        String saveLocation = sessionFile != null && sessionFile.getParent() != null
                ? sessionFile.getParent()
                : Preferences.userRoot().get("sessionSaveLocation", "");

        File file = EditorUtils.getSaveFileWithPath(suggestedName(sessionWrapper.getName()), "txt",
                JOptionUtils.centeringComp(), false, "Save Session Description...", saveLocation);

        if (file == null) {
            return;
        }

        try {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "The session description could not be saved as " + file.getAbsolutePath() + ".");
            return;
        }

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                "A description of session \"" + sessionWrapper.getName() + "\" was saved to\n"
                + file.getAbsolutePath(),
                "Session Description Saved", JOptionPane.INFORMATION_MESSAGE);
    }
}
