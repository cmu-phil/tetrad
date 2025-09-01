package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Tsc;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

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
    private final boolean allowTriviallySizedClusters;

    /**
     * Constructs an instance of {@code TscTestBlockDiscoverer}, which discovers blocks of variables based on the TSC
     * (Testing Strong Causal structures) algorithm.
     *
     * @param dataSet The dataset containing the variables to be clustered based on causal structure.
     * @param alpha   The statistical significance level used for hypothesis tests, must be in the range (0, 1).
     * @param ess     The expected sample size used for clustering and scoring computations.
     * @param policy  The policy determining how to handle cases where multiple clusters overlap or conflict.
     */
    public TscTestBlockDiscoverer(DataSet dataSet, double alpha, int ess, double ridge, int rMax,
                                  SingleClusterPolicy policy, boolean allowTriviallySizedClusters, boolean verbose) {
        this.dataSet = dataSet;
        this.alpha = alpha;
        this.ess = ess;
        this.rMax = rMax;
        this.policy = policy;
        this.allowTriviallySizedClusters = allowTriviallySizedClusters;
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
        tsc.setExpectedSampleSize(ess);
        tsc.setRmax(rMax);
        tsc.setAllowTriviallySizedClusters(allowTriviallySizedClusters);
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