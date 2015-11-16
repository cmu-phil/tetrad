///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.util.LicenseUtils;
import edu.cmu.tetrad.util.Version;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class AboutTetradAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public AboutTetradAction() {
        super("About Tetrad " + Version.currentViewableVersion());
    }

    /**
     * Closes the frontmost session of this action's desktop.
     */
    public void actionPerformed(ActionEvent e) {
        Box b1 = Box.createVerticalBox();
        Version currentVersion = Version.currentViewableVersion();

        String copyright = LicenseUtils.copyright();
        copyright = copyright.replaceAll("\n", "<br>");


        JLabel label = new JLabel();
        label.setText("<html>" + "<b>Tetrad " + currentVersion + "</b>" +
                "<br>" +
                "<br>Laboratory for Symbolic and Educational Computing" +
                "<br>Department of Philosophy" +
                "<br>Carnegie Mellon University" + "<br>" +
                "<br>Project Direction: Clark Glymour, Richard Scheines, Peter Spirtes" +
                "<br>Lead Developer: Joseph Ramsey" +
                "<br>" + "<br>" + copyright + "</html>");
        label.setBackground(Color.LIGHT_GRAY);
        label.setFont(new Font("Dialog", Font.PLAIN, 12));
        label.setBorder(new CompoundBorder(new LineBorder(Color.DARK_GRAY),
                new EmptyBorder(10, 10, 10, 10)));

        b1.add(label);

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), b1,
                "About Tetrad...", JOptionPane.PLAIN_MESSAGE);
    }
}





