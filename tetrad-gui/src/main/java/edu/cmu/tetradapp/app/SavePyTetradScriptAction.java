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
import edu.cmu.tetradapp.description.PyTetradScript;
import edu.cmu.tetradapp.description.SessionDescriber;
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
 * Saves a py-tetrad script for the frontmost session: a Python program that loads the session's data, sets up its
 * knowledge and runs its searches with the settings they have. The script is written from the same description of
 * the session that Save Session Description writes. What the script does not cover is said in the script, and is
 * shown after it is saved. Saving a script does not change the session and does not count as saving it.
 *
 * @author Claude (Anthropic)
 * @see PyTetradScript
 */
final class SavePyTetradScriptAction extends AbstractAction {

    /**
     * Constructs the action.
     */
    public SavePyTetradScriptAction() {
        super("Save Session as py-tetrad Script...");
    }

    /**
     * Returns the name to suggest for the script: the session's name without its ".tet" ending, with anything that
     * is awkward in the name of a Python file made an underscore.
     */
    static String suggestedName(String sessionName) {
        String base = sessionName == null || sessionName.isBlank() ? "untitled" : sessionName.strip();

        if (base.toLowerCase().endsWith(".tet")) {
            base = base.substring(0, base.length() - 4);
        }

        base = base.replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return base.isEmpty() ? "session" : base;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Asks where to save the script for the frontmost session, saves it there, and says what it does not cover.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        SessionEditorIndirectRef sessionEditorRef = DesktopController.getInstance().getFrontmostSessionEditor();

        if (!(sessionEditorRef instanceof SessionEditor sessionEditor)) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "There is no session to write a script for.");
            return;
        }

        SessionWrapper sessionWrapper = sessionEditor.getSessionWorkbench().getSessionWrapper();
        PyTetradScript.Result result;

        try {
            result = PyTetradScript.render(new SessionDescriber().describe(sessionWrapper));
        } catch (RuntimeException exception) {
            exception.printStackTrace(System.err);
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "The script could not be written: " + exception);
            return;
        }

        // Open the chooser in the session's own directory when it has one, as Save Session As does.
        File sessionFile = sessionWrapper.getSessionFile();
        String saveLocation = sessionFile != null && sessionFile.getParent() != null
                ? sessionFile.getParent()
                : Preferences.userRoot().get("sessionSaveLocation", "");

        File file = EditorUtils.getSaveFileWithPath(suggestedName(sessionWrapper.getName()), "py",
                JOptionUtils.centeringComp(), false, "Save Session as py-tetrad Script...", saveLocation);

        if (file == null) {
            return;
        }

        try {
            Files.writeString(file.toPath(), result.script(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "The script could not be saved as " + file.getAbsolutePath() + ".");
            return;
        }

        StringBuilder message = new StringBuilder("A py-tetrad script for session \"" + sessionWrapper.getName()
                                                  + "\" was saved to\n" + file.getAbsolutePath());

        if (!result.notes().isEmpty()) {
            message.append("\n\nThe script does not cover everything in the session:");

            for (String note : result.notes()) {
                message.append("\n\n").append(wrapped(note));
            }
        }

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), message.toString(), "py-tetrad Script Saved",
                result.notes().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Breaks text between words so that a dialog showing it is not very wide.
     */
    private static String wrapped(String text) {
        StringBuilder out = new StringBuilder();
        int column = 0;

        for (String word : text.split(" ")) {
            if (column > 0 && column + 1 + word.length() > 90) {
                out.append("\n");
                column = 0;
            } else if (column > 0) {
                out.append(" ");
                column++;
            }

            out.append(word);
            column += word.length();
        }

        return out.toString();
    }
}
