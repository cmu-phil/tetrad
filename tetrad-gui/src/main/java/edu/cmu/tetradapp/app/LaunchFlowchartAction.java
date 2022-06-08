///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class LaunchFlowchartAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public LaunchFlowchartAction() {
        super("Launch Algorithm Flowchart");
    }

    /**
     * Closes the frontmost session of this action's desktop.
     */
    public void actionPerformed(ActionEvent e) {
        Desktop d = Desktop.getDesktop();
        try {
            d.browse(new URI("https://htmlpreview.github.io/?https://raw.githubusercontent.com/cmu-phil/tetrad/" +
                    "development/docs/manual/flowchart.html"));
        } catch (IOException | URISyntaxException e2) {
            e2.printStackTrace();
        }
    }
}



