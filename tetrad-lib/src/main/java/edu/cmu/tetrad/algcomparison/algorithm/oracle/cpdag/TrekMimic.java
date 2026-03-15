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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.independence.BlocksIndTestTs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Tsc;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.search.test.IndTestBlocksTs;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.*;
import org.ejml.simple.SimpleMatrix;

import java.io.Serial;
import java.util.*;

/**
 * The TrekMimic class implements methods for performing advanced graph-based search algorithms
 * using statistical and structural approaches. This class is an extension of various abstract
 * and utility classes, combining functionalities to manipulate, recover, and analyze latent
 * structures in a given data model. It includes methodologies to discover latent variables,
 * assess relationships, and estimate statistical properties from data.
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "TrekMimic",
        command = "trek-mimic",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class TrekMimic extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private final IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs a new instance of the TrekMimic class. This constructor initializes the
     * critical independence test mechanism required for the algorithm's operation.
     * Specifically, it instantiates a BlocksIndTestTs object and assigns it to the internal
     * test field, which is used for performing independence tests based on "Blocks-Test-TS".
     */
    public TrekMimic() {
        this.test = new BlocksIndTestTs();
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;
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

        ((BlocksIndTestTs) this.test).setBlockSpec(spec);

        edu.cmu.tetrad.search.Pc.ColliderOrientationStyle colliderOrientationStyle = edu.cmu.tetrad.search.Pc.ColliderOrientationStyle.MAX_P;

        IndependenceTest test = this.test.getTest(dataModel, parameters);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        ((IndTestBlocksTs) test).setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));

        edu.cmu.tetrad.search.Pc search = new edu.cmu.tetrad.search.Pc(test);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setKnowledge(this.knowledge);
        search.setFasStable(true);
        search.setColliderOrientationStyle(colliderOrientationStyle);
        Graph graph = search.search();

        for (int i = 0; i < spec.blocks().size(); i++) {
            Node var = spec.blockVariables().get(i);

            for (int j : spec.blocks().get(i)) {
                Node node2 = spec.dataSet().getVariables().get(j);
                graph.addNode(node2);
                graph.addDirectedEdge(var, node2);
            }
        }

        graph = GraphUtils.replaceNodes(graph, data.getVariables());

        for (Node node : data.getVariables()) {
            if (graph.getNode(node.getName()) == null) {
                graph.addNode(node);
            }
        }

        List<Node> allLatentNodes = new ArrayList<>(spec.blockVariables());
        List<Node> allChildren = getObservedChildrenUnion(graph, allLatentNodes);

        List<Node> pool = new ArrayList<>(data.getVariables());
        pool.removeAll(allChildren);

        List<Node> variables = data.getVariables();
        SimpleMatrix s = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();

        int sampleSize = data.getNumRows();
        double alpha = parameters.getDouble(Params.ALPHA);

        List<List<Node>> recoveredGroups =
                recoverCliqueRankOneGroups(pool, allChildren, variables, s, sampleSize, alpha);

        Map<Node, List<Node>> assignment = assignParentGroupsToLatents(
                recoveredGroups, allLatentNodes, graph, variables, s, sampleSize, alpha);

        for (Node latent : assignment.keySet()) {
            List<Node> parents = assignment.get(latent);

            graph.addNode(latent);
            for (Node parent : parents) {
                graph.addDirectedEdge(parent, latent);
            }
        }

        // Experimental higher-rank expansion stage.
        // You can expose this as a parameter later if you want.
        int maxLatentSubsetSize = Math.min(3, allLatentNodes.size());

        expandHigherRankParentSets(
                graph, allLatentNodes, pool, variables, s, sampleSize, alpha, maxLatentSubsetSize
        );

        return graph;
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

    private List<Node> getObservedParents(Graph graph, Node latent) {
        return getObservedParentsUnion(graph, Collections.singletonList(latent));
    }

    private List<List<Node>> getLatentSubsets(List<Node> latentNodes, int subsetSize) {
        List<List<Node>> subsets = new ArrayList<>();

        if (subsetSize < 1 || subsetSize > latentNodes.size()) {
            return subsets;
        }

        ChoiceGenerator gen = new ChoiceGenerator(latentNodes.size(), subsetSize);
        int[] choice;

        while ((choice = gen.next()) != null) {
            subsets.add(GraphUtils.asList(choice, latentNodes));
        }

        return subsets;
    }

    private void expandHigherRankParentSets(Graph graph,
                                            List<Node> allLatentNodes,
                                            List<Node> initialPool,
                                            List<Node> variables,
                                            SimpleMatrix s,
                                            int sampleSize,
                                            double alpha,
                                            int maxLatentSubsetSize) {

        System.out.println("Expanding higher rank parent sets for latent nodes: " + allLatentNodes);

        LinkedHashSet<Node> unused = new LinkedHashSet<>(initialPool);
        unused.removeAll(getObservedParentsUnion(graph, allLatentNodes));

        List<ExpansionState> currentStates = new ArrayList<>();

        // Initialize with singleton latent states of rank 1.
        for (Node latent : allLatentNodes) {
            List<Node> latentSubset = Collections.singletonList(latent);
            List<Node> parentSet = getObservedParents(graph, latent);

            int rank = estimateRank(parentSet,
                    getObservedChildrenUnion(graph, latentSubset),
                    variables, s, sampleSize, alpha);

            System.out.println("Initial singleton state: " + latentSubset
                    + " parentSet = " + parentSet
                    + " rank = " + rank);

            if (rank == 1) {
                currentStates.add(new ExpansionState(latentSubset, parentSet, rank));
            }
        }

        int maxSize = 2;// Math.min(maxLatentSubsetSize, allLatentNodes.size());

        for (int targetSize = 2; targetSize <= maxSize; targetSize++) {
            System.out.println("Expanding states to latent subset size " + targetSize);

            List<ExpansionState> nextStates = new ArrayList<>();
            Set<String> seen = new HashSet<>();

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

                    String key = canonicalNodeListKey(expandedSubset);
//                    if (seen.contains(key)) {
//                        continue;
//                    }

                    int targetRank = expandedSubset.size();

                    // THIS is the important "both sides" union:
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

                    if (!result.success()) {
                        continue;
                    }

                    System.out.println("Successful expansion: " + currentSubset
                            + " + " + newLatent
                            + " -> " + result.expandedLatents()
                            + " newParents = " + result.newParents()
                            + " fullParentSet = " + result.fullParentSet());

                    // Commit only once.
                    for (Node parent : result.newParents()) {
                        for (Node latent : result.expandedLatents()) {
                            if (!graph.isParentOf(parent, latent)) {
//                                graph.addDirectedEdge(parent, latent);
                                System.out.println("Adding parent " + parent + " to latent " + latent);
                            }
                        }
                        // Leave commented out if parents may be reused across multiple expansions.
                        // unused.remove(parent);
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

//                    seen.add(key);
                }
            }

            currentStates = nextStates;
            if (currentStates.isEmpty()) {
                System.out.println("No successful expansions at size " + targetSize);
                break;
            }
        }
    }

    private String canonicalNodeListKey(List<Node> nodes) {
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                buf.append("|");
            }
            buf.append(nodes.get(i).getName());
        }

        return buf.toString();
    }

    private void removeExplainedLatentEdges(Graph graph,
                                            List<Node> latentSubset,
                                            List<Node> newParents,
                                            List<Node> variables, SimpleMatrix s,
                                            int sampleSize, double alpha) {
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

            int rank = estimateRankConditioned(getObservedChildren(graph, x), getObservedChildren(graph, y),
                    newParents,
                    variables,
                    s, sampleSize, alpha);

            if (explained && rank == 0) {
                graph.removeEdge(edge);
            }
        }
    }

    private ExpansionResult expandParentSetForLatentExpansion(Graph graph,
                                                              List<Node> latentSubset,
                                                              List<Node> baseParents,
                                                              int targetRank,
                                                              Set<Node> unused,
                                                              List<Node> variables,
                                                              SimpleMatrix s,
                                                              int sampleSize,
                                                              double alpha) {
        List<Node> expandedLatents = new ArrayList<>(latentSubset);
        List<Node> childSet = getObservedChildrenUnion(graph, expandedLatents);

        // Start from inherited parents from both sides.
        LinkedHashSet<Node> fullParents = new LinkedHashSet<>(baseParents);

        // These are only the parents newly discovered during this expansion step.
        List<Node> newParents = new ArrayList<>();

        int currentRank = estimateRank(
                new ArrayList<>(fullParents),
                childSet,
                variables,
                s,
                sampleSize,
                alpha
        );

        System.out.println("Expanding latent subset " + expandedLatents
                + " from fullParentSet = " + fullParents
                + " currentRank = " + currentRank
                + " targetRank = " + targetRank);

        // Phase 1: If needed, add parents until the overall rank reaches targetRank.
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

                List<Node> proposedParents = new ArrayList<>(fullParents);
                proposedParents.add(candidate);

                int proposedRank = estimateRank(
                        proposedParents,
                        childSet,
                        variables,
                        s,
                        sampleSize,
                        alpha
                );

                if (proposedRank <= currentRank) {
                    continue;
                }

                if (proposedRank > targetRank) {
                    continue;
                }

                double strength = blockStrength(proposedParents, childSet, variables, s);

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

                System.out.println("Phase 1 added " + bestAdd
                        + ", fullParentSet = " + fullParents
                        + ", currentRank = " + currentRank);
            }
        }

        // If we still didn't reach the required rank, fail.
        if (currentRank != targetRank) {
            System.out.println("Failed to reach target rank for expanded subset " + expandedLatents
                    + "; discarding new parents " + newParents);
            return new ExpansionResult(
                    false,
                    expandedLatents,
                    new ArrayList<>(baseParents),
                    Collections.emptyList()
            );
        }

        // Phase 2: Look for additional SHARED parents that help explain internal latent-latent edges.
        //
        // For now, a candidate is considered useful if, after adding it to the conditioning set,
        // it makes at least one latent-latent child-set conditional rank drop to 0 where it was
        // previously positive. This is the "common parent" signal you were after.
        boolean foundShared;
        do {
            foundShared = false;

            Node bestShared = null;
            int bestExplainedPairs = 0;
            double bestStrength = Double.NEGATIVE_INFINITY;

            for (Node candidate : unused) {
                if (fullParents.contains(candidate)) {
                    continue;
                }

                List<Node> proposedCond = new ArrayList<>(fullParents);
                proposedCond.add(candidate);

                int explainedPairs = countExplainedLatentPairs(
                        graph,
                        expandedLatents,
                        proposedCond,
                        variables,
                        s,
                        sampleSize,
                        alpha
                );

                if (explainedPairs == 0) {
                    continue;
                }

                double strength = blockStrength(proposedCond, childSet, variables, s);

                if (explainedPairs > bestExplainedPairs ||
                        (explainedPairs == bestExplainedPairs && strength > bestStrength)) {
                    bestExplainedPairs = explainedPairs;
                    bestStrength = strength;
                    bestShared = candidate;
                }
            }

            if (bestShared != null) {
                fullParents.add(bestShared);
                newParents.add(bestShared);
                foundShared = true;

                System.out.println("Phase 2 added shared parent " + bestShared
                        + ", fullParentSet = " + fullParents
                        + ", explained latent pairs = "
                        + countExplainedLatentPairs(graph, expandedLatents, fullParents, variables, s, sampleSize, alpha));
            }
        } while (foundShared);

        System.out.println("Reached target rank for expanded subset " + expandedLatents
                + "; fullParentSet = " + fullParents
                + "; newParents = " + newParents);

        return new ExpansionResult(
                true,
                expandedLatents,
                new ArrayList<>(fullParents),
                new ArrayList<>(newParents)
        );
    }

    private int countExplainedLatentPairs(Graph graph,
                                          List<Node> latentSubset,
                                          Collection<Node> condSet,
                                          List<Node> variables,
                                          SimpleMatrix s,
                                          int sampleSize,
                                          double alpha) {
        int explained = 0;

        ChoiceGenerator gen = new ChoiceGenerator(latentSubset.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = latentSubset.get(choice[0]);
            Node y = latentSubset.get(choice[1]);

            Edge edge = graph.getEdge(x, y);
            if (edge == null) {
                // Still count the statistical explanation even if the edge is already gone.
                // That makes the score stable during search.
            }

            int rank = estimateRankConditioned(
                    getObservedChildren(graph, x),
                    getObservedChildren(graph, y),
                    new ArrayList<>(condSet),
                    variables,
                    s,
                    sampleSize,
                    alpha
            );

            if (rank == 0) {
                explained++;
            }
        }

        return explained;
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

    private boolean uncorrelated(Node a, Node b, List<Node> variables, SimpleMatrix s, int sampleSize, double alpha) {
        int i = variables.indexOf(a);
        int j = variables.indexOf(b);

        double r = s.get(i, j);

        if (Math.abs(r) >= 1.0) {
            return false;
        }

        double z = 0.5 * Math.log((1.0 + r) / (1.0 - r)) * Math.sqrt(sampleSize - 3.0);
        double cutoff = StatUtils.getZForAlpha(alpha);

        return Math.abs(z) < cutoff;
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

            System.out.println("Evaluating pair " + pair + " with rank " + rank);

            if (rank != 1) {
                continue;
            }

            pairs.add(pair);
            System.out.println("Added pair " + pair);
        }

        Graph pairGraph = buildRankOnePairGraph(pool, allChildren, variables, s, sampleSize, alpha);

        for (List<Node> seedPair : pairs) {
            List<Node> group = growCliqueRankOneSet(seedPair, pool, pairGraph, allChildren, variables, s, sampleSize, alpha);
            groups.add(new HashSet<>(group));
        }

        List<List<Node>> _groups = new ArrayList<>();

        for (Set<Node> group : groups) {
            _groups.add(new ArrayList<>(group));
        }

        return _groups;
    }

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

    private double blockStrengthToLatents(List<Node> group,
                                          Graph graph,
                                          Collection<Node> latents,
                                          List<Node> variables,
                                          SimpleMatrix s) {
        List<Node> childSet = getObservedChildrenUnion(graph, latents);
        return blockStrength(group, childSet, variables, s);
    }

    private int estimateRankToLatents(List<Node> group,
                                      Graph graph,
                                      Collection<Node> latents,
                                      List<Node> variables,
                                      SimpleMatrix s,
                                      int sampleSize,
                                      double alpha) {
        List<Node> childSet = getObservedChildrenUnion(graph, latents);
        return estimateRank(group, childSet, variables, s, sampleSize, alpha);
    }

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
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Trek-Mimic using " + this.test.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.DEPTH);
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

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

    private record ClustersAtRankAboveIndicators(List<Node> pool, List<List<Node>> groups) {
    }
}

