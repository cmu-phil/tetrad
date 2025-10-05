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
import java.util.Random;

/**
 * Creates a random graph by adding forward edges.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RandomMim implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the RandomTwoFactorMim.
     */
    public RandomMim() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph createGraph(Parameters parameters) {
        String latentGroupSpecs = parameters.getString("mimLatentGroupSpecs", "5:5(1)");
        int numStructuralEdges = parameters.getInt("mimNumStructuralEdges", 3);
        int metaEdgeConnectionType = parameters.getInt(Params.META_EDGE_CONNECTION_TYPE);
        int numLatentMeasuredImpureParents = parameters.getInt("mimLatentMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureParents = parameters.getInt("mimMeasuredMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureAssociations = parameters.getInt("mimMeasuredMeasuredImpureAssociations", 0);

        List<edu.cmu.tetrad.graph.RandomMim.LatentGroupSpec> specs = edu.cmu.tetrad.graph.RandomMim.parseLatentGroupSpecs(latentGroupSpecs);
        return edu.cmu.tetrad.graph.RandomMim.constructRandomMim(specs, numStructuralEdges,
                numLatentMeasuredImpureParents,
                numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations,
                edu.cmu.tetrad.graph.RandomMim.LatentLinkMode.values()[metaEdgeConnectionType - 1],
                new Random());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Random two-factor MIM (Multiple Indicator Model)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("mimLatentGroupSpecs");
//        parameters.add("mimNumStructuralNodes");
        parameters.add("mimNumStructuralEdges");
        parameters.add(Params.META_EDGE_CONNECTION_TYPE);
//        parameters.add("mimMeasurementModelDegree");
        parameters.add("mimLatentMeasuredImpureParents");
        parameters.add("mimMeasuredMeasuredImpureParents");
        parameters.add("mimMeasuredMeasuredImpureAssociations");
        return parameters;
    }
}

