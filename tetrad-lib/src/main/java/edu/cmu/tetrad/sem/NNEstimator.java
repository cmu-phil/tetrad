package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.AdequacyParams;
import edu.cmu.tetrad.sem.AdequacyReport;
import edu.cmu.tetrad.sem.CVReport;
import edu.cmu.tetrad.sem.NodeCVSummary;
import edu.cmu.tetrad.sem.RandomFeatureMMD;
import edu.cmu.tetrad.sem.TrainedDagAdequacy;
import edu.cmu.tetrad.sem.TrainedDagSimulatorGNM;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure-library neural-network estimator for a DAG factorization.
 *
 * <p>Given a dataset and a DAG, this class:
 * <ol>
 *   <li>Trains a small neural network for each node given its parents
 *       (via {@link TrainedDagSimulatorGNM}).</li>
 *   <li>Simulates a new dataset of any requested size from the fitted model.</li>
 *   <li>Computes an {@link AdequacyReport} comparing the observed and simulated
 *       joint distributions (MMD² plus per-node summaries).</li>
 *   <li>Optionally runs k-fold cross-validation to produce honest OOS metrics
 *       at both the node level and the whole-graph level ({@link #crossValidate}).</li>
 * </ol>
 *
 * <p>This class has no dependency on any GUI toolkit and can be used directly
 * from the Tetrad library without a Tetrad session.
 *
 * <p>Typical usage:
 * <pre>{@code
 *   NNEstimator est = new NNEstimator(observedData, dag);
 *   est.fit();
 *   DataSet simulated = est.simulate(observedData.getNumRows());
 *   AdequacyReport report = est.getAdequacyReport();
 *
 *   // Optional: honest OOS assessment
 *   CVReport cv = est.crossValidate(5);
 *   System.out.println(cv.toText());
 * }</pre>
 */
public final class NNEstimator implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ── inputs ───────────────────────────────────────────────────────────────

    private final DataSet observedData;
    private final Graph dag;
    private final NNEstimatorParams params;

    // ── state ────────────────────────────────────────────────────────────────

    /** The fitted simulator; null until {@link #fit()} is called. */
    private TrainedDagSimulatorGNM fittedSimulator;

    /** The most recent simulated dataset; null until {@link #simulate} is called. */
    private DataSet simulatedData;

    /** The most recent adequacy report; null until {@link #simulate} is called. */
    private AdequacyReport adequacyReport;

    /** The most recent CV report; null until {@link #crossValidate} is called. */
    private CVReport cvReport;

    // ── constructors ─────────────────────────────────────────────────────────

    /**
     * Creates an estimator with default parameters.
     *
     * @param observedData the dataset to train on; must contain all nodes in {@code dag}
     * @param dag          the DAG defining the factorization structure
     */
    public NNEstimator(DataSet observedData, Graph dag) {
        this(observedData, dag, new NNEstimatorParams());
    }

    /**
     * Creates an estimator with explicit parameters.
     *
     * @param observedData the dataset to train on
     * @param dag          the DAG defining the factorization structure
     * @param params       tuning parameters for the NN and adequacy assessment
     */
    public NNEstimator(DataSet observedData, Graph dag, NNEstimatorParams params) {
        this.observedData = Objects.requireNonNull(observedData, "observedData");
        this.dag          = GraphUtils.replaceNodes(Objects.requireNonNull(dag,          "dag"), observedData.getVariables());
        this.params       = Objects.requireNonNull(params,       "params");
    }

    // ── public API ───────────────────────────────────────────────────────────

    /**
     * Trains one neural network per node (given its parents) on the observed data.
     * Must be called before {@link #simulate}.
     */
    public void fit() {
        fittedSimulator = buildSimulator(observedData, params.seed);
        fittedSimulator.fit();
    }

    /**
     * Simulates a dataset of the requested size from the fitted model and
     * computes an {@link AdequacyReport} comparing it to the observed data.
     *
     * @param sampleSize number of rows to simulate; must be &ge; 1
     * @return the simulated dataset
     * @throws IllegalStateException if {@link #fit()} has not been called
     */
    public DataSet simulate(int sampleSize) {
        checkFitted();
        if (sampleSize < 1) throw new IllegalArgumentException("sampleSize must be >= 1");

        TrainedDagSimulatorGNM.SimResult result = fittedSimulator.simulate(sampleSize);
        simulatedData = result.toDataSet();
        simulatedData.setName("Simulated");

        adequacyReport = TrainedDagAdequacy.mmd2(
                observedData,
                simulatedData,
                fittedSimulator,
                buildAdequacyParams());

        return simulatedData;
    }

    /**
     * Convenience method: fits the model and immediately simulates
     * {@code sampleSize} rows.
     *
     * @param sampleSize number of rows to simulate
     * @return the simulated dataset
     */
    public DataSet fitAndSimulate(int sampleSize) {
        fit();
        return simulate(sampleSize);
    }

    /**
     * Runs k-fold cross-validation and returns a {@link CVReport} with honest
     * OOS metrics at both the node level and the whole-graph level.
     *
     * <p>For each fold:
     * <ol>
     *   <li>A fresh {@link TrainedDagSimulatorGNM} is trained on the k-1
     *       training folds.</li>
     *   <li>Node-level OOS MSE (continuous) or 0-1 loss (discrete) is computed
     *       by calling {@link TrainedDagSimulatorGNM#predictNode} on each
     *       held-out row. Root nodes are skipped.</li>
     *   <li>A whole-graph OOS MMD² is computed by simulating
     *       {@code testSize} rows from the fold-trained model and comparing
     *       the joint distribution to the held-out rows.</li>
     * </ol>
     *
     * <p>This method does <em>not</em> require {@link #fit()} to have been called
     * first — it is self-contained. It does not update the full-data fitted model;
     * call {@link #fit()} separately if you also want that.
     *
     * @param k number of folds; must be &ge; 2 and &le; n
     * @return a {@link CVReport} with per-node and whole-graph OOS metrics
     */
    public CVReport crossValidate(int k) {
        int n = observedData.getNumRows();
        if (k < 2)  throw new IllegalArgumentException("k must be >= 2");
        if (k > n)  throw new IllegalArgumentException("k must be <= number of rows (" + n + ")");

        List<Node> variables = observedData.getVariables();
        int p = variables.size();

        // Per-variable OOS accumulators.
        double[] sseCont  = new double[p];  // sum of squared errors (continuous nodes)
        int[]    nCont    = new int[p];     // OOS row count (continuous)
        double[] lossDisc = new double[p];  // sum of 0-1 loss (discrete nodes)
        int[]    nDisc    = new int[p];     // OOS row count (discrete)

        // Whole-graph OOS MMD² across folds.
        double totalMmd2 = 0.0;
        int    mmd2Count = 0;

        // Baseline statistics from the full dataset.
        double[] baselineMse  = computeBaselineMse(variables);
        double[] baselineXent = computeBaselineXent(variables);

        int foldSize = n / k;

        for (int fold = 0; fold < k; fold++) {
            int testStart = fold * foldSize;
            int testEnd   = (fold == k - 1) ? n : testStart + foldSize;
            int testN     = testEnd - testStart;
            int trainN    = n - testN;

            // Partition row indices.
            int[] trainRows = new int[trainN];
            int[] testRows  = new int[testN];
            int ti = 0, vi = 0;
            for (int r = 0; r < n; r++) {
                if (r >= testStart && r < testEnd) testRows[vi++]  = r;
                else                               trainRows[ti++] = r;
            }

            DataSet trainSet = rowSubset(observedData, trainRows);
            DataSet testSet  = rowSubset(observedData, testRows);

            // Train a fresh simulator on this fold's training data.
            long foldSeed = params.seed ^ (long) fold * 0x9E3779B97F4A7C15L;
            TrainedDagSimulatorGNM sim = buildSimulator(trainSet, foldSeed);
            sim.fit();

            // ── Node-level OOS ────────────────────────────────────────────────

            for (int j = 0; j < p; j++) {
                Node var   = variables.get(j);
                boolean isDisc = (var instanceof DiscreteVariable);

                for (int ti2 = 0; ti2 < testN; ti2++) {
                    // predictNode operates on testSet indices (0-based within testSet).
                    double pred = sim.predictNode(j, testSet, ti2);

                    // NaN signals a root node — no conditional prediction available.
                    if (!Double.isFinite(pred)) continue;

                    if (!isDisc) {
                        double obs = testSet.getDouble(ti2, j);
                        if (!Double.isFinite(obs)) continue;
                        double err = obs - pred;
                        sseCont[j] += err * err;
                        nCont[j]++;
                    } else {
                        int obs = TrainedDagSimulatorGNM.safeGetInt(testSet, ti2, j);
                        if (obs < 0) continue;
                        // pred is argmax class; use 0-1 loss as proxy for cross-entropy.
                        lossDisc[j] += (obs == (int) pred) ? 0.0 : 1.0;
                        nDisc[j]++;
                    }
                }
            }

            // ── Whole-graph OOS MMD² ──────────────────────────────────────────

            try {
                TrainedDagSimulatorGNM.SimResult simResult = sim.simulate(testN);
                DataSet simTest = simResult.toDataSet();

                double[][] X = toMatrix(testSet, variables);
                double[][] Y = toMatrix(simTest, variables);

                double mmd2 = RandomFeatureMMD.compute(
                        X, Y,
                        params.mmdFeatures,
                        params.mmdSeed ^ fold,
                        1.0,
                        params.mmdMaxRows);

                totalMmd2 += mmd2;
                mmd2Count++;
            } catch (Exception ignored) {
                // If simulation fails for a fold, skip its MMD² contribution.
            }
        }

        // ── Assemble per-node summaries (non-root nodes only) ─────────────────

        List<NodeCVSummary> summaries = new ArrayList<>();

        for (int j = 0; j < p; j++) {
            Node var   = variables.get(j);
            boolean isDisc = (var instanceof DiscreteVariable);

            List<String> parentNames = new ArrayList<>();
            for (Node parent : dag.getParents(var)) parentNames.add(parent.getName());
            if (parentNames.isEmpty()) continue;   // skip roots

            double oosMse  = (nCont[j] > 0) ? sseCont[j]  / nCont[j]  : Double.NaN;
            double oosLoss = (nDisc[j] > 0) ? lossDisc[j] / nDisc[j]  : Double.NaN;

            summaries.add(new NodeCVSummary(
                    var.getName(),
                    isDisc,
                    parentNames,
                    k,
                    isDisc  ? Double.NaN : oosMse,
                    isDisc  ? Double.NaN : baselineMse[j],
                    !isDisc ? Double.NaN : oosLoss,
                    !isDisc ? Double.NaN : baselineXent[j]));
        }

        double meanMmd2 = (mmd2Count > 0) ? totalMmd2 / mmd2Count : Double.NaN;
        cvReport = new CVReport(k, summaries, meanMmd2);
        return cvReport;
    }

    // ── accessors ────────────────────────────────────────────────────────────

    /** @return the observed (input) dataset */
    public DataSet getObservedData() { return observedData; }

    /** @return the DAG used for factorization */
    public Graph getDag() { return dag; }

    /** @return the parameters used by this estimator */
    public NNEstimatorParams getParams() { return params; }

    /**
     * @return the most recently simulated dataset, or {@code null} if
     *         {@link #simulate} has not yet been called
     */
    public DataSet getSimulatedData() { return simulatedData; }

    /**
     * @return the adequacy report from the most recent {@link #simulate} call,
     *         or {@code null} if {@link #simulate} has not yet been called
     */
    public AdequacyReport getAdequacyReport() { return adequacyReport; }

    /**
     * @return the CV report from the most recent {@link #crossValidate} call,
     *         or {@code null} if {@link #crossValidate} has not yet been called
     */
    public CVReport getCvReport() { return cvReport; }

    /** @return {@code true} if {@link #fit()} has been called successfully */
    public boolean isFitted() { return fittedSimulator != null; }

    // ── private helpers ──────────────────────────────────────────────────────

    private void checkFitted() {
        if (fittedSimulator == null)
            throw new IllegalStateException("fit() must be called before simulate()");
    }

    private TrainedDagSimulatorGNM buildSimulator(DataSet data, long seed) {
        TrainedDagSimulatorGNM.Params gnmParams = new TrainedDagSimulatorGNM.Params();
        gnmParams.seed = seed;
        return new TrainedDagSimulatorGNM(data, dag, gnmParams);
    }

    private AdequacyParams buildAdequacyParams() {
        AdequacyParams ap  = new AdequacyParams();
        ap.mmdFeatures     = params.mmdFeatures;
        ap.mmdSeed         = params.mmdSeed;
        ap.mmdMaxRows      = params.mmdMaxRows;
        return ap;
    }

    /**
     * Extracts a row subset of {@code source} by index array.
     * Works for mixed continuous/discrete datasets via {@link MixedDataBox}.
     */
    private static DataSet rowSubset(DataSet source, int[] rows) {
        List<Node> vars = source.getVariables();
        int p = vars.size();
        int n = rows.length;
        MixedDataBox box = new MixedDataBox(vars, n);
        for (int i = 0; i < n; i++) {
            int r = rows[i];
            for (int j = 0; j < p; j++) {
                if (vars.get(j) instanceof DiscreteVariable) {
                    box.set(i, j, TrainedDagSimulatorGNM.safeGetInt(source, r, j));
                } else {
                    box.set(i, j, source.getDouble(r, j));
                }
            }
        }
        return new BoxDataSet(box, vars);
    }

    /**
     * Converts a DataSet to a double matrix for MMD² computation.
     * Discrete variables are represented by their integer code cast to double.
     */
    private static double[][] toMatrix(DataSet data, List<Node> variables) {
        int n = data.getNumRows();
        int p = variables.size();
        double[][] out = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                out[i][j] = (variables.get(j) instanceof DiscreteVariable)
                        ? TrainedDagSimulatorGNM.safeGetInt(data, i, j)
                        : data.getDouble(i, j);
            }
        }
        return out;
    }

    /**
     * Computes marginal variance (baseline MSE) per continuous variable.
     * Returns NaN for discrete variables.
     */
    private double[] computeBaselineMse(List<Node> variables) {
        int p = variables.size();
        int n = observedData.getNumRows();
        double[] baseline = new double[p];
        for (int j = 0; j < p; j++) {
            if (variables.get(j) instanceof DiscreteVariable) {
                baseline[j] = Double.NaN;
                continue;
            }
            double sum = 0, sum2 = 0;
            int count = 0;
            for (int i = 0; i < n; i++) {
                double v = observedData.getDouble(i, j);
                if (!Double.isFinite(v)) continue;
                sum += v; sum2 += v * v; count++;
            }
            if (count < 2) { baseline[j] = Double.NaN; continue; }
            double mean = sum / count;
            baseline[j] = (sum2 - count * mean * mean) / count;
        }
        return baseline;
    }

    /**
     * Computes marginal entropy (baseline cross-entropy) per discrete variable.
     * Returns NaN for continuous variables.
     */
    private double[] computeBaselineXent(List<Node> variables) {
        int p = variables.size();
        int n = observedData.getNumRows();
        double[] baseline = new double[p];
        for (int j = 0; j < p; j++) {
            if (!(variables.get(j) instanceof DiscreteVariable dv)) {
                baseline[j] = Double.NaN;
                continue;
            }
            int L = dv.getNumCategories();
            double[] counts = new double[L];
            int total = 0;
            for (int i = 0; i < n; i++) {
                int v = TrainedDagSimulatorGNM.safeGetInt(observedData, i, j);
                if (v < 0 || v >= L) continue;
                counts[v]++; total++;
            }
            double xent = 0.0;
            for (int c = 0; c < L; c++) {
                if (counts[c] == 0) continue;
                double pp = counts[c] / total;
                xent -= pp * TMath.log(pp);
            }
            baseline[j] = xent;
        }
        return baseline;
    }
}
