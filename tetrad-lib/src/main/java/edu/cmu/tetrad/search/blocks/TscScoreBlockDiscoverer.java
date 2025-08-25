package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Tsc;

import java.util.*;

/**
 * The TscScoreBlockDiscoverer class implements the BlockDiscoverer interface and provides functionality to discover
 * blocks or clusters of indices using the TSC scoring algorithm. It processes a dataset with specified parameters and
 * includes mechanisms for block validation, canonicalization, and policy-specific adjustments.
 * <p>
 * This class uses the TSC scoring framework to calculate clusters based on correlations and penalties, taking into
 * account domain-specific parameters such as alpha, EBIC gamma, ridge regularization, penalty discounting, and expected
 * sample size (ESS). The discovered blocks are processed to meet validation criteria and tailored to any specified
 * single-cluster policies.
 * <p>
 * Key features of this class: - Integration with the TSC scoring algorithm to compute clusters based on data and
 * scoring parameters. - Validation of resulting clusters to ensure they adhere to predefined correctness criteria. -
 * Canonicalization of blocks to maintain consistency and avoid redundant clusters. - Enforcement of single-cluster
 * policies, where applicable, to handle overlapping or conflicting clusters. - Returns a BlockSpec instance
 * encapsulating the final discovered clusters and related metadata.
 */
public class TscScoreBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final double alpha;
    private final double ebicGamma;
    private final double ridge;
    private final double penaltyDiscount;
    private final int ess;
    private final SingleClusterPolicy policy;

    /**
     * Initializes a new instance of the TscScoreBlockDiscoverer with the specified parameters.
     *
     * @param dataSet         The dataset to be used for block discovery.
     * @param alpha           The significance level for hypothesis testing.
     * @param ebicGamma       The gamma parameter for the Extended Bayesian Information Criterion (EBIC).
     * @param ridge           The ridge regularization parameter.
     * @param penaltyDiscount The discount factor for penalizing complexity.
     * @param ess             The equivalent sample size for scoring methods.
     * @param policy          The policy determining the handling of single clusters.
     */
    public TscScoreBlockDiscoverer(DataSet dataSet, double alpha, double ebicGamma, double ridge, double penaltyDiscount,
                                   int ess, SingleClusterPolicy policy) {
        this.dataSet = dataSet;
        this.alpha = alpha;
        this.ebicGamma = ebicGamma;
        this.ridge = ridge;
        this.penaltyDiscount = penaltyDiscount;
        this.ess = ess;
        this.policy = policy;
    }

    /**
     * Discovers and returns a block specification for the given dataset based on TscScoreBlockDiscoverer's configured
     * parameters. This method performs validation of input values and utilizes the Tsc algorithm to identify clusters
     * within the dataset. The identified clusters are then processed and adjusted according to the configured
     * policies.
     *
     * @return A BlockSpec representing the discovered clusters, with all blocks validated, canonicalized, and adjusted
     * based on the specified single cluster policy.
     * @throws IllegalArgumentException If any parameter violates the expected constraints: - `alpha` must be in the
     *                                  range (0, 1). - `ebicGamma` must be ≥ 0. - `ridge` must be ≥ 0. -
     *                                  `penaltyDiscount` must be &gt; 0.
     */
    @Override
    public BlockSpec discover() {
        // sanity checks (choose clamp-or-throw style you prefer)
        if (!(alpha > 0 && alpha < 1)) throw new IllegalArgumentException("alpha must be in (0,1)");
        if (ebicGamma < 0) throw new IllegalArgumentException("ebicGamma must be ≥ 0");
        if (ridge < 0) throw new IllegalArgumentException("ridge must be ≥ 0");
        if (penaltyDiscount <= 0) throw new IllegalArgumentException("penaltyDiscount must be > 0");

        Tsc tsc = new Tsc(dataSet.getVariables(), new CorrelationMatrix(dataSet));
        tsc.setAlpha(alpha);
        tsc.setEbicGamma(ebicGamma);
        tsc.setRidge(ridge);
        tsc.setPenaltyDiscount(penaltyDiscount);
        tsc.setExpectedSampleSize(ess);
        tsc.setMode(Tsc.Mode.Scoring);

        Map<Set<Integer>, Integer> clusters = tsc.findClusters();

        List<List<Integer>> blocks = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();

        for (Set<Integer> block : clusters.keySet()) {
            List<Integer> blockList = new ArrayList<>(block);
            Collections.sort(blockList);
            blocks.add(blockList);
            ranks.add(clusters.get(block));
        }

        // OK for blocks to be empty; just ensure indices are sane and canonicalize.
        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        blocks = BlocksUtil.applySingleClusterPolicy(policy, blocks, dataSet);

        return BlocksUtil.toSpec(blocks, ranks, dataSet);
    }
}