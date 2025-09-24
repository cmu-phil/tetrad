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

package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.DiscreteBicScore;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Number of parameters for a discrete Bayes model of the data. Must be for a discrete dataset.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumParametersEst implements Statistic {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for NumParametersEst.</p>
     */
    public NumParametersEst() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "NumParams";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of parameters for the estimated graph for a Bayes or SEM model";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        if (dataModel.isDiscrete()) {
            DiscreteBicScore score = new DiscreteBicScore((DataSet) dataModel);

            Graph dag = GraphTransforms.dagFromCpdag(estGraph, null);
            List<Node> nodes = dag.getNodes();

            double params = 0.0;

            for (Node node : dag.getNodes()) {
                score.setPenaltyDiscount(1);
                int i = nodes.indexOf(node);
                List<Node> parents = dag.getParents(node);
                int[] parentIndices = new int[parents.size()];

                for (Node parent : parents) {
                    parentIndices[parents.indexOf(parent)] = nodes.indexOf(parent);
                }

                params += score.numParameters(i, parentIndices);
            }

            return params;
        } else if (dataModel.isContinuous()) {
            return estGraph.getNumEdges();
        } else {
            throw new IllegalArgumentException("Data must be discrete");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }
}


