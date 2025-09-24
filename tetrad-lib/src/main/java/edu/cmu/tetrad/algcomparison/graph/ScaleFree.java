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

import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Returns a scale free graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ScaleFree implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the ScaleFree.
     */
    public ScaleFree() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public edu.cmu.tetrad.graph.Graph createGraph(Parameters parameters) {
        return edu.cmu.tetrad.graph.RandomGraph.randomScaleFreeGraph(
                parameters.getInt("numMeasures") + parameters.getInt("numLatents"),
                parameters.getInt("numLatents"),
                parameters.getDouble("scaleFreeAlpha"),
                parameters.getDouble("scaleFreeBeta"),
                parameters.getDouble("scaleFreeDeltaIn"),
                parameters.getDouble("scaleFreeDeltaOut")
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Scale-free graph using the Bollobas et al. algorithm";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numMeasures");
        parameters.add("numLatents");
        parameters.add("scaleFreeAlpha");
        parameters.add("scaleFreeBeta");
        parameters.add("scaleFreeDeltaIn");
        parameters.add("scaleFreeDeltaOut");
        return parameters;
    }
}

