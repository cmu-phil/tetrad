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
     * Represents a candidate higher-rank parent group together with the latent subset
     * from which it was originally generated.
     */
    private static final class HigherRankCandidate {
        private final List<Node> group;
        private final List<Node> latentSubset;
        private final int rank;

        private HigherRankCandidate(List<Node> group, List<Node> latentSubset, int rank) {
            this.group = new ArrayList<>(group);
            this.latentSubset = new ArrayList<>(latentSubset);
            this.rank = rank;
        }

        public List<Node> getGroup() {
            return new ArrayList<>(this.group);
        }

        public List<Node> getLatentSubset() {
            return new ArrayList<>(this.latentSubset);
        }

        public int getRank() {
            return this.rank;
        }
    }

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

        Graph structureGraph = new EdgeListGraph(spec.blockVariables());

        for (int i = 0; i < spec.blocks().size(); i++) {
            for (int j = 0; j < spec.blocks().size(); j++) {
                Edge edge = graph.getEdge(spec.blockVariables().get(i), spec.blockVariables().get(j));
                if (edge == null) continue;
                structureGraph.addEdge(edge);
            }
        }

        for (Edge edge : structureGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> parentsx = graph.getParents(x);
            List<Node> parentsy = graph.getParents(y);

            List<Node> childrenx = getObservedChildren(graph, x);
            List<Node> childreny = getObservedChildren(graph, y);

            parentsx.removeIf(n -> n.getNodeType() == NodeType.LATENT);
            parentsy.removeIf(n -> n.getNodeType() == NodeType.LATENT);

            boolean allUncorrelatedxy = true;
            boolean pairTestedxy = false;

            for (Node parentx : parentsx) {
                for (Node childy : childreny) {
                    if (!uncorrelated(parentx, childy, variables, s, sampleSize, alpha)) {
                        allUncorrelatedxy = false;
                    }
                    pairTestedxy = true;
                }
            }

            if (allUncorrelatedxy && pairTestedxy) {
                graph.removeEdge(edge);
                graph.addDirectedEdge(y, x);
                continue;
            }

            boolean allUncorrelatedyx = true;
            boolean pairTestedyx = false;

            for (Node parenty : parentsy) {
                for (Node childx : childrenx) {
                    if (!uncorrelated(parenty, childx, variables, s, sampleSize, alpha)) {
                        allUncorrelatedyx = false;
                    }
                    pairTestedyx = true;
                }
            }

            if (allUncorrelatedyx && pairTestedyx) {
                graph.removeEdge(edge);
                graph.addDirectedEdge(x, y);
            }
        }

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

    private List<List<Node>> allLatentSubsetsOfSize(List<Node> latentNodes, int subsetSize) {
        List<List<Node>> subsets = new ArrayList<>();

        if (subsetSize < 1 || subsetSize > latentNodes.size()) {
            return subsets;
        }

        ChoiceGenerator gen = new ChoiceGenerator(latentNodes.size(), subsetSize);
        int[] choice;

        while ((choice = gen.next()) != null) {
            subsets.add(GraphUtils.asList(choice, latentNodes));
        }

        System.out.println("All Latent subsets: " + subsets);

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
        int currentRank = estimateRank(new ArrayList<>(getObservedParentsUnion(graph, allLatentNodes)),
                getObservedChildrenUnion(graph, allLatentNodes), variables, s, sampleSize, alpha);

        System.out.println("Expanding higher rank parent sets for latent nodes: " + allLatentNodes
                + " current rank = " + currentRank);

        LinkedHashSet<Node> unused = new LinkedHashSet<>(initialPool);

        // Remove any variables already used as observed parents of any latent.
        getObservedParentsUnion(graph, allLatentNodes).forEach(unused::remove);

        int maxSize = Math.min(maxLatentSubsetSize, allLatentNodes.size());

        for (int subsetSize = 2; subsetSize <= maxSize; subsetSize++) {
            List<List<Node>> allLatentSubsetsOfSize = allLatentSubsetsOfSize(allLatentNodes, subsetSize);
            List<HigherRankCandidate> candidates = new ArrayList<>();

            // Step 1: generate candidate groups for this rank.
            for (List<Node> latentSubset : allLatentSubsetsOfSize) {
                System.out.println("Generating candidate for latent subset: " + latentSubset);

                int _currentRank = estimateRank(new ArrayList<>(getObservedParentsUnion(graph, latentSubset)),
                        getObservedChildrenUnion(graph, allLatentNodes), variables, s, sampleSize, alpha);

                System.out.println("Expanding higher rank parent sets for latent nodes: " + latentSubset
                        + " current rank = " + _currentRank);

                List<Node> newParents = expandParentSetForLatentSubset(
                        graph, latentSubset, _currentRank, unused, variables, s, sampleSize, alpha
                );

                if (newParents.isEmpty()) {
                    continue;
                }

                candidates.add(new HigherRankCandidate(newParents, latentSubset, subsetSize));
            }

            for (HigherRankCandidate candidate : candidates) {
                System.out.println();
                System.out.println("Candidate: " + candidate);
                System.out.println("Candidate group: " + candidate.getGroup());
                System.out.println("Candidate latent subset: " + candidate.getLatentSubset());
                System.out.println("Candidate latent subset size: " + candidate.getLatentSubset().size());
                System.out.println("Candidate rank: " + candidate.getRank());
                System.out.println();
            }

            // Group candidates by canonicalized parent group.
            Map<String, List<HigherRankCandidate>> candidateMap = new LinkedHashMap<>();
            Map<String, List<Node>> keyToGroup = new LinkedHashMap<>();

            for (HigherRankCandidate candidate : candidates) {
                List<Node> group = new ArrayList<>(candidate.getGroup());
                group.sort(Comparator.comparingInt(variables::indexOf));

                String key = canonicalNodeListKey(group);

                candidateMap.computeIfAbsent(key, k -> new ArrayList<>()).add(candidate);
                keyToGroup.putIfAbsent(key, group);
            }

            // Step 2: assign each distinct group once, considering only the latent subsets
            // that originally generated that group.
            for (String key : candidateMap.keySet()) {
                List<Node> group = keyToGroup.get(key);
                List<List<Node>> candidateLatentSubsets = new ArrayList<>();

                for (HigherRankCandidate candidate : candidateMap.get(key)) {
                    candidateLatentSubsets.add(candidate.getLatentSubset());
                }

                System.out.println("For group " + group + ", candidate latent subsets: " + candidateLatentSubsets);

                List<Node> bestSubset = assignHigherRankGroupToBestLatentSubset(
                        group, candidateLatentSubsets, graph, variables, s, sampleSize, alpha
                );

                if (bestSubset == null) {
                    continue;
                }

                System.out.println("Assigning higher-rank group " + group + " to latent subset " + bestSubset);

                for (Node parent : group) {
                    for (Node latent : bestSubset) {
                        if (!graph.isParentOf(parent, latent)) {
                            graph.addDirectedEdge(parent, latent);
                        }
                    }
                    unused.remove(parent);
                }

                removeExplainedLatentEdges(graph, bestSubset, group, variables, s, sampleSize, alpha);
            }

            // Recompute the stage rank after successful additions at this size.
            currentRank = estimateRank(new ArrayList<>(getObservedParentsUnion(graph, allLatentNodes)),
                    getObservedChildrenUnion(graph, allLatentNodes), variables, s, sampleSize, alpha);

            System.out.println("After subset size " + subsetSize + ", updated current rank = " + currentRank);
        }
    }

    private List<Node> assignHigherRankGroupToBestLatentSubset(List<Node> group,
                                                               List<List<Node>> latentSubsets,
                                                               Graph graph,
                                                               List<Node> variables,
                                                               SimpleMatrix s,
                                                               int sampleSize,
                                                               double alpha) {

        System.out.println("B: Assigning higher-rank group " + group + " to best latent subset");
        System.out.println("B: Latent subsets: " + latentSubsets);

        List<Node> bestSubset = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int targetRank = -1;

        for (List<Node> latentSubset : latentSubsets) {
            int r = latentSubset.size();

            List<Node> childSet = getObservedChildrenUnion(graph, latentSubset);
            List<Node> existingParents = getObservedParentsUnion(graph, latentSubset);

            List<Node> proposedParents = new ArrayList<>(existingParents);
            for (Node node : group) {
                if (!proposedParents.contains(node)) {
                    proposedParents.add(node);
                }
            }

            int rank = estimateRank(proposedParents, childSet, variables, s, sampleSize, alpha);

            if (rank != r) {
                continue;
            }

            double score = blockStrength(proposedParents, childSet, variables, s);

            System.out.println("B: Latent subset " + latentSubset + " rank " + rank + " score " + score);

            // Slight preference for the subset from which the group explains the
            // child set most strongly.
            if (score > bestScore) {
                bestScore = score;
                bestSubset = latentSubset;
                targetRank = r;
            }
        }

        if (bestSubset != null) {
            System.out.println("Best subset for higher-rank group " + group
                    + " is " + bestSubset
                    + " with target rank " + targetRank
                    + " and score " + bestScore);
        } else {
            System.out.println("No admissible latent subset found for higher-rank group " + group);
        }

        return bestSubset;
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

    private List<Node> expandParentSetForLatentSubset(Graph graph,
                                                      List<Node> latentSubset,
                                                      int currentRank,
                                                      Set<Node> unused,
                                                      List<Node> variables,
                                                      SimpleMatrix s,
                                                      int sampleSize,
                                                      double alpha) {
//        int targetRank = latentSubset.size();

        List<Node> childSet = getObservedChildrenUnion(graph, latentSubset);
        LinkedHashSet<Node> currentParents = new LinkedHashSet<>(getObservedParentsUnion(graph, latentSubset));
        List<Node> newlyAdded = new ArrayList<>();


        System.out.println("Coming expandParentSetForLatentSubset " + latentSubset + " with current rank " + currentRank
                + /*+ " target rank " + targetRank + "*/ "currentParents = " + currentParents + " childSet = " + childSet);

//        if (currentRank >= targetRank) {
//            System.out.println("Already at or above target rank; returning empty.");
//            return newlyAdded;
//        }

        boolean changed = true;

        while (changed) {
            changed = false;

            Node bestAdd = null;
            double bestStrength = Double.NEGATIVE_INFINITY;
            int bestRank = -1;

            for (Node candidate : unused) {
                if (currentParents.contains(candidate)) {
                    continue;
                }

                List<Node> proposed = new ArrayList<>(currentParents);
                proposed.add(candidate);

                int proposedRank = estimateRank(proposed, childSet, variables, s, sampleSize, alpha);

                // Before reaching target rank, require a STRICT rank increase,
                // but do not allow overshooting target rank.
                if (proposedRank <= currentRank) {
                    continue;
                }

                double strength = blockStrength(proposed, childSet, variables, s);

                // Prefer higher proposed rank first, then stronger block strength.
                if (proposedRank > bestRank ||
                        (proposedRank == bestRank && strength > bestStrength)) {
                    bestRank = proposedRank;
                    bestStrength = strength;
                    bestAdd = candidate;
                }
            }

            if (bestAdd != null) {
                currentParents.add(bestAdd);
                newlyAdded.add(bestAdd);
                currentRank = estimateRank(new ArrayList<>(currentParents), childSet, variables, s, sampleSize, alpha);
                changed = true;

                System.out.println("Added " + bestAdd + ", currentParents = " + currentParents +
                        ", currentRank = " + currentRank);
            }
        }

//        if (currentRank != targetRank) {
//            System.out.println("Did not reach target rank; discarding additions. (Newly added = " + newlyAdded + ")");
//            return new ArrayList<>();
//        }

        System.out.println("Reached higher rank; returning newlyAdded = " + newlyAdded);

        return newlyAdded;
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
                                                              List<Node> latentNodes,
                                                              Graph graph,
                                                              List<Node> variables,
                                                              SimpleMatrix s,
                                                              int sampleSize,
                                                              double alpha) {
        Map<Node, List<Node>> assignment = new LinkedHashMap<>();

        for (List<Node> group : recoveredGroups) {
            Node bestLatent = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (Node latent : latentNodes) {
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
}

