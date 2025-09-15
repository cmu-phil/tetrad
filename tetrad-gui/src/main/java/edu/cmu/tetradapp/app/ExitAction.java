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

import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Exits the Tetrad application.
 *
 * @author josephramsey
 */
final class ExitAction extends AbstractAction {

    /**
     * Creates an exit action.
     */
    public ExitAction() {
        super("Exit");
    }

    /**
     * This method is called when an action event occurs. It invokes the exitProgram() method in the DesktopController
     * class to exit the Tetrad application.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        DesktopController.getInstance().exitProgram();
    }
}






