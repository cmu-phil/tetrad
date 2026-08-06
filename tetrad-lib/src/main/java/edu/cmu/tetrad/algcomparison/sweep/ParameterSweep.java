///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.algcomparison.sweep;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.GraphSampling;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A harness for evaluating a search algorithm over a grid of parameter settings and recording, per setting, the
 * evidence a parameter choice should rest on: the point-estimate graph on the full data, a probability-annotated
 * aggregate of searches on shared resamples (edge probabilities displaying identically to bootstrap results
 * elsewhere in Tetrad), the StARS-style adjacency instability across those resamples, and Markov-check statistics
 * for the point graph if a Markov-check test is configured. Results are returned as a {@link SweepReport}, which
 * serializes to markdown and JSON and exposes explicit, overridable selection rules.
 * <p>
 * The resamples are drawn once per sweep and shared across all settings, so that instability comparisons between
 * settings are paired (the convention of Tetrad's StARS implementation). Resampling is seed-controlled and the
 * resample searches are race-free: each resample writes its graph to its own slot. Note the determinism contract:
 * under a fixed seed the resampled row sets are exactly reproducible, but end-to-end reproducibility of the report
 * additionally requires the wrapped algorithm to be deterministic; algorithms with internal thread pools (e.g.
 * FGES) can break score near-ties differently between runs, producing small run-to-run wobble in instabilities.
 * <p>
 * The harness executes and measures; it does not decide. Choosing among settings - even by the provided rules on
 * {@link SweepReport} - is a user decision. Background knowledge should be set on the algorithm wrapper before
 * sweeping, as usual for algcomparison algorithms.
 * <p>
 * See Zheng, Verma, Gill, Dai, Spirtes, and Zhang (2026), "Causal discovery in the era of agents"
 * (arXiv:2606.23608), for the assistance-versus-evidence principle this harness is designed to support: the sweep
 * makes the evidence behind a parameter recommendation cheap to produce and inspect, wherever that recommendation
 * comes from.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see SweepReport
 * @see SweepResult
 */
public final class ParameterSweep {

    /**
     * The algorithm to sweep.
     */
    private final Algorithm algorithm;

    /**
     * The base parameters; copied per setting, never mutated.
     */
    private final Parameters baseParameters;

    /**
     * The Markov-check test wrapper, or null to skip Markov checking.
     */
    private IndependenceWrapper markovCheckTest = null;

    /**
     * The conditioning set type for the Markov check.
     */
    private ConditioningSetType conditioningSetType = ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY;

    /**
     * The number of resamples per sweep (shared across settings). Zero disables resampling.
     */
    private int numResamples = 50;

    /**
     * The fraction of the sample size drawn per resample.
     */
    private double percentResampleSize = 1.0;

    /**
     * Whether resampling is with replacement.
     */
    private boolean withReplacement = true;

    /**
     * The random seed for drawing resamples, or -1 for time-based.
     */
    private long seed = -1;

    /**
     * Whether to run resample searches and the Markov check in parallel.
     */
    private boolean parallelized = true;

    /**
     * Whether to log progress.
     */
    private boolean verbose = false;

    /**
     * Constructs a sweep harness for the given algorithm and base parameters. The base parameters are copied per
     * setting and never mutated.
     *
     * @param algorithm      the algorithm to sweep; may not be null. Set knowledge on this wrapper beforehand if
     *                       desired.
     * @param baseParameters the base parameters; may not be null.
     */
    public ParameterSweep(Algorithm algorithm, Parameters baseParameters) {
        if (algorithm == null) throw new NullPointerException("algorithm");
        if (baseParameters == null) throw new NullPointerException("baseParameters");
        this.algorithm = algorithm;
        this.baseParameters = baseParameters;
    }

    //==================================== CONFIGURATION ====================================//

    /**
     * Sets the test used to Markov-check each setting's point graph, or null to skip Markov checking. The test is
     * instantiated per setting from the data and that setting's parameters, so test parameters (e.g. alpha) may
     * themselves be swept.
     *
     * @param markovCheckTest the test wrapper, or null.
     */
    public void setMarkovCheckTest(IndependenceWrapper markovCheckTest) {
        this.markovCheckTest = markovCheckTest;
    }

    /**
     * Sets the conditioning set type for the Markov check. Default ORDERED_LOCAL_MARKOV_PROPERTY.
     *
     * @param conditioningSetType the type; may not be null.
     */
    public void setConditioningSetType(ConditioningSetType conditioningSetType) {
        if (conditioningSetType == null) throw new NullPointerException("conditioningSetType");
        this.conditioningSetType = conditioningSetType;
    }

    /**
     * Sets the number of resamples drawn per sweep and shared across settings. Zero disables resampling (no
     * probability graphs, instability NaN). Default 50.
     *
     * @param numResamples the number of resamples; may not be negative.
     */
    public void setNumResamples(int numResamples) {
        if (numResamples < 0) throw new IllegalArgumentException("numResamples < 0");
        this.numResamples = numResamples;
    }

    /**
     * Sets the fraction of the sample size drawn per resample, in (0, 1]. Default 1.0. For classical StARS-style
     * subsampling, use around 0.5 with {@link #setWithReplacement(boolean)} false; for bootstrap-style edge
     * probabilities, use 1.0 with replacement.
     *
     * @param percentResampleSize the fraction; must be in (0, 1].
     */
    public void setPercentResampleSize(double percentResampleSize) {
        if (percentResampleSize <= 0 || percentResampleSize > 1) {
            throw new IllegalArgumentException("percentResampleSize must be in (0, 1]");
        }
        this.percentResampleSize = percentResampleSize;
    }

    /**
     * Sets whether resampling is with replacement. Default true.
     *
     * @param withReplacement true for with replacement.
     */
    public void setWithReplacement(boolean withReplacement) {
        this.withReplacement = withReplacement;
    }

    /**
     * Sets the random seed for drawing resamples, for reproducibility, or -1 for time-based. Default -1.
     *
     * @param seed the seed or -1.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Sets whether resample searches and the Markov check run in parallel. Default true.
     *
     * @param parallelized true for parallel.
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * Sets whether to log progress to the Tetrad log. Default false.
     *
     * @param verbose true to log.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    //==================================== SWEEPING ====================================//

    /**
     * Sweeps a single parameter over the given values.
     *
     * @param data      the dataset; may not be null.
     * @param parameter the parameter name (a Params constant); may not be null.
     * @param values    the values to evaluate, in order; may not be null or empty. Elements are typically Double,
     *                  Integer, or Boolean.
     * @return the report.
     * @throws InterruptedException if a search is interrupted.
     */
    public SweepReport sweep(DataSet data, String parameter, List<?> values) throws InterruptedException {
        if (parameter == null) throw new NullPointerException("parameter");
        List<Map<String, Object>> settings = new ArrayList<>();

        for (Object v : values) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put(parameter, v);
            settings.add(s);
        }

        return sweepSettings(data, settings);
    }

    /**
     * Sweeps the cross product of two parameters over the given value lists, varying the second parameter fastest.
     *
     * @param data       the dataset; may not be null.
     * @param parameter1 the first parameter name; may not be null.
     * @param values1    the first parameter's values; may not be null or empty.
     * @param parameter2 the second parameter name; may not be null.
     * @param values2    the second parameter's values; may not be null or empty.
     * @return the report.
     * @throws InterruptedException if a search is interrupted.
     */
    public SweepReport sweep(DataSet data, String parameter1, List<?> values1, String parameter2, List<?> values2)
            throws InterruptedException {
        if (parameter1 == null) throw new NullPointerException("parameter1");
        if (parameter2 == null) throw new NullPointerException("parameter2");
        List<Map<String, Object>> settings = new ArrayList<>();

        for (Object v1 : values1) {
            for (Object v2 : values2) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put(parameter1, v1);
                s.put(parameter2, v2);
                settings.add(s);
            }
        }

        return sweepSettings(data, settings);
    }

    /**
     * Sweeps an explicit list of settings, each a map from parameter name to value. This is the general entry
     * point; the one- and two-parameter overloads delegate here.
     *
     * @param data     the dataset; may not be null.
     * @param settings the settings to evaluate, in order; may not be null or empty.
     * @return the report.
     * @throws InterruptedException if a search is interrupted.
     */
    public SweepReport sweepSettings(DataSet data, List<Map<String, Object>> settings) throws InterruptedException {
        if (data == null) throw new NullPointerException("data");
        if (settings == null || settings.isEmpty()) throw new IllegalArgumentException("No settings given.");

        List<DataSet> resamples = drawResamples(data, this.numResamples, this.percentResampleSize,
                this.withReplacement, this.seed);

        List<SweepResult> results = new ArrayList<>();

        for (Map<String, Object> setting : settings) {
            long start = System.currentTimeMillis();
            Parameters params = new Parameters(this.baseParameters);

            for (Map.Entry<String, Object> e : setting.entrySet()) {
                params.set(e.getKey(), e.getValue());
            }

            if (this.verbose) {
                TetradLogger.getInstance().log("ParameterSweep: evaluating " + setting);
            }

            Graph pointGraph = this.algorithm.search(data, params);
            pointGraph = GraphUtils.replaceNodes(pointGraph, data.getVariables());

            Graph probabilityGraph = null;
            double instability = Double.NaN;

            if (!resamples.isEmpty()) {
                List<Graph> resampleGraphs = searchOnResamples(this.algorithm, params, resamples,
                        data.getVariables(), this.parallelized);
                probabilityGraph = GraphSampling.createGraphWithHighProbabilityEdges(resampleGraphs);
                instability = adjacencyInstability(resampleGraphs, data.getVariables());
            }

            SweepResult.MarkovStats markovStats = null;

            if (this.markovCheckTest != null) {
                markovStats = markovStats(pointGraph, data, params);
            }

            long elapsed = System.currentTimeMillis() - start;
            results.add(new SweepResult(setting, pointGraph, probabilityGraph, instability, markovStats,
                    resamples.size(), elapsed));

            if (this.verbose) {
                TetradLogger.getInstance().log("ParameterSweep: " + results.getLast());
            }
        }

        return new SweepReport(this.algorithm.getDescription(),
                this.markovCheckTest == null ? null : this.markovCheckTest.getDescription(),
                data.getNumRows(), data.getNumColumns(), this.numResamples, this.percentResampleSize,
                this.withReplacement, this.seed, results);
    }

    //==================================== SHARED MACHINERY ====================================//

    /**
     * Draws seed-controlled resamples of the given dataset: row-index samples of size percent * n, with or without
     * replacement. Drawing resamples once and reusing them across settings makes between-setting comparisons
     * paired. This method is also used by the StARS and StabilitySelection algorithm wrappers.
     *
     * @param data            the dataset.
     * @param numResamples    the number of resamples; zero returns an empty list.
     * @param percent         the fraction of the sample size per resample, in (0, 1].
     * @param withReplacement whether to draw with replacement.
     * @param seed            the random seed, or -1 for time-based.
     * @return the resampled datasets, in draw order.
     */
    public static List<DataSet> drawResamples(DataSet data, int numResamples, double percent,
                                              boolean withReplacement, long seed) {
        Random rand = (seed == -1) ? new Random() : new Random(seed);
        int n = data.getNumRows();
        int m = Math.max(1, (int) Math.round(percent * n));
        List<DataSet> resamples = new ArrayList<>();

        for (int s = 0; s < numResamples; s++) {
            int[] rows;

            if (withReplacement) {
                rows = new int[m];
                for (int i = 0; i < m; i++) rows[i] = rand.nextInt(n);
            } else {
                if (m > n) throw new IllegalArgumentException("Cannot draw " + m + " rows from " + n
                        + " without replacement.");
                int[] perm = new int[n];
                for (int i = 0; i < n; i++) perm[i] = i;

                for (int i = 0; i < m; i++) {
                    int j = i + rand.nextInt(n - i);
                    int t = perm[i];
                    perm[i] = perm[j];
                    perm[j] = t;
                }

                rows = new int[m];
                System.arraycopy(perm, 0, rows, 0, m);
            }

            resamples.add(data.subsetRows(rows));
        }

        return resamples;
    }

    /**
     * Runs the algorithm at the given parameters on each resample and returns the graphs, with nodes replaced by
     * the given variable list so graphs are comparable. Race-free: each resample writes to its own slot. This
     * method is also used by the StARS and StabilitySelection algorithm wrappers.
     *
     * @param algorithm    the algorithm.
     * @param parameters   the parameters (not mutated).
     * @param resamples    the resampled datasets.
     * @param variables    the variables of the original dataset, for node replacement.
     * @param parallelized whether to run the resample searches in parallel.
     * @return the graphs, in resample order.
     * @throws InterruptedException if a search is interrupted.
     */
    public static List<Graph> searchOnResamples(Algorithm algorithm, Parameters parameters, List<DataSet> resamples,
                                                List<Node> variables, boolean parallelized)
            throws InterruptedException {
        Graph[] graphs = new Graph[resamples.size()];

        try {
            java.util.stream.IntStream stream = java.util.stream.IntStream.range(0, resamples.size());
            if (parallelized) stream = stream.parallel();

            stream.forEach(s -> {
                try {
                    Graph g = algorithm.search(resamples.get(s), new Parameters(parameters));
                    graphs[s] = GraphUtils.replaceNodes(g, variables);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException ie) throw ie;
            throw e;
        }

        return List.of(graphs);
    }

    /**
     * Computes the StARS-style adjacency instability of a set of graphs over the given variables: the mean over
     * unordered variable pairs of 2 * theta * (1 - theta), where theta is the fraction of graphs in which the pair
     * is adjacent. In [0, 0.5]; 0 means every pair's adjacency is unanimous across the graphs.
     *
     * @param graphs    the graphs (nodes must be from the given variable list).
     * @param variables the variables.
     * @return the instability, or NaN if there are no graphs or fewer than two variables.
     */
    public static double adjacencyInstability(List<Graph> graphs, List<Node> variables) {
        int p = variables.size();
        if (graphs.isEmpty() || p < 2) return Double.NaN;

        double d = 0.0;
        int count = 0;

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                Node x = variables.get(i);
                Node y = variables.get(j);
                double theta = 0.0;

                for (Graph graph : graphs) {
                    if (graph.isAdjacentTo(x, y)) theta += 1.0;
                }

                theta /= graphs.size();
                d += 2 * theta * (1.0 - theta);
                count++;
            }
        }

        return d / count;
    }

    //==================================== PRIVATE ====================================//

    /**
     * Runs the Markov check on a point graph with the configured test at the given setting's parameters. A failure
     * to run the check yields all-NaN statistics rather than aborting the sweep; the failure is logged.
     *
     * @param graph  the point graph.
     * @param data   the dataset.
     * @param params the setting's parameters.
     * @return the statistics.
     */
    private SweepResult.MarkovStats markovStats(Graph graph, DataSet data, Parameters params) {
        try {
            IndependenceTest test = this.markovCheckTest.getTest(data, params);
            MarkovCheck mc = new MarkovCheck(graph, test, this.conditioningSetType);
            mc.setParallelized(this.parallelized);
            mc.generateAllResults();

            return new SweepResult.MarkovStats(
                    mc.getAndersonDarlingP(true), mc.getAndersonDarlingP(false),
                    mc.getKsPValue(true), mc.getKsPValue(false),
                    mc.getBinomialPValue_(true), mc.getBinomialPValue_(false),
                    mc.getFractionDependent(true), mc.getFractionDependent(false),
                    mc.getNumTests(true), mc.getNumTests(false));
        } catch (Exception e) {
            TetradLogger.getInstance().log("ParameterSweep: Markov check failed: " + e.getMessage());
            return new SweepResult.MarkovStats(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, 0, 0);
        }
    }
}
