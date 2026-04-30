package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.NNEstimator;
import edu.cmu.tetrad.sem.NNEstimatorParams;
import edu.cmu.tetrad.sem.AdequacyReport;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetradapp.session.SessionModel;

import java.util.Objects;

/**
 * Tetrad session wrapper for {@link NNEstimator}.
 *
 * <p>Accepts a (DataWrapper, GraphWrapper) pair — no parametric model (PM/IM)
 * is required. The actual estimation logic lives in {@link NNEstimator} in the
 * {@code edu.cmu.tetrad.nn} package, which has no GUI dependencies and can be
 * used directly from the Tetrad library.
 *
 * <p>This class is intentionally thin: it extracts the graph and dataset from
 * the session wrappers, delegates all work to {@link NNEstimator}, and exposes
 * the results for the editor.
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
     * Re-trains the NN estimator on the input data and simulates a fresh
     * dataset of {@code sampleSize} rows. This is the method the editor calls
     * when the user clicks "Resimulate"; it may be slow and should be run off
     * the EDT (e.g. via SwingWorker).
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

    // ── accessors for the editor ──────────────────────────────────────────────

    /** @return the original observed dataset */
    public DataSet getInputData() {
        return inputData;
    }

    /** @return the DAG used for factorization */
    public Graph getInputGraph() {
        return inputGraph;
    }

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

    /** @return the sample size used in the most recent resimulate() call */
    public int getSampleSize() {
        return sampleSize;
    }

    /** @return the underlying library estimator (for advanced / library use) */
    public NNEstimator getEstimator() {
        return estimator;
    }

    // ── GraphSource ───────────────────────────────────────────────────────────

//    @Override
    public Graph getGraph() {
        return inputGraph;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private NNEstimatorParams buildParams() {
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = System.nanoTime();
        // Future: read mmdFeatures, mmdSeed, mmdMaxRows from this.parameters if
        // the user has exposed them in the Parameters dialog.
        return p;
    }
}
