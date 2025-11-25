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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Launches the Tetrad project website in the system browser.
 *
 * @author josephramsey
 */
final class LaunchWebsiteAction extends AbstractAction {

    // Tetrad project website URL.
    private static final String TETRAD_WEBSITE_URL =
            "https://www.cmu.edu/dietrich/philosophy/tetrad/index.html";

    /**
     * Creates a new action that opens the Tetrad website.
     */
    public LaunchWebsiteAction() {
        super("Visit Tetrad Website");
    }

    /**
     * Handles the action: attempts to open the Tetrad website
     * in the system default browser, and falls back to a dialog
     * with the URL if that fails.
     *
     * @param e the event to be processed
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(
                    null,
                    "Desktop browsing is not supported on this platform.\n" +
                    "You can open the Tetrad website manually at:\n" + TETRAD_WEBSITE_URL,
                    "Visit Tetrad Website",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        Desktop desktop = Desktop.getDesktop();
        try {
            desktop.browse(new URI(TETRAD_WEBSITE_URL));
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "Unable to open the Tetrad website in your browser.\n" +
                    "You can still access it at:\n" + TETRAD_WEBSITE_URL,
                    "Visit Tetrad Website",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}