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

package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the RLCD algorithm (Rank-based Latent Causal Discovery; Dong et al., ICLR 2024), which learns a graph
 * over the observed variables and newly introduced latent variables from rank constraints on the covariance matrix.
 * The output graph contains the observed variables and the discovered latents, marked as latent nodes.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.Rlcd
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "RLCD",
        command = "rlcd",
        algoType = AlgType.search_for_structure_over_latents,
        dataType = DataType.Continuous
)
public class Rlcd implements Algorithm {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public Rlcd() {
    }

    /**
     * Runs RLCD on a continuous data set.
     *
     * @param dataModel  the data; must be a continuous tabular data set.
     * @param parameters the parameters.
     * @return the graph over observed and latent variables.
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("RLCD requires a continuous tabular data set.");
        }

        edu.cmu.tetrad.search.Rlcd rlcd = new edu.cmu.tetrad.search.Rlcd(dataSet);
        rlcd.setAlpha(parameters.getDouble(Params.ALPHA));
        rlcd.setMaxK(parameters.getInt(Params.RLCD_MAX_K));
        rlcd.setAllowNonLeafX(parameters.getBoolean(Params.RLCD_ALLOW_NON_LEAF_X));
        rlcd.setUnfoldCovers(parameters.getBoolean(Params.RLCD_UNFOLD_COVERS));
        rlcd.setCheckV(parameters.getBoolean(Params.RLCD_CHECK_V));
        rlcd.setPartitionCliqueThreshold(parameters.getInt(Params.RLCD_PARTITION_CLIQUE_THRESHOLD));
        rlcd.setStage1Method(parameters.getBoolean(Params.RLCD_STAGE1_FGES)
                ? edu.cmu.tetrad.search.Rlcd.Stage1Method.FGES
                : edu.cmu.tetrad.search.Rlcd.Stage1Method.BOSS);
        rlcd.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT_DEFAULT_1));
        rlcd.setSeed(parameters.getLong(Params.SEED));
        rlcd.setVerbose(parameters.getBoolean(Params.VERBOSE));

        try {
            return rlcd.search();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RLCD was interrupted.", e);
        }
    }

    /**
     * The true graph itself is used for comparison, since the output includes latent nodes.
     *
     * @param graph the true graph.
     * @return a copy of it.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "RLCD (Rank-based Latent Causal Discovery)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.RLCD_MAX_K);
        parameters.add(Params.RLCD_ALLOW_NON_LEAF_X);
        parameters.add(Params.RLCD_UNFOLD_COVERS);
        parameters.add(Params.RLCD_CHECK_V);
        parameters.add(Params.RLCD_PARTITION_CLIQUE_THRESHOLD);
        parameters.add(Params.RLCD_STAGE1_FGES);
        parameters.add(Params.PENALTY_DISCOUNT_DEFAULT_1);
        parameters.add(Params.SEED);
        parameters.add(Params.VERBOSE);
        return parameters;
    }
}
