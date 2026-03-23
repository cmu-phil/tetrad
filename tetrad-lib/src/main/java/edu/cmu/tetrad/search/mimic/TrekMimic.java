package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Hybrid version of Trek-MIMIC parent recovery.
 *
 * <p>This class runs the full Trek-MIMIC pipeline from data:
 * </p>
 * <ol>
 *     <li>Runs TSC to obtain clusters and a block specification.</li>
 *     <li>Runs PC with the trek/block test to obtain a latent-indicator graph.</li>
 *     <li>Recovers measured parents of the latent variables.</li>
 *     <li>Prunes latent-latent edges explained by recovered parents.</li>
 *     <li>Optionally orients latent-latent edges using parent/child correlations.</li>
 * </ol>
 *
 * <p>Expected use:
 * </p>
 * <pre>
 * TrekMimic tm = new TrekMimic(data, parameters);
 * tm.setKnowledge(knowledge);
 * tm.setInputNames(inputNames);
 * tm.setOutputNames(outputNames);
 * Graph g = tm.search();
 * </pre>
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
    private int maxLatentSubsetSize = 3;

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
    private boolean orientAndPrune = true;

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

    private Map<Node, Integer> latentRanks = new LinkedHashMap<>();

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

    /**
     * Runs the full Trek-MIMIC search.
     *
     * @return the resulting graph
     * @throws InterruptedException if interrupted
     */
    public Graph search() throws InterruptedException {
        validateSearchInputs();

        TrekMeasurementModelBuilderPc builder =
                new TrekMeasurementModelBuilderPc(dataSet, parameters);

        builder.setKnowledge(this.knowledge);
        builder.setInputNames(this.inputNames);
        builder.setOutputNames(this.outputNames);
        builder.setDepth(this.depth);
        builder.setVerbose(this.verbose);

        TrekMeasurementModelBuilderPc.MeasurementBuildResult result = builder.build();

        this.graph = result.graph();
        this.allLatents = new ArrayList<>(result.latents());
        this.initialPool = new ArrayList<>(result.parentPool());
        this.variables = new ArrayList<>(result.variables());
        this.s = result.matrix();
        this.sampleSize = result.sampleSize();
        this.alpha = result.alpha();

        this.latentRanks = new LinkedHashMap<>();
        BlockSpec spec = result.spec();
        for (int i = 0; i < spec.blockVariables().size(); i++) {
            this.latentRanks.put(spec.blockVariables().get(i), spec.ranks().get(i));
        }

        recoverMeasuredParentsHybrid();

        LatentGraphRefinement refinement = new LatentGraphRefinement(variables, s, sampleSize, alpha);

        // Remove any edges that are removable by conditional rank.
        refinement.orientAndPrintEdges(graph);

        // Orient latents and remove latent-transitive inputs.
        if (orientAndPrune) {
            List<Graph> graphs = refinement.orientAndPrintEdges(graph);
            Graph oriented = removeUnconnectedVariables(graphs.get(0));
            Graph pruned = removeUnconnectedVariables(graphs.get(1));

            // The latent-transitive edges are non-identifiable, so we print them.
            if (verbose) {
                Set<Edge> set1 = oriented.getEdges();
                Set<Edge> set2 = pruned.getEdges();
                Set<Edge> set3 = new HashSet<>(set1);
                set3.removeAll(set2);

                TetradLogger.getInstance().log("Latent-transitive edges (non-identifiable): " + set3);
            }

            return pruned;
        }

        return removeUnconnectedVariables(graph);
    }

    private Graph removeUnconnectedVariables(Graph graph) {
        graph = new EdgeListGraph(graph);

        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (graph.getAdjacentNodes(node).isEmpty()) {
                graph.removeNode(node);
            }
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
     * @param orientAndPrune true if so
     */
    public void setOrientAndPrune(boolean orientAndPrune) {
        this.orientAndPrune = orientAndPrune;
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
                recoverCliqueRankOneGroups(pool, allChildren);

        Map<Node, List<Node>> assignment =
                assignParentGroupsToLatents(recoveredGroups, latents, graph);

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
            pruneTransitiveInputEdges(graph, latents);  // remove transitive closure edges

        }
    }

    private void expandHigherRankParentSetsV2(Graph graph,
                                              List<Node> allLatents,
                                              List<Node> initialPool) {
        LinkedHashSet<Node> unused = new LinkedHashSet<>(initialPool);
        getObservedParentsUnion(graph, allLatents).forEach(unused::remove);

        List<Node> allChildren = getObservedChildrenUnion(graph, allLatents);

        List<List<Node>> latentSubsets = new ArrayList<>();
        for (int subsetSize = 2;
             subsetSize <= Math.min(maxLatentSubsetSize, allLatents.size());
             subsetSize++) {
            latentSubsets.addAll(getLatentSubsets(allLatents, subsetSize));
        }

        double lambda = 2.0;

        // --- Single-node pass: evaluate all candidates against the original graph
        // before applying any, so no node has first-mover advantage.
        Map<Node, List<Node>> singleNodeAssignments = new LinkedHashMap<>();
        for (Node node : new ArrayList<>(unused)) {
            List<Node> group = Collections.singletonList(node);
            List<Node> bestSubset = assignSharedGroupToBestLatentSubset(
                    group, latentSubsets, lambda);
            if (bestSubset != null) {
                singleNodeAssignments.put(node, bestSubset);
            }
        }

        for (Map.Entry<Node, List<Node>> entry : singleNodeAssignments.entrySet()) {
            Node node = entry.getKey();
            List<Node> bestSubset = entry.getValue();
            List<Node> group = Collections.singletonList(node);
            addMeasuredGroupToLatentSubset(graph, group, bestSubset);
            removeExplainedLatentEdges(graph, bestSubset, group);
            unused.remove(node);
        }

        // Pair pass: reinitialise generator after each accepted pair.
        List<Node> remaining = new ArrayList<>(unused);
        ChoiceGenerator gen = new ChoiceGenerator(remaining.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> pair = GraphUtils.asList(choice, remaining);

            int rankAboveAll = estimateRank(pair, allChildren);
            if (rankAboveAll < 1 || rankAboveAll == Integer.MAX_VALUE) continue;

            List<Node> bestSubset = assignSharedGroupToBestLatentSubset(
                    pair, latentSubsets, lambda);

            if (bestSubset != null) {
                addMeasuredGroupToLatentSubset(graph, pair, bestSubset);
                removeExplainedLatentEdges(graph, bestSubset, pair);
                pair.forEach(unused::remove);
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
                                                        List<Node> allChildren) {
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
                                                              Graph graph) {
        Map<Node, List<Node>> assignment = new LinkedHashMap<>();
        Map<Node, Double> bestScores = new LinkedHashMap<>();

        for (List<Node> group : recoveredGroups) {
            Node bestLatent = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (Node latent : allLatentNodes) {
                List<Node> childSet = getObservedChildren(graph, latent);
                int rank = estimateRank(group, childSet);
                if (rank != 1) continue;

                double score = blockStrength(group, childSet);
                if (score > bestScore) {
                    bestScore = score;
                    bestLatent = latent;
                }
            }

            if (bestLatent != null) {
                Double existing = bestScores.get(bestLatent);
                if (existing == null || bestScore > existing) {
                    assignment.put(bestLatent, group);
                    bestScores.put(bestLatent, bestScore);
                }
            }
        }

        return assignment;
    }

//    private Map<Node, List<Node>> assignParentGroupsToLatents(List<List<Node>> recoveredGroups,
//                                                              List<Node> allLatentNodes,
//                                                              Graph graph) {
//        Map<Node, List<Node>> assignment = new LinkedHashMap<>();
//        Map<Node, Double> bestScores = new LinkedHashMap<>();
//        Set<Node> claimed = new LinkedHashSet<>(); // track assigned nodes
//
//        for (List<Node> group : recoveredGroups) {
//            // Skip groups that contain already-claimed nodes.
//            if (!Collections.disjoint(group, claimed)) continue;
//
//            Node bestLatent = null;
//            double bestScore = Double.NEGATIVE_INFINITY;
//
//            for (Node latent : allLatentNodes) {
//                List<Node> childSet = getObservedChildren(graph, latent);
//                int rank = estimateRank(group, childSet);
//                if (rank != 1) continue;
//
//                double score = blockStrength(group, childSet);
//                if (score > bestScore) {
//                    bestScore = score;
//                    bestLatent = latent;
//                }
//            }
//
//            if (bestLatent != null) {
//                Double existing = bestScores.get(bestLatent);
//                if (existing == null || bestScore > existing) {
//                    assignment.put(bestLatent, group);
//                    bestScores.put(bestLatent, bestScore);
//                    claimed.addAll(group);
//                }
//            }
//        }
//
//        return assignment;
//    }

    /**
     * Assigns a group of measured inputs to the best qualifying latent subset.
     *
     * <p>A subset qualifies when two trek-separation conditions hold:
     * <ol>
     *   <li><b>Trek-rank condition.</b> The joint rank of (existing parents of S)
     *       ∪ group against the indicator union of S equals the expected rank
     *       (sum of latent ranks in S). This means the group contributes the
     *       missing channel(s) — it need not span the full space alone.</li>
     *   <li><b>Explanatory condition.</b> Conditioning on the full proposed
     *       parent set strictly reduces the cross-indicator rank for at least
     *       one adjacent latent pair in S, confirming the group explains at
     *       least one observed LL correlation.</li>
     * </ol>
     *
     * <p>Among qualifying subsets, the one with the highest block strength is
     * chosen. A second-best subset is only accepted if its strength is within
     * a relative tolerance of the best — otherwise the assignment is treated
     * as ambiguous and null is returned.
     *
     * @param group         the candidate group of measured inputs
     * @param latentSubsets the latent subsets to evaluate
     * @return the best qualifying subset, or null if none or ambiguous
     */
    private List<Node> assignSharedGroupToBestLatentSubset(List<Node> group,
                                                           List<List<Node>> latentSubsets,
                                                           double lambda) {
        List<Node> bestSubset = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double secondBestScore = Double.NEGATIVE_INFINITY;

        for (List<Node> latentSubset : latentSubsets) {
            if (latentSubset.size() < 2) continue;

            // Build proposed joint parent set: existing parents + group.
            // The group need not span the full space alone; it must contribute
            // the missing channels given what is already attached.
            List<Node> existingParents = getObservedParentsUnion(graph, latentSubset);
            List<Node> proposedParents = new ArrayList<>(existingParents);
            for (Node node : group) {
                if (!proposedParents.contains(node)) proposedParents.add(node);
            }

            // Trek-rank condition on the JOINT set (not group alone).
            int expectedRank = 0;
            for (Node latent : latentSubset) {
                expectedRank += latentRanks.getOrDefault(latent, 1);
            }

            List<Node> allIndicators = getObservedChildrenUnion(graph, latentSubset);
            if (estimateRank(proposedParents, allIndicators) != expectedRank) continue;

            // Explanatory condition: the proposed parents must explain at least
            // one existing LL edge via the conditioned rank.
            int explainedPairs = countExplainedExistingLatentPairs(
                    graph, latentSubset, proposedParents);
            if (explainedPairs == 0) continue;

            // Score: explained pairs dominate; block strength breaks ties
            // naturally, favouring direct causation over transitive paths
            // because direct correlations are stronger than multi-hop ones.
            double strength = blockStrength(proposedParents, allIndicators);
            double score = 1000.0 * explainedPairs
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

        if (bestSubset == null) return null;

        // Gap threshold: require a clear margin over the second-best candidate.
        // The 50.0 value is in block-strength units (sqrt of sum of squared
        // correlations). Direct causation produces substantially stronger
        // correlations than indirect two-hop paths, making this threshold
        // an effective discriminant between direct and transitive shared causes.
//        if (secondBestScore > Double.NEGATIVE_INFINITY
//                && bestScore - secondBestScore < 50.0) {
//            return null;
//        }

        if (secondBestScore > Double.NEGATIVE_INFINITY) {
            // Scale-free margin: the winning subset must lead by at least 10%
            // of its own score. This means the same relative advantage is
            // required whether block strengths are small (weak correlations)
            // or large (strong correlations), unlike the fixed 50.0 threshold.
            // Direct causation reliably produces a larger margin than transitive
            // paths because direct correlations are stronger than multi-hop ones.
            if (bestScore - secondBestScore < 0.1 * Math.abs(bestScore)) {
                return null;
            }
        }

        return bestSubset;
    }

    private int countExplainedExistingLatentPairs(Graph graph,
                                                  List<Node> latentSubset,
                                                  List<Node> proposedParents) {
        int explained = 0;
        int adjacent = 0;

        for (int i = 0; i < latentSubset.size(); i++) {
            Node li = latentSubset.get(i);

            for (int j = i + 1; j < latentSubset.size(); j++) {
                Node lj = latentSubset.get(j);

                if (!graph.isAdjacentTo(li, lj)) continue;

                adjacent++;

                List<Node> liIndicators = getObservedChildren(graph, li);
                List<Node> ljIndicators = getObservedChildren(graph, lj);

                if (liIndicators.isEmpty() || ljIndicators.isEmpty()) continue;

                int condRank = estimateRankConditioned(
                        liIndicators,
                        ljIndicators,
                        proposedParents);

                if (condRank == 0) {
                    explained++;
                }
            }
        }

        // Require ALL adjacent pairs to be explained, not just one.
        // A true shared parent should screen off every pair it connects,
        // not just the easiest one. This is the stricter evidence requirement.
        if (adjacent == 0) return 0;
        return (explained == adjacent) ? explained : 0;
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

    /**
     * Removes spurious transitive input edges added during higher-rank expansion.
     *
     * <p>An edge X -> Lk is removed if there exists a latent Lj such that:
     * <ol>
     *   <li>X -> Lj is in the graph (X is a direct cause of Lj)</li>
     *   <li>Lj -> Lk is in the graph (Lj is a cause of Lk)</li>
     *   <li>X is independent of Lk's indicators given Lj's indicators,
     *       meaning the X-Lk correlation is fully mediated by Lj</li>
     * </ol>
     *
     * <p>This is called after {@link #expandHigherRankParentSetsV2} to clean up
     * transitive closure edges that the rank test cannot rule out on its own.
     *
     * @param graph   the working graph, mutated in place
     * @param latents the latent nodes
     */
    private void pruneTransitiveInputEdges(Graph graph, List<Node> latents) {
        Map<Node, List<Node>> indicatorsByLatent = new LinkedHashMap<>();
        for (Node latent : latents) {
            indicatorsByLatent.put(latent, getObservedChildren(graph, latent));
        }

        for (Node latent : latents) {
            List<Node> measuredParents = new ArrayList<>();
            for (Node parent : graph.getParents(latent)) {
                if (parent.getNodeType() != NodeType.LATENT) {
                    measuredParents.add(parent);
                }
            }

            List<Node> lkIndicators = indicatorsByLatent.get(latent);
            if (lkIndicators == null || lkIndicators.isEmpty()) continue;

            for (Node x : new ArrayList<>(measuredParents)) {
                // Check if any latent Lj mediates the X -> Lk path.
                boolean fullyMediated = false;

                for (Node lj : latents) {
                    if (lj == latent) continue;
                    if (!graph.isParentOf(x, lj)) continue;
                    if (!graph.isParentOf(lj, latent) && !graph.isAncestorOf(lj, latent)) continue;

                    List<Node> ljIndicators = indicatorsByLatent.get(lj);
                    if (ljIndicators == null || ljIndicators.isEmpty()) continue;

                    // X should be independent of Lk's indicators given Lj's
                    // indicators if and only if Lj fully mediates the X-Lk path.
                    int condRank = estimateRankConditioned(
                            Collections.singletonList(x),
                            lkIndicators,
                            ljIndicators);

                    if (condRank == 0) {
                        fullyMediated = true;
                        break;
                    }
                }

                if (fullyMediated) {
                    Edge edge = graph.getEdge(x, latent);
                    if (edge != null) {
                        graph.removeEdge(edge);
                    }
                }
            }
        }
    }
}