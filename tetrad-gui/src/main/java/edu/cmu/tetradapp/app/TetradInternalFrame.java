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

import edu.cmu.tetradapp.util.ImageUtils;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Extends JInternalFrame to ask the user if she wants to close the window.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TetradInternalFrame extends JInternalFrame {

    private static final long serialVersionUID = 907395289049591825L;

    /**
     * Constructs a new frame which will throw up a warning dialog if someone tries to close it.
     *
     * @param title the title of the frame.
     */
    public TetradInternalFrame(String title) {
        super(title, false, true, false, false);
        Image image = ImageUtils.getImage(this, "tyler16.png");
        setFrameIcon(new ImageIcon(image));

        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addInternalFrameListener(new InternalFrameAdapter() {

            /**
             * Throws up a warning dialog and then closes the frame if the user
             * says to.  Otherwise ignores the attempt.
             */
            public void internalFrameClosing(InternalFrameEvent e) {
                ActionEvent e2 = new ActionEvent(e.getSource(),
                        ActionEvent.ACTION_PERFORMED, "FrameClosing");

                CloseSessionAction closeSessionAction =
                        new CloseSessionAction();
                closeSessionAction.actionPerformed(e2);
            }
        });
    }
}






