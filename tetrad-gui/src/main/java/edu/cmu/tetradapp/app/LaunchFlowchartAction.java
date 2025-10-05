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
 * Closes the frontmost session of the given desktop.
 *
 * @author josephramsey
 */
final class LaunchFlowchartAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public LaunchFlowchartAction() {
        super("Launch Algorithm Flowchart");
    }

    /**
     * This method handles the action event triggered by a user interaction.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        Desktop d = Desktop.getDesktop();
        try {
            d.browse(new URI("https://htmlpreview.github.io/?https:///github.com/cmu-phil/" +
                             "tetrad/blob/development/tetrad-lib/src/main/resources/docs/manual/flowchart.html"));
        } catch (IOException | URISyntaxException e2) {
            e2.printStackTrace();
        }
    }
}




