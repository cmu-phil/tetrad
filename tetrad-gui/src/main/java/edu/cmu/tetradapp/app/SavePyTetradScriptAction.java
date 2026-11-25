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
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.Preferences;

/**
 * Saves a py-tetrad script for the frontmost session: a Python program that loads the session's data, sets up its
 * knowledge and runs its searches with the settings they have. The script is written from the same description of
 * the session that Save Session Description writes.
 * <p>
 * Such a script covers less than a session does, so before anything is saved the user is shown what this one would
 * hold: which boxes are written as code, which are left to be written by hand, which searches will not run, which
 * boxes are left out, and what a script of this kind is in general. The user may cancel there. Saving a script does
 * not change the session and does not count as saving it.
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
     * Shows what the script for the frontmost session would hold and, unless the user cancels, asks where to save
     * it and saves it there.
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

        if (!confirmed(result.preview(sessionWrapper.getName()))) {
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

        int byHand = result.count(PyTetradScript.Fate.BY_HAND);
        String message = "A py-tetrad script for session \"" + sessionWrapper.getName() + "\" was saved to\n"
                         + file.getAbsolutePath();

        if (byHand > 0) {
            message += "\n\nIt will stop at " + (byHand == 1 ? "the step" : "each of the " + byHand + " steps")
                       + " left for you to write by hand. " + (byHand == 1 ? "It is" : "They are")
                       + " marked in the script.";
        }

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), message, "py-tetrad Script Saved",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Shows what the script would hold, and asks whether to go on and save it.
     *
     * @param preview what the script would hold, as text.
     * @return true if the user chose to save the script.
     */
    private static boolean confirmed(String preview) {
        String save = "Save Script...";
        int choice = JOptionPane.showOptionDialog(JOptionUtils.centeringComp(), previewComponent(preview),
                "Save Session as py-tetrad Script", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[]{save, "Cancel"}, save);
        return choice == 0;
    }

    /**
     * The text of what the script would hold, in a scrolling area sized so that a long session does not make the
     * dialog taller than the screen.
     *
     * @param preview the text.
     * @return the component to show.
     */
    static JComponent previewComponent(String preview) {
        JTextArea area = new JTextArea(preview);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        area.setMargin(new Insets(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        FontMetrics metrics = area.getFontMetrics(area.getFont());
        int lines = Math.min(30, Math.max(14, preview.split("\n", -1).length + 6));
        scroll.setPreferredSize(new Dimension(metrics.charWidth('n') * 96, metrics.getHeight() * lines));
        return scroll;
    }
}
