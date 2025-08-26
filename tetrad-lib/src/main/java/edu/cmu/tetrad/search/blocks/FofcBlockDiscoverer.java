package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Fofc;
import edu.cmu.tetrad.search.ntad_test.NtadTest;

import java.util.List;

/**
 * A concrete implementation of the {@code BlockDiscoverer} interface that discovers clusters or "blocks" of indices
 * using the FOFC algorithm. The discovered blocks are represented as a {@code BlockSpec} and include features such
 * as validation, canonicalization, and policy-based adjustments.
 *
 * This class leverages the FOFC algorithm to generate clusters by analyzing the provided dataset and utilizing a
 * statistical test with a specified confidence level and equivalent sample size. The discovered clusters can be further
 * refined based on policy configurations for handling overlapping or conflicting blocks.
 */
public class FofcBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final NtadTest ntadTest;
    private final double alpha;
    private final int ess;
    private final SingleClusterPolicy policy;

    /**
     * Constructs a new instance of {@code FofcBlockDiscoverer}, which is used to discover clusters or "blocks"
     * of indices in a dataset based on the FOFC algorithm. The discovered blocks are adjusted and refined
     * according to the specified configuration parameters.
     *
     * @param dataSet the dataset to be analyzed for block discovery.
     * @param ntadTest the statistical test used for conditional independence testing within the FOFC algorithm.
     * @param alpha the significance level used in the statistical test to determine independence.
     * @param ess the equivalent sample size parameter used in Bayesian methods within the FOFC algorithm.
     * @param policy the policy to handle scenarios involving overlapping or conflicting blocks.
     */
    public FofcBlockDiscoverer(DataSet dataSet, NtadTest ntadTest, double alpha, int ess,
                               SingleClusterPolicy policy) {
        this.dataSet = dataSet;
        this.ntadTest = ntadTest;
        this.alpha = alpha;
        this.ess = ess;
        this.policy = policy;
    }

    /**
     * Discovers clusters or "blocks" of indices in the dataset using the FOFC algorithm. The method performs
     * block validation, canonicalization, and policy-based adjustments to refine the discovered clusters.
     *
     * @return a {@code BlockSpec} object representing the discovered and refined set of blocks.
     */
    @Override
    public BlockSpec discover() {
        Fofc fofc = new Fofc(dataSet, ntadTest, alpha);
        List<List<Integer>> blocks = fofc.findClusters();

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        BlockSpec spec = BlocksUtil.toSpec(blocks, dataSet);
        spec = BlocksUtil.applySingleClusterPolicy(spec, policy, alpha);
        return spec;
    }
}