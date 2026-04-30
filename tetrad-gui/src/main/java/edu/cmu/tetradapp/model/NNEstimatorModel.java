package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.NNEstimator;
import edu.cmu.tetrad.sem.NNEstimatorParams;
import edu.cmu.tetrad.sem.AdequacyReport;
import edu.cmu.tetrad.sem.CVReport;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetradapp.session.SessionModel;

import java.util.Objects;

/**
 * Tetrad session wrapper for {@link NNEstimator}.
 *
 * <p>Accepts a (DataWrapper, GraphWrapper) pair — no parametric model (PM/IM)
 * is required. The actual estimation logic lives in {@link NNEstimator} in the
 * {@code edu.cmu.tetrad.sem} package, which has no GUI dependencies and can be
 * used directly from the Tetrad library.
 *
 * <p>This class is intentionally thin: it extracts the graph and dataset from
 * the session wrappers, delegates all work to {@link NNEstimator}, and exposes
 * the results for the editor.
 *
 * <p>Two distinct operations are exposed:
 * <ul>
 *   <li>{@link #resimulate(int)} — fits on all data and simulates; fast enough
 *       to run on demand from the UI.</li>
 *   <li>{@link #runCrossValidate(int)} — runs k-fold CV for honest OOS metrics;
 *       slower (k full fits), should be run via SwingWorker.</li>
 * </ul>
 */
public final class NNEstimatorModel extends DataWrapper implements SessionModel {

    // ── session inputs ────────────────────────────────────────────────────────

    private final Graph inputGraph;
    private final DataSet inputData;
    private final Parameters parameters;

    // ── core estimator ────────────────────────────────────────────────────────

    /** The library-level estimator; recreated on each resimulate() call. */
    private NNEstimator estimator;

    // ── derived state ─────────────────────────────────────────────────────────

    private int sampleSize;

    // ── constructor ───────────────────────────────────────────────────────────

    public NNEstimatorModel(DataWrapper dataWrapper,
                            GraphWrapper graphWrapper,
                            Parameters parameters) {
        Objects.requireNonNull(dataWrapper, "dataWrapper");
        Objects.requireNonNull(graphWrapper, "graphWrapper");
        this.parameters = Objects.requireNonNull(parameters, "parameters");

        this.inputGraph = graphWrapper.getGraph();
        if (this.inputGraph == null) {
            throw new IllegalArgumentException("No graph provided for NN estimator.");
        }

        DataModel dm = dataWrapper.getDataModelList().isEmpty()
                ? null
                : dataWrapper.getDataModelList().getFirst();
        if (!(dm instanceof DataSet ds)) {
            throw new IllegalArgumentException("A DataSet is required for NN estimator.");
        }
        this.inputData = ds;

        this.sampleSize = TMath.max(1, inputData.getNumRows());
        resimulate(this.sampleSize);
    }

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Re-trains the NN estimator on the full input data and simulates a fresh
     * dataset of {@code sampleSize} rows. Called when the user clicks
     * "Resimulate". Should be run off the EDT via SwingWorker.
     *
     * @param sampleSize number of rows to simulate; clipped to &ge; 1
     */
    public void resimulate(int sampleSize) {
        this.sampleSize = TMath.max(1, sampleSize);

        NNEstimatorParams params = buildParams();
        estimator = new NNEstimator(inputData, inputGraph, params);
        DataSet simulated = estimator.fitAndSimulate(this.sampleSize);

        // Make both datasets available to downstream session nodes.
        try {
            inputData.setName("Observed");
            simulated.setName("Simulated");
            DataModelList list = new DataModelList();
            list.add(inputData);
            list.add(simulated);
            list.setSelectedModel(simulated);
            setDataModelList(list);
        } catch (Throwable ignored) {
            // DataWrapper may not support setDataModelList in all configurations.
        }
    }

    /**
     * Runs k-fold cross-validation and stores the result, which can then be
     * retrieved via {@link #getCvReport()}. This does not retrain the full-data
     * model — call {@link #resimulate} for that.
     *
     * <p>This may be slow (k full fits). Should be run off the EDT via SwingWorker.
     *
     * @param k number of folds; must be &ge; 2 and &le; number of data rows
     * @throws IllegalStateException if resimulate() has not been called yet
     */
    public void runCrossValidate(int k) {
        if (estimator == null)
            throw new IllegalStateException("resimulate() must be called before runCrossValidate().");
        estimator.crossValidate(k);
    }

    // ── accessors for the editor ──────────────────────────────────────────────

    /** @return the original observed dataset */
    public DataSet getInputData() { return inputData; }

    /** @return the DAG used for factorization */
    public Graph getInputGraph() { return inputGraph; }

    /** @return the most recently simulated dataset, or null before first resimulate() */
    public DataSet getSimulatedData() {
        return estimator == null ? null : estimator.getSimulatedData();
    }

    /**
     * @return the adequacy report from the most recent resimulate() call,
     *         or null before first resimulate()
     */
    public AdequacyReport getAdequacyReport() {
        return estimator == null ? null : estimator.getAdequacyReport();
    }

    /**
     * @return the CV report from the most recent runCrossValidate() call,
     *         or null if cross-validation has not yet been run
     */
    public CVReport getCvReport() {
        return estimator == null ? null : estimator.getCvReport();
    }

    /** @return the sample size used in the most recent resimulate() call */
    public int getSampleSize() { return sampleSize; }

    /** @return the underlying library estimator (for advanced / library use) */
    public NNEstimator getEstimator() { return estimator; }

    // ── GraphSource ───────────────────────────────────────────────────────────

    public Graph getGraph() { return inputGraph; }

    // ── private helpers ───────────────────────────────────────────────────────

    private NNEstimatorParams buildParams() {
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = System.nanoTime();
        // Future: read mmdFeatures, mmdSeed, mmdMaxRows from this.parameters
        // once they are exposed in the Parameters dialog.
        return p;
    }
}
