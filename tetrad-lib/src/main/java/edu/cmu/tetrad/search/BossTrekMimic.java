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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Uses a fixed latent-indicator measurement model and recovers measured parents
 * of those latents using BOSS parent relations among the indicators.
 *
 * <p>The intended use is:
 * <ol>
 *     <li>Obtain a measurement model externally, for example from TSC/PC.</li>
 *     <li>Supply that graph here. It must already contain latent nodes and latent -> indicator edges.</li>
 *     <li>Run BOSS on the measured variables.</li>
 *     <li>For each measured non-indicator variable X and each latent L, count how many
 *     indicators of L have X as a BOSS parent.</li>
 *     <li>Add X -> L when that support is high enough.</li>
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

    private List<Node> variables;
    private SimpleMatrix s;
    private int sampleSize;
    private double alpha = 0.01;

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

//    @Override
//    public Graph search() {
//        if (this.measurementGraph == null) {
//            throw new IllegalStateException("A fixed measurement graph must be supplied.");
//        }
//
//        Graph graph = new EdgeListGraph(this.measurementGraph);
//
//        List<Node> latents = getLatents(graph);
//        List<Node> indicators = getIndicators(graph, latents);
//        List<Node> measuredNodes = getMeasuredNodes(graph);
//        List<Node> inputPool = new ArrayList<>(measuredNodes);
//        inputPool.removeAll(indicators);
//
//        Graph bossGraph = runBoss();
//
//        Map<Node, List<Node>> indicatorsByLatent = getIndicatorsByLatent(graph, latents);
//
//        for (Node input : inputPool) {
//            Map<Node, Integer> supportCounts = new LinkedHashMap<>();
//
//            for (Node latent : latents) {
//                int count = countBossChildrenInIndicatorBlock(input, indicatorsByLatent.get(latent), bossGraph);
//                supportCounts.put(latent, count);
//            }
//
//            if (allowMultipleLatentParents) {
//                attachInputToSupportedLatents(graph, input, latents, indicatorsByLatent, supportCounts);
//            } else {
//                attachInputToBestLatent(graph, input, latents, indicatorsByLatent, supportCounts);
//            }
//        }
//
//        removeDegenerateLatents(graph);
//
//        pruneLatentLatentEdgesByConditionedRank(graph);
//
//        return graph;
//    }

    @Override
    public Graph search() {
        if (this.measurementGraph == null) {
            throw new IllegalStateException("A fixed measurement graph must be supplied.");
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

        pruneLatentLatentEdgesByConditionedRank(graph);
//        removeDegenerateLatents(graph);

        return graph;
    }

    /**
     * Removes latent-latent edges that are explained by the measured parents
     * recovered for the incident latents.
     *
     * <p>For each latent-latent edge L1 - L2, let C1 and C2 be the indicator
     * sets of L1 and L2, and let P be the union of the measured parents of
     * L1 and L2. If rank(C1, C2 | P) = 0, then the latent-latent edge is
     * removed.</p>
     *
     * <p>This method requires that variables, s, sampleSize, and alpha have
     * been supplied.</p>
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

            int rank = estimateRankConditioned(childrenX, childrenY, cond, variables, s, sampleSize, alpha);

            if (rank == 0) {
                graph.removeEdge(edge);
            }
        }
    }

    private List<Node> getMeasuredChildren(Graph graph, Node x) {
        List<Node> children = new ArrayList<>();

        for (Node child : graph.getChildren(x)) {
            if (child.getNodeType() != NodeType.LATENT) {
                children.add(child);
            }
        }

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
     * Sets the knowledge used by BOSS.
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

    public void setVariables(List<Node> variables) {
        this.variables = new ArrayList<>(variables);
    }

    public void setMatrix(SimpleMatrix s) {
        this.s = s;
    }

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Sets whether multiple latent parents are allowed for one input.
     *
     * @param allowMultipleLatentParents true if allowed
     */
    public void setAllowMultipleLatentParents(boolean allowMultipleLatentParents) {
        this.allowMultipleLatentParents = allowMultipleLatentParents;
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
}