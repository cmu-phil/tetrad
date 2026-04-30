package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.io.Serializable;
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
 * }</pre>
 */
public final class NNEstimator implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

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
        this.dag = Objects.requireNonNull(dag, "dag");
        this.params = Objects.requireNonNull(params, "params");
    }

    // ── public API ───────────────────────────────────────────────────────────

    /**
     * Trains one neural network per node (given its parents) on the observed data.
     * Must be called before {@link #simulate}.
     *
     * @throws IllegalStateException if training fails for any node
     */
    public void fit() {
        TrainedDagSimulatorGNM.Params gnmParams = new TrainedDagSimulatorGNM.Params();
        gnmParams.seed = params.seed;

        fittedSimulator = new TrainedDagSimulatorGNM(observedData, dag, gnmParams);
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

    // ── accessors ────────────────────────────────────────────────────────────

    /** @return the observed (input) dataset */
    public DataSet getObservedData() {
        return observedData;
    }

    /** @return the DAG used for factorization */
    public Graph getDag() {
        return dag;
    }

    /** @return the parameters used by this estimator */
    public NNEstimatorParams getParams() {
        return params;
    }

    /**
     * @return the most recently simulated dataset, or {@code null} if
     *         {@link #simulate} has not yet been called
     */
    public DataSet getSimulatedData() {
        return simulatedData;
    }

    /**
     * @return the adequacy report from the most recent {@link #simulate} call,
     *         or {@code null} if {@link #simulate} has not yet been called
     */
    public AdequacyReport getAdequacyReport() {
        return adequacyReport;
    }

    /**
     * @return {@code true} if {@link #fit()} has been called successfully
     */
    public boolean isFitted() {
        return fittedSimulator != null;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private void checkFitted() {
        if (fittedSimulator == null) {
            throw new IllegalStateException("fit() must be called before simulate()");
        }
    }

    private AdequacyParams buildAdequacyParams() {
        AdequacyParams ap = new AdequacyParams();
        ap.mmdFeatures = params.mmdFeatures;
        ap.mmdSeed = params.mmdSeed;
        ap.mmdMaxRows = params.mmdMaxRows;
        return ap;
    }
}
