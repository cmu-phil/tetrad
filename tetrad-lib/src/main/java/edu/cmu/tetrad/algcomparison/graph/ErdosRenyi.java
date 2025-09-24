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
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a random graph by the Erdos-Renyi method (probabiliy of edge fixed, # edges not).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ErdosRenyi implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the ErdosRenyi class.
     */
    public ErdosRenyi() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph createGraph(Parameters parameters) {
        double p = parameters.getDouble(Params.PROBABILITY_OF_EDGE);
        int m = parameters.getInt(Params.NUM_MEASURES);
        int l = parameters.getInt(Params.NUM_LATENTS);
        int t = (m + l) * (m + l - 1) / 2;
        final int max = Integer.MAX_VALUE;
        int e = (int) (p * t);

        return edu.cmu.tetrad.graph.RandomGraph.randomGraphRandomForwardEdges(
                m + l, l, e, max, max, max, false, -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Graph constructed the Erdos-Renyi method (p fixed, # edges not)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.NUM_MEASURES);
        parameters.add(Params.NUM_LATENTS);
        parameters.add(Params.PROBABILITY_OF_EDGE);
        parameters.add(Params.COMPARE_GRAPH_ALGCOMP);
        return parameters;
    }
}

