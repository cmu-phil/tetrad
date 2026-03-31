///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.algcomparison.independence.BlocksIndTestTs;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.Tsc;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.search.blocks.SingletonClusterPolicy;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the shared PC/TSC-based measurement model used by TrekMimic-style searches.
 *
 * <p>This helper performs the common setup steps used by both TrekMimic and
 * BossTrekMimic:
 * </p>
 * <ol>
 *     <li>Runs TSC to obtain clusters and ranks.</li>
 *     <li>Builds a {@link BlockSpec} and applies the single-cluster policy.</li>
 *     <li>Runs PC using the trek/block test.</li>
 *     <li>Adds latent -> indicator edges implied by the block specification.</li>
 *     <li>Computes the latent list, observed child set, and measured parent pool.</li>
 * </ol>
 *
 * <p>The result is returned as a {@link MeasurementBuildResult} record.</p>
 *
 * @author josephramsey
 */
public final class TrekMeasurementModelBuilderPc {

    /**
     * Data set.
     */
    private final DataSet dataSet;

    /**
     * Parameters.
     */
    private final Parameters parameters;

    /**
     * Blocks-test wrapper.
     */
    private final BlocksIndTestTs test;

    /**
     * Optional knowledge.
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
     * PC depth.
     */
    private int depth = -1;

    /**
     * Verbosity.
     */
    private boolean verbose = false;

    /**
     * Constructs the builder.
     *
     * @param dataSet the data set
     * @param parameters the parameters
     */
    public TrekMeasurementModelBuilderPc(DataSet dataSet, Parameters parameters) {
        if (dataSet == null) {
            throw new NullPointerException("Data set must not be null.");
        }

        if (parameters == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        this.dataSet = dataSet;
        this.parameters = parameters;
        this.test = new BlocksIndTestTs();
    }

    /**
     * Builds the measurement model and associated derived objects.
     *
     * @return the build result
     * @throws InterruptedException if interrupted
     */
    public MeasurementBuildResult build() throws InterruptedException {
        validateInputOutputKnowledge();

        BlockSpec spec = buildBlockSpec();
        IndependenceTest indTest = buildBlocksTest(spec);
        Graph graph = buildMeasurementGraph(indTest, spec);

        List<Node> latents = new ArrayList<>(spec.blockVariables());
        List<Node> observedChildren = determineObservedChildren(graph, latents, dataSet.getVariables());
        List<Node> parentPool = determineParentPool(dataSet.getVariables(), observedChildren);

        List<Node> variables = new ArrayList<>(dataSet.getVariables());
        int sampleSize = dataSet.getNumRows();
        double alpha = parameters.getDouble(Params.ALPHA);

        return new MeasurementBuildResult(
                spec,
                graph,
                latents,
                observedChildren,
                parentPool,                variables,
                sampleSize,
                alpha
        );
    }

    /**
     * Sets the knowledge.
     *
     * @param knowledge the knowledge
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
                if (name != null) {
                    this.inputNames.add(name);
                }
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
                if (name != null) {
                    this.outputNames.add(name);
                }
            }
        }

        validateInputOutputKnowledge();
    }

    /**
     * Sets the PC depth.
     *
     * @param depth the depth
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets verbosity.
     *
     * @param verbose true if verbose
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Builds the block specification from TSC.
     *
     * @return the block specification
     */
    private BlockSpec buildBlockSpec() {
//        Tsc tsc = new Tsc(dataModel.getVariables(),
//                new CovarianceMatrix(dataSet));
        Tsc tsc = new Tsc(dataSet.getVariables(), dataSet);

        tsc.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        tsc.setRmin(1); // TODO - should this be configurable?
        tsc.setRmax(parameters.getInt(Params.MAX_RANK));
        tsc.setMinRedundancy(parameters.getInt(Params.TSC_MIN_REDUNDANCY));
        tsc.setAlpha(parameters.getDouble(Params.ALPHA));

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

        return BlocksUtil.applySingleClusterPolicy(
                spec,
                SingletonClusterPolicy.EXCLUDE,
                parameters.getDouble(Params.ALPHA)
        );
    }

    /**
     * Builds the trek/block independence test.
     *
     * @param spec the block specification
     * @return the independence test
     */
    private IndependenceTest buildBlocksTest(BlockSpec spec) {
        this.test.setBlockSpec(spec);
        IndependenceTest indTest = this.test.getTest(dataSet, parameters);
        indTest.setAlpha(parameters.getDouble(Params.ALPHA));
        return indTest;
    }

    /**
     * Builds the initial measurement graph using PC and then adds latent -> indicator edges.
     *
     * @param indTest the independence test
     * @param spec the block specification
     * @return the graph
     * @throws InterruptedException if interrupted
     */
    private Graph buildMeasurementGraph(IndependenceTest indTest,
                                        BlockSpec spec) throws InterruptedException {
        Pc pc = new Pc(indTest);
        pc.setDepth(depth);
        pc.setKnowledge(knowledge);
        pc.setFasStable(false);
        pc.setVerbose(verbose); // set once; no second override

        Graph graph = pc.search();

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
        // variables that are absent from the PC output (e.g., isolated variables
        // that are neither indicators nor connected to anything). The loop below
        // is therefore a correctness requirement: downstream stages look up nodes
        // by name and will silently miss any that were dropped.
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        for (Node node : dataSet.getVariables()) {
            if (graph.getNode(node.getName()) == null) {
                graph.addNode(node);
            }
        }

        return graph;
    }

    /**
     * Returns true if the given node is known to be an input.
     *
     * @param node the node
     * @return true if known input
     */
    private boolean isKnownInput(Node node) {
        return node != null && this.inputNames.contains(node.getName());
    }

    /**
     * Returns true if the given node is known to be an output.
     *
     * @param node the node
     * @return true if known output
     */
    private boolean isKnownOutput(Node node) {
        return node != null && this.outputNames.contains(node.getName());
    }

    /**
     * Returns true if the given observed node may be treated as a child of a latent.
     *
     * @param node the node
     * @return true if the node may be treated as a child of a latent
     */
    private boolean mayBeLatentChild(Node node) {
        // Known inputs are never latent children; everything else
        // (known outputs and unclassified variables) may be.
        return !isKnownInput(node);
    }

    /**
     * Returns all observed children of the supplied latent collection.
     *
     * @param graph the graph
     * @param latents the latent nodes
     * @return the observed children
     */
    private List<Node> getObservedChildrenUnion(Graph graph, Collection<Node> latents) {
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

    /**
     * Returns the observed variables to treat as children of the supplied latents.
     *
     * @param graph the graph
     * @param latents the latent nodes
     * @param observedVariables the observed variables
     * @return the observed child set
     */
    private List<Node> determineObservedChildren(Graph graph,
                                                 Collection<Node> latents,
                                                 List<Node> observedVariables) {
        LinkedHashMap<String, Node> byName = new LinkedHashMap<>();

        for (Node node : observedVariables) {
            byName.put(node.getName(), node);
        }

        LinkedHashSet<Node> children = new LinkedHashSet<>();

        for (Node child : getObservedChildrenUnion(graph, latents)) {
            Node resolved = byName.get(child.getName());

            if (resolved != null && !isKnownInput(resolved)) {
                children.add(resolved);
            }
        }

        for (String name : this.outputNames) {
            Node resolved = byName.get(name);

            if (resolved != null) {
                children.add(resolved);
            }
        }

        return new ArrayList<>(children);
    }

    /**
     * Returns the observed variables to treat as the parent pool.
     *
     * @param observedVariables the observed variables
     * @param observedChildren the observed child variables
     * @return the parent pool
     */
    private List<Node> determineParentPool(List<Node> observedVariables,
                                           Collection<Node> observedChildren) {
        Set<Node> childSet = new LinkedHashSet<>(observedChildren);
        List<Node> pool = new ArrayList<>();

        for (Node node : observedVariables) {
            // Known outputs are never parents.
            if (isKnownOutput(node)) continue;

            // Known inputs are always eligible parents, even when the measurement
            // model also placed them as latent children — they sit upstream of
            // the latents by definition.
            // All other variables are eligible only if they are not latent children.
            if (isKnownInput(node) || !childSet.contains(node)) {
                pool.add(node);
            }
        }

        return pool;
    }

    /**
     * Ensures that no observed variable is simultaneously declared as both an input and an output.
     */
    private void validateInputOutputKnowledge() {
        Set<String> intersection = new LinkedHashSet<>(this.inputNames);
        intersection.retainAll(this.outputNames);

        if (!intersection.isEmpty()) {
            throw new IllegalArgumentException(
                    "The same variables cannot be declared as both inputs and outputs: " + intersection
            );
        }
    }

    /**
     * Result of building the shared measurement model.
     *
     * @param spec             the block specification
     * @param graph            the measurement graph
     * @param latents          the latent variables
     * @param observedChildren the observed child variables
     * @param parentPool       the measured parent pool
     * @param variables        the measured variables in matrix order
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
            int sampleSize,
            double alpha
    ) {
    }
}