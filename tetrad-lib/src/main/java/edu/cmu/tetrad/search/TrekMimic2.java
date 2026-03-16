package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid second version of Trek-MIMIC parent recovery.
 *
 * <p>This version deliberately keeps the singleton-parent recovery logic from the
 * original TrekMimic, since that logic appears to work well empirically. It then
 * optionally adds a second higher-rank expansion phase for measured groups shared
 * by multiple latents.</p>
 *
 * <p>Expected input state:
 * <ul>
 *     <li>The graph already contains the latent nodes.</li>
 *     <li>The graph already contains latent -> indicator edges.</li>
 *     <li>The initial pool contains measured non-indicator variables that may be
 *     parents of one or more latents.</li>
 * </ul>
 * </p>
 */
public final class TrekMimic2 {

    /**
     * Whether to run the higher-rank expansion phase.
     */
    private boolean doHigherRankExpansion = true;

    /**
     * Maximum latent subset size to consider in higher-rank expansion.
     */
    private int maxLatentSubsetSize = 3;

    /**
     * Runs the hybrid parent recovery procedure.
     *
     * @param graph the graph containing latent nodes and latent -> indicator edges
     * @param initialPool the measured non-indicator pool
     * @param allLatents the latent nodes
     * @param variables measured variables in matrix order
     * @param s covariance or correlation matrix
     * @param sampleSize sample size
     * @param alpha rank-test alpha
     */
    public void recoverMeasuredParentsHybrid(Graph graph,
                                             List<Node> initialPool,
                                             List<Node> allLatents,
                                             List<Node> variables,
                                             SimpleMatrix s,
                                             int sampleSize,
                                             double alpha) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        if (initialPool == null || allLatents == null || variables == null || s == null) {
            throw new NullPointerException("Arguments must not be null.");
        }

        List<Node> pool = new ArrayList<>(initialPool);
        List<Node> latents = new ArrayList<>(allLatents);
        latents.sort(Comparator.comparing(Node::getName));

        // ============================================================
        // Phase 1: singleton parent recovery (borrowed from TrekMimic)
        // ============================================================
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

        // ============================================================
        // Phase 2: optional higher-rank / shared-parent expansion
        // ============================================================
        if (doHigherRankExpansion) {
            expandHigherRankParentSets(
                    graph,
                    latents,
                    pool,
                    variables,
                    s,
                    sampleSize,
                    alpha,
                    maxLatentSubsetSize
            );
        }
    }

    /**
     * Returns all observed children of the supplied latent collection.
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
     * Returns observed children of a single latent.
     */
    private List<Node> getObservedChildren(Graph graph, Node latent) {
        return getObservedChildrenUnion(graph, Collections.singletonList(latent));
    }

    /**
     * Returns all observed parents of the supplied latent collection.
     */
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

    /**
     * Returns observed parents of a single latent.
     */
    private List<Node> getObservedParents(Graph graph, Node latent) {
        return getObservedParentsUnion(graph, Collections.singletonList(latent));
    }

    /**
     * Recovers clique-like measured groups that behave as rank-1 above all observed indicators.
     *
     * <p>This is borrowed from the original TrekMimic logic.</p>
     */
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
            int rank = estimateRank(pair, allChildren, variables, s, sampleSize, alpha);

            if (rank != 1) {
                continue;
            }

            pairs.add(pair);
        }

        Graph pairGraph = buildRankOnePairGraph(pool, allChildren, variables, s, sampleSize, alpha);

        for (List<Node> seedPair : pairs) {
            List<Node> group = growCliqueRankOneSet(
                    seedPair, pool, pairGraph, allChildren, variables, s, sampleSize, alpha
            );
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

    /**
     * Builds a graph whose adjacencies indicate pairwise rank-1 behavior above all indicators.
     */
    private Graph buildRankOnePairGraph(List<Node> pool,
                                        List<Node> allChildren,
                                        List<Node> variables,
                                        SimpleMatrix s,
                                        int sampleSize,
                                        double alpha) {
        Graph pairGraph = new EdgeListGraph(pool);

        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> pair = GraphUtils.asList(choice, pool);

            int rank = estimateRank(pair, allChildren, variables, s, sampleSize, alpha);

            if (rank == 1) {
                pairGraph.addUndirectedEdge(pair.get(0), pair.get(1));
            }
        }

        return pairGraph;
    }

    /**
     * Grows a seed pair into a larger clique-like rank-1 group above all indicators.
     */
    private List<Node> growCliqueRankOneSet(List<Node> seedPair,
                                            List<Node> pool,
                                            Graph pairGraph,
                                            List<Node> allChildren,
                                            List<Node> variables,
                                            SimpleMatrix s,
                                            int sampleSize,
                                            double alpha) {
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

                int rank = estimateRank(proposed, allChildren, variables, s, sampleSize, alpha);

                if (rank != 1) {
                    continue;
                }

                double strength = blockStrength(proposed, allChildren, variables, s);

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

    /**
     * Assigns each recovered singleton group to the best latent by rank-1 fit and block strength.
     *
     * <p>This is also borrowed from the original TrekMimic logic.</p>
     */
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

                int rank = estimateRank(group, childSet, variables, s, sampleSize, alpha);

                if (rank != 1) {
                    continue;
                }

                double score = blockStrength(group, childSet, variables, s);

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

    /**
     * Optional higher-rank expansion stage for shared parent groups.
     *
     * <p>This is a compact carry-over of the original TrekMimic higher-rank idea.</p>
     */
    private void expandHigherRankParentSets(Graph graph,
                                            List<Node> allLatentNodes,
                                            List<Node> initialPool,
                                            List<Node> variables,
                                            SimpleMatrix s,
                                            int sampleSize,
                                            double alpha,
                                            int maxLatentSubsetSize) {
        LinkedHashSet<Node> unused = new LinkedHashSet<>(initialPool);
        unused.removeAll(getObservedParentsUnion(graph, allLatentNodes));

        List<ExpansionState> currentStates = new ArrayList<>();

        for (Node latent : allLatentNodes) {
            List<Node> latentSubset = Collections.singletonList(latent);
            List<Node> parentSet = getObservedParents(graph, latent);

            int rank = estimateRank(
                    parentSet,
                    getObservedChildrenUnion(graph, latentSubset),
                    variables,
                    s,
                    sampleSize,
                    alpha
            );

            if (rank == 1) {
                currentStates.add(new ExpansionState(latentSubset, parentSet, rank));
            }
        }

        int maxSize = Math.min(maxLatentSubsetSize, allLatentNodes.size());

        for (int targetSize = 2; targetSize <= maxSize; targetSize++) {
            List<ExpansionState> nextStates = new ArrayList<>();

            for (ExpansionState state : currentStates) {
                List<Node> currentSubset = state.getLatentSubset();
                List<Node> currentParents = state.getParentSet();

                for (Node newLatent : allLatentNodes) {
                    if (currentSubset.contains(newLatent)) {
                        continue;
                    }

                    List<Node> expandedSubset = new ArrayList<>(currentSubset);
                    expandedSubset.add(newLatent);
                    expandedSubset.sort(Comparator.comparing(Node::getName));

                    int targetRank = expandedSubset.size();

                    LinkedHashSet<Node> baseParents = new LinkedHashSet<>(currentParents);
                    baseParents.addAll(getObservedParents(graph, newLatent));

                    ExpansionResult result = expandParentSetForLatentExpansion(
                            graph,
                            expandedSubset,
                            new ArrayList<>(baseParents),
                            targetRank,
                            unused,
                            variables,
                            s,
                            sampleSize,
                            alpha
                    );

                    if (!result.success() || result.newParents().isEmpty()) {
                        continue;
                    }

                    for (Node parent : result.newParents()) {
                        for (Node latent : result.expandedLatents()) {
                            if (!graph.isParentOf(parent, latent)) {
                                graph.addDirectedEdge(parent, latent);
                            }
                        }
                    }

                    removeExplainedLatentEdges(
                            graph,
                            result.expandedLatents(),
                            result.newParents(),
                            variables,
                            s,
                            sampleSize,
                            alpha
                    );

                    nextStates.add(new ExpansionState(
                            result.expandedLatents(),
                            result.fullParentSet(),
                            targetRank
                    ));
                }
            }

            currentStates = nextStates;

            if (currentStates.isEmpty()) {
                break;
            }
        }
    }

    /**
     * Expands a parent set until it realizes the target rank above the supplied latent subset.
     */
    private ExpansionResult expandParentSetForLatentExpansion(Graph graph,
                                                              List<Node> latentSubset,
                                                              List<Node> baseParents,
                                                              int targetRank,
                                                              Set<Node> unused,
                                                              List<Node> variables,
                                                              SimpleMatrix s,
                                                              int sampleSize,
                                                              double alpha) {
        List<Node> childSet = getObservedChildrenUnion(graph, latentSubset);

        LinkedHashSet<Node> fullParents = new LinkedHashSet<>(baseParents);
        List<Node> newParents = new ArrayList<>();

        int currentRank = estimateRank(
                new ArrayList<>(fullParents),
                childSet,
                variables,
                s,
                sampleSize,
                alpha
        );

        boolean changed = true;

        while (changed && currentRank < targetRank) {
            changed = false;

            Node bestAdd = null;
            double bestStrength = Double.NEGATIVE_INFINITY;
            int bestRank = -1;

            for (Node candidate : unused) {
                if (fullParents.contains(candidate)) {
                    continue;
                }

                List<Node> proposed = new ArrayList<>(fullParents);
                proposed.add(candidate);

                int proposedRank = estimateRank(
                        proposed,
                        childSet,
                        variables,
                        s,
                        sampleSize,
                        alpha
                );

                if (proposedRank <= currentRank || proposedRank > targetRank) {
                    continue;
                }

                double strength = blockStrength(proposed, childSet, variables, s);

                if (proposedRank > bestRank ||
                        (proposedRank == bestRank && strength > bestStrength)) {
                    bestRank = proposedRank;
                    bestStrength = strength;
                    bestAdd = candidate;
                }
            }

            if (bestAdd != null) {
                fullParents.add(bestAdd);
                newParents.add(bestAdd);

                currentRank = estimateRank(
                        new ArrayList<>(fullParents),
                        childSet,
                        variables,
                        s,
                        sampleSize,
                        alpha
                );

                changed = true;
            }
        }

        if (currentRank != targetRank) {
            return new ExpansionResult(
                    false,
                    latentSubset,
                    new ArrayList<>(baseParents),
                    Collections.emptyList()
            );
        }

        return new ExpansionResult(
                true,
                latentSubset,
                new ArrayList<>(fullParents),
                new ArrayList<>(newParents)
        );
    }

    /**
     * Removes latent-latent edges explained by new shared parents.
     */
    private void removeExplainedLatentEdges(Graph graph,
                                            List<Node> latentSubset,
                                            List<Node> newParents,
                                            List<Node> variables,
                                            SimpleMatrix s,
                                            int sampleSize,
                                            double alpha) {
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
                    newParents,
                    variables,
                    s,
                    sampleSize,
                    alpha
            );

            if (explained && rank == 0) {
                graph.removeEdge(edge);
            }
        }
    }

    /**
     * Estimates the rank between two measured sets.
     */
    private int estimateRank(List<Node> xSet,
                             List<Node> ySet,
                             List<Node> variables,
                             SimpleMatrix s,
                             int sampleSize,
                             double alpha) {
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

    /**
     * Estimates the rank between two measured sets conditioned on a conditioning set.
     */
    private int estimateRankConditioned(List<Node> xSet,
                                        List<Node> ySet,
                                        List<Node> cond,
                                        List<Node> variables,
                                        SimpleMatrix s,
                                        int sampleSize,
                                        double alpha) {
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

    /**
     * Simple cross-block strength score.
     */
    private double blockStrength(List<Node> xSet,
                                 List<Node> ySet,
                                 List<Node> variables,
                                 SimpleMatrix s) {
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
     * Enables or disables the higher-rank expansion phase.
     */
    public void setDoHigherRankExpansion(boolean doHigherRankExpansion) {
        this.doHigherRankExpansion = doHigherRankExpansion;
    }

    /**
     * Sets the maximum latent subset size for higher-rank expansion.
     */
    public void setMaxLatentSubsetSize(int maxLatentSubsetSize) {
        this.maxLatentSubsetSize = maxLatentSubsetSize;
    }

    /**
     * State for higher-rank expansion.
     */
    private static final class ExpansionState {
        private final List<Node> latentSubset;
        private final List<Node> parentSet;
        private final int rank;

        private ExpansionState(List<Node> latentSubset, List<Node> parentSet, int rank) {
            this.latentSubset = new ArrayList<>(latentSubset);
            this.parentSet = new ArrayList<>(parentSet);
            this.rank = rank;
        }

        public List<Node> getLatentSubset() {
            return new ArrayList<>(this.latentSubset);
        }

        public List<Node> getParentSet() {
            return new ArrayList<>(this.parentSet);
        }

        public int getRank() {
            return this.rank;
        }
    }

    /**
     * Result of higher-rank parent-set expansion.
     */
    private static final class ExpansionResult {
        private final boolean success;
        private final List<Node> expandedLatents;
        private final List<Node> fullParentSet;
        private final List<Node> newParents;

        private ExpansionResult(boolean success,
                                List<Node> expandedLatents,
                                List<Node> fullParentSet,
                                List<Node> newParents) {
            this.success = success;
            this.expandedLatents = new ArrayList<>(expandedLatents);
            this.fullParentSet = new ArrayList<>(fullParentSet);
            this.newParents = new ArrayList<>(newParents);
        }

        public boolean success() {
            return success;
        }

        public List<Node> expandedLatents() {
            return new ArrayList<>(expandedLatents);
        }

        public List<Node> fullParentSet() {
            return new ArrayList<>(fullParentSet);
        }

        public List<Node> newParents() {
            return new ArrayList<>(newParents);
        }
    }
}