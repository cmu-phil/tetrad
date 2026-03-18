///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see the GNU General Public License v3+.   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.Tsc;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.search.blocks.SingleClusterPolicy;
import edu.cmu.tetrad.search.score.BlocksBicScoreTrekSoft;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the shared TSC/BOSS-based measurement model used by BossTrekMimic-style searches.
 *
 * <p>This helper performs the common setup steps:
 * <ol>
 *     <li>Runs TSC to obtain clusters and ranks.</li>
 *     <li>Builds a {@link BlockSpec} and applies the single-cluster policy.</li>
 *     <li>Constructs a {@link BlocksBicScoreTrekSoft} over the block structure.</li>
 *     <li>Runs BOSS using that score to obtain the latent-to-latent skeleton.</li>
 *     <li>Adds latent -> indicator edges implied by the block specification.</li>
 *     <li>Computes the latent list, observed child set, and measured parent pool.</li>
 * </ol>
 * </p>
 *
 * <p>The result is returned as a {@link MeasurementBuildResult} record, which is
 * identical in shape to the one produced by {@link TrekMeasurementModelBuilderPc}
 * so that downstream searchers can use either builder interchangeably.</p>
 *
 * <p>Expected use:
 * <pre>
 * TrekMeasurementModelBuilderBoss builder =
 *         new TrekMeasurementModelBuilderBoss(dataSet, parameters);
 * builder.setKnowledge(knowledge);
 * builder.setInputNames(inputNames);
 * builder.setOutputNames(outputNames);
 * builder.setVerbose(verbose);
 * TrekMeasurementModelBuilderBoss.MeasurementBuildResult result = builder.build();
 * </pre>
 * </p>
 *
 * @author josephramsey
 */
public final class TrekMeasurementModelBuilderBoss {

    /**
     * Data model (used by TSC for variable list and covariance).
     */
    private final DataModel dataModel;

    /**
     * Data set.
     */
    private final DataSet dataSet;

    /**
     * Parameters controlling TSC, the score, and BOSS.
     */
    private final Parameters parameters;

    /**
     * Optional background knowledge passed to BOSS.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Optional known input variable names.
     */
    private final Set<String> inputNames = new LinkedHashSet<>();

    /**
     * Optional known output variable names.
     */
    private final Set<String> outputNames = new LinkedHashSet<>();

    /**
     * Verbosity flag passed to BOSS.
     */
    private boolean verbose = false;

    /**
     * Constructs the builder.
     *
     * @param dataSet    the data set; must not be null
     * @param parameters the parameters; must not be null
     */
    public TrekMeasurementModelBuilderBoss(DataSet dataSet, Parameters parameters) {
        if (dataSet == null) {
            throw new NullPointerException("Data set must not be null.");
        }
        if (parameters == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        this.dataModel = dataSet;
        this.dataSet = dataSet;
        this.parameters = parameters;
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Builds the measurement model and all derived objects needed by downstream
     * parent-recovery stages.
     *
     * @return the build result
     * @throws InterruptedException if TSC or BOSS is interrupted
     */
    public MeasurementBuildResult build() throws InterruptedException {
        validateInputOutputKnowledge();

        BlockSpec spec  = buildBlockSpec();
        BlocksBicScoreTrekSoft score = buildScore(spec);
        Graph graph     = buildMeasurementGraph(score, spec);

        List<Node> latents          = new ArrayList<>(spec.blockVariables());
        List<Node> observedChildren = determineObservedChildren(
                graph, latents, dataSet.getVariables());
        List<Node> parentPool       = determineParentPool(
                dataSet.getVariables(), observedChildren);

        List<Node>   variables  = new ArrayList<>(dataSet.getVariables());
        SimpleMatrix matrix     = new CorrelationMatrix(dataSet)
                .getMatrix().getSimpleMatrix();
        int    sampleSize       = dataSet.getNumRows();
        double alpha            = parameters.getDouble(Params.ALPHA);

        return new MeasurementBuildResult(
                spec, graph, latents,
                observedChildren, parentPool,
                variables, matrix, sampleSize, alpha);
    }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    /**
     * Sets background knowledge forwarded to BOSS.
     *
     * @param knowledge the knowledge; must not be null
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets known input variable names.
     *
     * @param inputNames the input names
     */
    public void setInputNames(Collection<String> inputNames) {
        this.inputNames.clear();
        if (inputNames != null) {
            for (String name : inputNames) {
                if (name != null) this.inputNames.add(name);
            }
        }
        validateInputOutputKnowledge();
    }

    /**
     * Sets known output variable names.
     *
     * @param outputNames the output names
     */
    public void setOutputNames(Collection<String> outputNames) {
        this.outputNames.clear();
        if (outputNames != null) {
            for (String name : outputNames) {
                if (name != null) this.outputNames.add(name);
            }
        }
        validateInputOutputKnowledge();
    }

    /**
     * Sets verbose output forwarded to BOSS.
     *
     * @param verbose true if verbose
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // -------------------------------------------------------------------------
    // Private pipeline stages
    // -------------------------------------------------------------------------

    /**
     * Runs TSC and returns the canonicalised, single-cluster-policy-filtered
     * block specification.
     *
     * @return the block specification
     */
    private BlockSpec buildBlockSpec() {
        Tsc tsc = new Tsc(dataModel.getVariables(),
                new CovarianceMatrix(dataSet));
        tsc.setEffectiveSampleSize(
                parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        tsc.setRmax(3);
        tsc.setMinRedundancy(0);
        tsc.setAlpha(parameters.getDouble(Params.ALPHA));

        Map<Set<Integer>, Integer> clusters = tsc.findClusters();
        List<List<Integer>> blocks  = new ArrayList<>();
        List<Integer>       ranks   = new ArrayList<>();

        for (Set<Integer> block : clusters.keySet()) {
            List<Integer> blockList = new ArrayList<>(block);
            Collections.sort(blockList);
            blocks.add(blockList);
            ranks.add(clusters.get(block));
        }

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);

        BlockSpec spec = BlocksUtil.toSpec(blocks, ranks, dataSet);

        return BlocksUtil.applySingleClusterPolicy(
                spec,
                SingleClusterPolicy.EXCLUDE,
                parameters.getDouble(Params.ALPHA));
    }

    /**
     * Constructs a {@link BlocksBicScoreTrekSoft} from the block specification.
     * The penalty discount and trek penalty multiplier are drawn from parameters
     * when present; otherwise defaults are used.
     *
     * @param spec the block specification
     * @return the configured score
     */
    private BlocksBicScoreTrekSoft buildScore(BlockSpec spec) {
        BlocksBicScoreTrekSoft score = new BlocksBicScoreTrekSoft(spec);

        score.setPenaltyDiscount(
                parameters.getDouble(Params.PENALTY_DISCOUNT));

        // Trek penalty multiplier: how strongly the score biases the selected
        // canonical rank toward the trek-implied rank. Drawn from parameters
        // when available; falls back to the score's own default.
//        score.setTrekPenaltyMultiplier(0);
        score.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));

//        if (parameters.containsParameter(Params.TREK_PENALTY_MULTIPLIER)) {
//            score.setTrekPenaltyMultiplier(
//                    parameters.getDouble(Params.TREK_PENALTY_MULTIPLIER));
//        }
//
//        if (parameters.containsParameter(Params.EFFECTIVE_SAMPLE_SIZE)) {
//            score.setEffectiveSampleSize(
//                    parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
//        }

        return score;
    }

    /**
     * Runs BOSS with the supplied score to obtain the latent skeleton, then
     * adds latent -> indicator edges from the block specification.
     *
     * @param score the block BIC score
     * @param spec  the block specification
     * @return the measurement graph
     * @throws InterruptedException if BOSS is interrupted
     */
    private Graph buildMeasurementGraph(BlocksBicScoreTrekSoft score,
                                        BlockSpec spec)
            throws InterruptedException {

        Graph graph = runBoss(score);

        for (int i = 0; i < spec.blocks().size(); i++) {
            Node latent = spec.blockVariables().get(i);

            for (int j : spec.blocks().get(i)) {
                Node indicator = spec.dataSet().getVariables().get(j);

                if (!mayBeLatentChild(indicator)) {
                    continue;
                }

                graph.addNode(indicator);

                if (!graph.isParentOf(latent, indicator)) {
                    graph.addDirectedEdge(latent, indicator);
                }
            }
        }

        // GraphUtils.replaceNodes matches nodes already in the graph to the
        // dataset's canonical node objects by name. It does NOT add dataset
        // variables absent from the BOSS output (e.g., isolated variables).
        // The loop below is a correctness requirement: downstream stages look
        // up nodes by name and will silently miss any that were dropped.
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        for (Node node : dataSet.getVariables()) {
            if (graph.getNode(node.getName()) == null) {
                graph.addNode(node);
            }
        }

        return graph;
    }

    /**
     * Runs BOSS with the supplied score and background knowledge.
     *
     * <p>InterruptedException is propagated directly rather than being wrapped,
     * since {@link #build()} already declares it. This preserves the interrupt
     * signal for the calling thread.</p>
     *
     * @param score the score to use
     * @return the BOSS output graph
     * @throws InterruptedException if BOSS is interrupted
     */
    private Graph runBoss(BlocksBicScoreTrekSoft score)
            throws InterruptedException {
        PermutationSearch boss = new PermutationSearch(new Boss(score));
        boss.setKnowledge(this.knowledge);
        return boss.search();
    }

    // -------------------------------------------------------------------------
    // Node-classification helpers
    // -------------------------------------------------------------------------

    private boolean isKnownInput(Node node) {
        return node != null && this.inputNames.contains(node.getName());
    }

    private boolean isKnownOutput(Node node) {
        return node != null && this.outputNames.contains(node.getName());
    }

    /**
     * Returns true if the given observed node may be treated as a child of a
     * latent. Known inputs are never latent children; everything else may be.
     */
    private boolean mayBeLatentChild(Node node) {
        return !isKnownInput(node);
    }

    // -------------------------------------------------------------------------
    // Pool / child determination (identical logic to the PC builder)
    // -------------------------------------------------------------------------

    private List<Node> getObservedChildrenUnion(Graph graph,
                                                Collection<Node> latents) {
        LinkedHashSet<Node> children = new LinkedHashSet<>();
        for (Node latent : latents) {
            for (Node child : graph.getChildren(latent)) {
                if (child.getNodeType() != NodeType.LATENT) {
                    children.add(child);
                }
            }
        }
        return new ArrayList<>(children);
    }

    private List<Node> determineObservedChildren(Graph graph,
                                                 Collection<Node> latents,
                                                 List<Node> observedVariables) {
        LinkedHashMap<String, Node> byName = new LinkedHashMap<>();
        for (Node node : observedVariables) byName.put(node.getName(), node);

        LinkedHashSet<Node> children = new LinkedHashSet<>();

        for (Node child : getObservedChildrenUnion(graph, latents)) {
            Node resolved = byName.get(child.getName());
            if (resolved != null && !isKnownInput(resolved)) {
                children.add(resolved);
            }
        }

        for (String name : this.outputNames) {
            Node resolved = byName.get(name);
            if (resolved != null) children.add(resolved);
        }

        return new ArrayList<>(children);
    }

    private List<Node> determineParentPool(List<Node> observedVariables,
                                           Collection<Node> observedChildren) {
        Set<Node> childSet = new LinkedHashSet<>(observedChildren);
        List<Node> pool    = new ArrayList<>();

        for (Node node : observedVariables) {
            if (isKnownOutput(node)) continue;

            // Known inputs are always eligible parents even when the measurement
            // model placed them as latent children — they sit upstream by definition.
            // All other variables are eligible only if they are not latent children.
            if (isKnownInput(node) || !childSet.contains(node)) {
                pool.add(node);
            }
        }

        return pool;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validateInputOutputKnowledge() {
        Set<String> intersection = new LinkedHashSet<>(this.inputNames);
        intersection.retainAll(this.outputNames);

        if (!intersection.isEmpty()) {
            throw new IllegalArgumentException(
                    "The same variables cannot be declared as both inputs " +
                            "and outputs: " + intersection);
        }
    }

    // -------------------------------------------------------------------------
    // Result record (identical shape to TrekMeasurementModelBuilderPc)
    // -------------------------------------------------------------------------

    /**
     * Result of building the shared measurement model.
     *
     * @param spec             the block specification
     * @param graph            the measurement graph
     * @param latents          the latent variables
     * @param observedChildren the observed child variables
     * @param parentPool       the measured parent pool
     * @param variables        the measured variables in matrix order
     * @param matrix           the correlation matrix
     * @param sampleSize       the sample size
     * @param alpha            the alpha level
     */
    public record MeasurementBuildResult(
            BlockSpec spec,
            Graph graph,
            List<Node> latents,
            List<Node> observedChildren,
            List<Node> parentPool,
            List<Node> variables,
            SimpleMatrix matrix,
            int sampleSize,
            double alpha
    ) {}
}