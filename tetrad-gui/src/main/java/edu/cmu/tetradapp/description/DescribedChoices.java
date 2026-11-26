/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.description;

import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.datamanip.SelectionWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Makes, from the lines a session description writes them in, the choices that a data box keeps as objects and not
 * as plain values: how a discretization box cuts each variable, and which rows a selection box keeps.
 * <p>
 * A py-tetrad script written from a session calls these to give a box's own step the choices the box had. They are
 * read by the same code that reads them when a session is rebuilt from a description, so the step in the script is
 * given what the rebuilt box would be given.
 *
 * @author Claude (Anthropic)
 * @see PyTetradScript
 * @see SessionRebuilder
 */
public final class DescribedChoices {

    private DescribedChoices() {
    }

    /**
     * Reads how a discretization box cuts each variable.
     *
     * @param lines the values of the box's "Discretize" fields, one to a line.
     * @return the specifications by variable, as the box keeps them under "discretizationSpecs".
     * @throws IllegalArgumentException if a line cannot be read, naming the line and the reason.
     */
    public static Map<Node, DiscretizationSpec> discretization(String lines) {
        Map<Node, DiscretizationSpec> specs = new HashMap<>();

        for (String line : lines.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }

            try {
                StandardDescribers.readDiscretizeLine(line.strip(), specs);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("The discretization \"" + line.strip() + "\" was not read, since "
                                                   + e.getMessage());
            }
        }

        return specs;
    }

    /**
     * Reads which rows a selection box keeps.
     *
     * @param lines the values of the box's "Select" fields, one to a line.
     * @return the specifications by variable, as the box keeps them under "selectionSpecs".
     * @throws IllegalArgumentException if a line cannot be read, naming the line and the reason.
     */
    public static Map<Node, SelectionWrapper.SelectionSpec> selection(String lines) {
        Map<Node, SelectionWrapper.SelectionSpec> specs = new HashMap<>();

        for (String line : lines.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }

            try {
                StandardDescribers.readSelectLine(line.strip(), specs);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("The selection \"" + line.strip() + "\" was not read, since "
                                                   + e.getMessage());
            }
        }

        return specs;
    }
}
