///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.search.work_in_progress.MagRicfBicScorer;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for LV-BOSS: the fully score-based latent-variable search that embeds a MAG projection
 * inside a BOSS-style search over orders, scored by the exact RICF Gaussian MAG BIC
 * ({@link MagRicfBicScorer}). No independence test and no separate score are taken; the scorer is
 * built from the data (a continuous data set or a covariance matrix) with the shared penalty
 * discount.
 *
 * <p>The search returns an ancestral graph. When that graph is a legal MAG -- the usual case -- its
 * PAG is reported, so the output is comparable with the other algorithms in this family. When the
 * score prefers a graph that is not a legal MAG (possible on latent projections, whose distributions
 * can satisfy rank-type constraints that let a sparser, non-maximal ancestral graph fit with fewer
 * parameters), the ancestral graph itself is returned and a note is logged; that choice is a design
 * call and can be revisited.</p>
 *
 * <p>Experimental; knowledge is not supported. See the LV-BOSS stage notes for the oracle and
 * finite-sample evidence and the honest limitations (no consistency theorem; the true MAG is not
 * always the BIC-optimal ancestral graph even in the population).</p>
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.work_in_progress.LvBossExperimental
 * @see edu.cmu.tetrad.search.work_in_progress.MagOrderProjection
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "LV-BOSS",
//        command = "lv-boss",
//        algoType = AlgType.allow_latent_common_causes,
//        dataType = DataType.Continuous
//)
@Bootstrapping
@Experimental
public class LvBossExperimental extends AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs,
        TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the wrapper. Used for reflection; do not delete it.
     */
    public LvBossExperimental() {
    }

    /**
     * Runs LV-BOSS on the given data model.
     *
     * @param dataModel  a continuous data set or a covariance matrix.
     * @param parameters the parameters.
     * @return the PAG of the found MAG, or the found ancestral graph itself when it is not a legal
     * MAG (logged).
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        MagRicfBicScorer scorer;

        if (dataModel instanceof ICovarianceMatrix cov) {
            scorer = new MagRicfBicScorer(cov);
        } else if (dataModel instanceof DataSet dataSet && dataSet.isContinuous()) {
            scorer = new MagRicfBicScorer(dataSet);
        } else {
            throw new IllegalArgumentException("LV-BOSS expects a continuous data set or a covariance matrix.");
        }

        scorer.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        edu.cmu.tetrad.search.work_in_progress.LvBossExperimental search =
                new edu.cmu.tetrad.search.work_in_progress.LvBossExperimental(scorer);
        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));

        long seed = parameters.getLong(Params.SEED);
        search.setSeed(seed < 0 ? RandomUtil.getInstance().nextLong() : seed);

        Graph mag = search.search();

        if (mag.paths().isLegalMag()) {
            return new MagToPag(mag).convert(false, true);
        } else {
            TetradLogger.getInstance().log("LV-BOSS: the best-scoring graph is not a legal MAG "
                                           + "(a score-preferred non-maximal ancestral graph); returning it as is "
                                           + "rather than a PAG.");
            return mag;
        }
    }

    /**
     * Retrieves a comparison graph by transforming a true directed graph into a PAG.
     *
     * @param graph the true, directed graph, if there is one.
     * @return the comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph, false);
    }

    /**
     * Returns a short, one-line description of this algorithm.
     *
     * @return the description.
     */
    @Override
    public String getDescription() {
        return "LV-BOSS (BOSS-style order search over MAGs with an exact RICF Gaussian MAG BIC)";
    }

    /**
     * Retrieves the data type required by the search algorithm.
     *
     * @return continuous.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters used by the algorithm.
     *
     * @return the parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.PENALTY_DISCOUNT);
        params.add(Params.NUM_STARTS);
        params.add(Params.SEED);
        return params;
    }
}
