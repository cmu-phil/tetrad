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

/**
 * Markos up a graph using the PAG edge specialization algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PagEdgeSpecialization extends JCheckBoxMenuItem {

    /**
     * Creates a new copy subsession action for the given desktop and clipboard.
     *
     * @param workbench a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public PagEdgeSpecialization(GraphWorkbench workbench) {
        super("Add/Remove PAG Specialization Markups");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        final GraphWorkbench _workbench = workbench;

        _workbench.markPagEdgeSpecializations(workbench.isPagEdgeSpecializationMarked());
        setSelected(workbench.isPagEdgeSpecializationMarked());

        addItemListener(e -> {
            _workbench.markPagEdgeSpecializations(isSelected());
        });
    }

    private String breakDown(String reason, int maxColumns) {
        StringBuilder buf1 = new StringBuilder();
        StringBuilder buf2 = new StringBuilder();

        String[] tokens = reason.split(" ");

        for (String token : tokens) {
            if (buf1.length() + token.length() > maxColumns) {
                buf2.append(buf1);
                buf2.append("\n");
                buf1 = new StringBuilder();
                buf1.append(token);
            } else {
                buf1.append(" ").append(token);
            }
        }

        if (!buf1.isEmpty()) {
            buf2.append(buf1);
        }

        return buf2.toString().trim();
    }
}




