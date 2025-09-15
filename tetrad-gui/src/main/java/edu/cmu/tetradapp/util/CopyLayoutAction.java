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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.Graph;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * Copies the getModel graph in a given container, storing it so that another container can be laid out the same way.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CopyLayoutAction extends AbstractAction implements ClipboardOwner {

    /**
     * The LayoutEditable containing the target session editor.
     */
    private final LayoutEditable layoutEditable;

    /**
     * Creates a new copy subsession action for the given LayoutEditable and clipboard.
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public CopyLayoutAction(LayoutEditable layoutEditable) {
        super("Copy Layout");

        if (layoutEditable == null) {
            throw new NullPointerException();
        }

        this.layoutEditable = layoutEditable;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Copies a parentally closed selection of session nodes in the frontmost session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        Graph layoutGraph = this.layoutEditable.getGraph();
        LayoutSelection selection = new LayoutSelection(layoutGraph);
        InternalClipboard.getLayoutInstance().setContents(selection, this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}






