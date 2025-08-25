package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Ftfc;
import edu.cmu.tetrad.search.ntad_test.NtadTest;

import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of the {@code BlockDiscoverer} interface that uses the FTFC (Fast Threshold-Free Clustering)
 * algorithm to identify clusters or "blocks" of variables within a dataset. This class integrates statistical tests and
 * policies to refine and validate the resulting blocks.
 * <p>
 * The {@code FtfcBlockDiscoverer} applies the FTFC algorithm in conjunction with statistical tests to analyze the
 * dataset and generate clusters. It ensures that the clusters conform to predefined constraints through validation and
 * optional canonicalization, while also respecting single-cluster policies to handle overlapping or conflicting
 * clusters.
 * <p>
 * Key features include: - Utilizing the FTFC algorithm to discover variable clusters based on data and the specified
 * parameters. - Validating the discovered blocks to ensure they meet structural and algorithmic criteria. -
 * Canonicalizing blocks to maintain consistency in representation. - Applying single-cluster policies to account for
 * dataset-specific constraints or complexities.
 */
public class FtfcBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final NtadTest ntadTest;
    private final double alpha;
    private final int ess;
    private final SingleClusterPolicy policy;

    /**
     * Constructs an instance of the {@code FtfcBlockDiscoverer} class to discover clusters or "blocks" of variables
     * within a given dataset using the FTFC (Fast Threshold-Free Clustering) algorithm.
     *
     * @param dataSet  the dataset to be analyzed for cluster discovery
     * @param ntadTest the statistical test applied to determine thresholds used in the FTFC algorithm
     * @param alpha    the significance level for the statistical test, influencing the clustering process
     * @param ess      the equivalent sample size parameter used in the FTFC algorithm
     * @param policy   the single-cluster policy applied to refine or resolve conflicts in discovered blocks
     */
    public FtfcBlockDiscoverer(DataSet dataSet, NtadTest ntadTest, double alpha, int ess, SingleClusterPolicy policy) {
        this.dataSet = dataSet;
        this.ntadTest = ntadTest;
        this.alpha = alpha;
        this.ess = ess;
        this.policy = policy;
    }

    /**
     * Discovers clusters or "blocks" of variables within the provided dataset using the FTFC (Fast Threshold-Free
     * Clustering) algorithm. The method processes the discovered blocks through validation, canonicalization, and
     * applies a single-cluster policy to refine the results.
     *
     * @return a BlockSpec object representing the discovered and processed blocks within the dataset
     */
    @Override
    public BlockSpec discover() {
        Ftfc ftfc = new Ftfc(dataSet, ntadTest, alpha);
        List<List<Integer>> blocks = ftfc.findClusters();

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        blocks = BlocksUtil.applySingleClusterPolicy(policy, blocks, dataSet);
        List<Integer> ranks = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            ranks.add(2);
        }
        return BlocksUtil.toSpec(blocks, ranks, dataSet);
    }
}
