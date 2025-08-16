package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.score.BlocksBicScore;
import edu.cmu.tetrad.search.test.IndTestBlocksWilksRankCachedTS;

import java.util.*;

/**
 * Runs TSC to find clusters and then uses these as blocks of variables, together possibly with unclustered variables
 * considered as singleton blocks, to infer a graph over the blocks. The default is to use PC for the metagraph; BOSS is
 * also offered as an options.
 *
 * @author josephramsey
 */
public class TscPc implements IGraphSearch {
    /**
     * The raw unclustered dataset to use.
     */
    private final DataSet dataSet;
    /**
     * A significance threshold for the rank test for finding clusters. This will be used both for clustering and for
     * the PC search.
     */
    private double alpha = 0.01;
    /**
     * A depth of the PC search.
     */
    private int depth = 2;
    /**
     * The effective sample size to use.
     */
    private int effectiveSampleSize;
    /**
     * The penalty discount to use for the BOSS option.
     */
    private double penaltyDiscount = 2;
    /**
     * The number of random restarts to use for the BOSS option.
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
     * EBIC gamma for the BOSS option.
     */
    private double ebicGamma = 0;
    /**
     * Whether to use the BOSS option instead of PC.
     */
    private boolean useBoss = false;

    /**
     * Constructor,
     *
     * @param dataSet The dataset. This should include at least all of the variables that are to be clustered by TSC.
     */
    public TscPc(DataSet dataSet) {
        this.dataSet = dataSet;
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

        Graph cpdag;

        if (useBoss) {
            // Score & search (be sure this is the fixed BlocksBicScore)
            BlocksBicScore score = new BlocksBicScore(dataSet, blocks, metaVars);
            score.setPenaltyDiscount(penaltyDiscount);
            score.setRidge(ridge);
            score.setEbicGamma(ebicGamma);

            Boss suborderSearch = new Boss(score);
            suborderSearch.setVerbose(verbose);
            suborderSearch.setNumStarts(numStarts);

            PermutationSearch permutationSearch = new PermutationSearch(suborderSearch);
            cpdag = permutationSearch.search();
        } else {
            IndTestBlocksWilksRankCachedTS test = new IndTestBlocksWilksRankCachedTS(dataSet, blocks, metaVars);
            test.setAlpha(alpha);

            Pc pc = new Pc(test);
            pc.setDepth(depth);
            cpdag = pc.search();
        }

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

    /**
     * Sets the gamma parameter for the EBIC (Extended Bayesian Information Criterion). The EBIC gamma parameter
     * controls the penalty term for model complexity when performing model selection. This is used for the BOSS
     * option.
     *
     * @param ebicGamma The EBIC gamma value to set. This should be a non-negative double, typically in the range [0,
     *                  1], where 0 corresponds to the standard BIC and higher values increase the penalty for model
     *                  complexity.
     */
    public void setEbicGamma(double ebicGamma) {
        this.ebicGamma = ebicGamma;
    }

    /**
     * Configures whether to use the BOSS (Bayesian Optimization Structure Search) step in the algorithm.
     *
     * @param useBoss A boolean indicating whether the BOSS step should be used. If true, the BOSS step will be included
     *                in the algorithm; if false, it will be skipped.
     */
    public void setUseBoss(boolean useBoss) {
        this.useBoss = useBoss;
    }
}
