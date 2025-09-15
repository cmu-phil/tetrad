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
 * Returns a cyclic graph build up from small cyclic graph components.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Cyclic implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the Cyclic class.
     */
    public Cyclic() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph createGraph(Parameters parameters) {
        return edu.cmu.tetrad.graph.RandomGraph.randomCyclicGraph3(parameters.getInt("numMeasures"),
                parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2,
                parameters.getInt("maxDegree"), parameters.getDouble("probCycle"),
                parameters.getInt("probTwoCycle"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Cyclic graph built from small cyclic components";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> paramDescriptions = new ArrayList<>();
        paramDescriptions.add("numMeasures");
        paramDescriptions.add("avgDegree");
        paramDescriptions.add("maxDegree");
        paramDescriptions.add("probCycle");
        paramDescriptions.add("probTwoCycle");
        return paramDescriptions;
    }
}

