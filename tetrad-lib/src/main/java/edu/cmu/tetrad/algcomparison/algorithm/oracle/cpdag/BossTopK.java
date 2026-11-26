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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.PermutationSearchTopK;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BOSS-TopK (Best Order Score Search, Top K).
 * <p>
 * This is an algcomparison wrapper around {@link edu.cmu.tetrad.search.BossTopK} /
 * {@link edu.cmu.tetrad.search.PermutationSearchTopK}. It runs the top-k BOSS search and returns the single
 * top-scoring graph (so it behaves like the ordinary BOSS wrapper for comparison purposes). As a side effect it
 * prints, to the console, each of the top permutations found (with its score), followed by each of their graphs.
 * After a search the models kept are also available, best first, from {@link #getTopGraphs()},
 * {@link #getTopScores()} and {@link #getTopOrders()}.
 * <p>
 * The number of models retained ({@code topK}), the split threshold ({@code splitDelta}), and the run cap
 * ({@code maxRuns}) are read from the parameters with sensible defaults. Note that with {@code splitDelta == 0} and
 * {@code numStarts == 1}, only a single model is produced; to obtain several distinct models set {@code splitDelta}
 * greater than 0 and/or use multiple restarts.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "BOSS-TopK",
        command = "boss-topk",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class BossTopK extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper, AcceptsKnowledge,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Parameter name: number of top models to retain.
     */
    private static final String TOP_K = Params.TOP_K;

    /**
     * Parameter name: split threshold delta (>= 0). 0 disables splitting.
     */
    private static final String SPLIT_DELTA = Params.SPLIT_DELTA;

    /**
     * Parameter name: hard cap on the total number of hill-climb runs.
     */
    private static final String MAX_RUNS = Params.MAX_RUNS;

    /**
     * Parameter name: whether to de-duplicate top models by Markov equivalence class (canonical CPDAG) instead of by
     * permutation. Defaults to true.
     */
    private static final String DEDUP_BY_CPDAG = Params.DEDUP_BY_CPDAG;

    /**
     * Parameter name: whether to offer every ordering visited across all branches to the top-k pool (including
     * within-branch suboptimal orderings), rather than only each branch's converged optimum. Defaults to false.
     */
    private static final String OPTIMAL_ACROSS_BRANCHES = Params.OPTIMAL_ACROSS_BRANCHES;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The graphs of the models kept by the last search, best first. Not saved with a session, so null in an algorithm
     * read back from one until it is run again.
     */
    private transient List<Graph> topGraphs;

    /**
     * The scores of the models kept by the last search, in the same order.
     */
    private transient List<Double> topScores;

    /**
     * The variable orders of the models kept by the last search, in the same order.
     */
    private transient List<List<Node>> topOrders;

    /**
     * How many searches have kept their models since the models were last cleared. More than one means that the
     * search was run on resamples, and the models held are those of whichever of them ran last.
     */
    private transient int searchesKept;

    /**
     * The number of hill-climb runs the last search made.
     */
    private transient int numRuns;

    /**
     * Whether the cap on runs cut the last search short.
     */
    private transient boolean stoppedByMaxRuns;

    /**
     * Constructs a new BOSS-TopK algorithm.
     */
    public BossTopK() {
        // Used in reflection; do not delete.
    }

    /**
     * Constructs a new BOSS-TopK algorithm with the given score.
     *
     * @param score the score to use
     */
    public BossTopK(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Runs the search and returns the top-scoring graph, as the other algorithms do. The models kept are then
     * available from {@link #getTopGraphs()}, {@link #getTopScores()} and {@link #getTopOrders()}. With bootstrapping
     * there are none to give: the search is run on each resample, and the models of one resample are not the models
     * of the data.
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) throws InterruptedException {
        clearTopModels();

        Graph graph = super.search(dataModel, parameters);

        synchronized (this) {
            if (this.searchesKept != 1) clearTopModels();
        }

        return graph;
    }

    /**
     * Returns the graphs of the models kept by the last search, best first. The first is the graph the search
     * returned. Each is a CPDAG or a DAG as the search was asked for. The list is empty before a search, after a
     * search with bootstrapping, and in an algorithm read back from a saved session.
     *
     * @return the graphs, best first
     */
    public synchronized List<Graph> getTopGraphs() {
        return this.topGraphs == null ? List.of() : Collections.unmodifiableList(this.topGraphs);
    }

    /**
     * Returns the scores of the models kept by the last search, in the order of {@link #getTopGraphs()}. A higher
     * score is better.
     *
     * @return the scores, best first
     */
    public synchronized List<Double> getTopScores() {
        return this.topScores == null ? List.of() : Collections.unmodifiableList(this.topScores);
    }

    /**
     * Returns the variable orders of the models kept by the last search, in the order of {@link #getTopGraphs()}.
     * Each model's graph is the one the search builds from its order.
     *
     * @return the orders, best first
     */
    public synchronized List<List<Node>> getTopOrders() {
        return this.topOrders == null ? List.of() : Collections.unmodifiableList(this.topOrders);
    }

    /**
     * Returns the number of hill-climb runs the last search made: its starts, and one for each placement that was set
     * aside and then searched from. With knowledge that puts every variable in a tier this is the sum over the
     * tiers, and the cap on runs applies to each tier's search separately. It is 0 wherever {@link #getTopGraphs()}
     * is empty.
     *
     * @return the number of runs
     */
    public synchronized int getNumRuns() {
        return this.numRuns;
    }

    /**
     * Returns whether the cap on runs, the maxRuns parameter, cut the last search short: true if a placement that
     * was to be set aside was not searched from because of the cap. The models kept are then the best of those
     * reached, not of all there are within the split delta. A search that used up its cap exactly, with nothing left
     * set aside, was not cut short. It is false wherever {@link #getTopGraphs()} is empty.
     *
     * @return true if the cap left a placement unsearched
     */
    public synchronized boolean isStoppedByMaxRuns() {
        return this.stoppedByMaxRuns;
    }

    private synchronized void clearTopModels() {
        this.topGraphs = null;
        this.topScores = null;
        this.topOrders = null;
        this.searchesKept = 0;
        this.numRuns = 0;
        this.stoppedByMaxRuns = false;
    }

    private synchronized void keepTopModels(PermutationSearchTopK permutationSearch) {
        List<Node> variables = permutationSearch.getVariables();
        List<Graph> graphs = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        List<List<Node>> orders = new ArrayList<>();

        for (int i = 0; i < permutationSearch.getNumModels(); i++) {
            graphs.add(permutationSearch.getModelGraph(i));
            scores.add(permutationSearch.getModelScore(i));

            List<Node> order = new ArrayList<>();
            for (int index : permutationSearch.getModelPermutation(i)) order.add(variables.get(index));
            orders.add(Collections.unmodifiableList(order));
        }

        this.topGraphs = graphs;
        this.topScores = scores;
        this.topOrders = orders;
        this.numRuns = permutationSearch.getNumRuns();
        this.stoppedByMaxRuns = permutationSearch.isStoppedByMaxRuns();
        this.searchesKept++;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Runs the BOSS-TopK algorithm, returning the top-scoring graph and printing the top permutations and their graphs
     * to the console.
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        long seed = parameters.getLong(Params.SEED);

        // The number of threads is the user's to set. From 2024-2-25 to 2026-11-25 a line here set it to 4 on every
        // run, in the parameters themselves, so that whatever was asked for was ignored and the parameter read 4
        // afterward.

        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG), knowledge);
            if (dataModel.getName() != null) {
                timeSeries.setName(dataModel.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        Score myScore = this.score.getScore(dataModel, parameters);

//        int k = 10;//parameters.getInt(TOP_K, 10);
//        double delta = .5;//parameters.getDouble(SPLIT_DELTA, 0.0);
//        int maxRuns = 10000;//parameters.getInt(MAX_RUNS, 10000);
//        boolean dedupByCpdag = true;//parameters.getBoolean(DEDUP_BY_CPDAG, true);
//        boolean optimalAcrossBranches = true;//parameters.getBoolean(OPTIMAL_ACROSS_BRANCHES, false);

        int k = parameters.getInt(TOP_K, 10);
        double delta = parameters.getDouble(SPLIT_DELTA, 0.0);
        int maxRuns = parameters.getInt(MAX_RUNS, 10000);
        boolean dedupByCpdag = parameters.getBoolean(DEDUP_BY_CPDAG, true);
        boolean optimalAcrossBranches = parameters.getBoolean(OPTIMAL_ACROSS_BRANCHES, false);
        boolean deferTies = parameters.getBoolean(Params.SPLIT_DEFER_TIES);

        edu.cmu.tetrad.search.BossTopK boss = new edu.cmu.tetrad.search.BossTopK(myScore);

        boss.setUseBes(parameters.getBoolean(Params.USE_BES));
        boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        boss.setNumThreads(parameters.getInt(Params.NUM_THREADS));
        boss.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        boss.setVerbose(parameters.getBoolean(Params.VERBOSE));

        PermutationSearchTopK permutationSearch = new PermutationSearchTopK(boss, k, delta);
        permutationSearch.setMaxRuns(maxRuns);
        permutationSearch.setDedupByCpdag(dedupByCpdag);
        permutationSearch.setOptimalAcrossBranches(optimalAcrossBranches);
        permutationSearch.setDeferTies(deferTies);
        permutationSearch.setKnowledge(this.knowledge);
        permutationSearch.setSeed(seed);
        permutationSearch.setReplicatingGraph(parameters.getBoolean(Params.TIME_LAG_REPLICATING_GRAPH));

        try {
            Graph graph = permutationSearch.search(parameters.getBoolean(Params.OUTPUT_CPDAG));
            LogUtilsSearch.stampWithScore(graph, boss.getScore());
            LogUtilsSearch.stampWithBic(graph, dataModel);

            printTopModels(permutationSearch);
            keepTopModels(permutationSearch);

            return graph;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Prints the top permutations found (each with its score), followed by each of their graphs, to the console.
     *
     * @param permutationSearch The completed search.
     */
    private void printTopModels(PermutationSearchTopK permutationSearch) {
        List<Node> variables = permutationSearch.getVariables();
        int n = permutationSearch.getNumModels();

        System.out.println();
        System.out.println("==================== BOSS-TopK: top models ====================");
        System.out.println("Requested k = " + permutationSearch.getK()
                + ", split delta = " + permutationSearch.getDelta()
                + ", models found = " + n
                + ", runs made = " + permutationSearch.getNumRuns());

        if (permutationSearch.isStoppedByMaxRuns()) {
            System.out.println("The cap on runs (maxRuns) stopped the search with placements still set aside, so "
                    + "these are the best models of those reached.");
        }

        System.out.println();
        System.out.println("---- Top permutations (best first) ----");
        for (int i = 0; i < n; i++) {
            int[] perm = permutationSearch.getModelPermutation(i);
            double s = permutationSearch.getModelScore(i);

            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < perm.length; j++) {
                if (j > 0) sb.append(", ");
                sb.append(variables.get(perm[j]).getName());
            }

            System.out.printf("Model %d  score = %.6f%n", i + 1, s);
            System.out.println("  order:      " + sb);
            System.out.println("  indices:    " + arrayToString(perm));
        }

        System.out.println();
        System.out.println("---- Top graphs (best first) ----");
        for (int i = 0; i < n; i++) {
            System.out.printf("Model %d  score = %.6f%n", i + 1, permutationSearch.getModelScore(i));
            System.out.println(permutationSearch.getModelGraph(i));
            System.out.println();
        }
        System.out.println("===============================================================");
        System.out.println();
    }

    /**
     * Renders an int array as a bracketed, comma-separated string.
     *
     * @param a The array.
     * @return Its string form.
     */
    private static String arrayToString(int[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(a[i]);
        }
        return sb.append("]").toString();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the true graph if there is one.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "BOSS-TopK (Best Order Score Search Top K) using " + this.score.getDescription();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters for the algorithm.
     */
    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Top-k specific parameters.
        params.add(TOP_K);
        params.add(SPLIT_DELTA);
        params.add(Params.SPLIT_DEFER_TIES);
        params.add(MAX_RUNS);
        params.add(DEDUP_BY_CPDAG);
        params.add(OPTIMAL_ACROSS_BRANCHES);

        // Standard BOSS parameters.
        params.add(Params.USE_BES);
        params.add(Params.NUM_STARTS);
        params.add(Params.TIME_LAG);
        params.add(Params.TIME_LAG_REPLICATING_GRAPH);
        params.add(Params.NUM_THREADS);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.OUTPUT_CPDAG);
        params.add(Params.SEED);
        params.add(Params.VERBOSE);

        return params;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the score wrapper.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the knowledge.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the knowledge.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

}
