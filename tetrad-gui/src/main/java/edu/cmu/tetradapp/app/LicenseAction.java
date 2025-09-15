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
import edu.cmu.tetradapp.util.LicenseUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author josephramsey
 */
final class LicenseAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public LicenseAction() {
        super("License");
    }

    /**
     * Performs an action when an event is triggered.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        String license = LicenseUtils.license();

        JTextArea textArea = new JTextArea(license);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(600, 400));

        Box b = Box.createVerticalBox();
        b.add(scroll);

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), b,
                "License", JOptionPane.PLAIN_MESSAGE);
    }
}




