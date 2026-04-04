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
 * Creates a random graph by adding forward edges.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RandomMimic implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the RandomTwoFactorMim.
     */
    public RandomMimic() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph createGraph(Parameters parameters) {
        int numStructuralEdges = parameters.getInt("mimNumStructuralEdges", 3);
        int numSharedParents = parameters.getInt("mimicNumSharedParents", 0);
        int numLatentMeasuredImpureParents = parameters.getInt("mimLatentMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureParents = parameters.getInt("mimMeasuredMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureAssociations = parameters.getInt("mimMeasuredMeasuredImpureAssociations", 0);


        List<edu.cmu.tetrad.graph.RandomMimic.MimicGroupSpec> specs = edu.cmu.tetrad.graph.RandomMimic.parseMimicGroupSpecs(
                parameters.getString("mimicGroupSpecs"));
        return edu.cmu.tetrad.graph.RandomMimic.constructRandomMimic(
                specs,
                numStructuralEdges,
                numSharedParents,
                numLatentMeasuredImpureParents,
                numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations
        );

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Random MIMIC (Multiple Indicators, Multiple Conditions)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("mimicGroupSpecs");
        parameters.add("mimNumStructuralEdges");
        parameters.add("mimicNumSharedParents");
        parameters.add("mimLatentMeasuredImpureParents");
        parameters.add("mimMeasuredMeasuredImpureParents");
        parameters.add("mimMeasuredMeasuredImpureAssociations");
        return parameters;
    }
}

