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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.StatUtils;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Uses a fixed latent-indicator measurement model and recovers measured parents
 * of those latents using complete BOSS parent bicliques over the indicators.
 *
 * <p>This class runs the full Boss-Trek-MIMIC2 pipeline from data:
 * <ol>
 *     <li>Builds a measurement model using the shared PC/TSC builder.</li>
 *     <li>Keeps that measurement model fixed.</li>
 *     <li>Runs BOSS on the measured variables.</li>
 *     <li>For each latent L, computes the maximum set of measured non-indicator
 *     variables X such that X is a BOSS parent of every indicator of L.</li>
 *     <li>Adds X -> L for every such X.</li>
 *     <li>Optionally prunes latent-latent edges explained by the recovered parents.</li>
 *     <li>Optionally orients latent-latent edges using parent/child correlations.</li>
 * </ol>
 * </p>
 *
 * <p>Equivalently, for each latent L with indicator set I(L), this algorithm recovers
 * the largest measured parent set P(L) such that P(L) x I(L) is contained in the
 * directed edge set of the BOSS graph.</p>
 *
 * @author josephramsey
 */
public final class BossTrekMimic2 implements IGraphSearch {

    /**
     * Input data set.
     */
    private DataSet dataSet;

    /**
     * Parameters controlling the search.
     */
    private Parameters parameters;

    /**
     * Score used by BOSS.
     */
    private final Score score;

    /**
     * Optional background knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Optional known measured inputs by name.
     */
    private final Set<String> inputNames = new LinkedHashSet<>();

    /**
     * Optional known measured outputs by name.
     */
    private final Set<String> outputNames = new LinkedHashSet<>();

    /**
     * If true, allow one measured input to be attached to multiple latents if it
     * belongs to the complete parent biclique for each of them. Otherwise attach it
     * only to one latent, chosen by the largest indicator block.
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
     * PC depth.
     */
    private int depth = -1;

    /**
     * Verbosity flag.
     */
    private boolean verbose = false;

    /**
     * Working graph.
     */
    private Graph graph;

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
     * Constructs an uninitialized BossTrekMimic2 search.
     * Use setters before calling {@link #search()}.
     *
     * @param score the score to use for BOSS
     */
    public BossTrekMimic2(Score score) {
        if (score == null) {
            throw new NullPointerException("Score must not be null.");
        }

        this.score = score;
    }

    /**
     * Constructs a BossTrekMimic2 search with data, parameters, and score.
     *
     * @param dataSet the data set
     * @param parameters the parameters
     * @param score the score to use for BOSS
     */
    public BossTrekMimic2(DataSet dataSet, Parameters parameters, Score score) {
        this(score);
        setDataSet(dataSet);
        setParameters(parameters);
    }

    /**
     * Runs the full Boss-Trek-MIMIC2 search.
     *
     * @return the resulting graph
     * @throws InterruptedException if interrupted
     */
    @Override
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

        this.graph = new EdgeListGraph(result.graph());
        this.variables = new ArrayList<>(result.variables());
        this.s = result.matrix();
        this.sampleSize = result.sampleSize();
        this.alpha = result.alpha();

        recoverMeasuredParentsByCompleteBiclique();

        if (pruneLatentEdges) {
            pruneLatentLatentEdgesByConditionedRank();
        }

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
     * Sets whether to orient latent-latent edges after pruning.
     *
     * @param orientLatentEdges true if so
     */
    public void setOrientLatentEdges(boolean orientLatentEdges) {
        this.orientLatentEdges = orientLatentEdges;
    }

    /**
     * Sets the PC depth used in measurement-model construction.
     *
     * @param depth the depth
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets verbose output for measurement-model construction.
     *
     * @param verbose true if verbose
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
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
     * Runs the BOSS-based measured-parent recovery stage using complete bicliques.
     *
     * <p>For each latent L, computes the intersection of the measured-parent sets
     * of all indicators of L in the BOSS graph. That intersection is the maximum
     * measured parent set P such that P x I(L) is fully present in the BOSS graph.</p>
     */
    private void recoverMeasuredParentsByCompleteBiclique() {
        if (graph == null) {
            throw new IllegalStateException("Graph has not been initialized.");
        }

        List<Node> latents = getLatents(graph);
        List<Node> indicators = getIndicators(graph, latents);
        List<Node> measuredNodes = getMeasuredNodes(graph);

        LinkedHashSet<Node> inputPool = new LinkedHashSet<>(measuredNodes);
        inputPool.removeAll(indicators);

        Graph bossGraph = runBoss();
        Map<Node, List<Node>> indicatorsByLatent = getIndicatorsByLatent(graph, latents);

        if (allowMultipleLatentParents) {
            for (Node latent : latents) {
                Set<Node> commonParents = getCompleteBicliqueParentsForLatent(
                        latent, indicatorsByLatent.get(latent), inputPool, bossGraph
                );

                for (Node parent : commonParents) {
                    if (!graph.isParentOf(parent, latent)) {
                        graph.addDirectedEdge(parent, latent);
                    }
                }
            }
        } else {
            Map<Node, Node> bestLatentForParent = new LinkedHashMap<>();
            Map<Node, Integer> bestLatentSizeForParent = new LinkedHashMap<>();

            for (Node latent : latents) {
                List<Node> latentIndicators = indicatorsByLatent.get(latent);
                Set<Node> commonParents = getCompleteBicliqueParentsForLatent(
                        latent, latentIndicators, inputPool, bossGraph
                );

                int blockSize = latentIndicators.size();

                for (Node parent : commonParents) {
                    Integer bestSize = bestLatentSizeForParent.get(parent);

                    if (bestSize == null || blockSize > bestSize) {
                        bestLatentForParent.put(parent, latent);
                        bestLatentSizeForParent.put(parent, blockSize);
                    }
                }
            }

            for (Map.Entry<Node, Node> entry : bestLatentForParent.entrySet()) {
                Node parent = entry.getKey();
                Node latent = entry.getValue();

                if (!graph.isParentOf(parent, latent)) {
                    graph.addDirectedEdge(parent, latent);
                }
            }
        }
    }

    /**
     * Returns the maximum measured parent set whose Cartesian product with the
     * latent's indicator set is present in the BOSS graph.
     *
     * @param latent the latent
     * @param latentIndicators the indicators of the latent
     * @param inputPool candidate measured non-indicator variables
     * @param bossGraph the BOSS graph
     * @return the maximal complete-biclique parent set
     */
    private Set<Node> getCompleteBicliqueParentsForLatent(Node latent,
                                                          List<Node> latentIndicators,
                                                          Set<Node> inputPool,
                                                          Graph bossGraph) {
        LinkedHashSet<Node> commonParents = new LinkedHashSet<>();

        if (latentIndicators == null || latentIndicators.isEmpty()) {
            return commonParents;
        }

        boolean first = true;

        for (Node indicator : latentIndicators) {
            LinkedHashSet<Node> indicatorParents = new LinkedHashSet<>();

            for (Node parent : bossGraph.getParents(indicator)) {
                if (parent.getNodeType() == NodeType.LATENT) {
                    continue;
                }

                if (!inputPool.contains(parent)) {
                    continue;
                }

                indicatorParents.add(parent);
            }

            if (first) {
                commonParents.addAll(indicatorParents);
                first = false;
            } else {
                commonParents.retainAll(indicatorParents);
            }

            if (commonParents.isEmpty()) {
                break;
            }
        }

        commonParents.remove(latent);
        commonParents.removeAll(latentIndicators);

        return commonParents;
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
}