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

package edu.cmu.tetrad.data.missing;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.GraphSampling;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The search wrapper implementing {@link MissingDataPolicy#MULTIPLE_IMPUTATION}: impute m completed datasets, run
 * the given algorithm on each, and pool the resulting graphs by edge frequency using the existing
 * {@link GraphSampling} machinery (so pooled graphs carry per-edge probabilities and render with the same GUI
 * visualization as bootstrap results). This is the component that accepts the "mi" policy; individual scores and
 * tests reject it by design.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ImputationSearch {

    /**
     * Private constructor to prevent instantiation.
     */
    private ImputationSearch() {
    }

    /**
     * Runs the multiple-imputation search. If the dataset has no missing values, the algorithm is simply run once.
     *
     * @param dataSet    The dataset.
     * @param algorithm  The algcomparison algorithm to run on each completed dataset.
     * @param parameters The parameters for the algorithm.
     * @param imputer    The imputer, or null to choose a default: MvnImputer for all-continuous data,
     *                   MiceLiteImputer for discrete or mixed data.
     * @param spec       The spec (policy MULTIPLE_IMPUTATION), or null for the default of m = 10.
     * @return The result: the pooled graph and the per-imputation graphs.
     * @throws InterruptedException          If the search is interrupted.
     * @throws UnsupportedOperationException If no imputer is given and the data are not all-continuous.
     */
    public static Result search(DataSet dataSet, Algorithm algorithm, Parameters parameters,
                                MultipleImputer imputer, MissingDataSpec spec) throws InterruptedException {
        if (dataSet == null) throw new NullPointerException("Dataset is null.");
        if (algorithm == null) throw new NullPointerException("Algorithm is null.");
        if (parameters == null) parameters = new Parameters();

        if (spec == null) {
            spec = MissingDataSpec.multipleImputation(10);
        } else if (spec.getPolicy() != MissingDataPolicy.MULTIPLE_IMPUTATION) {
            throw new IllegalArgumentException("ImputationSearch requires the MULTIPLE_IMPUTATION policy; got "
                    + spec.getPolicy() + ".");
        }

        if (!dataSet.existsMissingValue()) {
            TetradLogger.getInstance().log("ImputationSearch: The dataset has no missing values; running the "
                    + "algorithm once without imputation.");
            Graph graph = algorithm.search(dataSet, parameters);
            return new Result(graph, Collections.singletonList(graph));
        }

        if (imputer == null) {
            if (dataSet.isContinuous()) {
                imputer = new MvnImputer(spec);
            } else {
                imputer = new MiceLiteImputer();
            }
        }

        int m = spec.getNumImputations();
        TetradLogger.getInstance().log("ImputationSearch: imputing " + m + " datasets. "
                + MissingDataUtils.briefSummary(dataSet));

        List<DataSet> completed = imputer.impute(dataSet, m, spec.getSeed());
        List<Graph> graphs = new ArrayList<>(m);

        for (DataSet complete : completed) {
            graphs.add(algorithm.search(complete, parameters));
        }

        return new Result(GraphSampling.createGraphWithHighProbabilityEdges(graphs), graphs);
    }

    /**
     * The result of a multiple-imputation search: the pooled graph (with per-edge frequencies attached by
     * GraphSampling) and the individual per-imputation graphs.
     */
    public static final class Result {

        /**
         * The pooled graph.
         */
        public final Graph pooledGraph;

        /**
         * The per-imputation graphs, in imputation order.
         */
        public final List<Graph> imputationGraphs;

        /**
         * Constructs a result.
         *
         * @param pooledGraph      The pooled graph.
         * @param imputationGraphs The per-imputation graphs.
         */
        private Result(Graph pooledGraph, List<Graph> imputationGraphs) {
            this.pooledGraph = pooledGraph;
            this.imputationGraphs = Collections.unmodifiableList(imputationGraphs);
        }
    }
}
