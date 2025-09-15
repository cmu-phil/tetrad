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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import java.util.ArrayList;
import java.util.List;

/**
 * Initializes a graph by adding the previous time step only of each variable.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PreviousStepOnly implements GraphInitializer {
    /**
     * <p>Constructor for PreviousStepOnly.</p>
     */
    public PreviousStepOnly() {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Randomizes the graph.
     */
    public void initialize(LagGraph lagGraph) {

        lagGraph.clearEdges();

        List<String> factors = new ArrayList<>(lagGraph.getFactors());

        // Add edges one time step back.
        for (String factor1 : factors) {
            LaggedFactor laggedFactor = new LaggedFactor(factor1, 1);
            lagGraph.addEdge(factor1, laggedFactor);
        }
    }
}






