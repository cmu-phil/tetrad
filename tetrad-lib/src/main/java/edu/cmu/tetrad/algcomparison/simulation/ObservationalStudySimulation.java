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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates data with the anatomy of a real observational scientific dataset (the archetype is
 * the Algerian Forest Fires dataset, but with all complications defaulted off this is a plain
 * mixed-data cross-sectional study). Variables come in roles:
 * <ul>
 * <li><b>Context variables</b> (C1, ...): exogenous drivers -- weather, season, region,
 * demographics. A fraction osPropContextDiscrete are discrete (region-like, osNumCategories
 * levels); the rest are continuous. In serial mode, continuous context follows a stationary
 * AR(1) process with coefficient osArCoef, and discrete context follows a sticky Markov chain.
 * Optionally, osNumHiddenContext additional context variables influence the system but are
 * OMITTED from the dataset (latent confounders); they appear in the true graph as latent
 * nodes, so FCI-style evaluation is possible.</li>
 * <li><b>System variables</b> (S1, ...): a causal DAG among themselves (average degree
 * osAvgSystemDegree), with context parents, monotone transmission nonlinearity
 * (osNonlinearity), and heterogeneous non-Gaussian noise. In serial mode each system variable
 * has a self-lag, and cross-lag edges (probability osPropCrossLag per ordered pair) allow
 * honest representation of feedback as X{t-1} -> Y{t}, Y{t-1} -> X{t}.</li>
 * <li><b>Index variables</b> (I1, ...): near-deterministic functions (noise osIndexNoise) of
 * system variables and EARLIER INDEX VARIABLES (chains like ISI/BUI -> FWI). In serial mode
 * each index is a recursive accumulator I{t} = delta I{t-1} + g(parents{t}) + noise, with
 * memory delta drawn per index from [osIndexMemoryLow, osIndexMemoryHigh] -- heterogeneous
 * drought-code-style memory, making indices MORE serially dependent than their drivers.</li>
 * <li><b>Outcome variables</b> (Y1, ...): depend on system, index, and context variables, with
 * pairwise interactions (weight osInteraction). If osDiscreteOutcome, outcomes are discrete
 * via softmax logits (fire/no-fire style).</li>
 * </ul>
 * Further options:
 * <ul>
 * <li><b>Ordinalization</b> (osPropOrdinalized): that fraction of system variables is
 * generated continuous but RECORDED as ordered categories at random cutpoints -- measurement
 * coarsening, reproducing the ordinal nominal-coding pathology. The true graph is the graph of
 * the underlying continuous system; the coarsening is measurement, exactly as with real
 * ordinal data.</li>
 * <li><b>Serial dependence</b> (osMaxLag): if greater than 0, one time series per subject is
 * generated and the TRUE GRAPH IS A TimeLagGraph with that maximum lag, following the
 * TimeSeriesSemSimulation convention, so lag-data and cross-lag tier machinery apply.
 * Self-lags, AR(1) context, and index accumulators act at lag 1; each cross-lag edge acts at a
 * lag drawn uniformly from 1..osMaxLag. The contemporaneous summary graph is available from
 * getContemporaneousGraph(index). If 0, rows are i.i.d. and the true graph is an ordinary
 * DAG.</li>
 * <li><b>Censoring</b> (osPropCensored, osCensorQuantile): that fraction of the continuous,
 * non-ordinalized system and outcome variables is censored at a detection limit -- values
 * beyond the limit are recorded AT the limit (side chosen at random). Rows are kept; this is
 * measurement, not selection, and the true graph is unchanged.</li>
 * <li><b>Missingness</b> (osMissingMechanism, osPropMissing): cells of system, index, and
 * outcome variables are marked missing (NaN, or the discrete missing value) at an exact
 * per-column rate. "mcar" is independent of everything; "mar" is driven by the first observed
 * context variable, which is always fully observed; "mnar" is driven by the cell's own
 * underlying value.</li>
 * <li><b>Panel structure</b> (osNumSubjects): the sample is divided into that many independent
 * subjects (replicates of the same system, sampleSize / osNumSubjects rows each,
 * concatenated). Each subject has a random intercept on the system variables, which acts as a
 * subject-level latent confounder in the pooled data. Subject boundaries are available from
 * getSubjectStarts(index); in serial mode, lagging must not cross these boundaries.</li>
 * </ul>
 * The osEdgeDensity parameter scales all cross-role edge probabilities (context to system,
 * system to index, index to index, context to index, and all parent probabilities of
 * outcomes); osAvgSystemDegree separately controls the density of the DAG among the system
 * variables. Minimum-parent floors (indices get at least two system parents, outcomes at
 * least two parents) are kept regardless, so indices and outcomes remain functions of
 * something.
 * <p>
 * NOTE: the random graph passed to the constructor is IGNORED; the role structure is generated
 * internally.
 *
 * @author josephramsey
 */
public class ObservationalStudySimulation implements Simulation {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph generator (ignored; kept for factory compatibility).
     */
    private final RandomGraph randomGraph;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The true graphs (DAGs, or TimeLagGraphs in serial mode).
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * The contemporaneous summary graphs (equal to the true graphs in non-serial mode).
     */
    private List<Graph> contemporaneousGraphs = new ArrayList<>();

    /**
     * Per-run subject start row indices.
     */
    private List<int[]> subjectStarts = new ArrayList<>();

    /**
     * Constructs an ObservationalStudySimulation. The given random graph is ignored; the role
     * structure is generated internally from parameters.
     *
     * @param graph the RandomGraph object (ignored).
     */
    public ObservationalStudySimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    /**
     * Creates simulated data and associated graphs based on the given parameters.
     *
     * @param parameters The parameters used to control the simulation process.
     * @param newModel   A flag indicating whether a new model should be created.
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();
        this.contemporaneousGraphs = new ArrayList<>();
        this.subjectStarts = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            simulateOne(parameters);
        }
    }

    /**
     * Simulates one run: structure, data, coarsening; appends results.
     *
     * @param parameters the simulation parameters.
     */
    private void simulateOne(Parameters parameters) {
        int numContext = Math.max(0, parameters.getInt(Params.OS_NUM_CONTEXT));
        int numHidden = Math.max(0, parameters.getInt(Params.OS_NUM_HIDDEN_CONTEXT));
        int numSystem = Math.max(1, parameters.getInt(Params.OS_NUM_SYSTEM));
        int numIndices = Math.max(0, parameters.getInt(Params.OS_NUM_INDICES));
        int numOutcomes = Math.max(0, parameters.getInt(Params.OS_NUM_OUTCOMES));
        double avgDegree = parameters.getDouble(Params.OS_AVG_SYSTEM_DEGREE);
        double propContextDiscrete = parameters.getDouble(Params.OS_PROP_CONTEXT_DISCRETE);
        int numCategories = Math.max(2, parameters.getInt(Params.OS_NUM_CATEGORIES));
        boolean discreteOutcome = parameters.getBoolean(Params.OS_DISCRETE_OUTCOME);
        double propOrdinalized = parameters.getDouble(Params.OS_PROP_ORDINALIZED);
        int maxLag = Math.max(0, parameters.getInt(Params.OS_MAX_LAG));
        boolean serial = maxLag > 0;
        double arCoef = parameters.getDouble(Params.OS_AR_COEF);
        double memLow = parameters.getDouble(Params.OS_INDEX_MEMORY_LOW);
        double memHigh = parameters.getDouble(Params.OS_INDEX_MEMORY_HIGH);
        double propCrossLag = parameters.getDouble(Params.OS_PROP_CROSS_LAG);
        int numSubjects = Math.max(1, parameters.getInt(Params.OS_NUM_SUBJECTS));
        double indexNoise = parameters.getDouble(Params.OS_INDEX_NOISE);
        double nonlinearity = parameters.getDouble(Params.OS_NONLINEARITY);
        double interaction = parameters.getDouble(Params.OS_INTERACTION);
        double density = parameters.getDouble(Params.OS_EDGE_DENSITY);
        String missingMechanism = parameters.getString(Params.OS_MISSING_MECHANISM)
                .trim().toLowerCase();
        double propMissing = parameters.getDouble(Params.OS_PROP_MISSING);
        double propCensored = parameters.getDouble(Params.OS_PROP_CENSORED);
        double censorQuantile = parameters.getDouble(Params.OS_CENSOR_QUANTILE);
        int sampleSize = parameters.getInt(Params.SAMPLE_SIZE);

        if (!missingMechanism.equals("none") && !missingMechanism.equals("mcar")
            && !missingMechanism.equals("mar") && !missingMechanism.equals("mnar")) {
            throw new IllegalArgumentException(
                    "osMissingMechanism must be one of: none, mcar, mar, mnar.");
        }

        RandomUtil rand = RandomUtil.getInstance();

        int nC = numContext + numHidden; // observed context first, hidden after
        int total = nC + numSystem + numIndices + numOutcomes;

        // ---------- Roles, names, types ----------

        String[] names = new String[total];
        boolean[] isDiscrete = new boolean[total];
        boolean[] isHidden = new boolean[total];

        for (int j = 0; j < numContext; j++) {
            names[j] = "C" + (j + 1);
            isDiscrete[j] = rand.nextDouble() < propContextDiscrete;
        }
        for (int j = 0; j < numHidden; j++) {
            names[numContext + j] = "H" + (j + 1);
            isHidden[numContext + j] = true; // hidden context is kept continuous
        }
        for (int j = 0; j < numSystem; j++) names[nC + j] = "S" + (j + 1);
        for (int j = 0; j < numIndices; j++) names[nC + numSystem + j] = "I" + (j + 1);
        for (int j = 0; j < numOutcomes; j++) {
            int col = nC + numSystem + numIndices + j;
            names[col] = "Y" + (j + 1);
            isDiscrete[col] = discreteOutcome;
        }

        // Ordinalized system variables (generated continuous, recorded discrete).
        boolean[] ordinalized = new boolean[total];
        int numOrd = (int) Math.round(propOrdinalized * numSystem);
        List<Integer> sysCols = new ArrayList<>();
        for (int j = 0; j < numSystem; j++) sysCols.add(nC + j);
        for (int k = 0; k < numOrd; k++) {
            int pick = sysCols.remove(rand.nextInt(sysCols.size()));
            ordinalized[pick] = true;
        }

        // ---------- Contemporaneous structure ----------

        boolean[][] parent = new boolean[total][total]; // parent[i][j]: i -> j

        int sys0 = nC, idx0 = nC + numSystem, out0 = nC + numSystem + numIndices;

        // Context -> system.
        for (int c = 0; c < nC; c++) {
            for (int s = sys0; s < idx0; s++) {
                if (rand.nextDouble() < 0.4 * density) parent[c][s] = true;
            }
        }

        // System DAG in order S1 < S2 < ...
        double pEdge = numSystem > 1 ? Math.min(1.0, avgDegree / (numSystem - 1)) : 0;
        for (int i = sys0; i < idx0; i++) {
            for (int j = i + 1; j < idx0; j++) {
                if (rand.nextDouble() < pEdge) parent[i][j] = true;
            }
        }

        // Indices: system parents (p = 0.5, at least 2 where possible), earlier indices
        // (p = 0.4), direct context (p = 0.2).
        for (int i = idx0; i < out0; i++) {
            int count = 0;
            for (int s = sys0; s < idx0; s++) {
                if (rand.nextDouble() < 0.5 * density) {
                    parent[s][i] = true;
                    count++;
                }
            }
            while (count < Math.min(2, numSystem)) {
                int s = sys0 + rand.nextInt(numSystem);
                if (!parent[s][i]) {
                    parent[s][i] = true;
                    count++;
                }
            }
            for (int i2 = idx0; i2 < i; i2++) if (rand.nextDouble() < 0.4 * density) parent[i2][i] = true;
            for (int c = 0; c < nC; c++) if (rand.nextDouble() < 0.2 * density) parent[c][i] = true;
        }

        // Outcomes: system (0.5), indices (0.7), context (0.3), at least 2 parents.
        for (int y = out0; y < total; y++) {
            int count = 0;
            for (int s = sys0; s < idx0; s++)
                if (rand.nextDouble() < 0.5 * density) {
                    parent[s][y] = true;
                    count++;
                }
            for (int i = idx0; i < out0; i++)
                if (rand.nextDouble() < 0.7 * density) {
                    parent[i][y] = true;
                    count++;
                }
            for (int c = 0; c < nC; c++)
                if (rand.nextDouble() < 0.3 * density) {
                    parent[c][y] = true;
                    count++;
                }
            while (count < 2 && numSystem + numIndices >= 2) {
                int p = sys0 + rand.nextInt(numSystem + numIndices);
                if (!parent[p][y]) {
                    parent[p][y] = true;
                    count++;
                }
            }
        }

        // ---------- Lag structure (serial mode) ----------

        double[] selfLag = new double[total];   // 0 = none; self-lags act at lag 1
        boolean[][] crossLag = new boolean[total][total]; // crossLag[i][j]: i{t-k} -> j{t}
        double[][] crossLagCoef = new double[total][total];
        int[][] crossLagLag = new int[total][total];      // the lag k of each cross-lag edge

        if (serial) {
            for (int c = 0; c < nC; c++) selfLag[c] = arCoef; // AR(1) context; sticky chains for discrete
            for (int s = sys0; s < idx0; s++) selfLag[s] = rand.nextUniform(0.2, 0.6);
            for (int i = idx0; i < out0; i++) selfLag[i] = rand.nextUniform(memLow, memHigh);

            for (int i = sys0; i < idx0; i++) {
                for (int j = sys0; j < idx0; j++) {
                    if (i != j && rand.nextDouble() < propCrossLag) {
                        crossLag[i][j] = true;
                        crossLagCoef[i][j] = rand.nextUniform(0.2, 0.5)
                                             * (rand.nextDouble() < 0.5 ? -1 : 1);
                        crossLagLag[i][j] = 1 + rand.nextInt(maxLag);
                    }
                }
            }
        }

        // ---------- Mechanisms ----------

        // Per-(edge) transmission for continuous parents: pointwise monotone map mixed with the
        // identity by osNonlinearity, with an approximate unit-variance normalizer; coefficient.
        MonotoneMap[][] map = new MonotoneMap[total][total];
        double[][] coef = new double[total][total];
        // Per-(discrete parent, child, level) shifts.
        double[][][] shift = new double[total][total][];

        for (int i = 0; i < total; i++) {
            for (int j = 0; j < total; j++) {
                if (!parent[i][j]) continue;
                if (isDiscrete[i]) {
                    shift[i][j] = new double[numCategories];
                    for (int k = 0; k < numCategories; k++) {
                        shift[i][j][k] = rand.nextGaussian(0, 0.7);
                    }
                } else {
                    map[i][j] = new MonotoneMap(nonlinearity, rand);
                    coef[i][j] = rand.nextUniform(0.2, 0.8) * (rand.nextDouble() < 0.5 ? -1 : 1);
                }
            }
        }

        // Interaction terms for indices and outcomes: pairs of continuous parents.
        List<int[]>[] interPairs = new List[total];
        double[][] interCoefArr = new double[total][];

        for (int j = idx0; j < total; j++) {
            List<Integer> cps = new ArrayList<>();
            for (int i = 0; i < total; i++) if (parent[i][j] && !isDiscrete[i]) cps.add(i);
            List<int[]> pairs = new ArrayList<>();
            List<Double> cs = new ArrayList<>();
            for (int a = 0; a < cps.size(); a++) {
                for (int b = a + 1; b < cps.size(); b++) {
                    pairs.add(new int[]{cps.get(a), cps.get(b)});
                    cs.add(rand.nextUniform(0.3, 0.7) * (rand.nextDouble() < 0.5 ? -1 : 1));
                }
            }
            interPairs[j] = pairs;
            interCoefArr[j] = new double[cs.size()];
            for (int k = 0; k < cs.size(); k++) interCoefArr[j][k] = cs.get(k);
        }

        // Softmax logit coefficients for discrete outcomes.
        double[][][] logitCoef = new double[total][][];
        for (int y = out0; y < total; y++) {
            if (!isDiscrete[y]) continue;
            logitCoef[y] = new double[numCategories][total];
            for (int k = 0; k < numCategories; k++) {
                for (int i = 0; i < total; i++) {
                    if (parent[i][y]) logitCoef[y][k][i] = rand.nextGaussian(0, 1.0);
                }
            }
        }

        int[] noiseFamily = new int[total];
        for (int j = 0; j < total; j++) noiseFamily[j] = rand.nextInt(4);

        // ---------- Generation ----------

        int perSubject = Math.max(2, sampleSize / numSubjects);
        int burn = serial ? 100 + 10 * maxLag : 0;

        double[][] data = new double[perSubject * numSubjects][total];
        int[] starts = new int[numSubjects];

        for (int subj = 0; subj < numSubjects; subj++) {
            starts[subj] = subj * perSubject;

            // Subject random intercepts on system variables (latent in pooled data).
            double[] subjShift = new double[total];
            if (numSubjects > 1) {
                for (int s = sys0; s < idx0; s++) subjShift[s] = rand.nextGaussian(0, 0.5);
            }

            // History ring: hist[0] is the previous row (lag 1), hist[k-1] is lag k.
            double[][] hist = new double[Math.max(1, maxLag)][total];
            int histCount = 0;

            for (int t = -burn; t < perSubject; t++) {
                double[] row = new double[total];

                for (int j = 0; j < total; j++) {
                    if (j < nC) {
                        // Context.
                        if (isDiscrete[j]) {
                            if (serial && histCount > 0) {
                                row[j] = rand.nextDouble() < 0.85 ? hist[0][j]
                                        : rand.nextInt(numCategories);
                            } else {
                                row[j] = rand.nextInt(numCategories);
                            }
                        } else {
                            if (serial && histCount > 0) {
                                row[j] = selfLag[j] * hist[0][j]
                                         + Math.sqrt(1 - selfLag[j] * selfLag[j])
                                           * rand.nextGaussian(0, 1);
                            } else {
                                row[j] = rand.nextGaussian(0, 1);
                            }
                        }
                        continue;
                    }

                    // System / index / outcome: contributions from parents at time t.
                    double add = subjShift[j];
                    for (int i = 0; i < total; i++) {
                        if (!parent[i][j]) continue;
                        if (isDiscrete[i]) {
                            add += shift[i][j][(int) row[i]];
                        } else {
                            add += coef[i][j] * map[i][j].apply(row[i]);
                        }
                    }

                    if (j >= idx0 && interPairs[j] != null) {
                        double inter = 0;
                        for (int k = 0; k < interPairs[j].size(); k++) {
                            int[] pr = interPairs[j].get(k);
                            inter += interCoefArr[j][k] * softclip(row[pr[0]]) * softclip(row[pr[1]]);
                        }
                        add = (1 - interaction) * add + interaction * inter;
                    }

                    if (serial && histCount > 0) {
                        if (selfLag[j] > 0) add += selfLag[j] * hist[0][j];
                        for (int i = 0; i < total; i++) {
                            if (crossLag[i][j] && histCount >= crossLagLag[i][j]) {
                                add += crossLagCoef[i][j] * hist[crossLagLag[i][j] - 1][i];
                            }
                        }
                    }

                    if (isDiscrete[j]) {
                        // Discrete outcome via softmax over parent contributions.
                        double[] logits = new double[numCategories];
                        for (int k = 0; k < numCategories; k++) {
                            for (int i = 0; i < total; i++) {
                                if (!parent[i][j]) continue;
                                double v = isDiscrete[i] ? row[i] : softclip(row[i]);
                                logits[k] += logitCoef[j][k][i] * v;
                            }
                        }
                        row[j] = sampleSoftmax(logits, rand);
                    } else {
                        double noiseSd = j >= idx0 && j < out0 ? indexNoise : 0.5;
                        row[j] = add + noiseSd * standardizedNoise(rand, noiseFamily[j]);
                    }
                }

                if (t >= 0) System.arraycopy(row, 0, data[starts[subj] + t], 0, total);

                for (int k = hist.length - 1; k > 0; k--) {
                    System.arraycopy(hist[k - 1], 0, hist[k], 0, total);
                }
                System.arraycopy(row, 0, hist[0], 0, total);
                if (histCount < hist.length) histCount++;
            }
        }

        // Standardize continuous columns over the pooled sample.
        for (int j = 0; j < total; j++) {
            if (!isDiscrete[j]) standardizeColumn(data, j);
        }

        // Censor a fraction of the continuous, non-ordinalized system and outcome columns at a
        // detection limit: values beyond the limit are RECORDED AT the limit (right- or
        // left-censoring, side chosen at random). Rows are kept; this is measurement, not
        // selection, and the true graph is unchanged.
        if (propCensored > 0) {
            List<Integer> eligible = new ArrayList<>();
            for (int j = sys0; j < idx0; j++) {
                if (!isDiscrete[j] && !ordinalized[j]) eligible.add(j);
            }
            for (int j = out0; j < total; j++) {
                if (!isDiscrete[j] && !ordinalized[j]) eligible.add(j);
            }
            int numCensor = (int) Math.round(propCensored * eligible.size());
            for (int k = 0; k < numCensor && !eligible.isEmpty(); k++) {
                int j = eligible.remove(rand.nextInt(eligible.size()));
                boolean right = rand.nextDouble() < 0.5;
                double q = right ? censorQuantile : 1.0 - censorQuantile;

                double[] col = new double[data.length];
                for (int r = 0; r < data.length; r++) col[r] = data[r][j];
                double[] sorted = col.clone();
                java.util.Arrays.sort(sorted);
                double limit = sorted[(int) Math.min(data.length - 1,
                        Math.max(0, Math.round(q * (data.length - 1))))];

                for (int r = 0; r < data.length; r++) {
                    if (right && data[r][j] > limit) data[r][j] = limit;
                    if (!right && data[r][j] < limit) data[r][j] = limit;
                }
            }
        }

        // Missingness mask over system, index, and outcome columns (context is always fully
        // observed). Rates are exact per column: the propMissing fraction of cells with the
        // highest (driver + noise) score is marked missing. mcar: the driver is pure noise.
        // mar: the driver is the first observed context variable, which is fully observed, so
        // missingness is at random given context. mnar: the driver is the cell's own
        // (underlying) value.
        boolean[][] missing = new boolean[data.length][total];

        if (!missingMechanism.equals("none") && propMissing > 0) {
            int contextDriver = numContext > 0 ? 0 : -1;

            for (int j = sys0; j < total; j++) {
                double[] score = new double[data.length];
                for (int r = 0; r < data.length; r++) {
                    double driver;
                    if (missingMechanism.equals("mnar")) {
                        driver = data[r][j];
                    } else if (missingMechanism.equals("mar") && contextDriver >= 0) {
                        driver = data[r][contextDriver];
                    } else {
                        driver = 0.0; // mcar (or mar with no context available)
                    }
                    score[r] = driver + 0.5 * rand.nextGaussian(0, 1);
                }

                double[] sorted = score.clone();
                java.util.Arrays.sort(sorted);
                double threshold = sorted[(int) Math.min(data.length - 1, Math.max(0,
                        Math.round((1.0 - propMissing) * (data.length - 1))))];

                for (int r = 0; r < data.length; r++) {
                    if (score[r] > threshold) missing[r][j] = true;
                }
            }
        }

        // Ordinalize the chosen system columns at random cutpoints.
        int[][] ordinalValues = new int[total][];
        for (int j = 0; j < total; j++) {
            if (!ordinalized[j]) continue;
            double[] col = new double[data.length];
            for (int r = 0; r < data.length; r++) col[r] = data[r][j];
            double[] sorted = col.clone();
            java.util.Arrays.sort(sorted);
            double[] cuts = new double[numCategories - 1];
            double[] qs = new double[numCategories - 1];
            for (int k = 0; k < numCategories - 1; k++) qs[k] = rand.nextUniform(0.15, 0.85);
            java.util.Arrays.sort(qs);
            for (int k = 0; k < numCategories - 1; k++) {
                cuts[k] = sorted[(int) (qs[k] * (data.length - 1))];
            }
            int[] vals = new int[data.length];
            for (int r = 0; r < data.length; r++) {
                int cat = 0;
                while (cat < numCategories - 1 && col[r] > cuts[cat]) cat++;
                vals[r] = cat;
            }
            ordinalValues[j] = vals;
        }

        // ---------- Assemble graph and dataset ----------

        List<Node> nodes = new ArrayList<>();
        for (int j = 0; j < total; j++) {
            Node node;
            if ((isDiscrete[j] || ordinalized[j]) && !isHidden[j]) {
                node = new DiscreteVariable(names[j], numCategories);
            } else {
                node = new ContinuousVariable(names[j]);
            }
            if (isHidden[j]) node.setNodeType(NodeType.LATENT);
            nodes.add(node);
        }

        Graph contemporaneous = new EdgeListGraph(nodes);
        for (int i = 0; i < total; i++) {
            for (int j = 0; j < total; j++) {
                if (parent[i][j]) contemporaneous.addDirectedEdge(nodes.get(i), nodes.get(j));
            }
        }
        LayoutUtil.defaultLayout(contemporaneous);

        Graph trueGraph;

        if (serial) {
            TimeLagGraph lagGraph = new TimeLagGraph();
            lagGraph.setMaxLag(maxLag);
            for (Node node : nodes) lagGraph.addNode(node);
            for (int i = 0; i < total; i++) {
                for (int j = 0; j < total; j++) {
                    if (parent[i][j]) {
                        lagGraph.addDirectedEdge(lagGraph.getNode(names[i], 0),
                                lagGraph.getNode(names[j], 0));
                    }
                    if (crossLag[i][j]) {
                        lagGraph.addDirectedEdge(lagGraph.getNode(names[i], crossLagLag[i][j]),
                                lagGraph.getNode(names[j], 0));
                    }
                }
                if (selfLag[i] > 0) {
                    lagGraph.addDirectedEdge(lagGraph.getNode(names[i], 1),
                            lagGraph.getNode(names[i], 0));
                }
            }
            trueGraph = lagGraph;
        } else {
            trueGraph = contemporaneous;
        }

        // Data: observed variables only.
        List<Node> observed = new ArrayList<>();
        List<Integer> obsCols = new ArrayList<>();
        for (int j = 0; j < total; j++) {
            if (!isHidden[j]) {
                observed.add(nodes.get(j));
                obsCols.add(j);
            }
        }

        MixedDataBox box = new MixedDataBox(observed, data.length);
        DataSet dataSet = new BoxDataSet(box, observed);

        for (int r = 0; r < data.length; r++) {
            for (int oj = 0; oj < obsCols.size(); oj++) {
                int j = obsCols.get(oj);
                if (missing[r][j]) {
                    if (ordinalized[j] || isDiscrete[j]) {
                        dataSet.setInt(r, oj, DiscreteVariable.MISSING_VALUE);
                    } else {
                        dataSet.setDouble(r, oj, Double.NaN);
                    }
                } else if (ordinalized[j]) {
                    dataSet.setInt(r, oj, ordinalValues[j][r]);
                } else if (isDiscrete[j]) {
                    dataSet.setInt(r, oj, (int) data[r][j]);
                } else {
                    dataSet.setDouble(r, oj, data[r][j]);
                }
            }
        }

        this.graphs.add(trueGraph);
        this.contemporaneousGraphs.add(contemporaneous);
        this.dataSets.add(dataSet);
        this.subjectStarts.add(starts);
    }

    /**
     * A pointwise strictly monotone map: g(x) = (1 - w) x + w h(softclip(x)) / s0, where h is a
     * randomly chosen strictly increasing function and s0 is an approximate unit-variance
     * normalizer for h under a standard normal input, estimated numerically at construction.
     */
    private static final class MonotoneMap {
        private final double w;
        private final int type;
        private final double a;
        private final boolean flip;
        private final double center;
        private final double scale;

        MonotoneMap(double w, RandomUtil rand) {
            this.w = w;
            this.type = rand.nextInt(4);
            switch (type) {
                case 0 -> this.a = rand.nextUniform(0.3, 0.7);
                case 1 -> this.a = rand.nextUniform(1.0, 3.0);
                case 2 -> this.a = rand.nextUniform(0.5, 2.0);
                default -> this.a = rand.nextUniform(0.4, 1.1);
            }
            this.flip = rand.nextDouble() < 0.5;

            // Numerical mean/sd of h under N(0, 1), via a fixed quadrature grid.
            double m = 0, s = 0;
            int grid = 400;
            double[] hs = new double[grid];
            double wsum = 0;
            double[] ws = new double[grid];
            for (int k = 0; k < grid; k++) {
                double x = -4.0 + 8.0 * k / (grid - 1);
                double weight = Math.exp(-0.5 * x * x);
                hs[k] = raw(x);
                ws[k] = weight;
                wsum += weight;
            }
            for (int k = 0; k < grid; k++) m += ws[k] * hs[k];
            m /= wsum;
            for (int k = 0; k < grid; k++) s += ws[k] * (hs[k] - m) * (hs[k] - m);
            s = Math.sqrt(s / wsum);
            this.center = m;
            this.scale = s > 0 ? s : 1.0;
        }

        private double raw(double xIn) {
            double x = 4.0 * Math.tanh(xIn / 4.0);
            return switch (type) {
                case 0 -> Math.sinh(a * x);
                case 1 -> Math.log(a * x + Math.sqrt(a * a * x * x + 1.0));
                case 2 -> Math.signum(x) * Math.pow(Math.abs(x), a);
                default -> flip ? -Math.exp(-a * x) : Math.exp(a * x);
            };
        }

        double apply(double x) {
            return (1.0 - w) * x + w * (raw(x) - center) / scale;
        }
    }

    /**
     * Soft clip at about +/-4.
     *
     * @param x the input.
     * @return 4 tanh(x / 4).
     */
    private static double softclip(double x) {
        return 4.0 * Math.tanh(x / 4.0);
    }

    /**
     * Samples a category from softmax logits.
     *
     * @param logits the logits.
     * @param rand   the random utility.
     * @return the sampled category.
     */
    private static int sampleSoftmax(double[] logits, RandomUtil rand) {
        double max = Double.NEGATIVE_INFINITY;
        for (double l : logits) max = Math.max(max, l);
        double sum = 0;
        double[] p = new double[logits.length];
        for (int k = 0; k < logits.length; k++) {
            p[k] = Math.exp(logits[k] - max);
            sum += p[k];
        }
        double u = rand.nextDouble() * sum, acc = 0;
        for (int k = 0; k < logits.length; k++) {
            acc += p[k];
            if (u <= acc) return k;
        }
        return logits.length - 1;
    }

    /**
     * Draws one unit-variance noise value from the given family.
     *
     * @param rand   the random utility.
     * @param family 0 = Gaussian, 1 = scaled t(7), 2 = centered exponential, 3 = standardized
     *               Gumbel.
     * @return the noise draw.
     */
    private double standardizedNoise(RandomUtil rand, int family) {
        switch (family) {
            case 1:
                return rand.nextT(7) / Math.sqrt(7.0 / 5.0);
            case 2:
                return rand.nextExponential(1.0) - 1.0;
            case 3:
                return (rand.nextGumbel(0.0, 1.0) - 0.5772156649015329) / (Math.PI / Math.sqrt(6.0));
            default:
                return rand.nextGaussian(0, 1);
        }
    }

    /**
     * Standardizes the given column of the data matrix in place. No-op if constant.
     *
     * @param data the data matrix.
     * @param j    the column index.
     */
    private void standardizeColumn(double[][] data, int j) {
        int rows = data.length;

        double mean = 0.0;
        for (double[] row : data) mean += row[j];
        mean /= rows;

        double var = 0.0;
        for (double[] row : data) var += (row[j] - mean) * (row[j] - mean);
        var /= (rows - 1);
        double sd = Math.sqrt(var);
        if (sd == 0) return;

        for (int r = 0; r < rows; r++) data[r][j] = (data[r][j] - mean) / sd;
    }

    /**
     * Returns the contemporaneous summary graph for the given run (equal to the true graph in
     * non-serial mode).
     *
     * @param index the run index.
     * @return the contemporaneous graph.
     */
    public Graph getContemporaneousGraph(int index) {
        return this.contemporaneousGraphs.get(index);
    }

    /**
     * Returns the subject start row indices for the given run. In serial mode, lagging must not
     * cross these boundaries.
     *
     * @param index the run index.
     * @return the subject start rows.
     */
    public int[] getSubjectStarts(int index) {
        return this.subjectStarts.get(index).clone();
    }

    /**
     * Returns the true graph at the specified index: a DAG, or a TimeLagGraph with maximum lag
     * 1 in serial mode.
     *
     * @param index The index of the desired true graph.
     * @return The true graph at the specified index.
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * Returns the number of data models.
     *
     * @return The number of data sets to simulate.
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * Returns the data model at the specified index.
     *
     * @param index The index of the desired simulated data set.
     * @return The data model at the specified index.
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * Returns the data type of the data set.
     *
     * @return Mixed in general (the actual composition depends on the parameters).
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Returns the description of the simulation.
     *
     * @return a short, one-line description of the simulation.
     */
    public String getDescription() {
        return "Observational Study (context / system / indices / outcome; mixed data; optional "
               + "serial dependence, panel structure, hidden context, ordinalization); the "
               + "random graph is ignored";
    }

    /**
     * Returns the short name of the simulation.
     *
     * @return The short name of the simulation.
     */
    public String getShortName() {
        return "ObsStudy";
    }

    /**
     * Retrieves the parameters required for the simulation. Graph parameters are omitted since
     * the structure is generated internally.
     *
     * @return A list of String names representing the parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.OS_NUM_CONTEXT);
        parameters.add(Params.OS_NUM_HIDDEN_CONTEXT);
        parameters.add(Params.OS_NUM_SYSTEM);
        parameters.add(Params.OS_NUM_INDICES);
        parameters.add(Params.OS_NUM_OUTCOMES);
        parameters.add(Params.OS_AVG_SYSTEM_DEGREE);
        parameters.add(Params.OS_PROP_CONTEXT_DISCRETE);
        parameters.add(Params.OS_NUM_CATEGORIES);
        parameters.add(Params.OS_DISCRETE_OUTCOME);
        parameters.add(Params.OS_PROP_ORDINALIZED);
        parameters.add(Params.OS_MAX_LAG);
        parameters.add(Params.OS_AR_COEF);
        parameters.add(Params.OS_INDEX_MEMORY_LOW);
        parameters.add(Params.OS_INDEX_MEMORY_HIGH);
        parameters.add(Params.OS_PROP_CROSS_LAG);
        parameters.add(Params.OS_NUM_SUBJECTS);
        parameters.add(Params.OS_INDEX_NOISE);
        parameters.add(Params.OS_NONLINEARITY);
        parameters.add(Params.OS_INTERACTION);
        parameters.add(Params.OS_EDGE_DENSITY);
        parameters.add(Params.OS_MISSING_MECHANISM);
        parameters.add(Params.OS_PROP_MISSING);
        parameters.add(Params.OS_PROP_CENSORED);
        parameters.add(Params.OS_CENSOR_QUANTILE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SEED);

        return parameters;
    }

    /**
     * Returns the random graph class used in the simulation (ignored for structure).
     *
     * @return The class of the random graph.
     */
    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    /**
     * Returns the class of the current simulation.
     *
     * @return The simulation class.
     */
    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }
}
