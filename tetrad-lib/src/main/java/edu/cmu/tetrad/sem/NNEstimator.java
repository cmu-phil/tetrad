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
        if (k < 2) throw new IllegalArgumentException("k must be >= 2");
        if (k > n) throw new IllegalArgumentException("k must be <= number of rows (" + n + ")");

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

        int foldSize = n / k;

        // ── Parallel fold loop ────────────────────────────────────────────────────
        // Each fold trains its own simulator on its own training subset — no shared
        // mutable state between folds. Accumulators use DoubleAdder/AtomicInteger
        // for lock-free thread-safe updates.

        IntStream.range(0, k).parallel().forEach(fold -> {
            int testStart = fold * foldSize;
            int testEnd   = (fold == k - 1) ? n : testStart + foldSize;
            int testN     = testEnd - testStart;
            int trainN    = n - testN;

            // Partition row indices.
            int[] trainRows = new int[trainN];
            int[] testRows  = new int[testN];
            int ti = 0, vi = 0;
            for (int r = 0; r < n; r++) {
                if (r >= testStart && r < testEnd) testRows[vi++] = r;
                else                               trainRows[ti++] = r;
            }

            DataSet trainSet = rowSubset(observedData, trainRows);
            DataSet testSet  = rowSubset(observedData, testRows);

            // Train a fresh simulator on this fold's training data.
            long foldSeed = params.seed ^ (long) fold * 0x9E3779B97F4A7C15L;
            TrainedDagSimulatorGNM sim = buildSimulator(trainSet, foldSeed);
            sim.fit();

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

    /**
     * @return the observed (input) dataset
     */
    public DataSet getObservedData() {
        return observedData;
    }

    /**
     * @return the DAG used for factorization
     */
    public Graph getDag() {
        return dag;
    }

    /**
     * @return the parameters used by this estimator
     */
    public NNEstimatorParams getParams() {
        return params;
    }

    /**
     * @return the most recently simulated dataset, or {@code null} if
     * {@link #simulate} has not yet been called
     */
    public DataSet getSimulatedData() {
        return simulatedData;
    }

    /**
     * @return the adequacy report from the most recent {@link #simulate} call,
     * or {@code null} if {@link #simulate} has not yet been called
     */
    public AdequacyReport getAdequacyReport() {
        return adequacyReport;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * @return the CV report from the most recent {@link #crossValidate} call,
     * or {@code null} if {@link #crossValidate} has not yet been called
     */
    public CVReport getCvReport() {
        return cvReport;
    }

    /**
     * @return {@code true} if {@link #fit()} has been called successfully
     */
    public boolean isFitted() {
        return fittedSimulator != null;
    }

    // ── Edge strength ─────────────────────────────────────────────────────────

    /**
     * Computes the strength of a single edge (parentName → childName) by
     * retraining <em>only the child's mechanism</em> without that parent,
     * holding all other node mechanisms fixed, and comparing the resulting
     * marginal distribution of the child to the original.
     *
     * <p>This is the correct isolation: only the affected structural equation
     * is changed, so the comparison cleanly reflects the contribution of that
     * one edge rather than cascading changes through the graph.
     *
     * <p>Three complementary measures are returned:
     * <ul>
     *   <li><b>MMD²</b> — nonparametric, captures any distributional change.</li>
     *   <li><b>Variance difference</b> (continuous) — var(Y_removed) − var(Y_original).
     *       Analogous to DoWhy's default arrow_strength metric.</li>
     *   <li><b>KL divergence in bits</b> (discrete) — KL(P_removed ‖ P_original).
     *       Analogous to DoWhy's categorical arrow_strength metric.</li>
     * </ul>
     *
     * <p>Requires {@link #fit()} to have been called first.
     *
     * @param parentName name of the parent variable (tail of the edge)
     * @param childName  name of the child variable (head of the edge)
     * @param simulatedN number of rows to simulate from each model for
     *                   comparison; larger = more stable (suggest &ge; 2000)
     * @return an {@link EdgeStrengthResult} with all three measures
     * @throws IllegalStateException    if {@link #fit()} has not been called
     * @throws IllegalArgumentException if the edge does not exist, or either
     *                                  variable name is not found
     */
    public EdgeStrengthResult computeEdgeStrength(String parentName,
                                                  String childName,
                                                  int simulatedN) {
//        if (true) {
//            return new EdgeStrengthResult(parentName, childName, false, 0.0, 0.0, 0.0, 0);
//        }

        checkFitted();
        if (simulatedN < 1)
            throw new IllegalArgumentException("simulatedN must be >= 1");

        // ── Validate edge ─────────────────────────────────────────────────────

        Node parentNode = findVariable(parentName);
        Node childNode  = findVariable(childName);

        if (!dag.isParentOf(parentNode, childNode)) {
            throw new IllegalArgumentException(
                    "No edge " + parentName + " → " + childName + " in the DAG.");
        }

        boolean isDisc = (childNode instanceof DiscreteVariable);

        // ── Build reduced parent index array for the child ────────────────────

        List<Node> allParents = dag.getParents(childNode);
        List<Integer> reducedParentIdxList = new ArrayList<>();

        Map<String, Integer> indexByName = new HashMap<>();
        for (int j = 0; j < observedData.getVariables().size(); j++) {
            indexByName.put(observedData.getVariables().get(j).getName(), j);
        }

        for (Node p : allParents) {
            if (!p.getName().equals(parentName)) {
                Integer idx = indexByName.get(p.getName());
                if (idx != null) reducedParentIdxList.add(idx);
            }
        }

        int[] reducedParentIndices = reducedParentIdxList.stream()
                .mapToInt(Integer::intValue).toArray();

        int childIndex = indexByName.get(childName);

        // ── Build hybrid simulator: only child mechanism retrained ────────────

        TrainedDagSimulatorGNM hybridSim = fittedSimulator.withReducedParents(
                childIndex, reducedParentIndices, params.seed ^ 0xDEADBEEFL);

        // ── Simulate from both models ─────────────────────────────────────────

        DataSet origSim    = fittedSimulator.simulate(simulatedN).toDataSet();
        DataSet reducedSim = hybridSim.simulate(simulatedN).toDataSet();

        // ── Extract child column marginals ────────────────────────────────────

        int origCol    = origSim.getColumnIndex(origSim.getVariable(childName));
        int reducedCol = reducedSim.getColumnIndex(reducedSim.getVariable(childName));

        double[][] origVec    = extractColumn(origSim,    origCol,    isDisc);
        double[][] reducedVec = extractColumn(reducedSim, reducedCol, isDisc);

        // ── MMD² on marginal ──────────────────────────────────────────────────

        double mmd2 = RandomFeatureMMD.compute(
                origVec, reducedVec,
                params.mmdFeatures,
                params.mmdSeed,
                1.0,
                params.mmdMaxRows);

        // ── Variance difference (continuous) ──────────────────────────────────

        double varianceDiff = Double.NaN;
        if (!isDisc) {
            double varOrig    = columnVariance(origSim,    origCol);
            double varReduced = columnVariance(reducedSim, reducedCol);
            varianceDiff = varReduced - varOrig;
        }

        // ── KL divergence in bits (discrete) ─────────────────────────────────

        double klDivBits = Double.NaN;
        if (isDisc) {
            int L = ((DiscreteVariable) childNode).getNumCategories();
            double[] pOrig    = empiricalProbs(origSim,    origCol,    L);
            double[] pReduced = empiricalProbs(reducedSim, reducedCol, L);
            klDivBits = klDivergenceBits(pReduced, pOrig);
        }

        return new EdgeStrengthResult(
                parentName, childName, isDisc,
                mmd2, varianceDiff, klDivBits,
                simulatedN);
    }

    /**
     * Computes the partial edge strength of X → Y by residualizing Y on its
     * other parents and measuring how much X explains in the residual via
     * k-fold cross-validation.
     *
     * <p>This is the nonparametric analog of partial R² in regression. Unlike
     * {@link #computeEdgeStrength}, which measures the marginal contribution
     * of X averaged over the joint distribution of all parents, this method
     * controls for the other parents first — making it less sensitive to
     * inter-parent correlations and giving a cleaner signal about whether X
     * causally contributes to Y.
     *
     * <p>For continuous children:
     * <ol>
     *   <li>Compute residuals R = Y − Ŷ on the observed data, where Ŷ is
     *       the zero-noise point prediction of the fitted mechanism using all
     *       parents (i.e. the conditional mean estimate).</li>
     *   <li>Pass R and the observed values of X to
     *       {@link TrainedDagSimulatorGNM#fitResidualRegressionOosR2}, which
     *       fits a small MLP of R ~ X via k-fold CV and returns OOS R².</li>
     * </ol>
     *
     * <p>For discrete children, a CV cross-entropy improvement is computed
     * by comparing the full model (all parents) against the reduced model
     * (all parents except X) on held-out folds.
     *
     * <p>Requires {@link #fit()} to have been called first.
     *
     * @param parentName name of the parent variable X
     * @param childName  name of the child variable Y
     * @param k          number of CV folds for the residual regression
     * @return a {@link PartialEdgeStrengthResult} with partial R² (continuous)
     *         or cross-entropy improvement (discrete)
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

        int n = observedData.getNumRows();
        if (k < 2) throw new IllegalArgumentException("k must be >= 2");
        if (k > n) throw new IllegalArgumentException(
                "k must be <= number of rows (" + n + ")");

        // ── Build column index map ────────────────────────────────────────────

        List<Node> variables = observedData.getVariables();
        Map<String, Integer> indexByName = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) {
            indexByName.put(variables.get(j).getName(), j);
        }
        int childIdx  = indexByName.get(childName);
        int parentIdx = indexByName.get(parentName);

        // ── Continuous child ──────────────────────────────────────────────────

        if (!isDisc) {

            // Build reduced parent index array: all parents of Y except X.
            List<Node> allParents = dag.getParents(childNode);
            int[] reducedParentIndices = allParents.stream()
                    .filter(p -> !p.getName().equals(parentName))
                    .mapToInt(p -> indexByName.get(p.getName()))
                    .toArray();

            // Build reduced simulator: retrain only Y's mechanism without X,
            // all other mechanisms unchanged.
            TrainedDagSimulatorGNM reducedSim = fittedSimulator.withReducedParents(
                    childIdx, reducedParentIndices, params.seed ^ 0xDEADBEEFL);

            // Compute residuals R = Y - Ŷ_reduced on all observed rows.
            // Ŷ_reduced is the prediction from the reduced mechanism (without X),
            // so R still contains X's signal — it has not been absorbed yet.
            double[] xVals = new double[n];
            double[] rVals = new double[n];

            double residSum = 0, residSum2 = 0;
            int residCount = 0;

            for (int i = 0; i < n; i++) {
                double yObs = observedData.getDouble(i, childIdx);
                double yHat = reducedSim.predictNode(childIdx, observedData, i);
                double xVal = observedData.getDouble(i, parentIdx);

                xVals[i] = xVal;

                if (Double.isFinite(yObs) && Double.isFinite(yHat)) {
                    double r = yObs - yHat;
                    rVals[i] = r;
                    residSum  += r;
                    residSum2 += r * r;
                    residCount++;
                } else {
                    rVals[i] = Double.NaN;
                }
            }

            // Residual variance — baseline for partial R².
            double residVar = Double.NaN;
            if (residCount > 1) {
                double residMean = residSum / residCount;
                residVar = (residSum2 - residCount * residMean * residMean)
                        / (residCount - 1);
            }

            // OOS R² of residual regression R ~ X via TrainedDagSimulatorGNM.
            double partialR2 = fittedSimulator.fitResidualRegressionOosR2(
                    xVals, rVals, k, params.seed ^ 0xDEADBEEFL);

            return new PartialEdgeStrengthResult(
                    parentName, childName, false,
                    partialR2, residVar, Double.NaN, k);

            // ── Discrete child ────────────────────────────────────────────────────

        } else {

            // Build reduced parent index array: all parents of Y except X.
            List<Node> allParents = dag.getParents(childNode);
            int[] reducedParentIndices = allParents.stream()
                    .filter(p -> !p.getName().equals(parentName))
                    .mapToInt(p -> indexByName.get(p.getName()))
                    .toArray();

            int L = ((DiscreteVariable) childNode).getNumCategories();
            int foldSize = n / k;

            double totalXentFull    = 0.0;
            double totalXentReduced = 0.0;
            int    totalN           = 0;

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

                // Full model: all parents, trained on this fold's training data.
                long foldSeed = params.seed ^ (long) fold * 0x9E3779B97F4A7C15L;
                TrainedDagSimulatorGNM fullSim = buildSimulator(trainSet, foldSeed);
                fullSim.fit();

                // Reduced model: all parents except X, retrain child only.
                TrainedDagSimulatorGNM reducedSim = fullSim.withReducedParents(
                        childIdx, reducedParentIndices, foldSeed ^ 0xDEADBEEFL);

                // Evaluate cross-entropy of both models on held-out rows.
                for (int ti2 = 0; ti2 < testN; ti2++) {
                    int obs = TrainedDagSimulatorGNM.safeGetInt(
                            testSet, ti2, childIdx);
                    if (obs < 0 || obs >= L) continue;

                    double[] fullProbs    = fullSim.predictNodeProbs(
                            childIdx, testSet, ti2);
                    double[] reducedProbs = reducedSim.predictNodeProbs(
                            childIdx, testSet, ti2);

                    if (fullProbs == null || reducedProbs == null) continue;

                    totalXentFull    += -TMath.log(
                            TMath.max(fullProbs[obs],    1e-300));
                    totalXentReduced += -TMath.log(
                            TMath.max(reducedProbs[obs], 1e-300));
                    totalN++;
                }
            }

            // Positive improvement = full model (with X) beats reduced model.
            double xentFull    = (totalN > 0)
                    ? totalXentFull    / totalN : Double.NaN;
            double xentReduced = (totalN > 0)
                    ? totalXentReduced / totalN : Double.NaN;
            double improvement = (Double.isFinite(xentFull)
                    && Double.isFinite(xentReduced))
                    ? xentReduced - xentFull
                    : Double.NaN;

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
     * Extracts a single column as an (n x 1) matrix for MMD² input.
     */
    private static double[][] extractColumn(DataSet data, int col, boolean isDisc) {
        int n = data.getNumRows();
        double[][] out = new double[n][1];
        for (int i = 0; i < n; i++) {
            out[i][0] = isDisc
                    ? TrainedDagSimulatorGNM.safeGetInt(data, i, col)
                    : data.getDouble(i, col);
        }
        return out;
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
     * Empirical marginal class probabilities for a discrete column.
     */
    private static double[] empiricalProbs(DataSet data, int col, int L) {
        double[] counts = new double[L];
        int total = 0;
        for (int i = 0; i < data.getNumRows(); i++) {
            int v = TrainedDagSimulatorGNM.safeGetInt(data, i, col);
            if (v < 0 || v >= L) continue;
            counts[v]++; total++;
        }
        double[] p = new double[L];
        if (total > 0)
            for (int k = 0; k < L; k++) p[k] = counts[k] / total;
        else
            for (int k = 0; k < L; k++) p[k] = 1.0 / L;
        return p;
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
