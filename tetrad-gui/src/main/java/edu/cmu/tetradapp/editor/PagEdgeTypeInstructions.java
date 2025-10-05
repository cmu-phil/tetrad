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

package edu.cmu.tetradapp.editor;

import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;

/**
 * Represents an action to display PAG Edge Type Instructions in a GraphWorkbench. This class extends AbstractAction and
 * implements ClipboardOwner.
 */
public class PagEdgeTypeInstructions extends AbstractAction implements ClipboardOwner {

    /**
     * Represents an action to display PAG Edge Type Instructions in a GraphWorkbench.
     */
    public PagEdgeTypeInstructions() {
        super("PAG Edge Type Instructions");
    }

    /**
     * Performs an action when an event occurs.
     *
     * @param e the event that triggered the action.
     */
    public void actionPerformed(ActionEvent e) {
        // Initialize helpSet
        final String helpHS = "/docs/javahelp/TetradHelp.hs";

        try {
            URL url = this.getClass().getResource(helpHS);
            HelpSet helpSet = new HelpSet(null, url);
            helpSet.setHomeID("graph_edge_types");
            HelpBroker broker = helpSet.createHelpBroker();
            broker.setCurrentView("Index");
            ActionListener listener = new CSH.DisplayHelpFromSource(broker);
            listener.actionPerformed(e);
        } catch (Exception ee) {
            System.out.println("HelpSet " + ee.getMessage());
            System.out.println("HelpSet " + helpHS + " not found");
            throw new IllegalArgumentException();
        }

    }

    /**
     * Called when ownership of the clipboard contents is lost.
     *
     * @param clipboard the clipboard that lost ownership
     * @param contents  the contents that were lost
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}




