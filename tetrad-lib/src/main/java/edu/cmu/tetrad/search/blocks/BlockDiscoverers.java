package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.ntad_test.NtadTest;

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
     * Creates a new instance of a {@code BpcBlockDiscoverer}, which is an implementation of the {@code BlockDiscoverer}
     * interface. This method utilizes the BPC algorithm to discover clusters or "blocks" of indices based on the
     * provided dataset and statistical criteria.
     * <p>
     * The BPC algorithm is designed to perform block discovery using specific parameters such as statistical
     * significance level and equivalent sample size, along with a policy for handling single-cluster constraints.
     *
     * @param data   the dataset on which the block discovery algorithm will operate
     * @param alpha  the significance level used in statistical tests, typically between 0 and 1
     * @param ess    the equivalent sample size, controlling the strength of prior information in scoring
     * @param policy the single-cluster policy applied to manage how individual clusters or blocks are processed
     * @return a {@code BpcBlockDiscoverer} instance configured with the specified parameters
     */
    public static BlockDiscoverer bpc(DataSet data, double alpha, int ess, SingleClusterPolicy policy,
                                      boolean verbose) {
        return new BpcBlockDiscoverer(data, alpha, ess, policy, verbose);
    }

    /**
     * Constructs a {@code FofcBlockDiscoverer} instance, which implements the FOFC algorithm for discovering clusters
     * or "blocks" of indices in a dataset. The FOFC algorithm uses statistical testing and configurable parameters to
     * identify meaningful groupings of variables within the data.
     *
     * @param data   the dataset on which the block discovery algorithm will operate
     * @param alpha  the significance level for the statistical tests, typically a value between 0 and 1
     * @param ess    the equivalent sample size, which determines the strength of prior information used in scoring
     * @param policy the policy for handling single-cluster constraints during block discovery
     * @return a {@code FofcBlockDiscoverer} instance configured with the specified parameters
     */
    public static BlockDiscoverer fofc(DataSet data, double alpha,
                                       int ess, SingleClusterPolicy policy, boolean verbose) {
        return new FofcBlockDiscoverer(data, alpha, ess, policy, verbose);
    }

    /**
     * Constructs an instance of {@code FtfcBlockDiscoverer}, which implements the FTFC algorithm for discovering
     * clusters or "blocks" of indices within a dataset. The FTFC algorithm utilizes statistical testing and
     * configurable parameters to identify meaningful groupings of variables.
     *
     * @param data   the dataset on which the block discovery algorithm will operate
     * @param alpha  the significance level used in statistical tests, typically a value between 0 and 1
     * @param ess    the equivalent sample size, controlling the strength of prior information in scoring
     * @param policy the single-cluster policy applied to manage how individual clusters or blocks are processed
     * @return a {@code FtfcBlockDiscoverer} instance configured with the specified parameters
     */
    public static BlockDiscoverer ftfc(DataSet data, double alpha,
                                       int ess, SingleClusterPolicy policy, boolean verbose) {
        return new FtfcBlockDiscoverer(data, alpha, ess, policy, verbose);
    }

    public static BlockDiscoverer gffc(DataSet data, double alpha,
                                       int ess, int rMax, SingleClusterPolicy policy, boolean verbose) {
        return new GffcBlockDiscoverer(data, alpha, ess, rMax, policy, verbose);
    }

    /**
     * Creates a new instance of a {@code TscTestBlockDiscoverer}, which is an implementation of the
     * {@code BlockDiscoverer} interface. This method uses the TSC algorithm to discover clusters or "blocks" of indices
     * in a dataset by applying statistical tests based on the specified parameters.
     *
     * @param data   the dataset on which the block discovery algorithm will operate
     * @param alpha  the significance level used in statistical tests, typically a value between 0 and 1
     * @param ess    the equivalent sample size, which determines the strength of prior information used in scoring
     * @param policy the single-cluster policy applied to handle constraints for managing individual clusters
     * @return a {@code TscTestBlockDiscoverer} instance configured with the specified parameters
     */
    public static BlockDiscoverer tscTest(DataSet data, double alpha, int ess, double ridge,
                                          int rMax, SingleClusterPolicy policy, boolean verbose) {
        return new TscTestBlockDiscoverer(data, alpha, ess, ridge, rMax, policy, verbose);
    }
}