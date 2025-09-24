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

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores a single graph for use in simulations, etc.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SingleGraph implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph to be used.
     */
    private final Graph graph;

    /**
     * <p>Constructor for SingleGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public SingleGraph(Graph graph) {
        this.graph = graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph createGraph(Parameters parameters) {
        return this.graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Graph supplied by user";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }
}

