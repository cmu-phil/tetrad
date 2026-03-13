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
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
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
 * Peter/Clark algorithm (PC).
 *
 * @author josephramsey
 * @version $Id: $Id
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
     * <p>Constructor for Pc.</p>
     */
    public TrekMimic() {
        this.test = new BlocksIndTestTs();
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;
        Tsc tsc = new Tsc(dataModel.getVariables(), new CovarianceMatrix(data));
        tsc.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        tsc.setRmax(5);
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

        List<List<Node>> childSets = new ArrayList<>();
        Set<Node> _allChildren = new HashSet<>();

        for (Node node : spec.blockVariables()) {
            List<Node> children = graph.getChildren(node);
            childSets.add(children);
            _allChildren.addAll(children);
        }

        List<Node> allChildren = new ArrayList<>(_allChildren);

        List<Node> pool = new ArrayList<>(data.getVariables());
        pool.removeAll(allChildren);

        List<Node> variables = data.getVariables();
        SimpleMatrix s = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();

        int sampleSize = data.getNumRows();
        double alpha = parameters.getDouble(Params.ALPHA);

        List<List<Node>> recoveredGroups =
                recoverCliqueRankOneGroups(pool, allChildren, variables, s, sampleSize, alpha);

        Map<Node, List<Node>> assignment = assignParentGroupsToLatents(
                recoveredGroups, spec.blockVariables(), childSets, variables, s, sampleSize, alpha);

        for (Node latent : assignment.keySet()) {
            List<Node> parents = assignment.get(latent);

            graph.addNode(latent);
            for (Node parent : parents) {
                graph.addDirectedEdge(parent, latent);
            }
        }

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

            List<Node> childrenx = graph.getChildren(x);
            List<Node> childreny = graph.getChildren(y);

            parentsx.removeIf(n -> n.getNodeType() == NodeType.LATENT);
            parentsy.removeIf(n -> n.getNodeType() == NodeType.LATENT);

            childrenx.removeIf(n -> n.getNodeType() == NodeType.LATENT);
            childreny.removeIf(n -> n.getNodeType() == NodeType.LATENT);

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
                                                              List<List<Node>> childSets,
                                                              List<Node> variables,
                                                              SimpleMatrix s,
                                                              int sampleSize,
                                                              double alpha) {
        Map<Node, List<Node>> assignment = new LinkedHashMap<>();

        for (List<Node> group : recoveredGroups) {
            Node bestLatent = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (int j = 0; j < latentNodes.size(); j++) {
                Node latent = latentNodes.get(j);
                List<Node> childSet = childSets.get(j);

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

    private List<Node> findBestRankOnePair(List<Node> pool,
                                           List<Node> allChildren,
                                           List<Node> variables,
                                           SimpleMatrix s,
                                           int sampleSize,
                                           double alpha) {
        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), 2);
        int[] choice;

        List<Node> bestPair = null;
        double bestStrength = Double.NEGATIVE_INFINITY;

        while ((choice = gen.next()) != null) {
            List<Node> pair = GraphUtils.asList(choice, pool);

            int rank = estimateRank(pair, allChildren, variables, s, sampleSize, alpha);

            if (rank != 1) {
                continue;
            }

            double strength = blockStrength(pair, allChildren, variables, s);

            if (strength > bestStrength) {
                bestStrength = strength;
                bestPair = new ArrayList<>(pair);
            }
        }

        return bestPair;
    }

    private List<Node> growCliqueRankOneSet(List<Node> seedPair,
                                            List<Node> pool,
                                            Graph pairGraph,
                                            List<Node> allChildren,
                                            List<Node> variables,
                                            SimpleMatrix s) {
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

            if (rank != 1) {
                continue;
            }

            pairs.add(pair);
        }

        Graph pairGraph = buildRankOnePairGraph(pool, allChildren, variables, s, sampleSize, alpha);

        for (List<Node> seedPair : pairs) {
            List<Node> group = growCliqueRankOneSet(seedPair, pool, pairGraph, allChildren, variables, s);
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

