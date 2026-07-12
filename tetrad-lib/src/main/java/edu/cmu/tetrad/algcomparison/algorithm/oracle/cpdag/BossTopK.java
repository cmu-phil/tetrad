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
import java.util.List;

/**
 * BOSS-TopK (Best Order Score Search, Top K).
 * <p>
 * This is an algcomparison wrapper around {@link edu.cmu.tetrad.search.BossTopK} /
 * {@link edu.cmu.tetrad.search.PermutationSearchTopK}. It runs the top-k BOSS search and returns the single
 * top-scoring graph (so it behaves like the ordinary BOSS wrapper for comparison purposes). As a side effect it
 * prints, to the console, each of the top permutations found (with its score), followed by each of their graphs. A
 * later revision may return multiple graphs for display in the interface.
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
    private static final String TOP_K = "topK";

    /**
     * Parameter name: split threshold delta (>= 0). 0 disables splitting.
     */
    private static final String SPLIT_DELTA = "splitDelta";

    /**
     * Parameter name: hard cap on the total number of hill-climb runs.
     */
    private static final String MAX_RUNS = "maxRuns";

    /**
     * Parameter name: whether to de-duplicate top models by Markov equivalence class (canonical CPDAG) instead of by
     * permutation. Defaults to true.
     */
    private static final String DEDUP_BY_CPDAG = "dedupByCpdag";

    /**
     * Parameter name: whether to offer every ordering visited across all branches to the top-k pool (including
     * within-branch suboptimal orderings), rather than only each branch's converged optimum. Defaults to false.
     */
    private static final String OPTIMAL_ACROSS_BRANCHES = "optimalAcrossBranches";

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

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
     * Runs the BOSS-TopK algorithm, returning the top-scoring graph and printing the top permutations and their graphs
     * to the console.
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        long seed = parameters.getLong(Params.SEED);
        parameters.set(Params.NUM_THREADS, 4);

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
        permutationSearch.setKnowledge(this.knowledge);
        permutationSearch.setSeed(seed);
        permutationSearch.setReplicatingGraph(parameters.getBoolean(Params.TIME_LAG_REPLICATING_GRAPH));

        try {
            Graph graph = permutationSearch.search(parameters.getBoolean(Params.OUTPUT_CPDAG));
            LogUtilsSearch.stampWithScore(graph, boss.getScore());
            LogUtilsSearch.stampWithBic(graph, dataModel);

            printTopModels(permutationSearch);

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
                + ", models found = " + n);

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
