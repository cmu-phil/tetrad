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

package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.List;

/**
 * An interface to represent a random graph of some sort.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface RandomGraph extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * <p>createGraph.</p>
     *
     * @param parameters Whatever parameters are need for the given graph. See getParameters().
     * @return Returns a random graph using the given parameters.
     */
    Graph createGraph(Parameters parameters);

    /**
     * Returns a short, one-line description of this graph type. This will be printed in the report.
     *
     * @return This description.
     */
    String getDescription();

    /**
     * Returns the parameters that this graph uses.
     *
     * @return A list of String names of parameters.
     */
    List<String> getParameters();
}

