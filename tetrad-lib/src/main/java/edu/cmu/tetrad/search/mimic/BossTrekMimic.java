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

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Uses a fixed latent-indicator measurement model and recovers measured parents
 * of those latents using BOSS parent relations among the indicators.
 *
 * <p>This class runs the full Boss-Trek-MIMIC pipeline from data:</p>
 * <ol>
 *     <li>Builds a measurement model using the shared PC/TSC builder.</li>
 *     <li>Keeps that measurement model fixed.</li>
 *     <li>Runs BOSS on the measured variables.</li>
 *     <li>For each measured non-indicator variable X and each latent L, counts how many
 *     indicators of L have X as a BOSS parent.</li>
 *     <li>Adds X -> L when that support is high enough.</li>
 *     <li>Optionally prunes latent-latent edges explained by the recovered parents.</li>
 *     <li>Optionally orients latent-latent edges using parent/child correlations.</li>
 * </ol>
 *
 * <p>Expected use:</p>
 * <pre>
 * BossTrekMimic tm = new BossTrekMimic(data, parameters, score);
 * tm.setKnowledge(knowledge);
 * tm.setInputNames(inputNames);
 * tm.setOutputNames(outputNames);
 * Graph g = tm.search();
 * </pre>
 *
 * @author josephramsey
 */
@Deprecated
public final class BossTrekMimic implements IGraphSearch {

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
    private boolean orientAndPrune = true;

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
     * Constructs an uninitialized BossTrekMimic search.
     * Use setters before calling {@link #search()}.
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
     * Constructs a BossTrekMimic search with data, parameters, and score.
     *
     * @param dataSet the data set
     * @param parameters the parameters
     * @param score the score to use for BOSS
     */
    public BossTrekMimic(DataSet dataSet, Parameters parameters, Score score) {
        this(score);
        setDataSet(dataSet);
        setParameters(parameters);
    }

    /**
     * Runs the full Boss-Trek-MIMIC search.
     *
     * @return the resulting graph
     * @throws InterruptedException if interrupted
     */
    @Override
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

        this.graph      = new EdgeListGraph(result.graph());
        this.variables  = new ArrayList<>(result.variables());
        this.sampleSize = result.sampleSize();
        this.alpha      = result.alpha();

        recoverMeasuredParentsByBoss();

        LatentGraphRefinement refinement = new LatentGraphRefinement(variables, s, dataSet, sampleSize, alpha);

        // Remove any edges that are removable by conditional rank.
        refinement.orientAndPruneEdges(graph);

        // Orient latents and remove latent-transitive inputs.
        if (orientAndPrune) {
            List<Graph> graphs = refinement.orientAndPruneEdges(graph);
            Graph oriented = graphs.get(0);
            Graph pruned = graphs.get(1);

            // The latent-transitive edges include non-identifiable ones, so we print them.
            if (verbose) {
                Set<Edge> set1 = oriented.getEdges();
                Set<Edge> set2 = pruned.getEdges();
                Set<Edge> set3 = new HashSet<>(set1);
                set3.removeAll(set2);

                TetradLogger.getInstance().log("Latent-transitive edges (includes some non-identifiable ones): " + set3);
            }

            return pruned;
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
     * @param orientAndPrune true if so
     */
    public void setOrientAndPrune(boolean orientAndPrune) {
        this.orientAndPrune = orientAndPrune;
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
     * Runs the BOSS-based measured-parent recovery stage.
     */
    private void recoverMeasuredParentsByBoss() throws InterruptedException {
        if (graph == null) {
            throw new IllegalStateException("Graph has not been initialized.");
        }

        List<Node> latents      = getLatents(graph);
        List<Node> indicators   = getIndicators(graph, latents);
        List<Node> measuredNodes = getMeasuredNodes(graph);
        List<Node> inputPool    = new ArrayList<>(measuredNodes);
        inputPool.removeAll(indicators);

        Graph bossGraph = runBoss();
        Map<Node, List<Node>> indicatorsByLatent = getIndicatorsByLatent(graph, latents);

        for (Node input : inputPool) {
            Map<Node, Integer> supportCounts = new LinkedHashMap<>();

            for (Node latent : latents) {
                int count = countBossChildrenInIndicatorBlock(
                        input, indicatorsByLatent.get(latent), bossGraph);
                supportCounts.put(latent, count);
            }

            if (allowMultipleLatentParents) {
                attachInputToSupportedLatents(input, latents, indicatorsByLatent, supportCounts);
            } else {
                attachInputToBestLatent(input, latents, indicatorsByLatent, supportCounts);
            }
        }
    }

    private Graph runBoss() throws InterruptedException {
        PermutationSearch boss = new PermutationSearch(new Boss(this.score));
        boss.setKnowledge(this.knowledge);
        return boss.search();
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

    private void attachInputToSupportedLatents(Node input,
                                               List<Node> latents,
                                               Map<Node, List<Node>> indicatorsByLatent,
                                               Map<Node, Integer> supportCounts) {
        for (Node latent : latents) {
            int support        = supportCounts.getOrDefault(latent, 0);
            int indicatorCount = indicatorsByLatent.get(latent).size();

            if (indicatorCount == 0) {
                continue;
            }

            // Require support to satisfy BOTH thresholds simultaneously.
            // This is equivalent to: support >= max(minIndicatorSupport,
            // ceil(indicatorCount * minIndicatorSupportProportion)).
            // For a 2-indicator block with defaults (minSupport=2, proportion=0.5)
            // this requires support=2, i.e. both indicators, rather than just one.
            int proportionThreshold =
                    (int) Math.ceil(indicatorCount * minIndicatorSupportProportion);
            int required = Math.max(minIndicatorSupport, proportionThreshold);

            if (support >= required) {
                if (!graph.isParentOf(input, latent)) {
                    graph.addDirectedEdge(input, latent);
                }
            }
        }
    }

    private void attachInputToBestLatent(Node input,
                                         List<Node> latents,
                                         Map<Node, List<Node>> indicatorsByLatent,
                                         Map<Node, Integer> supportCounts) {
        Node   bestLatent = null;
        double bestScore  = Double.NEGATIVE_INFINITY;

        for (Node latent : latents) {
            int support        = supportCounts.getOrDefault(latent, 0);
            int indicatorCount = indicatorsByLatent.get(latent).size();

            if (indicatorCount == 0) {
                continue;
            }

            int proportionThreshold =
                    (int) Math.ceil(indicatorCount * minIndicatorSupportProportion);
            int required = Math.max(minIndicatorSupport, proportionThreshold);

            if (support < required) {
                continue;
            }

            // Score: raw count plus the proportion, so larger blocks with the
            // same proportion rank above smaller ones. The proportion component
            // breaks ties when counts are equal across blocks of different sizes.
            double proportion = (double) support / indicatorCount;
            double score      = support + proportion;

            if (score > bestScore) {
                bestScore  = score;
                bestLatent = latent;
            }
        }

        if (bestLatent != null && !graph.isParentOf(input, bestLatent)) {
            graph.addDirectedEdge(input, bestLatent);
        }
    }
}