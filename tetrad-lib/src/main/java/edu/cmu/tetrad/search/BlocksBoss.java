package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.score.BlocksBicScore;

import java.util.*;

/**
 * The BlocksBoss class runs TSC to obtain causal clusters ane names for latents for those clusters and then runs BOSS
 * over these clusters and any unclustered variables (as singleton clusters) to produce a causal graph over the
 * clusters.
 * <p>
 * In the future, other algorithms than TSC may be used to obtain the causal clusters.
 * <p>
 * Notably, we tried PC here but for some cases it led to problematic exceptions or slow behavior, so we went with
 * BOSS.
 *
 * @author josephramsey
 */
public class BlocksBoss implements IGraphSearch {
    /**
     * Represents the dataset to be utilized by the BlocksBoss algorithm. This dataset contains the data necessary for
     * conducting the search and performing associated computations within the algorithm.
     */
    private final DataSet dataSet;
    /**
     * A significance threshold for the rank test for finding clusters.
     */
    private double alpha = 0.01;
    /**
     * A depth parameter for "weeding out" unwanted clusters from the graph.
     */
    private int depth = 2;
    /**
     * The effective sample size to use.
     */
    private int effectiveSampleSize;
    /**
     * The penanalty discount to use for the BOSS step.
     */
    private double penaltyDiscount = 2;
    /**
     * The number of random restarts to use for the BOSS step.
     */
    private int numStarts = 1;
    /**
     * Whether to give verbose output.
     */
    private boolean verbose = false;
    /**
     * A small non-negative value used as a ridge regularization parameter. It is added to the diagonal of correlation
     * matrices to address issues such as singularity or near-singularity. This helps ensure numerical stability during
     * computations. A typical default value is used, but it can be adjusted depending on the application needs.
     */
    private double ridge = 1e-8;

    /**
     * Constructor,
     *
     * @param dataSet             The dataset. This should include at least all of the variables that are to be
     *                            clustered by TSC.
     * @param effectiveSampleSize The effective sample size to use.
     */
    public BlocksBoss(DataSet dataSet, int effectiveSampleSize) {
        this.dataSet = dataSet;
        this.effectiveSampleSize = effectiveSampleSize;
    }

    /**
     * Performs the search.
     *
     * @return The graph over the clusters, including any singleton (unclustered) variables. The graph will include
     * latents for each cluster using names obtained from TSC.
     * @throws InterruptedException If any.
     */
    @Override
    public Graph search() throws InterruptedException {
        // (rename for clarity)
        CorrelationMatrix corr = new CorrelationMatrix(dataSet);

        TrekSeparationClusters tsc = new TrekSeparationClusters(
                corr.getVariables(),
                corr,
                effectiveSampleSize == -1 ? corr.getSampleSize() : effectiveSampleSize
        );
        tsc.setVerbose(verbose);
        tsc.setDepth(depth);
        tsc.setAlpha(alpha);
        tsc.setIncludeStructureModel(false);

        tsc.search(new int[][]{{2, 1}}, TrekSeparationClusters.Mode.METALOOP);

        List<List<Integer>> clusters = tsc.getClusters();
        List<String> latentNames = tsc.getLatentNames();

        // Build singleton set
        Set<Integer> clustered = new HashSet<>();
        for (List<Integer> c : clusters) clustered.addAll(c);
        List<Integer> singletons = new ArrayList<>();
        for (int j = 0; j < dataSet.getNumColumns(); j++) if (!clustered.contains(j)) singletons.add(j);

        // Build blocks + block-level variables (singletons first, then clusters)
        List<List<Integer>> blocks = new ArrayList<>();
        List<Node> metaVars = new ArrayList<>();

        for (int s : singletons) {
            blocks.add(Collections.singletonList(s));
            metaVars.add(dataSet.getVariable(s)); // keep identity for singletons
        }
        for (int i = 0; i < clusters.size(); i++) {
            blocks.add(clusters.get(i));
            metaVars.add(new ContinuousVariable(latentNames.get(i)));
        }

        // Score & search (be sure this is the fixed BlocksBicScore)
        BlocksBicScore score = new BlocksBicScore(dataSet, blocks, metaVars);
        score.setPenaltyDiscount(penaltyDiscount);
        // optional knobs
        score.setRidge(ridge);
        // score.setCondThreshold(...);

        Boss suborderSearch = new Boss(score);
        suborderSearch.setVerbose(verbose);
        suborderSearch.setNumStarts(numStarts);

        PermutationSearch permutationSearch = new PermutationSearch(suborderSearch);
        Graph cpdag = permutationSearch.search();

        // Add latent→member edges for true clusters
        for (int i = 0; i < blocks.size(); i++) {
            List<Integer> block = blocks.get(i);
            Node meta = metaVars.get(i);

            if (block.size() > 1) {
                meta.setNodeType(NodeType.LATENT);
                for (int col : block) {
                    Node child = dataSet.getVariable(col);
                    if (!cpdag.containsNode(child)) cpdag.addNode(child);
                    if (!cpdag.isAdjacentTo(meta, child)) cpdag.addDirectedEdge(meta, child);
                }
            }
        }

        return cpdag;
    }

    /**
     * Sets the alpha parameter, which is used as a significance level for the rank tests for TSC.
     *
     * @param alpha the significance level or threshold value to be set, must be in [0, 1].
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be between 0.0 and 1.0");
        }
        this.alpha = alpha;
    }

    /**
     * The 'depth', here being used to guide the sizes of variable sets employed for determining whether in-cluster
     * tests fail. This is used to help weed out unwanted clusters.
     *
     * @param depth This depth. A value of -1 indicates unlimited depth.
     */
    public void setDepth(int depth) {
        if (!(depth == -1 || depth >= 0)) {
            throw new IllegalArgumentException("depth must be non-negative or -1");
        }
        this.depth = depth;
    }

    /**
     * Whether verbose output should be generated.
     *
     * @param verbose Whether verbose output should be generated.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the effective sample size to be used for statistical computations or analysis.
     *
     * @param effectiveSampleSize The effective sample size to set. This should be a non-negative integer representing
     *                            the number of samples effectively contributing to the analysis. A value of -1 is used
     *                            to indicate that the sample size of the data is to be used.
     */
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        if (effectiveSampleSize < -1 || effectiveSampleSize == 0) {
            throw new IllegalArgumentException("effectiveSampleSize must be -1 (auto) or > 0");
        }
        this.effectiveSampleSize = effectiveSampleSize;
    }

    /**
     * Sets the penalty discount used for the BOSS step.
     *
     * @param penaltyDiscount The penalty discount value to set. This should be a non-negative double, representing the
     *                        discount factor to apply for penalties.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Sets the number of starts for the BOSS step. This parameter determines the number of times BOSS will be
     * initialized with different starting conditions to search for potentially better solutions.
     *
     * @param numStarts The number of starts to set. This must be a non-negative integer representing how many
     *                  independent initializations the algorithm should run.
     */
    public void setNumStarts(int numStarts) {
        if (numStarts < 1) {
            throw new IllegalArgumentException("numStarts must be > 0");
        }
        this.numStarts = numStarts;
    }

    /**
     * Sets the value to use for the ridge. This should be a small non-negative number and is added to the diagonal of
     * correlation matrices to avoid problems with singularity. A value of 0 means no ridge.
     *
     * @param ridge This small non-negative value.
     */
    public void setRidge(double ridge) {
        if (ridge < 0.0) {
            throw new IllegalArgumentException("ridge must be >= 0");
        }
        this.ridge = ridge;
    }
}
