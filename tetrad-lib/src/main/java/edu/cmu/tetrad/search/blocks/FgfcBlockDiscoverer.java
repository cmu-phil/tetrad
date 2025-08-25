package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Fgfc;

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
public class FgfcBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final double alpha;
    private final int ess;
    private final SingleClusterPolicy policy;

    /**
     * Constructs a new instance of {@code FofcBlockDiscoverer}, which is used to discover clusters or "blocks" of
     * indices in a dataset based on the FOFC algorithm. The discovered blocks are adjusted and refined according to the
     * specified configuration parameters.
     *
     * @param dataSet the dataset to be analyzed for block discovery.
     * @param alpha   the significance level used in the statistical test to determine independence.
     * @param ess     the equivalent sample size parameter used in Bayesian methods within the FOFC algorithm.
     * @param policy  the policy to handle scenarios involving overlapping or conflicting blocks.
     */
    public FgfcBlockDiscoverer(DataSet dataSet, double alpha, int ess,
                               SingleClusterPolicy policy) {
        this.dataSet = dataSet;
        this.alpha = alpha;
        this.ess = ess;
        this.policy = policy;
    }

    /**
     * Discovers clusters or "blocks" of indices in the dataset using the FOFC algorithm. The method performs block
     * validation, canonicalization, and policy-based adjustments to refine the discovered clusters.
     *
     * @return a {@code BlockSpec} object representing the discovered and refined set of blocks.
     */
    @Override
    public BlockSpec discover() {
        Fgfc fgfc = new Fgfc(dataSet, alpha);

        Map<List<Integer>, Integer> clusters = fgfc.findClusters();
        List<List<Integer>> blocks = new ArrayList<>(clusters.keySet());
        List<Integer> ranks = new ArrayList<>();
        for (List<Integer> block : blocks) {
            ranks.add(clusters.get(block));
        }

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        blocks = BlocksUtil.applySingleClusterPolicy(policy, blocks, dataSet);

        return BlocksUtil.toSpec(blocks, ranks, dataSet);
    }
}