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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Bpc;

import java.util.List;

/**
 * The {@code BpcBlockDiscoverer} class is an implementation of the {@code BlockDiscoverer} interface, designed to
 * discover clusters or "blocks" of indices within a dataset using the BPC (Bayesian Partitioning for Causal Discovery)
 * algorithm. This class leverages statistical testing and clustering policies to identify and refine meaningful
 * groupings of variables.
 * <p>
 * The discovery process involves utilizing BPC with a specified statistical test, significance threshold, and
 * equivalent sample size to generate initial clusters. These clusters are then adjusted and validated according to
 * predefined policies and canonicalization techniques.
 * <p>
 * Core functionality: - Discovers blocks of indices from a dataset using the BPC algorithm. - Validates and
 * canonicalizes the resulting clusters to ensure consistency and correctness. - Applies a single-cluster policy to
 * manage merging or refinement of blocks based on the given policy.
 * <p>
 * Constructor parameters: - {@code dataSet}: The data on which block discovery is performed. - {@code ntadTest}: The
 * statistical test used by the BPC algorithm. - {@code alpha}: The significance threshold for the statistical test in
 * BPC. - {@code ess}: The equivalent sample size parameter used in the BPC algorithm. - {@code policy}: The policy
 * applied to adjust or refine single clusters during block discovery.
 */
public class BpcBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final double alpha;
    private final int ess;
    private final SingleClusterPolicy policy;
    private final boolean verbose;

    /**
     * Constructor for the {@code BpcBlockDiscoverer} class, responsible for initiating the discovery of clusters or
     * blocks of indices within a dataset using the BPC (Bayesian Partitioning for Causal Discovery) algorithm.
     *
     * @param dataSet the dataset on which block discovery is performed
     * @param alpha   the significance threshold for the statistical test in BPC
     * @param ess     the equivalent sample size parameter used in the BPC algorithm
     * @param policy  the policy applied to adjust or refine single clusters during block discovery
     */
    public BpcBlockDiscoverer(DataSet dataSet, double alpha, int ess, SingleClusterPolicy policy, boolean verbose) {
        this.dataSet = dataSet;
        this.alpha = alpha;
        this.ess = ess;
        this.policy = policy;
        this.verbose = verbose;
    }

    /**
     * Discovers and returns the specification of blocks (clusters of indices) within the dataset using the Bayesian
     * Partitioning for Causal Discovery (BPC) algorithm.
     * <p>
     * The method: - Applies the BPC algorithm to find initial clusters based on the provided dataset, statistical test,
     * significance threshold, and equivalent sample size. - Canonicalizes the discovered clusters for consistency. -
     * Refines the clusters using the defined single-cluster policy. - Converts the resulting clusters into a BlockSpec
     * object for further use.
     *
     * @return the discovered block specification, which encapsulates the refined and canonicalized cluster structure
     */
    @Override
    public BlockSpec discover() {
        Bpc bpc = new Bpc(new CovarianceMatrix(dataSet), alpha, ess);
        bpc.setVerbose(verbose);
        List<List<Integer>> blocks = bpc.getClusters();
        blocks = BlocksUtil.canonicalizeBlocks(blocks);

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        BlockSpec spec = BlocksUtil.toSpec(blocks, dataSet);
        spec = BlocksUtil.applySingleClusterPolicy(spec, policy, alpha);
        return spec;
    }
}

