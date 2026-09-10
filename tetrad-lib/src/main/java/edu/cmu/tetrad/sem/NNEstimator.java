package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.IntStream;

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
 *       at both the node level and the whole-graph level ({@link #crossValidate});
 *       the fold models are cached and reused by
 *       {@link #computePartialEdgeStrength}.</li>
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

    /**
     * The dataset to train on.
     */
    private final DataSet observedData;
    /**
     * The DAG defining the factorization structure.
     */
    private final Graph dag;
    /**
     * The parameters used by this estimator.
     */
    private final NNEstimatorParams params;

    // ── state ────────────────────────────────────────────────────────────────

    /**
     * The fitted simulator; null until {@link #fit()} is called.
     */
    private TrainedDagSimulatorGNM fittedSimulator;

    /**
     * The most recent simulated dataset; null until {@link #simulate} is called.
     */
    private DataSet simulatedData;

    /**
     * The most recent adequacy report; null until {@link #simulate} is called.
     */
    private AdequacyReport adequacyReport;

    /**
     * The most recent CV report; null until {@link #crossValidate} is called.
     */
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
        this.dag = GraphUtils.replaceNodes(Objects.requireNonNull(dag, "dag"), observedData.getVariables());
        this.params = Objects.requireNonNull(params, "params");
    }

    // ── public API ───────────────────────────────────────────────────────────

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

    // ── accessors ────────────────────────────────────────────────────────────

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
     * <p>The k fold models (one simulator trained on each set of k−1 training
     * folds) are built once by {@link #getFoldModels(int)} and cached, so that
     * {@link #computePartialEdgeStrength} can reuse exactly the same folds.
     * For each fold:
     * <ol>
     *   <li>Node-level OOS MSE (continuous) or cross-entropy (discrete) is
     *       computed by calling {@link TrainedDagSimulatorGNM#predictNode} on
     *       each held-out row. Root nodes are skipped.</li>
     *   <li>A whole-graph OOS MMD² is computed by simulating
     *       {@code testSize} rows from the fold-trained model and comparing
     *       the joint distribution to the held-out rows.</li>
     * </ol>
     *
     * <p>Folds are contiguous blocks of rows in file order (a blocked CV);
     * shuffle the rows first if they are sorted by some variable.
     *
     * <p>This method does <em>not</em> require {@link #fit()} to have been called
     * first — it is self-contained. It does not update the full-data fitted model;
     * call {@link #fit()} separately if you also want that.
     *
     * @param k number of folds; must be &ge; 2 and &le; n
     * @return a {@link CVReport} with per-node and whole-graph OOS metrics
     */
    public CVReport crossValidate(int k) {
        FoldModels fm = getFoldModels(k);

        List<Node> variables = observedData.getVariables();
        int p = variables.size();

        // Thread-safe per-variable OOS accumulators.
        DoubleAdder[]        sseCont  = new DoubleAdder[p];
        AtomicInteger[]      nCont    = new AtomicInteger[p];
        DoubleAdder[]        xentDisc = new DoubleAdder[p];
        AtomicInteger[]      nDisc    = new AtomicInteger[p];
        for (int j = 0; j < p; j++) {
            sseCont[j]  = new DoubleAdder();
            nCont[j]    = new AtomicInteger(0);
            xentDisc[j] = new DoubleAdder();
            nDisc[j]    = new AtomicInteger(0);
        }
        DoubleAdder totalMmd2Adder  = new DoubleAdder();
        AtomicInteger mmd2CountAtomic = new AtomicInteger(0);

        // Baseline statistics from the full dataset.
        double[] baselineMse  = computeBaselineMse(variables);
        double[] baselineXent = computeBaselineXent(variables);

        IntStream.range(0, k).parallel().forEach(fold -> {
            TrainedDagSimulatorGNM sim = fm.sims[fold];
            DataSet testSet = fm.test[fold];
            int testN = testSet.getNumRows();

            // ── Node-level OOS ────────────────────────────────────────────────────
            for (int j = 0; j < p; j++) {
                Node var     = variables.get(j);
                boolean isDisc = (var instanceof DiscreteVariable);

                for (int ti2 = 0; ti2 < testN; ti2++) {
                    double pred = sim.predictNode(j, testSet, ti2);
                    // NaN signals a root node — no conditional prediction available.
                    if (!Double.isFinite(pred)) continue;

                    if (!isDisc) {
                        double obs = testSet.getDouble(ti2, j);
                        if (!Double.isFinite(obs)) continue;
                        double err = obs - pred;
                        sseCont[j].add(err * err);
                        nCont[j].incrementAndGet();
                    } else {
                        double[] probs = sim.predictNodeProbs(j, testSet, ti2);
                        if (probs == null) continue;
                        int obs = TrainedDagSimulatorGNM.safeGetInt(testSet, ti2, j);
                        if (obs < 0 || obs >= probs.length) continue;
                        xentDisc[j].add(-TMath.log(TMath.max(probs[obs], 1e-300)));
                        nDisc[j].incrementAndGet();
                    }
                }
            }

            // ── Whole-graph OOS MMD² ──────────────────────────────────────────────
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
                totalMmd2Adder.add(mmd2);
                mmd2CountAtomic.incrementAndGet();
            } catch (Exception ignored) {
                // If simulation fails for a fold, skip its MMD² contribution.
            }
        });

        // ── Assemble per-node summaries (non-root nodes only) ─────────────────────
        List<NodeCVSummary> summaries = new ArrayList<>();
        for (int j = 0; j < p; j++) {
            Node var     = variables.get(j);
            boolean isDisc = (var instanceof DiscreteVariable);

            List<String> parentNames = new ArrayList<>();
            for (Node parent : dag.getParents(var)) parentNames.add(parent.getName());
            if (parentNames.isEmpty()) continue;   // skip roots

            double oosMse  = (nCont[j].get() > 0)
                    ? sseCont[j].sum()  / nCont[j].get()  : Double.NaN;
            double oosXent = (nDisc[j].get() > 0)
                    ? xentDisc[j].sum() / nDisc[j].get() : Double.NaN;

            summaries.add(new NodeCVSummary(
                    var.getName(),
                    isDisc,
                    parentNames,
                    k,
                    isDisc  ? Double.NaN : oosMse,
                    isDisc  ? Double.NaN : baselineMse[j],
                    !isDisc ? Double.NaN : oosXent,
                    !isDisc ? Double.NaN : baselineXent[j]));
        }

        double meanMmd2 = (mmd2CountAtomic.get() > 0)
                ? totalMmd2Adder.sum() / mmd2CountAtomic.get() : Double.NaN;

        cvReport = new CVReport(k, summaries, meanMmd2);
        return cvReport;
    }

    // ── Fold models ───────────────────────────────────────────────────────────

    /**
     * The k fold models of a k-fold split: for each fold, the training rows,
     * the held-out rows, and a simulator fitted on the training rows only.
     * Built once per k and shared by {@link #crossValidate} and
     * {@link #computePartialEdgeStrength} so their numbers live on the same
     * folds.
     */
    private static final class FoldModels {
        final int k;
        final DataSet[] train;
        final DataSet[] test;
        final TrainedDagSimulatorGNM[] sims;

        FoldModels(int k, DataSet[] train, DataSet[] test, TrainedDagSimulatorGNM[] sims) {
            this.k = k;
            this.train = train;
            this.test = test;
            this.sims = sims;
        }
    }

    /** Cached fold models; rebuilt only when k changes. Not serialized. */
    private transient FoldModels foldModels;

    /**
     * Returns the fold models for a k-fold split, building and caching them
     * on first use (in parallel over folds). Folds are contiguous blocks of
     * rows in file order. Thread-safe: concurrent callers wait for the first
     * build rather than each building their own.
     *
     * @param k number of folds; must be &ge; 2 and &le; n
     * @return the cached fold models for this k
     */
    private synchronized FoldModels getFoldModels(int k) {
        int n = observedData.getNumRows();
        if (k < 2) throw new IllegalArgumentException("k must be >= 2");
        if (k > n) throw new IllegalArgumentException("k must be <= number of rows (" + n + ")");
        if (foldModels != null && foldModels.k == k) return foldModels;

        int foldSize = n / k;
        DataSet[] train = new DataSet[k];
        DataSet[] test  = new DataSet[k];
        TrainedDagSimulatorGNM[] sims = new TrainedDagSimulatorGNM[k];

        IntStream.range(0, k).parallel().forEach(fold -> {
            int testStart = fold * foldSize;
            int testEnd   = (fold == k - 1) ? n : testStart + foldSize;
            int testN     = testEnd - testStart;
            int trainN    = n - testN;

            int[] trainRows = new int[trainN];
            int[] testRows  = new int[testN];
            int ti = 0, vi = 0;
            for (int r = 0; r < n; r++) {
                if (r >= testStart && r < testEnd) testRows[vi++] = r;
                else                               trainRows[ti++] = r;
            }
            train[fold] = rowSubset(observedData, trainRows);
            test[fold]  = rowSubset(observedData, testRows);

            long foldSeed = params.seed ^ (long) fold * 0x9E3779B97F4A7C15L;
            TrainedDagSimulatorGNM sim = buildSimulator(train[fold], foldSeed);
            sim.fit();
            sims[fold] = sim;
        });

        foldModels = new FoldModels(k, train, test, sims);
        return foldModels;
    }

    /**
     * Reports whether fold models for {@code k} are currently cached.
     *
     * @param k number of folds
     * @return true if a call to {@link #crossValidate(int)} or
     * {@link #computePartialEdgeStrength} at this k would reuse cached
     * fold models rather than refitting
     */
    public boolean hasFoldModels(int k) {
        FoldModels fm = foldModels;
        return fm != null && fm.k == k;
    }

    /**
     * Returns the observed (input) dataset.
     * @return the observed (input) dataset
     */
    public DataSet getObservedData() {
        return observedData;
    }

    /**
     * Returns the DAG used for factorization.
     * @return the DAG used for factorization
     */
    public Graph getDag() {
        return dag;
    }

    /**
     * Returns the DAG as a string.
     * @return the parameters used by this estimator
     */
    public NNEstimatorParams getParams() {
        return params;
    }

    /**
     * Returns the number of nodes in the DAG.
     * @return the most recently simulated dataset, or {@code null} if
     * {@link #simulate} has not yet been called
     */
    public DataSet getSimulatedData() {
        return simulatedData;
    }

    /**
     * Returns the most recently fitted simulator, or {@code null} if
     * @return the adequacy report from the most recent {@link #simulate} call,
     * or {@code null} if {@link #simulate} has not yet been called
     */
    public AdequacyReport getAdequacyReport() {
        return adequacyReport;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Retrieves the CVReport object.
     *
     * @return the CVReport object representing the generated CV report.
     */
    public CVReport getCvReport() {
        return cvReport;
    }

    /**
     * Computes the baseline MSE for each variable in the DAG.
     * @return {@code true} if {@link #fit()} has been called successfully
     */
    public boolean isFitted() {
        return fittedSimulator != null;
    }

    // ── Edge strength ─────────────────────────────────────────────────────────

    /**
     * Computes the intervention strength of a single edge (parentName → childName)
     * in the sense of Janzing et al. (2013) and DoWhy's {@code arrow_strength}.
     *
     * <p>The child's fitted mechanism is held fixed; nothing is retrained. For
     * each of {@code numConfigs} parent configurations, sampled with
     * replacement from the observed rows on which the child's parents are all
     * present, the child is drawn {@code edgeDrawsPerConfig} times with the
     * configuration as is, and {@code edgeDrawsPerConfig} times again with the
     * parent's slot replaced by an independent draw from that parent's observed
     * marginal. The two conditional distributions are compared and the result
     * averaged over configurations.
     *
     * <p>Because the mechanism is not refit, this measures how much the
     * mechanism <em>uses</em> the parent, not whether the parent is predictively
     * necessary. A parent that is redundant with another parent still registers
     * as strong here if the network relies on it; see
     * {@link #computePartialEdgeStrength} for the complementary question.
     *
     * <p>Three measures are returned:
     * <ul>
     *   <li><b>MMD²</b> — mean over configurations of the MMD² between the two
     *       sets of draws. A continuous child is standardized by its observed SD
     *       first, so values are comparable across children.</li>
     *   <li><b>Variance difference</b> (continuous) — mean over configurations
     *       of var(Y | pa, X randomized) − var(Y | pa); also returned as a
     *       fraction of the child's observed variance. For a linear mechanism
     *       Y = aX + …, this is a²·var(X).</li>
     *   <li><b>KL divergence in bits</b> (discrete) — mean over configurations
     *       and randomized draws of KL(P(Y | pa) ‖ P(Y | pa, X randomized)).</li>
     * </ul>
     *
     * <p>Safe to call concurrently for different edges. Requires {@link #fit()}.
     *
     * @param parentName name of the parent variable (tail of the edge)
     * @param childName  name of the child variable (head of the edge)
     * @param numConfigs number of observed parent configurations to average
     *                   over; larger = more stable (suggest a few hundred)
     * @return an {@link EdgeStrengthResult}
     * @throws IllegalStateException    if {@link #fit()} has not been called
     * @throws IllegalArgumentException if the edge does not exist, either
     *                                  variable name is not found, or no
     *                                  observed row has the child's parents
     *                                  all present
     */
    public EdgeStrengthResult computeEdgeStrength(String parentName,
                                                  String childName,
                                                  int numConfigs) {
        checkFitted();
        if (numConfigs < 1)
            throw new IllegalArgumentException("numConfigs must be >= 1");
        int m = Math.max(2, params.edgeDrawsPerConfig);

        // ── Validate edge ─────────────────────────────────────────────────────
        Node parentNode = findVariable(parentName);
        Node childNode  = findVariable(childName);
        if (!dag.isParentOf(parentNode, childNode)) {
            throw new IllegalArgumentException(
                    "No edge " + parentName + " → " + childName + " in the DAG.");
        }
        boolean isDisc = (childNode instanceof DiscreteVariable);

        List<Node> variables = observedData.getVariables();
        int p = variables.size();
        Map<String, Integer> indexByName = new HashMap<>();
        for (int j = 0; j < p; j++) indexByName.put(variables.get(j).getName(), j);
        int childIdx  = indexByName.get(childName);
        int parentIdx = indexByName.get(parentName);
        boolean parentIsDisc = fittedSimulator.isDiscreteVariable(parentIdx);

        int[] parentCols = dag.getParents(childNode).stream()
                .mapToInt(q -> indexByName.get(q.getName())).toArray();

        // ── Observed rows usable as parent configurations ─────────────────────
        int n = observedData.getNumRows();
        List<Integer> usable = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            boolean ok = true;
            for (int c : parentCols) {
                if (fittedSimulator.isDiscreteVariable(c)) {
                    if (TrainedDagSimulatorGNM.safeGetInt(observedData, r, c) < 0) { ok = false; break; }
                } else {
                    if (!Double.isFinite(observedData.getDouble(r, c))) { ok = false; break; }
                }
            }
            if (ok) usable.add(r);
        }
        if (usable.isEmpty())
            throw new IllegalArgumentException(
                    "No observed row has all parents of " + childName + " present.");

        // ── Pool of independent parent values (observed marginal of X) ────────
        double[] xPoolCont = null;
        int[]    xPoolDisc = null;
        if (parentIsDisc) {
            xPoolDisc = IntStream.range(0, n)
                    .map(r -> TrainedDagSimulatorGNM.safeGetInt(observedData, r, parentIdx))
                    .filter(v -> v >= 0).toArray();
        } else {
            xPoolCont = IntStream.range(0, n)
                    .mapToDouble(r -> observedData.getDouble(r, parentIdx))
                    .filter(Double::isFinite).toArray();
        }
        if ((parentIsDisc && xPoolDisc.length == 0) || (!parentIsDisc && xPoolCont.length == 0))
            throw new IllegalArgumentException("No observed values for " + parentName + ".");

        // ── Child scale, for standardizing MMD² and ΔVar ──────────────────────
        double childVar = Double.NaN, childSd = 1.0;
        if (!isDisc) {
            childVar = columnVariance(observedData, childIdx);
            if (Double.isFinite(childVar) && childVar > 0) childSd = TMath.sqrt(childVar);
        }

        // ── Main loop over configurations ─────────────────────────────────────
        // Two RNG streams: one for the child's noise (shared by both conditions,
        // so the comparison is coupled), one for selecting configurations and
        // the randomized parent values.
        long base = params.seed ^ mixEdgeSeed(parentIdx, childIdx);
        Random selRng = new Random(base ^ 0xA5A5A5A5A5A5A5A5L);

        double[] contRow = new double[p];
        int[]    discRow = new int[p];
        double[][] drawsFull = new double[m][1];
        double[][] drawsCut  = new double[m][1];

        double sumMmd2 = 0.0, sumDVar = 0.0, sumKlBits = 0.0;
        int    countMmd = 0,  countDVar = 0, countKl = 0;

        for (int c = 0; c < numConfigs; c++) {
            int row = usable.get(selRng.nextInt(usable.size()));
            fittedSimulator.fillRowFromData(observedData, row, contRow, discRow);
            long noiseSeed = base ^ ((long) c * 0x9E3779B97F4A7C15L);

            // Condition A: configuration as observed.
            double[] pFull = null;
            if (isDisc) pFull = fittedSimulator.childProbsGiven(childIdx, contRow, discRow);
            Random rngA = new Random(noiseSeed);
            for (int i = 0; i < m; i++) {
                fittedSimulator.generateChild(childIdx, contRow, discRow, rngA);
                drawsFull[i][0] = isDisc ? discRow[childIdx] : contRow[childIdx] / childSd;
            }

            // Condition B: parent slot replaced by an independent draw each time.
            Random rngB = new Random(noiseSeed);
            double klAcc = 0.0; int klN = 0;
            for (int i = 0; i < m; i++) {
                if (parentIsDisc) discRow[parentIdx] = xPoolDisc[selRng.nextInt(xPoolDisc.length)];
                else              contRow[parentIdx] = xPoolCont[selRng.nextInt(xPoolCont.length)];
                if (isDisc) {
                    double[] pCut = fittedSimulator.childProbsGiven(childIdx, contRow, discRow);
                    double kl = klDivergenceBits(pFull, pCut);
                    if (Double.isFinite(kl)) { klAcc += kl; klN++; }
                }
                fittedSimulator.generateChild(childIdx, contRow, discRow, rngB);
                drawsCut[i][0] = isDisc ? discRow[childIdx] : contRow[childIdx] / childSd;
            }

            // ── Per-configuration measures ────────────────────────────────────
            double mmd2 = RandomFeatureMMD.compute(
                    drawsFull, drawsCut,
                    params.mmdFeatures,
                    params.mmdSeed ^ c,
                    1.0,
                    params.mmdMaxRows);
            if (Double.isFinite(mmd2)) { sumMmd2 += mmd2; countMmd++; }

            if (!isDisc) {
                double vFull = sampleVariance(drawsFull) * childSd * childSd;
                double vCut  = sampleVariance(drawsCut)  * childSd * childSd;
                if (Double.isFinite(vFull) && Double.isFinite(vCut)) {
                    sumDVar += (vCut - vFull); countDVar++;
                }
            } else if (klN > 0) {
                sumKlBits += klAcc / klN; countKl++;
            }
        }

        double mmd2 = countMmd > 0 ? sumMmd2 / countMmd : Double.NaN;
        double dVar = (!isDisc && countDVar > 0) ? sumDVar / countDVar : Double.NaN;
        double dVarFrac = (!isDisc && Double.isFinite(dVar) && Double.isFinite(childVar) && childVar > 0)
                ? dVar / childVar : Double.NaN;
        double klBits = (isDisc && countKl > 0) ? sumKlBits / countKl : Double.NaN;

        return new EdgeStrengthResult(
                parentName, childName, isDisc,
                mmd2, dVar, dVarFrac, klBits,
                numConfigs, m);
    }

    private static long mixEdgeSeed(int parentIdx, int childIdx) {
        long h = 0x5DEECE66DL;
        h = h * 31 + parentIdx;
        h = h * 31 + childIdx;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return h;
    }

    /** Sample variance of a one-column matrix. */
    private static double sampleVariance(double[][] col) {
        int n = col.length;
        if (n < 2) return Double.NaN;
        double sum = 0, sum2 = 0;
        for (double[] r : col) { sum += r[0]; sum2 += r[0] * r[0]; }
        double mean = sum / n;
        return (sum2 - n * mean * mean) / (n - 1);
    }

    /**
     * Computes the partial edge strength of X → Y: the held-out predictive
     * gain from X once Y's other parents are accounted for, on the same
     * k folds {@link #crossValidate(int)} uses.
     *
     * <p>For each fold, the cached fold model (all mechanisms trained on the
     * training rows) is the <em>full</em> model. A <em>reduced</em> model is
     * made from it by retraining only Y's mechanism, on the same training
     * rows, without X. Both predict the held-out rows.
     *
     * <p>For a continuous child the result is the difference in held-out
     * R², where R² = 1 − OOS MSE / marginal variance exactly as in the
     * Cross-Validation table:
     * <pre>  partialR2 = R²_full − R²_reduced = (MSE_reduced − MSE_full) / var(Y)</pre>
     * For a discrete child it is the difference in held-out cross-entropy,
     * in nats: {@code xent_reduced − xent_full}. Positive means X adds
     * information the other parents do not carry.
     *
     * <p>This is the complement of {@link #computeEdgeStrength}: a parent that
     * is redundant with another parent scores near zero here even if the
     * fitted mechanism leans on it, because the reduced mechanism can use the
     * other parent instead.
     *
     * <p>Requires {@link #fit()} to have been called first. Safe to call
     * concurrently for different edges; the first call at a given k builds the
     * fold models and later calls reuse them.
     *
     * @param parentName name of the parent variable X
     * @param childName  name of the child variable Y
     * @param k          number of CV folds
     * @return a {@link PartialEdgeStrengthResult}
     * @throws IllegalStateException    if {@link #fit()} has not been called
     * @throws IllegalArgumentException if the edge does not exist or either
     *                                  variable is not found
     */
    public PartialEdgeStrengthResult computePartialEdgeStrength(
            String parentName, String childName, int k) {
        checkFitted();

        // ── Validate ──────────────────────────────────────────────────────────
        Node parentNode = findVariable(parentName);
        Node childNode  = findVariable(childName);
        if (!dag.isParentOf(parentNode, childNode)) {
            throw new IllegalArgumentException(
                    "No edge " + parentName + " → " + childName + " in the DAG.");
        }
        boolean isDisc = (childNode instanceof DiscreteVariable);

        List<Node> variables = observedData.getVariables();
        Map<String, Integer> indexByName = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) {
            indexByName.put(variables.get(j).getName(), j);
        }
        int childIdx = indexByName.get(childName);

        int[] reducedParentIndices = dag.getParents(childNode).stream()
                .filter(q -> !q.getName().equals(parentName))
                .mapToInt(q -> indexByName.get(q.getName()))
                .toArray();

        FoldModels fm = getFoldModels(k);

        // Per-fold accumulators; folds run in parallel and each writes only
        // its own slot. The reduced child mechanism is a fresh object per fold,
        // and the shared fold simulators are safe for concurrent prediction.
        double[] sseFullF = new double[k], sseRedF = new double[k];
        double[] xentFullF = new double[k], xentRedF = new double[k];
        int[] nContF = new int[k], nDiscF = new int[k];

        IntStream.range(0, k).parallel().forEach(fold -> {
            TrainedDagSimulatorGNM full = fm.sims[fold];
            long foldSeed = params.seed ^ (long) fold * 0x9E3779B97F4A7C15L;
            TrainedDagSimulatorGNM reduced = full.withReducedParents(
                    childIdx, reducedParentIndices, foldSeed ^ 0xDEADBEEFL);

            DataSet testSet = fm.test[fold];
            int testN = testSet.getNumRows();

            for (int i = 0; i < testN; i++) {
                if (!isDisc) {
                    double yObs = testSet.getDouble(i, childIdx);
                    if (!Double.isFinite(yObs)) continue;
                    double yFull = full.predictNode(childIdx, testSet, i);
                    double yRed  = reduced.predictNode(childIdx, testSet, i);
                    if (!Double.isFinite(yFull) || !Double.isFinite(yRed)) continue;
                    sseFullF[fold] += (yObs - yFull) * (yObs - yFull);
                    sseRedF[fold]  += (yObs - yRed)  * (yObs - yRed);
                    nContF[fold]++;
                } else {
                    int obs = TrainedDagSimulatorGNM.safeGetInt(testSet, i, childIdx);
                    double[] pFull = full.predictNodeProbs(childIdx, testSet, i);
                    double[] pRed  = reduced.predictNodeProbs(childIdx, testSet, i);
                    if (pFull == null || pRed == null) continue;
                    if (obs < 0 || obs >= pFull.length) continue;
                    xentFullF[fold] += -TMath.log(TMath.max(pFull[obs], 1e-300));
                    xentRedF[fold]  += -TMath.log(TMath.max(pRed[obs],  1e-300));
                    nDiscF[fold]++;
                }
            }
        });

        double sseFull = 0.0, sseRed = 0.0, xentFull = 0.0, xentRed = 0.0;
        int nCont = 0, nDisc = 0;
        for (int fold = 0; fold < k; fold++) {
            sseFull += sseFullF[fold]; sseRed += sseRedF[fold]; nCont += nContF[fold];
            xentFull += xentFullF[fold]; xentRed += xentRedF[fold]; nDisc += nDiscF[fold];
        }

        if (!isDisc) {
            double mseFull = nCont > 0 ? sseFull / nCont : Double.NaN;
            double mseRed  = nCont > 0 ? sseRed  / nCont : Double.NaN;
            double baseVar = computeBaselineMse(variables)[childIdx];
            double partialR2 = (Double.isFinite(mseFull) && Double.isFinite(mseRed)
                    && Double.isFinite(baseVar) && baseVar > 0)
                    ? (mseRed - mseFull) / baseVar : Double.NaN;
            return new PartialEdgeStrengthResult(
                    parentName, childName, false,
                    partialR2, mseRed, Double.NaN, k);
        } else {
            double xf = nDisc > 0 ? xentFull / nDisc : Double.NaN;
            double xr = nDisc > 0 ? xentRed  / nDisc : Double.NaN;
            double improvement = (Double.isFinite(xf) && Double.isFinite(xr))
                    ? xr - xf : Double.NaN;
            return new PartialEdgeStrengthResult(
                    parentName, childName, true,
                    Double.NaN, Double.NaN, improvement, k);
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private Node findVariable(String name) {
        for (Node n : observedData.getVariables()) {
            if (n.getName().equals(name)) return n;
        }
        throw new IllegalArgumentException(
                "Variable not found in dataset: " + name);
    }

    /**
     * Sample variance of a continuous column.
     */
    private static double columnVariance(DataSet data, int col) {
        int n = data.getNumRows();
        double sum = 0, sum2 = 0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            double v = data.getDouble(i, col);
            if (!Double.isFinite(v)) continue;
            sum += v; sum2 += v * v; count++;
        }
        if (count < 2) return Double.NaN;
        double mean = sum / count;
        return (sum2 - count * mean * mean) / (count - 1);
    }

    /**
     * KL(p ‖ q) in bits. Clips q away from zero to avoid log(0).
     */
    private static double klDivergenceBits(double[] p, double[] q) {
        double kl  = 0.0;
        double eps = 1e-10;
        for (int k = 0; k < p.length; k++) {
            if (p[k] < eps) continue;
            kl += p[k] * (TMath.log(p[k]) - TMath.log(TMath.max(q[k], eps)));
        }
        return kl / TMath.log(2.0);   // nats → bits
    }


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
        AdequacyParams ap = new AdequacyParams();
        ap.mmdFeatures = params.mmdFeatures;
        ap.mmdSeed = params.mmdSeed;
        ap.mmdMaxRows = params.mmdMaxRows;
        return ap;
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
                sum += v;
                sum2 += v * v;
                count++;
            }
            if (count < 2) {
                baseline[j] = Double.NaN;
                continue;
            }
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
                counts[v]++;
                total++;
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
