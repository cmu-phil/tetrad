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

        List<Node> latentNodes = new ArrayList<>(spec.blockVariables());
        List<Node> allChildren = getObservedChildrenUnion(graph, latentNodes);

        List<Node> pool = new ArrayList<>(data.getVariables());
        pool.removeAll(allChildren);

        List<Node> variables = data.getVariables();
        SimpleMatrix s = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();

        int sampleSize = data.getNumRows();
        double alpha = parameters.getDouble(Params.ALPHA);

        List<List<Node>> recoveredGroups =
                recoverCliqueRankOneGroups(pool, allChildren, variables, s, sampleSize, alpha);

        Map<Node, List<Node>> assignment = assignParentGroupsToLatents(
                recoveredGroups, latentNodes, graph, variables, s, sampleSize, alpha);

        for (Node latent : assignment.keySet()) {
            List<Node> parents = assignment.get(latent);

            graph.addNode(latent);
            for (Node parent : parents) {
                graph.addDirectedEdge(parent, latent);
            }
        }

        // Experimental higher-rank expansion stage.
        // You can expose this as a parameter later if you want.
        int maxLatentSubsetSize = Math.min(3, latentNodes.size());

        expandHigherRankParentSets(
                graph, latentNodes, pool, variables, s, sampleSize, alpha, maxLatentSubsetSize
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
                                            List<Node> latentNodes,
                                            List<Node> initialPool,
                                            List<Node> variables,
                                            SimpleMatrix s,
                                            int sampleSize,
                                            double alpha,
                                            int maxLatentSubsetSize) {
        int currentRank = estimateRank(new ArrayList<>(getObservedParentsUnion(graph, latentNodes)),
                getObservedChildrenUnion(graph, latentNodes), variables, s, sampleSize, alpha);


        System.out.println("Expanding higher rank parent sets for latent nodes: " + latentNodes + " current rank = " + currentRank);

        LinkedHashSet<Node> unused = new LinkedHashSet<>(initialPool);

        // Remove any variables already used as observed parents of any latent
        unused.removeAll(getObservedParentsUnion(graph, latentNodes));

        int maxSize = Math.min(maxLatentSubsetSize, latentNodes.size());

        for (int subsetSize = 2; subsetSize <= maxSize; subsetSize++) {
            List<List<Node>> latentSubsets = getLatentSubsets(latentNodes, subsetSize);

            for (List<Node> latentSubset : latentSubsets) {
                System.out.println("Expanding latent subset: " + latentSubset);

                List<Node> newParents = expandParentSetForLatentSubset(
                        graph, latentSubset, currentRank, unused, variables, s, sampleSize, alpha
                );

                if (newParents.isEmpty()) {
                    continue;
                }

                for (Node newParent : newParents) {
                    for (Node latent : latentSubset) {
                        if (!graph.isParentOf(newParent, latent)) {
                            graph.addDirectedEdge(newParent, latent);
                        }
                    }
                    unused.remove(newParent);
                }

                removeExplainedLatentEdges(graph, latentSubset, newParents, variables, s, sampleSize, alpha);
            }
        }
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

    private List<Node> expandParentSetForLatentSubset(Graph graph,
                                                      List<Node> latentSubset,
                                                      int currentRank,
                                                      Set<Node> unused,
                                                      List<Node> variables,
                                                      SimpleMatrix s,
                                                      int sampleSize,
                                                      double alpha) {
        int targetRank = latentSubset.size();

        List<Node> childSet = getObservedChildrenUnion(graph, latentSubset);
        LinkedHashSet<Node> currentParents = new LinkedHashSet<>(getObservedParentsUnion(graph, latentSubset));
        List<Node> newlyAdded = new ArrayList<>();

        System.out.println("Coming expandParentSetForLatentSubset " + latentSubset + " with current rank " + currentRank
                + " target rank " + targetRank + " currentParents = " + currentParents + " childSet = " + childSet);

        if (currentRank >= targetRank) {
            System.out.println("Already at or above target rank; returning empty.");
            return newlyAdded;
        }

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
                if (currentRank < targetRank) {
                    if (proposedRank <= currentRank || proposedRank > targetRank) {
                        continue;
                    }
                } else {
                    // Once at target rank, only allow additions that preserve exact target rank.
                    if (proposedRank != targetRank) {
                        continue;
                    }
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

        if (currentRank != targetRank) {
            System.out.println("Did not reach target rank; discarding additions.");
            return new ArrayList<>();
        }

        System.out.println("Reached target rank; returning newlyAdded = " + newlyAdded);
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

