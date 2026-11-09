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

package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fofc;
import edu.cmu.tetrad.util.StratifiedRankTests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A concrete implementation of the {@code BlockDiscoverer} interface that discovers clusters or "blocks" of indices
 * using the FOFC algorithm. The discovered blocks are represented as a {@code BlockSpec} and include features such as
 * validation, canonicalization, and policy-based adjustments.
 * <p>
 * This class leverages the FOFC algorithm to generate clusters by analyzing the provided dataset and utilizing a
 * statistical test with a specified confidence level and equivalent sample size. The discovered clusters can be further
 * refined based on policy configurations for handling overlapping or conflicting blocks.
 */
public class FofcBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final double alpha;
    private final int ess;
    private final SingletonClusterPolicy policy;
    private final boolean verbose;
    private final CovarianceMatrix pooledCovariance;
    private final String domainColumn;

    /**
     * Constructs a new instance of {@code FofcBlockDiscoverer}, which is used to discover clusters or "blocks" of
     * indices in a dataset based on the FOFC algorithm. The discovered blocks are adjusted and refined according to the
     * specified configuration parameters.
     *
     * @param dataSet the dataset to be analyzed for block discovery.
     * @param alpha   the significance level used in the statistical test to determine independence.
     * @param ess     the equivalent sample size parameter used in Bayesian methods within the FOFC algorithm.
     * @param policy  the policy to handle scenarios involving overlapping or conflicting blocks.
     * @param verbose A boolean flag indicating whether verbose mode should be enabled (true) or disabled (false).
     */
    public FofcBlockDiscoverer(DataSet dataSet, double alpha, int ess,
                               SingletonClusterPolicy policy, boolean verbose) {
        this(dataSet, null, alpha, ess, policy, verbose);
    }

    /**
     * Constructs a FofcBlockDiscoverer over a pooled covariance matrix--for example, the averaged covariance of several
     * imputations of a data set with missing values (see {@code BlocksUtil.pooledCovariance}). The representative
     * data set anchors the resulting {@code BlockSpec} and must have the same variables, in the same order, as the
     * pooled matrix.
     *
     * @param dataSet          The representative data set for the resulting BlockSpec.
     * @param pooledCovariance The covariance matrix to run the algorithm on, or null to compute it from the data set.
     * @param alpha            The significance level for the statistical tests.
     * @param ess              The equivalent sample size.
     * @param policy           The single-cluster policy.
     * @param verbose          Whether verbose output is enabled.
     */
    public FofcBlockDiscoverer(DataSet dataSet, CovarianceMatrix pooledCovariance, double alpha, int ess,
                               SingletonClusterPolicy policy, boolean verbose) {
        this(dataSet, pooledCovariance, alpha, ess, policy, verbose, null);
    }

    /**
     * Constructs a FofcBlockDiscoverer that runs in stratified (domain-pooled) mode when a domain column is named:
     * the rows are split by that column, which is removed from the variable list, and every rank decision pools the
     * within-domain Bartlett statistics across domains (the Phase 1 upgrade of LCD-NOD, Dong et al., ICML 2026).
     * The resulting BlockSpec is anchored to the data set with the domain column removed. Stratified mode cannot be
     * combined with a pooled covariance matrix of several imputations.
     *
     * @param dataSet          The dataset to be analyzed for block discovery.
     * @param pooledCovariance The pooled covariance, or null; must be null when domainColumn is set.
     * @param alpha            The significance level for the statistical tests.
     * @param ess              The equivalent sample size; ignored in stratified mode.
     * @param policy           The single-cluster policy.
     * @param verbose          Whether verbose output is enabled.
     * @param domainColumn     The name of the domain column, or null for the ordinary single-sample behavior.
     */
    public FofcBlockDiscoverer(DataSet dataSet, CovarianceMatrix pooledCovariance, double alpha, int ess,
                               SingletonClusterPolicy policy, boolean verbose, String domainColumn) {
        if (domainColumn != null && pooledCovariance != null) {
            throw new IllegalArgumentException("Stratified mode (a domain column) cannot be combined with a pooled "
                                               + "covariance matrix of several imputations.");
        }
        this.dataSet = dataSet;
        this.pooledCovariance = pooledCovariance;
        this.alpha = alpha;
        this.ess = ess;
        this.policy = policy;
        this.verbose = verbose;
        this.domainColumn = domainColumn;
        BlocksUtil.checkPooledMatchesData(pooledCovariance, dataSet);
    }

    /**
     * Discovers clusters or "blocks" of indices in the dataset using the FOFC algorithm. The method performs block
     * validation, canonicalization, and policy-based adjustments to refine the discovered clusters.
     *
     * @return a {@code BlockSpec} object representing the discovered and refined set of blocks.
     */
    @Override
    public BlockSpec discover() {
        DataSet anchor = dataSet;
        Fofc fofc;

        if (domainColumn != null) {
            Node domain = dataSet.getVariable(domainColumn);
            if (domain == null) {
                throw new IllegalArgumentException("No column named " + domainColumn + " in the data set.");
            }
            List<DataSet> perDomain = StratifiedRankTests.split(dataSet, domain);

            int dcol = dataSet.getVariables().indexOf(domain);
            int[] keep = new int[dataSet.getNumColumns() - 1];
            int k = 0;
            for (int j = 0; j < dataSet.getNumColumns(); j++) if (j != dcol) keep[k++] = j;
            anchor = dataSet.subsetColumns(keep);

            fofc = new Fofc(anchor, alpha, -1);
            fofc.setStratifiedRankTests(StratifiedRankTests.fromDataSets(perDomain));
        } else {
            fofc = pooledCovariance != null
                    ? new Fofc(new CorrelationMatrix(pooledCovariance), alpha, ess)
                    : new Fofc(dataSet, alpha, ess);
        }

        fofc.setVerbose(verbose);

        Map<List<Integer>, Integer> clusters = fofc.findClusters();
        List<List<Integer>> blocks = new ArrayList<>(clusters.keySet());
        List<Integer> ranks = new ArrayList<>();
        for (List<Integer> block : blocks) {
            ranks.add(clusters.get(block));
        }

        BlocksUtil.validateBlocks(blocks, anchor);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        BlockSpec spec = BlocksUtil.toSpec(blocks, ranks, anchor);
        spec = BlocksUtil.applySingleClusterPolicy(spec, policy, alpha);
        return spec;
    }
}
