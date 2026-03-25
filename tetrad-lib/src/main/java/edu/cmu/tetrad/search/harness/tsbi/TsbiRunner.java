///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsbi;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.test.IndTestBlocksTs;

import java.util.*;

/**
 * Runs the PC algorithm with the TSBI (Trek-Separation Block Independence) test
 * as its conditional independence oracle, given a pre-determined block clustering.
 *
 * <p>This runner is designed for the TSBI simulation study, where the true
 * cluster partition is supplied directly so that structural-search performance
 * can be evaluated in isolation from cluster-recovery performance.  Each call
 * to {@link #run} builds a {@link BlockSpec} from the supplied clusters, wraps
 * it in an {@link IndTestBlocksTs} instance, and invokes PC to search for the
 * structural graph over the latent factor nodes.
 *
 * <p>Block variables are named after the corresponding true latent group leaders
 * (supplied as {@code trueLatentLeaders}), so that the recovered graph can be
 * compared directly to the true structural subgraph of the MIM by node name.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   List<Set<Node>>  trueClusters      = TrueClusterExtractor.extractClusters(mim);
 *   List<Node>       trueLeaders       = TrueClusterExtractor.extractLatentLeaders(mim);
 *   Graph            recoveredStructure = new TsbiRunner(0.01, n).run(data, trueClusters, trueLeaders);
 * }</pre>
 *
 * @author josephramsey
 * @see IndTestBlocksTs
 * @see edu.cmu.tetrad.search.blocks.BlockSpec
 */
public final class TsbiRunner {

    private final double alpha;
    private final int    effectiveSampleSize;

    /**
     * Creates a TSBI runner with the given settings.
     *
     * @param alpha               significance level for the Wilks rank test (e.g. 0.01).
     * @param effectiveSampleSize effective sample size passed to the Wilks test.
     *                            Equal to the nominal sample size for i.i.d. data.
     */
    public TsbiRunner(double alpha, int effectiveSampleSize) {
        if (alpha <= 0 || alpha >= 1)
            throw new IllegalArgumentException("alpha must be in (0, 1).");
        if (effectiveSampleSize < 1)
            throw new IllegalArgumentException("effectiveSampleSize must be >= 1.");
        this.alpha               = alpha;
        this.effectiveSampleSize = effectiveSampleSize;
    }

    /**
     * Runs PC with TSBI given a dataset and a pre-determined clustering.
     *
     * <p>The {@code trueLatentLeaders} list must be in the same order as
     * {@code trueClusters}: the i-th leader corresponds to the i-th cluster.
     * Block variables created internally are named after the leader nodes so
     * that the returned graph can be matched to the true structural graph by
     * name without additional bookkeeping.
     *
     * @param data               simulated dataset; observed variables only.
     * @param trueClusters       true cluster partition of the observed variables.
     *                           Each set contains the indicator nodes of one
     *                           latent group.
     * @param trueLatentLeaders  one latent node per group (the primary latent for
     *                           rank-1 models), in the same order as
     *                           {@code trueClusters}.
     * @return the structural graph over block-variable nodes as returned by PC;
     *         may contain both directed and undirected edges (a CPDAG).
     * @throws IllegalArgumentException if arguments are null or sizes do not match.
     * @throws InterruptedException
     */
    public Graph run(DataSet data,
                     List<Set<Node>> trueClusters,
                     List<Node> trueLatentLeaders) throws InterruptedException {

        if (data == null)
            throw new IllegalArgumentException("DataSet must not be null.");
        if (trueClusters == null || trueLatentLeaders == null)
            throw new IllegalArgumentException("Cluster lists must not be null.");
        if (trueClusters.size() != trueLatentLeaders.size())
            throw new IllegalArgumentException(
                    "trueClusters and trueLatentLeaders must have the same size.");

        // ---- Map observed variable names to column indices ----
        List<Node> dataVars = data.getVariables();
        Map<String, Integer> nameToIndex = new HashMap<>(dataVars.size() * 2);
        for (int i = 0; i < dataVars.size(); i++) {
            nameToIndex.put(dataVars.get(i).getName(), i);
        }

        // ---- Build BlockSpec lists ----
        List<List<Integer>> blocks    = new ArrayList<>(trueClusters.size());
        List<Node>          blockVars = new ArrayList<>(trueClusters.size());
        List<Integer>       ranks     = new ArrayList<>(trueClusters.size());

        for (int i = 0; i < trueClusters.size(); i++) {
            // Column indices for this cluster's indicators
            Set<Node> cluster = trueClusters.get(i);
            List<Integer> cols = new ArrayList<>(cluster.size());
            for (Node indicator : cluster) {
                Integer idx = nameToIndex.get(indicator.getName());
                if (idx == null) {
                    throw new IllegalArgumentException(
                            "Indicator node '" + indicator.getName()
                                    + "' not found in dataset variables.");
                }
                cols.add(idx);
            }
            Collections.sort(cols);  // deterministic ordering
            blocks.add(cols);

            // Block variable named after the true latent leader so the returned
            // graph can be compared to the true structural graph by name.
            Node leader = trueLatentLeaders.get(i);
            Node blockVar = new ContinuousVariable(leader.getName());
            blockVar.setNodeType(NodeType.LATENT);
            blockVars.add(blockVar);

            // Rank-1 for all clusters in this study.
            ranks.add(1);
        }

        // ---- Instantiate test and run PC ----
        BlockSpec spec = new BlockSpec(data, blocks, blockVars, ranks);

        IndTestBlocksTs test = new IndTestBlocksTs(spec);
        test.setAlpha(alpha);
        test.setEffectiveSampleSize(effectiveSampleSize);

        Pc pc = new Pc(test);
        pc.setVerbose(false);

        return pc.search();
    }

    /**
     * Returns the significance level used by this runner.
     *
     * @return alpha.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Returns the effective sample size used by this runner.
     *
     * @return effective sample size.
     */
    public int getEffectiveSampleSize() {
        return effectiveSampleSize;
    }
}
