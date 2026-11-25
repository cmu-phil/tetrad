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
import edu.cmu.tetradapp.description.SessionDescription;
import edu.cmu.tetradapp.description.SessionDescriptionParser;
import edu.cmu.tetradapp.description.SessionRebuilder;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Builds a new session from a session description, the text file that "Save Session Description" writes. The new
 * session has the boxes of the described session, in their places and connected as they were, each holding the
 * settings the description gives it. It has no data: the user loads the data and runs the searches again. When the
 * session is built, the user is told what remains to be done and what in the description could not be restored.
 *
 * @author josephramsey
 * @author Claude
 * @see SessionRebuilder
 * @see SaveSessionDescriptionAction
 */
final class LoadSessionDescriptionAction extends AbstractAction {

    /**
     * Constructs the action.
     */
    public LoadSessionDescriptionAction() {
        super("Load Session Description...");
    }

    /**
     * Returns a name for the new session: the name the description gives, or one made from the description file's
     * name, changed if need be so that no open session has it.
     */
    private static String sessionName(SessionDescription description, File file) {
        String name = description.getHeader(SessionDescription.SESSION);

        if (name == null || name.isBlank()) {
            name = file.getName();
            int dot = name.lastIndexOf('.');
            name = (dot > 0 ? name.substring(0, dot) : name) + ".tet";
        }

        String base = name.toLowerCase().endsWith(".tet") ? name.substring(0, name.length() - 4) : name;
        String candidate = base + ".tet";

        for (int i = 2; DesktopController.getInstance().existsSessionByName(candidate); i++) {
            candidate = base + "-" + i + ".tet";
        }

        return candidate;
    }

    /**
     * Returns the account shown to the user once the session is built.
     */
    private static String report(SessionRebuilder.Result result, List<String> warnings) {
        StringBuilder text = new StringBuilder();
        int boxes = result.session().getNodes().size();
        text.append("Built a session with ").append(boxes).append(boxes == 1 ? " box" : " boxes")
                .append(". It has no data yet.\n");

        if (!result.toDo().isEmpty()) {
            text.append("\nTo do, from the top of the session down:\n");

            for (String line : result.toDo()) {
                text.append("  ").append(line).append('\n');
            }
        }

        if (!result.problems().isEmpty()) {
            text.append("\nCould not be restored:\n");

            for (String line : result.problems()) {
                text.append("  ").append(line).append('\n');
            }
        }

        if (!warnings.isEmpty()) {
            text.append("\nNot understood in the description file:\n");

            for (String line : warnings) {
                text.append("  ").append(line).append('\n');
            }
        }

        return text.toString();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Asks for a session description file, builds a session from it, and opens the session.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(Preferences.userRoot().get("sessionSaveLocation", "")));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Load Session Description...");

        if (chooser.showOpenDialog(JOptionUtils.centeringComp()) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();

        if (file == null) {
            return;
        }

        SessionDescriptionParser.Result parsed;

        try {
            parsed = SessionDescriptionParser.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "The file could not be read as text: " + file.getAbsolutePath());
            return;
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), exception.getMessage());
            return;
        }

        SessionRebuilder.Result result;

        try {
            result = SessionRebuilder.rebuild(parsed.description(), sessionName(parsed.description(), file));
        } catch (RuntimeException exception) {
            exception.printStackTrace(System.err);
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "A session could not be built from this description: " + exception);
            return;
        }

        // The session is new: it has no file of its own yet, and it has not been saved.
        String name = result.session().getName();
        SessionEditor editor = new SessionEditor(name, new SessionEditorWorkbench(result.session()));
        result.session().setNewSession(true);
        result.session().setSessionChanged(true);

        DesktopController.getInstance().addSessionEditor(editor);
        DesktopController.getInstance().closeEmptySessions();

        JTextArea area = new JTextArea(report(result, parsed.warnings()));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, 360));

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), scroll, "Session Built From Description",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
