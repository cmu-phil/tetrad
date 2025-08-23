package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Tsc;

import java.util.List;

/**
 * The TscScoreBlockDiscoverer class implements the BlockDiscoverer interface and provides functionality to discover
 * blocks or clusters of indices using the TSC scoring algorithm. It processes a dataset with specified parameters and
 * includes mechanisms for block validation, canonicalization, and policy-specific adjustments.
 *
 * This class uses the TSC scoring framework to calculate clusters based on correlations and penalties, taking into
 * account domain-specific parameters such as alpha, EBIC gamma, ridge regularization, penalty discounting, and expected
 * sample size (ESS). The discovered blocks are processed to meet validation criteria and tailored to any specified
 * single-cluster policies.
 *
 * Key features of this class:
 * - Integration with the TSC scoring algorithm to compute clusters based on data and scoring parameters.
 * - Validation of resulting clusters to ensure they adhere to predefined correctness criteria.
 * - Canonicalization of blocks to maintain consistency and avoid redundant clusters.
 * - Enforcement of single-cluster policies, where applicable, to handle overlapping or conflicting clusters.
 * - Returns a BlockSpec instance encapsulating the final discovered clusters and related metadata.
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
     * @param dataSet The dataset to be used for block discovery.
     * @param alpha The significance level for hypothesis testing.
     * @param ebicGamma The gamma parameter for the Extended Bayesian Information Criterion (EBIC).
     * @param ridge The ridge regularization parameter.
     * @param penaltyDiscount The discount factor for penalizing complexity.
     * @param ess The equivalent sample size for scoring methods.
     * @param policy The policy determining the handling of single clusters.
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
     * Discovers and returns a block specification based on the given dataset and parameters.
     * The method applies a series of checks and processes, including validating input parameters,
     * scoring clusters, and ensuring blocks adhere to the specified policy.
     *
     * @return A {@code BlockSpec} object representing the discovered blocks.
     * @throws IllegalArgumentException if any of the input parameters are invalid, such as:
     *         {@code alpha} not in (0,1), {@code ebicGamma} < 0, {@code ridge} < 0,
     *         or {@code penaltyDiscount} <= 0.
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
        tsc.setIncludeAllNodes(true);
        tsc.setPenaltyDiscount(penaltyDiscount);
        tsc.setExpectedSampleSize(ess);
        tsc.setMode(Tsc.Mode.Scoring);

        List<List<Integer>> blocks = tsc.findClusters();

        // Defensive: empty result -> empty spec is fine; validate the rest.
        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        blocks = BlocksUtil.applySingleClusterPolicy(policy, blocks, dataSet);

        return BlocksUtil.toSpec(blocks, dataSet);
    }
}