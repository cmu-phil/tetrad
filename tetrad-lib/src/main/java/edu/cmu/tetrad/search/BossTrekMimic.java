/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.BlocksIndTestTs;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.search.blocks.SingleClusterPolicy;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.StatUtils;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Uses a fixed latent-indicator measurement model and recovers measured parents
 * of those latents using BOSS parent relations among the indicators.
 *
 * <p>The intended use is:
 * <ol>
 *     <li>Obtain a measurement model externally, for example from TSC/PC, and call
 *     {@link #setMeasurementGraph(Graph)} followed by {@link #search()}.</li>
 *     <li>Or call {@link #search(DataModel, Parameters)} to first build the
 *     measurement graph from data using TSC and trek-PC, then recover measured parents.</li>
 * </ol>
 * </p>
 *
 * @author josephramsey
 */
public final class BossTrekMimic implements IGraphSearch {

    /**
     * Score used by BOSS.
     */
    private final Score score;

    /**
     * Fixed measurement graph containing latent nodes and latent -> indicator edges.
     */
    private Graph measurementGraph;

    /**
     * Optional background knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Minimum number of indicators of a latent that must have X as a BOSS parent
     * before X is attached to that latent.
     */
    private int minIndicatorSupport = 2;

    /**
     * Minimum proportion of a latent's indicators that must have X as a BOSS parent
     * before X is attached to that latent.
     */
    private double minIndicatorSupportProportion = 0.5;

    /**
     * If true, allow one measured input to be attached to multiple latents if it
     * has enough support for each of them. Otherwise attach it only to the best latent.
     */
    private boolean allowMultipleLatentParents = true;

    /**
     * Whether to prune latent-latent edges after parent recovery.
     */
    private boolean pruneLatentEdges = true;

    /**
     * Whether to orient latent-latent edges after pruning.
     */
    private boolean orientLatentEdges = true;

    /**
     * Variables in matrix order.
     */
    private List<Node> variables;

    /**
     * Correlation matrix in variable order.
     */
    private SimpleMatrix s;

    /**
     * Sample size.
     */
    private int sampleSize;

    /**
     * Alpha level.
     */
    private double alpha = 0.01;

    /**
     * Optional known input variable names.
     */
    private final Set<String> inputNames = new LinkedHashSet<>();

    /**
     * Optional known output variable names.
     */
    private final Set<String> outputNames = new LinkedHashSet<>();

    /**
     * PC depth for measurement-graph construction.
     */
    private int depth = -1;

    /**
     * Verbosity flag for measurement-graph construction.
     */
    private boolean verbose = false;

    /**
     * Constructs the search.
     *
     * @param score the score to use for BOSS
     */
    public BossTrekMimic(Score score) {
        if (score == null) {
            throw new NullPointerException("Score must not be null.");
        }

        this.score = score;
    }

    /**
     * Convenience method: constructs the measurement graph from the data using TSC and trek-PC,
     * then runs the fixed-measurement BossTrekMimic search.
     *
     * @param dataModel the data model
     * @param parameters the parameters
     * @return the resulting graph
     * @throws InterruptedException if interrupted
     */
    public Graph search(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (!(dataModel instanceof DataSet)) {
            throw new IllegalArgumentException("BossTrekMimic requires a DataSet.");
        }

        DataSet data = (DataSet) dataModel;

        this.depth = parameters.getInt(Params.DEPTH);
        this.verbose = parameters.getBoolean(Params.VERBOSE);
        this.alpha = parameters.getDouble(Params.ALPHA);
        this.variables = new ArrayList<>(data.getVariables());
        this.s = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();
        this.sampleSize = data.getNumRows();

        applyKnowledgeTiersToInputOutputNames(data);

        BlockSpec spec = buildBlockSpec(dataModel, data, parameters);

        IndependenceTest indTest = buildBlocksTest(dataModel, parameters, spec);
        Graph graph = buildMeasurementGraph(indTest, data.getVariables(), spec);

        setMeasurementGraph(graph);

        Graph result = search();

        if (orientLatentEdges) {
            orientLatentEdgesByCorrelationOfParentsAndChildren(result);
        }

        return result;
    }

    /**
     * Runs the fixed-measurement BossTrekMimic search.
     *
     * @return the resulting graph
     */
    @Override
    public Graph search() {
        if (this.measurementGraph == null) {
            throw new IllegalStateException("A fixed measurement graph must be supplied.");
        }

        if (this.variables == null || this.s == null) {
            throw new IllegalStateException("Variables, matrix, and sample size must be supplied.");
        }

        Graph graph = new EdgeListGraph(this.measurementGraph);

        List<Node> latents = getLatents(graph);
        List<Node> indicators = getIndicators(graph, latents);
        List<Node> measuredNodes = getMeasuredNodes(graph);
        List<Node> inputPool = new ArrayList<>(measuredNodes);
        inputPool.removeAll(indicators);

        Graph bossGraph = runBoss();

        Map<Node, List<Node>> indicatorsByLatent = getIndicatorsByLatent(graph, latents);

        for (Node input : inputPool) {
            Map<Node, Integer> supportCounts = new LinkedHashMap<>();

            for (Node latent : latents) {
                int count = countBossChildrenInIndicatorBlock(input, indicatorsByLatent.get(latent), bossGraph);
                supportCounts.put(latent, count);
            }

            if (allowMultipleLatentParents) {
                attachInputToSupportedLatents(graph, input, latents, indicatorsByLatent, supportCounts);
            } else {
                attachInputToBestLatent(graph, input, latents, indicatorsByLatent, supportCounts);
            }
        }

        if (pruneLatentEdges) {
            pruneLatentLatentEdgesByConditionedRank(graph);
        }

        return graph;
    }

    /**
     * Builds a block specification from TSC.
     *
     * @param dataModel the data model
     * @param data the data
     * @param parameters the parameters
     * @return the block specification
     */
    private BlockSpec buildBlockSpec(DataModel dataModel, DataSet data, Parameters parameters) {
        Tsc tsc = new Tsc(dataModel.getVariables(), new CovarianceMatrix(data));
        tsc.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        tsc.setRmax(3);
        tsc.setMinRedundancy(0);
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

        BlocksUtil.validateBlocks(blocks, data);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);

        BlockSpec spec = BlocksUtil.toSpec(blocks, ranks, data);

        return BlocksUtil.applySingleClusterPolicy(
                spec,
                SingleClusterPolicy.EXCLUDE,
                parameters.getDouble(Params.ALPHA)
        );
    }

    /**
     * Builds the trek/block independence test.
     *
     * @param dataModel the data model
     * @param parameters the parameters
     * @param spec the block specification
     * @return the independence test
     */
    private IndependenceTest buildBlocksTest(DataModel dataModel,
                                             Parameters parameters,
                                             BlockSpec spec) {
        BlocksIndTestTs wrapper = new BlocksIndTestTs();
        wrapper.setBlockSpec(spec);

        IndependenceTest test = wrapper.getTest(dataModel, parameters);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        return test;
    }

    /**
     * Builds the initial measurement graph using PC with the trek/block test.
     *
     * @param test the independence test
     * @param measures the measured variables
     * @param spec the block specification
     * @return the measurement graph
     * @throws InterruptedException if interrupted
     */
    private Graph buildMeasurementGraph(IndependenceTest test,
                                        List<Node> measures,
                                        BlockSpec spec) throws InterruptedException {
        Pc pc = new Pc(test);
        pc.setDepth(depth);
        pc.setVerbose(verbose);
        pc.setKnowledge(this.knowledge);
        pc.setFasStable(false);
        pc.setVerbose(false);

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

        graph = GraphUtils.replaceNodes(graph, measures);

        for (Node node : measures) {
            if (graph.getNode(node.getName()) == null) {
                graph.addNode(node);
            }
        }

        return graph;
    }

    /**
     * Applies tier-0/tier-1 knowledge to the input/output name sets.
     *
     * @param data the data set
     */
    private void applyKnowledgeTiersToInputOutputNames(DataSet data) {
        if (knowledge == null) {
            return;
        }

        setInputsByName(knowledge.getTier(0));
        setOutputsByName(knowledge.getTier(1));

        // Keep only names present in the data.
        this.inputNames.removeIf(name -> data.getVariable(name) == null);
        this.outputNames.removeIf(name -> data.getVariable(name) == null);
    }

    /**
     * Supplies the fixed measurement graph.
     *
     * @param measurementGraph graph containing latent nodes and latent -> indicator edges
     */
    public void setMeasurementGraph(Graph measurementGraph) {
        if (measurementGraph == null) {
            throw new NullPointerException("Measurement graph must not be null.");
        }

        this.measurementGraph = new EdgeListGraph(measurementGraph);
    }

    /**
     * Sets the knowledge used by BOSS and by trek-PC graph construction.
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
     * Sets the minimum indicator support count.
     *
     * @param minIndicatorSupport the minimum support count
     */
    public void setMinIndicatorSupport(int minIndicatorSupport) {
        this.minIndicatorSupport = minIndicatorSupport;
    }

    /**
     * Sets the minimum indicator support proportion.
     *
     * @param minIndicatorSupportProportion the minimum support proportion
     */
    public void setMinIndicatorSupportProportion(double minIndicatorSupportProportion) {
        this.minIndicatorSupportProportion = minIndicatorSupportProportion;
    }

    /**
     * Sets the variables in matrix order.
     *
     * @param variables the variables
     */
    public void setVariables(List<Node> variables) {
        this.variables = new ArrayList<>(variables);
    }

    /**
     * Sets the correlation matrix.
     *
     * @param s the matrix
     */
    public void setMatrix(SimpleMatrix s) {
        this.s = s;
    }

    /**
     * Sets the sample size.
     *
     * @param sampleSize the sample size
     */
    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    /**
     * Sets the alpha level.
     *
     * @param alpha the alpha
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Sets whether one measured input may attach to multiple latents.
     *
     * @param allowMultipleLatentParents true if allowed
     */
    public void setAllowMultipleLatentParents(boolean allowMultipleLatentParents) {
        this.allowMultipleLatentParents = allowMultipleLatentParents;
    }

    /**
     * Sets whether to prune latent-latent edges.
     *
     * @param pruneLatentEdges true if so
     */
    public void setPruneLatentEdges(boolean pruneLatentEdges) {
        this.pruneLatentEdges = pruneLatentEdges;
    }

    /**
     * Sets whether to orient latent-latent edges after the main search.
     *
     * @param orientLatentEdges true if so
     */
    public void setOrientLatentEdges(boolean orientLatentEdges) {
        this.orientLatentEdges = orientLatentEdges;
    }

    /**
     * Sets the depth used in trek-PC graph construction.
     *
     * @param depth the depth
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets verbose output for trek-PC graph construction.
     *
     * @param verbose true if verbose
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets known input variables by node collection.
     *
     * @param inputs the inputs
     */
    public void setInputs(Collection<Node> inputs) {
        this.inputNames.clear();

        if (inputs != null) {
            for (Node node : inputs) {
                if (node != null) {
                    this.inputNames.add(node.getName());
                }
            }
        }

        validateInputOutputKnowledge();
    }

    /**
     * Sets known output variables by node collection.
     *
     * @param outputs the outputs
     */
    public void setOutputs(Collection<Node> outputs) {
        this.outputNames.clear();

        if (outputs != null) {
            for (Node node : outputs) {
                if (node != null) {
                    this.outputNames.add(node.getName());
                }
            }
        }

        validateInputOutputKnowledge();
    }

    /**
     * Sets known input variable names.
     *
     * @param inputNames the input names
     */
    public void setInputNames(Collection<String> inputNames) {
        setInputsByName(inputNames);
        validateInputOutputKnowledge();
    }

    /**
     * Sets known output variable names.
     *
     * @param outputNames the output names
     */
    public void setOutputNames(Collection<String> outputNames) {
        setOutputsByName(outputNames);
        validateInputOutputKnowledge();
    }

    private void setInputsByName(Collection<String> inputNames) {
        this.inputNames.clear();

        if (inputNames != null) {
            for (String name : inputNames) {
                if (name != null) {
                    this.inputNames.add(name);
                }
            }
        }
    }

    private void setOutputsByName(Collection<String> outputNames) {
        this.outputNames.clear();

        if (outputNames != null) {
            for (String name : outputNames) {
                if (name != null) {
                    this.outputNames.add(name);
                }
            }
        }
    }

    /**
     * Ensures that no observed variable is simultaneously declared as both input and output.
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
        if (isKnownInput(node)) {
            return false;
        }

        if (isKnownOutput(node)) {
            return true;
        }

        return true;
    }

    /**
     * Removes latent-latent edges that are explained by the measured parents
     * recovered for the incident latents.
     *
     * @param graph the graph to modify
     */
    private void pruneLatentLatentEdgesByConditionedRank(Graph graph) {
        if (this.variables == null || this.s == null) {
            throw new IllegalStateException("variables and matrix must be supplied for latent-edge pruning.");
        }

        List<Edge> edges = new ArrayList<>(graph.getEdges());

        for (Edge edge : edges) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (x.getNodeType() != NodeType.LATENT || y.getNodeType() != NodeType.LATENT) {
                continue;
            }

            List<Node> childrenX = getMeasuredChildren(graph, x);
            List<Node> childrenY = getMeasuredChildren(graph, y);

            if (childrenX.isEmpty() || childrenY.isEmpty()) {
                continue;
            }

            List<Node> cond = new ArrayList<>(getMeasuredParents(graph, x));

            for (Node parent : getMeasuredParents(graph, y)) {
                if (!cond.contains(parent)) {
                    cond.add(parent);
                }
            }

            if (cond.isEmpty()) {
                continue;
            }

            int rank = estimateRankConditioned(childrenX, childrenY, cond);

            if (rank == 0) {
                graph.removeEdge(edge);
            }
        }
    }

    /**
     * Orients latent-latent edges by correlation patterns of measured parents and measured children.
     *
     * @param graph the graph
     */
    private void orientLatentEdgesByCorrelationOfParentsAndChildren(Graph graph) {
        List<Edge> edges = new ArrayList<>(graph.getEdges());

        for (Edge edge : edges) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (x.getNodeType() != NodeType.LATENT || y.getNodeType() != NodeType.LATENT) {
                continue;
            }

            List<Node> parentsx = new ArrayList<>(graph.getParents(x));
            List<Node> parentsy = new ArrayList<>(graph.getParents(y));

            List<Node> childrenx = new ArrayList<>(graph.getChildren(x));
            List<Node> childreny = new ArrayList<>(graph.getChildren(y));

            parentsx.removeIf(n -> n.getNodeType() == NodeType.LATENT);
            parentsy.removeIf(n -> n.getNodeType() == NodeType.LATENT);

            childrenx.removeIf(n -> n.getNodeType() == NodeType.LATENT);
            childreny.removeIf(n -> n.getNodeType() == NodeType.LATENT);

            boolean allCorrelatedxy = true;
            boolean pairTestedxy = false;

            for (Node parentx : parentsx) {
                for (Node childy : childreny) {
                    pairTestedxy = true;

                    if (!correlated(parentx, childy)) {
                        allCorrelatedxy = false;
                        break;
                    }
                }

                if (!allCorrelatedxy) {
                    break;
                }
            }

            boolean orientXtoY = allCorrelatedxy && pairTestedxy;

            boolean allCorrelatedyx = true;
            boolean pairTestedyx = false;

            for (Node parenty : parentsy) {
                for (Node childx : childrenx) {
                    pairTestedyx = true;

                    if (!correlated(parenty, childx)) {
                        allCorrelatedyx = false;
                        break;
                    }
                }

                if (!allCorrelatedyx) {
                    break;
                }
            }

            boolean orientYtoX = allCorrelatedyx && pairTestedyx;

            if (orientXtoY == orientYtoX) {
                continue;
            }

            graph.removeEdge(edge);

            if (orientXtoY) {
                graph.addDirectedEdge(x, y);
            } else {
                graph.addDirectedEdge(y, x);
            }
        }
    }

    private boolean correlated(Node a, Node b) {
        int i = variables.indexOf(a);
        int j = variables.indexOf(b);

        double r = s.get(i, j);

        if (Math.abs(r) >= 1.0) {
            return true;
        }

        double z = 0.5 * Math.log((1.0 + r) / (1.0 - r)) * Math.sqrt(sampleSize - 3.0);
        double cutoff = StatUtils.getZForAlpha(alpha);

        return Math.abs(z) > cutoff;
    }

    private int estimateRankConditioned(List<Node> xSet,
                                        List<Node> ySet,
                                        List<Node> cond) {
        List<Node> x = new ArrayList<>(xSet);
        List<Node> y = new ArrayList<>(ySet);

        x.removeAll(y);

        if (x.isEmpty() || y.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        int[] xIndices = new int[x.size()];
        int[] yIndices = new int[y.size()];
        int[] condIndices = new int[cond.size()];

        for (int i = 0; i < x.size(); i++) {
            xIndices[i] = variables.indexOf(x.get(i));
        }

        for (int i = 0; i < y.size(); i++) {
            yIndices[i] = variables.indexOf(y.get(i));
        }

        for (int i = 0; i < cond.size(); i++) {
            condIndices[i] = variables.indexOf(cond.get(i));
        }

        return RankTests.estimateWilksRankConditioned(s, xIndices, yIndices, condIndices, sampleSize, alpha);
    }

    private Graph runBoss() {
        try {
            PermutationSearch boss = new PermutationSearch(new Boss(this.score));
            boss.setKnowledge(this.knowledge);
            return boss.search();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("BOSS search was interrupted.", e);
        }
    }

    private List<Node> getLatents(Graph graph) {
        List<Node> latents = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
            }
        }

        latents.sort(Comparator.comparing(Node::getName));
        return latents;
    }

    private List<Node> getMeasuredNodes(Graph graph) {
        List<Node> measured = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) {
                measured.add(node);
            }
        }

        measured.sort(Comparator.comparing(Node::getName));
        return measured;
    }

    private List<Node> getIndicators(Graph graph, List<Node> latents) {
        LinkedHashSet<Node> indicators = new LinkedHashSet<>();

        for (Node latent : latents) {
            for (Node child : graph.getChildren(latent)) {
                if (child.getNodeType() != NodeType.LATENT) {
                    indicators.add(child);
                }
            }
        }

        return new ArrayList<>(indicators);
    }

    private Map<Node, List<Node>> getIndicatorsByLatent(Graph graph, List<Node> latents) {
        Map<Node, List<Node>> map = new LinkedHashMap<>();

        for (Node latent : latents) {
            List<Node> indicators = new ArrayList<>();

            for (Node child : graph.getChildren(latent)) {
                if (child.getNodeType() != NodeType.LATENT) {
                    indicators.add(child);
                }
            }

            indicators.sort(Comparator.comparing(Node::getName));
            map.put(latent, indicators);
        }

        return map;
    }

    /**
     * Counts how many indicators in the block have the input as a BOSS parent.
     *
     * @param input the candidate input
     * @param indicators the latent's indicators
     * @param bossGraph the BOSS graph
     * @return the support count
     */
    private int countBossChildrenInIndicatorBlock(Node input, List<Node> indicators, Graph bossGraph) {
        int count = 0;

        for (Node indicator : indicators) {
            if (bossGraph.isParentOf(input, indicator)) {
                count++;
            }
        }

        return count;
    }

    private void attachInputToSupportedLatents(Graph graph,
                                               Node input,
                                               List<Node> latents,
                                               Map<Node, List<Node>> indicatorsByLatent,
                                               Map<Node, Integer> supportCounts) {
        for (Node latent : latents) {
            int support = supportCounts.getOrDefault(latent, 0);
            int indicatorCount = indicatorsByLatent.get(latent).size();

            if (indicatorCount == 0) {
                continue;
            }

            double proportion = (double) support / indicatorCount;

            if (support >= minIndicatorSupport || proportion >= minIndicatorSupportProportion) {
                if (!graph.isParentOf(input, latent)) {
                    graph.addDirectedEdge(input, latent);
                }
            }
        }
    }

    private void attachInputToBestLatent(Graph graph,
                                         Node input,
                                         List<Node> latents,
                                         Map<Node, List<Node>> indicatorsByLatent,
                                         Map<Node, Integer> supportCounts) {
        Node bestLatent = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Node latent : latents) {
            int support = supportCounts.getOrDefault(latent, 0);
            int indicatorCount = indicatorsByLatent.get(latent).size();

            if (indicatorCount == 0) {
                continue;
            }

            double proportion = (double) support / indicatorCount;

            if (!(support >= minIndicatorSupport || proportion >= minIndicatorSupportProportion)) {
                continue;
            }

            double score = support + proportion;

            if (score > bestScore) {
                bestScore = score;
                bestLatent = latent;
            }
        }

        if (bestLatent != null && !graph.isParentOf(input, bestLatent)) {
            graph.addDirectedEdge(input, bestLatent);
        }
    }

    private void removeDegenerateLatents(Graph graph) {
        List<Node> nodes = new ArrayList<>(graph.getNodes());

        for (Node node : nodes) {
            if (node.getNodeType() != NodeType.LATENT) {
                continue;
            }

            boolean hasMeasuredParent = false;
            boolean hasMeasuredChild = false;

            for (Node parent : graph.getParents(node)) {
                if (parent.getNodeType() != NodeType.LATENT) {
                    hasMeasuredParent = true;
                    break;
                }
            }

            for (Node child : graph.getChildren(node)) {
                if (child.getNodeType() != NodeType.LATENT) {
                    hasMeasuredChild = true;
                    break;
                }
            }

            if (!hasMeasuredParent || !hasMeasuredChild) {
                graph.removeNode(node);
            }
        }
    }

    private List<Node> getMeasuredChildren(Graph graph, Node latent) {
        List<Node> children = new ArrayList<>();

        for (Node child : graph.getChildren(latent)) {
            if (child.getNodeType() != NodeType.LATENT) {
                children.add(child);
            }
        }

        children.sort(Comparator.comparing(Node::getName));
        return children;
    }

    private List<Node> getMeasuredParents(Graph graph, Node node) {
        List<Node> parents = new ArrayList<>();

        for (Node parent : graph.getParents(node)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                parents.add(parent);
            }
        }

        parents.sort(Comparator.comparing(Node::getName));
        return parents;
    }
}