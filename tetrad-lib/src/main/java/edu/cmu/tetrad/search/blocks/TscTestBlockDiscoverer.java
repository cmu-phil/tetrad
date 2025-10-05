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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Tsc;
import edu.cmu.tetrad.util.RankTests;

import java.util.*;

/**
 * The {@code TscTestBlockDiscoverer} class is an implementation of the {@code BlockDiscoverer} interface that utilizes
 * the Testing Strong Causal structures (TSC) algorithm to identify and discover clusters or blocks of variables from a
 * given dataset. These blocks represent groups of variables that exhibit strong causal relationships.
 * <p>
 * This class supports functionality to validate, canonicalize, and apply policies to discovered blocks, ensuring
 * consistency and compliance with specified single cluster policies. It operates using parameters such as statistical
 * significance level (alpha), expected sample size (ess), and a policy for handling overlapping clusters.
 */
public class TscTestBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final double alpha;
    private final int ess;
    private final SingleClusterPolicy policy;
    private final boolean verbose;
    private final int rMax;
    private final int minRedundancy;

    /**
     * Constructs a TscTestBlockDiscoverer instance with the specified parameters for discovering and analyzing blocks
     * of variables in a dataset using the TSC algorithm.
     *
     * @param dataSet       The dataset to process for discovering variable relationships.
     * @param alpha         A significance level parameter used for hypothesis testing, must be in the range (0, 1).
     * @param ess           The equivalent sample size used in certain scoring or prior adjustments.
     * @param ridge         A regularization parameter for ridge regression; must be greater than or equal to 0.
     * @param rMax          The maximum number of iterations or search radius used in the discovery process.
     * @param policy        The single cluster policy dictating the criteria for canonicalizing discovered clusters.
     * @param minRedundancy The minimum allowed redundancy level among variables in discovered clusters.
     * @param verbose       A flag indicating whether to output detailed logging or debugging information during
     *                      processing.
     * @throws IllegalArgumentException If the ridge parameter is less than 0.
     */
    public TscTestBlockDiscoverer(DataSet dataSet, double alpha, int ess, double ridge, int rMax,
                                  SingleClusterPolicy policy, int minRedundancy, boolean verbose) {
        this.dataSet = dataSet;
        this.alpha = alpha;
        this.ess = ess;
        this.rMax = rMax;
        this.policy = policy;
        this.minRedundancy = minRedundancy;
        this.verbose = verbose;

        if (ridge < 0) {
            throw new IllegalArgumentException("Ridge must be >= 0");
        }

        RankTests.RIDGE = ridge;
    }

    /**
     * Discovers and identifies blocks of variables using the TSC (Testing Strong Causal structures) algorithm.
     * <p>
     * This method applies the following steps: - Validates the alpha parameter to ensure it is within the range (0, 1).
     * - Constructs a TscScored instance to compute clusters of variables based on the given dataset. - Ensures
     * discovered clusters (blocks) are valid, canonicalized, and consistent with the specified single cluster policy. -
     * Converts the final block structure to the {@code BlockSpec} format for further processing or use.
     *
     * @return The discovered {@code BlockSpec} which represents clusters of variables based on the TSC algorithm.
     * @throws IllegalArgumentException If the alpha parameter is not in the range (0, 1).
     */
    @Override
    public BlockSpec discover() {
        if (!(alpha > 0 && alpha < 1)) {
            throw new IllegalArgumentException("alpha must be in (0,1)");
        }

        Tsc tsc = new Tsc(dataSet.getVariables(), new CorrelationMatrix(dataSet));
        tsc.setAlpha(alpha);
        tsc.setEffectiveSampleSize(ess);
        tsc.setRmax(rMax);
        tsc.setMinRedundancy(minRedundancy);
        tsc.setVerbose(verbose);

        Map<Set<Integer>, Integer> clusters = tsc.findClusters();

        List<List<Integer>> blocks = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();

        for (Set<Integer> block : clusters.keySet()) {
            List<Integer> blockList = new ArrayList<>(block);
            Collections.sort(blockList);
            blocks.add(blockList);
            ranks.add(clusters.get(block));
        }

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        BlockSpec spec = BlocksUtil.toSpec(blocks, ranks, dataSet);
        spec = BlocksUtil.applySingleClusterPolicy(spec, policy, alpha);
        return spec;
    }
}
