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

import edu.cmu.tetradapp.model.EditorUtils;
import edu.cmu.tetradapp.util.InternalClipboard;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Copies a parentally closed selection of session nodes in the frontmost session editor to the clipboard.
 *
 * @author josephramsey
 */
class PasteSubgraphAction extends AbstractAction implements ClipboardOwner {
    /**
     * The desktop containing the target session editor.
     */
    private final GraphEditable graphEditor;

    /**
     * Constucts an action for loading the session in the given '.tet' file into the desktop.
     *
     * @param graphEditor a {@link edu.cmu.tetradapp.editor.GraphEditable} object
     */
    public PasteSubgraphAction(GraphEditable graphEditor) {
        super("Paste Selected Items");

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
        Transferable transferable = InternalClipboard.getInstance()
                .getContents(null);

        if (!(transferable instanceof SubgraphSelection selection)) {
            return;
        }

        DataFlavor flavor =
                new DataFlavor(SubgraphSelection.class, "Subgraph Selection");

        try {
            List modelList = (List) selection.getTransferData(flavor);
            Point point = EditorUtils.getTopLeftPoint(modelList);
            point.translate(50, 50);
            graphEditor().pasteSubsession(modelList, point);
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * Notifies this object that it is no longer the owner of the contents of the clipboard.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }

    private GraphEditable graphEditor() {
        return this.graphEditor;
    }
}






