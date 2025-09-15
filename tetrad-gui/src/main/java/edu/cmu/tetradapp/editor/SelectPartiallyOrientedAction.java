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

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * Selects all directed edges in the given display graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SelectPartiallyOrientedAction extends AbstractAction implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    /**
     * Creates a new copy subsession action for the given desktop and clipboard.
     *
     * @param workbench the given workbench.
     */
    public SelectPartiallyOrientedAction(GraphWorkbench workbench) {
        super("Highlight Partially Oriented Edges");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Selects all directed edges in the given display graph.
     */
    public void actionPerformed(ActionEvent e) {
        this.workbench.deselectAll();

        for (Component comp : this.workbench.getComponents()) {
            if (comp instanceof DisplayEdge) {
                Edge edge = ((DisplayEdge) comp).getModelEdge();
                if (Edges.isPartiallyOrientedEdge(edge)) {
                    this.workbench.selectEdge(edge);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}




