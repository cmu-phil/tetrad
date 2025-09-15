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

import edu.cmu.tetradapp.util.InternalClipboard;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Copies a selection of session nodes in the frontmost session editor, to the clipboard.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CopySubgraphAction extends AbstractAction implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphEditable graphEditor;

    /**
     * Creates a new copy subsession action for the given desktop and clipboard.
     *
     * @param graphEditor a {@link edu.cmu.tetradapp.editor.GraphEditable} object
     */
    public CopySubgraphAction(GraphEditable graphEditor) {
        super("Copy Selected Items");

        if (graphEditor == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.graphEditor = graphEditor;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Copies a parentally closed selection of session nodes in the frontmost session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        List modelComponents = this.graphEditor.getSelectedModelComponents();
        SubgraphSelection selection = new SubgraphSelection(modelComponents);
        InternalClipboard.getInstance().setContents(selection, this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}






