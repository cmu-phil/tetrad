/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;

/**
 * A utility class providing static factory methods for creating instances of various {@code BlockDiscoverer}
 * implementations. Each implementation represents a specific algorithm for discovering clusters or "blocks" of indices
 * in a dataset, based on provided data, statistical criteria, and algorithm-specific parameters.
 * <p>
 * This class is immutable and cannot be instantiated.
 */
public final class BlockDiscoverers {

    private BlockDiscoverers() {
    }

    /**
     * Constructs a {@code BpcBlockDiscoverer} instance, which implements the BPC algorithm for discovering clusters
     * or "blocks" of indices in a dataset. The BPC algorithm utilizes statistical criteria and configurable parameters
     * to identify meaningful groupings of variables within the data.
     *
     * @param data    the dataset on which the block discovery algorithm will operate
     * @param alpha   the significance level for the statistical tests, typically a value between 0 and 1
     * @param ess     the equivalent sample size, determining the strength of prior information in scoring
     * @param policy  the single-cluster policy applied to manage how individual clusters or blocks are processed
     * @param verbose a flag indicating whether verbose output is enabled for debugging or detailed logs
     * @return a {@code BpcBlockDiscoverer} instance configured with the specified parameters
     */
    public static BlockDiscoverer bpc(DataSet data, double alpha, int ess, SingletonClusterPolicy policy, boolean verbose) {
        return new BpcBlockDiscoverer(data, alpha, ess, policy, verbose);
    }

    /**
     * Creates a new instance of a {@code FofcBlockDiscoverer}, which is an implementation of the {@code BlockDiscoverer}
     * interface. This method utilizes the FOFC algorithm to discover clusters or "blocks" of indices in a dataset
     * based on statistical criteria and additional configurable parameters.
     *
     * The FOFC algorithm is designed to identify meaningful groupings of variables or features in the data, using parameters
     * such as statistical significance level, equivalent sample size, and a policy for managing single-cluster constraints.
     *
     * @param data    the dataset on which the block discovery algorithm will operate
     * @param alpha   the significance level used in the statistical tests, typically a value between 0 and 1
     * @param ess     the equivalent sample size, controlling the strength of prior information in scoring
     * @param policy  the single-cluster policy applied to manage how individual clusters or blocks are processed
     * @param verbose a flag indicating whether verbose output is enabled for debugging or detailed logs
     * @return a {@code FofcBlockDiscoverer} instance configured with the specified parameters
     */
    public static BlockDiscoverer fofc(DataSet data, double alpha, int ess, SingletonClusterPolicy policy, boolean verbose) {
        return new FofcBlockDiscoverer(data, alpha, ess, policy, verbose);
    }

    /**
     * Constructs a {@code FtfcBlockDiscoverer} instance, which implements the FTFC algorithm for discovering clusters
     * or "blocks" of indices in a dataset. The FTFC algorithm uses statistical measures and configurable parameters
     * to identify meaningful groupings of variables within the data.
     *
     * @param data   the dataset on which the block discovery algorithm will operate
     * @param alpha  the significance level for the statistical tests, typically a value between 0 and 1
     * @param ess    the equivalent sample size, which determines the strength of prior information used in scoring
     * @param policy the single-cluster policy applied to manage how individual clusters or blocks are processed
     * @param verbose a flag indicating whether verbose output is enabled for debugging or detailed logs
     * @return a {@code FtfcBlockDiscoverer} instance configured with the specified parameters
     */
    public static BlockDiscoverer ftfc(DataSet data, double alpha, int ess, SingletonClusterPolicy policy, boolean verbose) {
        return new FtfcBlockDiscoverer(data, alpha, ess, policy, verbose);
    }

    /**
     * Constructs a {@code GffcBlockDiscoverer} instance, which implements the GFFC algorithm for discovering clusters
     * or "blocks" of indices in a dataset. The GFFC algorithm utilizes statistical measures, configurable parameters,
     * and constraints to identify meaningful groupings of variables within the data.
     *
     * @param data    the dataset on which the block discovery algorithm will operate
     * @param alpha   the significance level for the statistical tests, typically a value between 0 and 1
     * @param ess     the equivalent sample size, determining the strength of prior information in scoring
     * @param rMax    the maximum size of the clusters or blocks to consider during the discovery process
     * @param policy  the single-cluster policy applied to manage clusters or blocks during the process
     * @param verbose a flag indicating whether verbose output is enabled for debugging or detailed logs
     * @return a {@code GffcBlockDiscoverer} instance configured with the specified parameters
     */
    public static BlockDiscoverer gffc(DataSet data, double alpha, int ess, int rMax, SingletonClusterPolicy policy, boolean verbose) {
        return new GffcBlockDiscoverer(data, alpha, ess, rMax, policy, verbose);
    }

    /**
     * Creates a new instance of a {@code TscTestBlockDiscoverer}, which is an implementation of the
     * {@code BlockDiscoverer} interface. This method utilizes the TSC algorithm to discover clusters or "blocks" of
     * indices based on the provided dataset and statistical criteria.
     * <p>
     * The TSC algorithm is designed to perform block discovery using specific parameters such as statistical
     * significance level, equivalent sample size, a ridge regularization value, redundancy constraints, and a maximum
     * block size.
     *
     * @param data          the dataset on which the block discovery algorithm will operate
     * @param alpha         the significance level used in statistical tests, typically between 0 and 1
     * @param ess           the equivalent sample size, controlling the strength of prior information in scoring
     * @param ridge         the ridge regularization parameter used to stabilize covariance matrix calculations
     * @param rMax          the maximum size of the blocks to consider during the discovery process
     * @param policy        the single-cluster policy applied to manage how clusters or blocks are processed
     * @param minRedundancy the minimum redundancy threshold to ensure the quality of the discovered blocks
     * @param verbose       a flag indicating whether verbose output is enabled for debugging or detailed logs
     * @return a {@code TscTestBlockDiscoverer} instance configured with the specified parameters
     */
    public static BlockDiscoverer tsc(DataSet data, double alpha, int ess, double ridge, int rMax, SingletonClusterPolicy policy, int minRedundancy, boolean verbose) {
        return new TscTestBlockDiscoverer(data, alpha, ess, ridge, rMax, policy, minRedundancy, verbose);
    }

    // ----- Pooled-covariance variants: run the algorithm on a supplied covariance matrix (for example, the -----
    // ----- averaged covariance of several imputations; see BlocksUtil.pooledCovariance), with the           -----
    // ----- representative data set anchoring the resulting BlockSpec.                                       -----

    /**
     * BPC over a pooled covariance matrix; see {@link BpcBlockDiscoverer}.
     *
     * @param data    the representative data set for the resulting BlockSpec
     * @param pooled  the covariance matrix to run on, or null to compute it from the data set
     * @param alpha   the significance level
     * @param ess     the equivalent sample size
     * @param policy  the single-cluster policy
     * @param verbose whether verbose output is enabled
     * @return a {@code BpcBlockDiscoverer} over the pooled matrix
     */
    public static BlockDiscoverer bpc(DataSet data, CovarianceMatrix pooled, double alpha, int ess,
                                      SingletonClusterPolicy policy, boolean verbose) {
        return new BpcBlockDiscoverer(data, pooled, alpha, ess, policy, verbose);
    }

    /**
     * FOFC over a pooled covariance matrix; see {@link FofcBlockDiscoverer}.
     *
     * @param data    the representative data set for the resulting BlockSpec
     * @param pooled  the covariance matrix to run on, or null to compute it from the data set
     * @param alpha   the significance level
     * @param ess     the equivalent sample size
     * @param policy  the single-cluster policy
     * @param verbose whether verbose output is enabled
     * @return a {@code FofcBlockDiscoverer} over the pooled matrix
     */
    public static BlockDiscoverer fofc(DataSet data, CovarianceMatrix pooled, double alpha, int ess,
                                       SingletonClusterPolicy policy, boolean verbose) {
        return new FofcBlockDiscoverer(data, pooled, alpha, ess, policy, verbose);
    }

    /**
     * FTFC over a pooled covariance matrix; see {@link FtfcBlockDiscoverer}.
     *
     * @param data    the representative data set for the resulting BlockSpec
     * @param pooled  the covariance matrix to run on, or null to compute it from the data set
     * @param alpha   the significance level
     * @param ess     the equivalent sample size
     * @param policy  the single-cluster policy
     * @param verbose whether verbose output is enabled
     * @return a {@code FtfcBlockDiscoverer} over the pooled matrix
     */
    public static BlockDiscoverer ftfc(DataSet data, CovarianceMatrix pooled, double alpha, int ess,
                                       SingletonClusterPolicy policy, boolean verbose) {
        return new FtfcBlockDiscoverer(data, pooled, alpha, ess, policy, verbose);
    }

    /**
     * GFFC over a pooled covariance matrix; see {@link GffcBlockDiscoverer}.
     *
     * @param data    the representative data set for the resulting BlockSpec
     * @param pooled  the covariance matrix to run on, or null to compute it from the data set
     * @param alpha   the significance level
     * @param ess     the equivalent sample size
     * @param rMax    the maximum rank
     * @param policy  the single-cluster policy
     * @param verbose whether verbose output is enabled
     * @return a {@code GffcBlockDiscoverer} over the pooled matrix
     */
    public static BlockDiscoverer gffc(DataSet data, CovarianceMatrix pooled, double alpha, int ess, int rMax,
                                       SingletonClusterPolicy policy, boolean verbose) {
        return new GffcBlockDiscoverer(data, pooled, alpha, ess, rMax, policy, verbose);
    }

    /**
     * TSC over a pooled covariance matrix; see {@link TscTestBlockDiscoverer}.
     *
     * @param data          the representative data set for the resulting BlockSpec
     * @param pooled        the covariance matrix to run on, or null to compute it from the data set
     * @param alpha         the significance level
     * @param ess           the equivalent sample size
     * @param ridge         the ridge regularization parameter
     * @param rMax          the maximum rank
     * @param policy        the single-cluster policy
     * @param minRedundancy the minimum redundancy threshold
     * @param verbose       whether verbose output is enabled
     * @return a {@code TscTestBlockDiscoverer} over the pooled matrix
     */
    public static BlockDiscoverer tsc(DataSet data, CovarianceMatrix pooled, double alpha, int ess, double ridge,
                                      int rMax, SingletonClusterPolicy policy, int minRedundancy, boolean verbose) {
        return new TscTestBlockDiscoverer(data, pooled, alpha, ess, ridge, rMax, policy, minRedundancy, verbose);
    }
}
