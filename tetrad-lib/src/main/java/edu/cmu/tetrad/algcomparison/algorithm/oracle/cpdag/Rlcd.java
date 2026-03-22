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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.rlcd.RLCD;
import edu.cmu.tetrad.search.rlcd.RLCDParams;
import edu.cmu.tetrad.search.rlcd.RankTest;
import edu.cmu.tetrad.search.rlcd.RankTestFactory;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Algorithm-comparison wrapper for Rank-based Latent Causal Discovery (RLCD).
 *
 * <p>Delegates to {@link RLCD}, which implements the three-phase algorithm from:
 * Dong et al., "A Versatile Causal Discovery Framework to Allow Causally-Related
 * Hidden Variables", ICLR 2024.</p>
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "RLCD",
        command = "rlcd",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class Rlcd extends AbstractBootstrapAlgorithm
        implements Algorithm, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs the wrapper.
     */
    public Rlcd() {
    }

    /**
     * Runs the RLCD search.
     *
     * @param dataModel  the data model (must be a continuous DataSet)
     * @param parameters the parameters
     * @return the resulting graph
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(
                    "RLCD requires a continuous DataSet. Got: " +
                            (dataModel == null ? "null" : dataModel.getClass().getName()));
        }

        RLCDParams params = buildRlcdParams(dataSet, parameters);
        RLCD rlcd = new RLCD(dataSet, params);
        return rlcd.search();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "RLCD (Rank-based Latent Causal Discovery)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.PENALTY_DISCOUNT);  // FGES sparsity
        parameters.add(Params.DEPTH);             // clique partition threshold
//        parameters.add(Params.MAX_K);
//        parameters.add(Params.STAGES);
//        parameters.add(Params.CHECK_V_STRUCTURES);
//        parameters.add(Params.UNFOLD_COVERS);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Translates algcomparison {@link Parameters} into {@link RLCDParams}.
     * Kept separate so it can be reused in tests without going through
     * the full wrapper machinery.
     */
    static RLCDParams buildRlcdParams(DataSet dataSet, Parameters parameters) {
        RLCDParams params = new RLCDParams();

        // Stage-1 skeleton method.
        String stage1Str = "ALL";// parameters.getString(Params.STAGE1_METHOD, "FGES");
        try {
            params.setStage1Method(RLCDParams.Stage1Method.valueOf(stage1Str.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            params.setStage1Method(RLCDParams.Stage1Method.ALL);
        }

        params.setStage1GesSparsity(parameters.getDouble(Params.PENALTY_DISCOUNT, 2.0));
        params.setStage1CiAlpha(parameters.getDouble(Params.ALPHA, 0.01));
        params.setStage1PartitionThreshold(parameters.getInt(Params.DEPTH, 3));

        // Latent-discovery parameters.
        int maxK = 3;// parameters.getInt(Params.MAX_K, 3);
        params.setMaxK(maxK);
        params.setStages(2);//parameters.getInt(Params.STAGES, 2));
        params.setCheckVStructures(true);//parameters.getBoolean(Params.CHECK_V_STRUCTURES, true));
        params.setUnfoldCovers(true);//parameters.getBoolean(Params.UNFOLD_COVERS, true));

        // Use a single alpha value for all k levels.
        double alpha = parameters.getDouble(Params.ALPHA, 0.01);
        double[] alphaByK = new double[maxK + 1];
        for (int i = 0; i <= maxK; i++) alphaByK[i] = alpha;
        params.setAlphaByK(alphaByK);

        // Rank-test factory: Wilks' canonical-correlation test, built once per search.
        SimpleMatrix cov = new CovarianceMatrix(dataSet).getMatrix().getSimpleMatrix();
        params.setRankTestFactory(new RankTestFactory() {
            @Override
            public RankTest create(DataSet ds) {
                return (pCols, qCols, k, a) -> {
                    int estimatedRank = RankTests.estimateWilksRank(cov, pCols, qCols, k, a);
                    System.out.println("Estimated rank: " + estimatedRank + " pcols: " + Arrays.toString(pCols)
                            + " qcols: " + Arrays.toString(qCols) + " k: " + k + " alpha: " + a);
                    return estimatedRank <= k;
                };
            }
        });

        return params;
    }
}
