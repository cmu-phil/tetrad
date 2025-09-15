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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Gffc;

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
public class GffcBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final double alpha;
    private final int ess;
    private final SingleClusterPolicy policy;
    private final boolean verbose;
    private int rMax = 2;

    /**
     * Constructs a new instance of {@code FofcBlockDiscoverer}, which is used to discover clusters or "blocks" of
     * indices in a dataset based on the FOFC algorithm. The discovered blocks are adjusted and refined according to the
     * specified configuration parameters.
     *
     * @param dataSet the dataset to be analyzed for block discovery.
     * @param alpha   the significance level used in the statistical test to determine independence.
     * @param ess     the equivalent sample size parameter used in Bayesian methods within the FOFC algorithm.
     * @param rMax    the maximum rank of clusters to be considered.
     * @param policy  the policy to handle scenarios involving overlapping or conflicting blocks.
     */
    public GffcBlockDiscoverer(DataSet dataSet, double alpha, int ess,
                               int rMax, SingleClusterPolicy policy, boolean verbose) {
        this.dataSet = dataSet;
        this.alpha = alpha;
        this.ess = ess;
        this.rMax = rMax;
        this.policy = policy;
        this.verbose = verbose;
    }

    /**
     * Discovers clusters or "blocks" of indices in the dataset using the FOFC algorithm. The method performs block
     * validation, canonicalization, and policy-based adjustments to refine the discovered clusters.
     *
     * @return a {@code BlockSpec} object representing the discovered and refined set of blocks.
     */
    @Override
    public BlockSpec discover() {
        Gffc gffc = new Gffc(dataSet, alpha, rMax, ess);
        gffc.setVerbose(verbose);

        Map<List<Integer>, Integer> clusters = gffc.findClusters();
        List<List<Integer>> blocks = new ArrayList<>(clusters.keySet());
        List<Integer> ranks = new ArrayList<>();
        for (List<Integer> block : blocks) {
            ranks.add(clusters.get(block));
        }

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        BlockSpec spec = BlocksUtil.toSpec(blocks, ranks, dataSet);
        spec = BlocksUtil.applySingleClusterPolicy(spec, policy, alpha);
        return spec;
    }
}
