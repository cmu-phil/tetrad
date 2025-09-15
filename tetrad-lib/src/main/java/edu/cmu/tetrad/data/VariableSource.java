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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.List;

/**
 * Inteface implemented by classes, instantiations of which are associated with lists of variables. Such lists of
 * variables are used for a number of purposes--creating data sets, creating graphs, comparing one data to another, and
 * so on.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface VariableSource extends TetradSerializable {

    /**
     * Returns the list of variables associated with this object.
     *
     * @return the list of variables associated with this object.
     */
    List<Node> getVariables();

    /**
     * Returns the variable names associated with this getVariableNames.
     *
     * @return the variable names associated with this getVariableNames.
     */
    List<String> getVariableNames();
}






