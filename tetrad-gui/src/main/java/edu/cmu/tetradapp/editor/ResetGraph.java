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

import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * This class represents an action to reset a graph to its original state in a GraphWorkbench. It implements the
 * ActionListener interface to respond to events triggered by clicking a button or selecting a menu option. It also
 * implements the ClipboardOwner interface to handle clipboard ownership changes.
 */
public class ResetGraph extends AbstractAction implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * This class represents an action to reset a graph to its original state in a GraphWorkbench. It implements the
     * ActionListener interface to respond to events triggered by clicking a button or selecting a menu option. It also
     * implements the ClipboardOwner interface to handle clipboard ownership changes.
     *
     * @param workbench The workbench containing the graph.
     */
    public ResetGraph(GraphWorkbench workbench) {
        super("Reset Graph");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * Performs an action when an event occurs.
     *
     * @param e the event that triggered the action.
     */
    public void actionPerformed(ActionEvent e) {
        this.workbench.deselectAll();
        this.workbench.setToOriginal();
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




