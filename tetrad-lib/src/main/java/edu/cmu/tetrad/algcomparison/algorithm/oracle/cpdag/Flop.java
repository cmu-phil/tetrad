/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * FLOP (Fast Learning of Order and Parents).
 * <p>
 * Wienobst, M., Henckel, L., &amp; Weichwald, S. (2026). Embracing Discrete Search: A Reasonable Approach to Causal
 * Structure Learning. International Conference on Learning Representations.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FLOP",
        command = "flop",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class Flop extends AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs,
        TakesCovarianceMatrix {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new FLOP algorithm.
     */
    public Flop() {
        // Used in reflection; do not delete.
    }

    /**
     * {@inheritDoc}
     * <p>
     * Runs the FLOP algorithm.
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        edu.cmu.tetrad.search.Flop flop;

        if (dataModel instanceof ICovarianceMatrix covariances) {
            flop = new edu.cmu.tetrad.search.Flop(covariances);
        } else if (dataModel instanceof DataSet dataSet && dataSet.isContinuous()) {
            flop = new edu.cmu.tetrad.search.Flop(dataSet);
        } else {
            throw new IllegalArgumentException("FLOP requires a continuous dataset or a covariance matrix.");
        }

        flop.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        flop.setNumRestarts(parameters.getInt(Params.FLOP_NUM_RESTARTS));
        flop.setSeed(parameters.getLong(Params.SEED));
        flop.setVerbose(parameters.getBoolean(Params.VERBOSE));

        try {
            Graph graph = flop.search();
            LogUtilsSearch.stampWithBic(graph, dataModel);
            return graph;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the true graph if there is one.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "FLOP (Fast Learning of Order and Parents) using the linear Gaussian BIC";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters for the algorithm.
     */
    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Parameters
        params.add(Params.PENALTY_DISCOUNT);
        params.add(Params.FLOP_NUM_RESTARTS);
        params.add(Params.SEED);
        params.add(Params.VERBOSE);

        return params;
    }
}
