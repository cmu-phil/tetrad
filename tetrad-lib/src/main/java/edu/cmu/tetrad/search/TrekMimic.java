package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.StatUtils;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Hybrid version of Trek-MIMIC parent recovery.
 *
 * <p>This class runs the full Trek-MIMIC pipeline from data:
 * <ol>
 *     <li>Runs TSC to obtain clusters and a block specification.</li>
 *     <li>Runs PC with the trek/block test to obtain a latent-indicator graph.</li>
 *     <li>Recovers measured parents of the latent variables.</li>
 *     <li>Prunes latent-latent edges explained by recovered parents.</li>
 *     <li>Optionally orients latent-latent edges using parent/child correlations.</li>
 * </ol>
 * </p>
 *
 * <p>Expected use:
 * <pre>
 * TrekMimic tm = new TrekMimic(data, parameters);
 * tm.setKnowledge(knowledge);
 * tm.setInputNames(inputNames);
 * tm.setOutputNames(outputNames);
 * Graph g = tm.search();
 * </pre>
 * </p>
 *
 * @author josephramsey
 */
public final class TrekMimic {

    /**
     * Optional known measured inputs by name.
     */
    private final Set<String> inputNames = new LinkedHashSet<>();
    /**
     * Optional known measured outputs by name.
     */
    private final Set<String> outputNames = new LinkedHashSet<>();
    /**
     * Input data set.
     */
    private DataSet dataSet;
    /**
     * Parameters controlling the search.
     */
    private Parameters parameters;
    /**
     * Optional knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Whether to run the higher-rank expansion phase.
     */
    private boolean doHigherRankExpansion = true;

    /**
     * Maximum latent subset size to consider in higher-rank expansion.
     */
    private int maxLatentSubsetSize = 2;

    /**
     * PC depth.
     */
    private int depth = -1;

    /**
     * Verbosity flag.
     */
    private boolean verbose = false;

    /**
     * Whether to orient latent-latent edges after pruning.
     */
    private boolean orientLatentEdges = true;

    /**
     * Working graph.
     */
    private Graph graph;

    /**
     * Working measured-parent pool.
     */
    private List<Node> initialPool;

    /**
     * Working latent list.
     */
    private List<Node> allLatents;

    /**
     * Measured variables in matrix order.
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
     * Constructs an uninitialized TrekMimic search.
     * Use setters before calling {@link #search()}.
     */
    public TrekMimic() {
    }

    /**
     * Constructs a TrekMimic search with data and parameters.
     *
     * @param dataSet    the data set
     * @param parameters the parameters
     */
    public TrekMimic(DataSet dataSet, Parameters parameters) {
        this();
        setDataSet(dataSet);
        setParameters(parameters);
    }

    private static List<Node> getMeasuredChildren(Graph graph, Node latent) {
        List<Node> children = new ArrayList<>();

        for (Node child : graph.getChildren(latent)) {
            if (child.getNodeType() != NodeType.LATENT) {
                children.add(child);
            }
        }

        children.sort(Comparator.comparing(Node::getName));
        return children;
    }

    private static List<Node> getMeasuredParents(Graph graph, Node node) {
        List<Node> parents = new ArrayList<>();

        for (Node parent : graph.getParents(node)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                parents.add(parent);
            }
        }

        parents.sort(Comparator.comparing(Node::getName));
        return parents;
    }

    /**
     * Runs the full Trek-MIMIC search.
     *
     * @return the resulting graph
     * @throws InterruptedException if interrupted
     */
    public Graph search() throws InterruptedException {
        validateSearchInputs();

        TrekMeasurementModelBuilder builder =
                new TrekMeasurementModelBuilder(dataSet, parameters);

        builder.setKnowledge(this.knowledge);
        builder.setInputNames(this.inputNames);
        builder.setOutputNames(this.outputNames);
        builder.setDepth(this.depth);
        builder.setVerbose(this.verbose);

        TrekMeasurementModelBuilder.MeasurementBuildResult result = builder.build();

        this.graph = result.graph();
        this.allLatents = new ArrayList<>(result.latents());
        this.initialPool = new ArrayList<>(result.parentPool());
        this.variables = new ArrayList<>(result.variables());
        this.s = result.matrix();
        this.sampleSize = result.sampleSize();
        this.alpha = result.alpha();

        recoverMeasuredParentsHybrid();
        pruneLatentLatentEdgesByConditionedRank();

        if (orientLatentEdges) {
            orientLatentEdgesByCorrelationOfParentsAndChildren();
        }

        return graph;
    }

    /**
     * Sets the data set.
     *
     * @param dataSet the data set
     */
    public void setDataSet(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Data set must not be null.");
        }

        this.dataSet = dataSet;
    }

    /**
     * Sets the parameters.
     *
     * @param parameters the parameters
     */
    public void setParameters(Parameters parameters) {
        if (parameters == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        this.parameters = parameters;
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
     * Sets whether to run the higher-rank expansion phase.
     *
     * @param doHigherRankExpansion true if so
     */
    public void setDoHigherRankExpansion(boolean doHigherRankExpansion) {
        this.doHigherRankExpansion = doHigherRankExpansion;
    }

    /**
     * Sets the maximum latent subset size for higher-rank expansion.
     *
     * @param maxLatentSubsetSize the maximum size
     */
    public void setMaxLatentSubsetSize(int maxLatentSubsetSize) {
        this.maxLatentSubsetSize = maxLatentSubsetSize;
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
     * Sets verbose output.
     *
     * @param verbose true if verbose
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets whether to orient latent-latent edges after pruning.
     *
     * @param orientLatentEdges true if so
     */
    public void setOrientLatentEdges(boolean orientLatentEdges) {
        this.orientLatentEdges = orientLatentEdges;
    }

    /**
     * Returns the current graph after search, if available.
     *
     * @return the graph
     */
    public Graph getGraph() {
        return graph;
    }

    /**
     * Returns the current latent nodes after search, if available.
     *
     * @return the latent nodes
     */
    public List<Node> getLatents() {
        return allLatents == null ? Collections.emptyList() : new ArrayList<>(allLatents);
    }

    /**
     * Validates that the required inputs for search have been supplied.
     */
    private void validateSearchInputs() {
        if (dataSet == null) {
            throw new IllegalStateException("Data set has not been supplied.");
        }

        if (parameters == null) {
            throw new IllegalStateException("Parameters have not been supplied.");
        }

        validateInputOutputKnowledge();
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
     * Runs the hybrid measured-parent recovery stage using the current fields.
     */
    private void recoverMeasuredParentsHybrid() {
        if (graph == null) {
            throw new IllegalStateException("Graph has not been initialized.");
        }

        if (initialPool == null || allLatents == null || variables == null || s == null) {
            throw new IllegalStateException("Search inputs have not been initialized.");
        }

        List<Node> pool = new ArrayList<>(initialPool);
        List<Node> latents = new ArrayList<>(allLatents);
        latents.sort(Comparator.comparing(Node::getName));

        List<Node> allChildren = getObservedChildrenUnion(graph, latents);

        List<List<Node>> recoveredGroups =
                recoverCliqueRankOneGroups(pool, allChildren, variables, s, sampleSize, alpha);

        Map<Node, List<Node>> assignment =
                assignParentGroupsToLatents(recoveredGroups, latents, graph, variables, s, sampleSize, alpha);

        for (Map.Entry<Node, List<Node>> entry : assignment.entrySet()) {
            Node latent = entry.getKey();
            List<Node> parents = entry.getValue();

            for (Node parent : parents) {
                if (!graph.isParentOf(parent, latent)) {
                    graph.addDirectedEdge(parent, latent);
                }
            }
        }

        if (doHigherRankExpansion) {
            expandHigherRankParentSetsV2(graph, latents, pool);
        }
    }

    /**
     * Removes latent-latent edges that are explained by recovered measured parents.
     */
    private void pruneLatentLatentEdgesByConditionedRank() {
        if (variables == null || s == null) {
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
     * Orients latent-latent edges using correlations of parents and children.
     */
    private void orientLatentEdgesByCorrelationOfParentsAndChildren() {
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

    private void expandHigherRankParentSetsV2(Graph graph,
                                              List<Node> allLatents,
                                              List<Node> initialPool) {
        LinkedHashSet<Node> unused = new LinkedHashSet<>(initialPool);
        unused.removeAll(getObservedParentsUnion(graph, allLatents));

        List<Node> allChildren = getObservedChildrenUnion(graph, allLatents);

        List<List<Node>> latentSubsets = new ArrayList<>();
        for (int subsetSize = 2; subsetSize <= Math.min(maxLatentSubsetSize, allLatents.size()); subsetSize++) {
            latentSubsets.addAll(getLatentSubsets(allLatents, subsetSize));
        }

        double lambda = 2.0;

        for (Node node : new ArrayList<>(unused)) {
            List<Node> group = Collections.singletonList(node);

            List<Node> bestSubset = assignSharedGroupToBestLatentSubset(group, latentSubsets, lambda);

            if (bestSubset != null) {
                addMeasuredGroupToLatentSubset(graph, group, bestSubset);
                removeExplainedLatentEdges(graph, bestSubset, group);
                unused.remove(node);
            }
        }

        List<Node> remaining = new ArrayList<>(unused);
        ChoiceGenerator gen = new ChoiceGenerator(remaining.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> pair = GraphUtils.asList(choice, remaining);

            int rankAboveAll = estimateRank(pair, allChildren);

            if (rankAboveAll < 1 || rankAboveAll == Integer.MAX_VALUE) {
                continue;
            }

            List<Node> bestSubset = assignSharedGroupToBestLatentSubset(pair, latentSubsets, lambda);

            if (bestSubset != null) {
                addMeasuredGroupToLatentSubset(graph, pair, bestSubset);
                removeExplainedLatentEdges(graph, bestSubset, pair);

                unused.removeAll(pair);
                remaining = new ArrayList<>(unused);
                gen = new ChoiceGenerator(remaining.size(), 2);
            }
        }
    }

    private void addMeasuredGroupToLatentSubset(Graph graph,
                                                List<Node> group,
                                                List<Node> latentSubset) {
        for (Node parent : group) {
            for (Node latent : latentSubset) {
                if (!graph.isParentOf(parent, latent)) {
                    graph.addDirectedEdge(parent, latent);
                }
            }
        }
    }

    private List<List<Node>> getLatentSubsets(List<Node> latentNodes, int subsetSize) {
        List<List<Node>> subsets = new ArrayList<>();

        if (subsetSize < 1 || subsetSize > latentNodes.size()) {
            return subsets;
        }

        ChoiceGenerator gen = new ChoiceGenerator(latentNodes.size(), subsetSize);
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> subset = GraphUtils.asList(choice, latentNodes);
            subset.sort(Comparator.comparing(Node::getName));
            subsets.add(subset);
        }

        return subsets;
    }

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

    private List<Node> getObservedChildren(Graph graph, Node latent) {
        return getObservedChildrenUnion(graph, Collections.singletonList(latent));
    }

    private List<Node> getObservedParentsUnion(Graph graph, Collection<Node> latents) {
        LinkedHashSet<Node> parents = new LinkedHashSet<>();

        for (Node latent : latents) {
            for (Node parent : graph.getParents(latent)) {
                if (parent.getNodeType() != NodeType.LATENT) {
                    parents.add(parent);
                }
            }
        }

        return new ArrayList<>(parents);
    }

    private List<List<Node>> recoverCliqueRankOneGroups(List<Node> initialPool,
                                                        List<Node> allChildren,
                                                        List<Node> variables,
                                                        SimpleMatrix s,
                                                        int sampleSize,
                                                        double alpha) {
        List<Node> pool = new ArrayList<>(initialPool);
        List<List<Node>> pairs = new ArrayList<>();
        Set<Set<Node>> groups = new HashSet<>();

        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> pair = GraphUtils.asList(choice, pool);
            int rank = estimateRank(pair, allChildren);

            if (rank != 1) {
                continue;
            }

            pairs.add(pair);
        }

        Graph pairGraph = buildRankOnePairGraph(pool, allChildren);

        for (List<Node> seedPair : pairs) {
            List<Node> group = growCliqueRankOneSet(seedPair, pool, pairGraph, allChildren);
            groups.add(new HashSet<>(group));
        }

        List<List<Node>> recovered = new ArrayList<>();

        for (Set<Node> group : groups) {
            List<Node> list = new ArrayList<>(group);
            list.sort(Comparator.comparing(Node::getName));
            recovered.add(list);
        }

        return recovered;
    }

    private Graph buildRankOnePairGraph(List<Node> pool,
                                        List<Node> allChildren) {
        Graph pairGraph = new EdgeListGraph(pool);

        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> pair = GraphUtils.asList(choice, pool);

            int rank = estimateRank(pair, allChildren);

            if (rank == 1) {
                pairGraph.addUndirectedEdge(pair.get(0), pair.get(1));
            }
        }

        return pairGraph;
    }

    private List<Node> growCliqueRankOneSet(List<Node> seedPair,
                                            List<Node> pool,
                                            Graph pairGraph,
                                            List<Node> allChildren) {
        LinkedHashSet<Node> current = new LinkedHashSet<>(seedPair);
        LinkedHashSet<Node> remaining = new LinkedHashSet<>(pool);
        remaining.removeAll(current);

        boolean changed = true;

        while (changed) {
            changed = false;

            Node bestAdd = null;
            double bestStrength = Double.NEGATIVE_INFINITY;

            for (Node candidate : remaining) {
                boolean adjacentToAll = true;

                for (Node existing : current) {
                    if (!pairGraph.isAdjacentTo(candidate, existing)) {
                        adjacentToAll = false;
                        break;
                    }
                }

                if (!adjacentToAll) {
                    continue;
                }

                List<Node> proposed = new ArrayList<>(current);
                proposed.add(candidate);

                int rank = estimateRank(proposed, allChildren);

                if (rank != 1) {
                    continue;
                }

                double strength = blockStrength(proposed, allChildren);

                if (strength > bestStrength) {
                    bestStrength = strength;
                    bestAdd = candidate;
                }
            }

            if (bestAdd != null) {
                current.add(bestAdd);
                remaining.remove(bestAdd);
                changed = true;
            }
        }

        return new ArrayList<>(current);
    }

    private Map<Node, List<Node>> assignParentGroupsToLatents(List<List<Node>> recoveredGroups,
                                                              List<Node> allLatentNodes,
                                                              Graph graph,
                                                              List<Node> variables,
                                                              SimpleMatrix s,
                                                              int sampleSize,
                                                              double alpha) {
        Map<Node, List<Node>> assignment = new LinkedHashMap<>();

        for (List<Node> group : recoveredGroups) {
            Node bestLatent = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (Node latent : allLatentNodes) {
                List<Node> childSet = getObservedChildren(graph, latent);

                int rank = estimateRank(group, childSet);

                if (rank != 1) {
                    continue;
                }

                double score = blockStrength(group, childSet);

                if (score > bestScore) {
                    bestScore = score;
                    bestLatent = latent;
                }
            }

            if (bestLatent != null) {
                assignment.put(bestLatent, group);
            }
        }

        return assignment;
    }

    private List<Node> assignSharedGroupToBestLatentSubset(List<Node> group,
                                                           List<List<Node>> latentSubsets,
                                                           double lambda) {
        List<Node> bestSubset = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double secondBestScore = Double.NEGATIVE_INFINITY;

        for (List<Node> latentSubset : latentSubsets) {
            if (latentSubset.size() < 2) {
                continue;
            }

            List<Node> existingParents = getObservedParentsUnion(graph, latentSubset);

            List<Node> proposedParents = new ArrayList<>(existingParents);
            for (Node node : group) {
                if (!proposedParents.contains(node)) {
                    proposedParents.add(node);
                }
            }

            int rank = estimateRank(proposedParents, getObservedChildrenUnion(graph, latentSubset));

            if (rank != latentSubset.size()) {
                continue;
            }

            int explainedPairs = countExplainedExistingLatentPairs(
                    graph,
                    latentSubset,
                    proposedParents
            );

            if (explainedPairs == 0) {
                continue;
            }

            double strength = blockStrength(proposedParents, getObservedChildrenUnion(graph, latentSubset));

            double score = 1000.0 * explainedPairs
                    + 100.0
                    + strength
                    - lambda * group.size() * latentSubset.size();

            if (score > bestScore) {
                secondBestScore = bestScore;
                bestScore = score;
                bestSubset = latentSubset;
            } else if (score > secondBestScore) {
                secondBestScore = score;
            }
        }

        if (bestSubset == null) {
            return null;
        }

        if (secondBestScore > Double.NEGATIVE_INFINITY && bestScore - secondBestScore < 50.0) {
            return null;
        }

        return bestSubset;
    }

    private int countExplainedExistingLatentPairs(Graph graph,
                                                  List<Node> latentSubset,
                                                  List<Node> proposedParents) {
        int explained = 0;

        for (int i = 0; i < latentSubset.size(); i++) {
            Node li = latentSubset.get(i);

            for (int j = i + 1; j < latentSubset.size(); j++) {
                Node lj = latentSubset.get(j);

                if (!graph.isAdjacentTo(li, lj)) {
                    continue;
                }

                List<Node> children = new ArrayList<>();

                for (Node child : graph.getChildren(li)) {
                    if (!children.contains(child)) {
                        children.add(child);
                    }
                }

                for (Node child : graph.getChildren(lj)) {
                    if (!children.contains(child)) {
                        children.add(child);
                    }
                }

                children.removeIf(node -> node.getNodeType() == NodeType.LATENT);

                int rank = estimateRank(proposedParents, children);

                if (rank == 2) {
                    explained++;
                }
            }
        }

        return explained;
    }

    private void removeExplainedLatentEdges(Graph graph,
                                            List<Node> latentSubset,
                                            List<Node> newParents) {
        if (newParents.isEmpty()) {
            return;
        }

        ChoiceGenerator gen = new ChoiceGenerator(latentSubset.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = latentSubset.get(choice[0]);
            Node y = latentSubset.get(choice[1]);

            Edge edge = graph.getEdge(x, y);
            if (edge == null) {
                continue;
            }

            boolean explained = false;

            for (Node parent : newParents) {
                if (graph.isParentOf(parent, x) && graph.isParentOf(parent, y)) {
                    explained = true;
                    break;
                }
            }

            int rank = estimateRankConditioned(
                    getObservedChildren(graph, x),
                    getObservedChildren(graph, y),
                    newParents
            );

            if (explained && rank == 0) {
                graph.removeEdge(edge);
            }
        }
    }

    private int estimateRank(List<Node> xSet,
                             List<Node> ySet) {
        List<Node> x = new ArrayList<>(xSet);
        List<Node> y = new ArrayList<>(ySet);

        x.removeAll(y);

        if (x.isEmpty() || y.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        int[] xIndices = new int[x.size()];
        int[] yIndices = new int[y.size()];

        for (int i = 0; i < x.size(); i++) {
            xIndices[i] = variables.indexOf(x.get(i));
        }

        for (int i = 0; i < y.size(); i++) {
            yIndices[i] = variables.indexOf(y.get(i));
        }

        return RankTests.estimateWilksRank(s, xIndices, yIndices, sampleSize, alpha);
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

    private double blockStrength(List<Node> xSet,
                                 List<Node> ySet) {
        List<Node> x = new ArrayList<>(xSet);
        List<Node> y = new ArrayList<>(ySet);

        x.removeAll(y);

        if (x.isEmpty() || y.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        int[] xIndices = new int[x.size()];
        int[] yIndices = new int[y.size()];

        for (int i = 0; i < x.size(); i++) {
            xIndices[i] = variables.indexOf(x.get(i));
        }

        for (int i = 0; i < y.size(); i++) {
            yIndices[i] = variables.indexOf(y.get(i));
        }

        double sumSquares = 0.0;

        for (int xIndex : xIndices) {
            for (int yIndex : yIndices) {
                double v = s.get(xIndex, yIndex);
                sumSquares += v * v;
            }
        }

        return Math.sqrt(sumSquares);
    }
}