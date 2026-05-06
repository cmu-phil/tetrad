package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 *
 * <p>Persisted state across session save/reload:
 * <ul>
 *   <li>{@link #persistedCvReport} — CV report from the most recent
 *       {@link #runCrossValidate(int)} call.</li>
 *   <li>{@link #persistedEdgeStrengthResults} — accumulated edge-strength rows
 *       from all {@link #addEdgeStrengthResult} calls, so the Edge Strength tab
 *       is repopulated on editor reopen without re-running the computation.</li>
 * </ul>
 */
public final class NNEstimatorModel extends DataWrapper implements SessionModel {

    @Serial
    private static final long serialVersionUID = 24L;

    // ── session inputs ──────────────────────────────────────────────────────
    private final Graph inputGraph;
    private final DataSet inputData;
    private final Parameters parameters;

    // ── core estimator (transient — refitted on demand) ─────────────────────
    private transient NNEstimator estimator;

    // ── persisted state ─────────────────────────────────────────────────────
    private int sampleSize;

    /**
     * The CV report is persisted separately from the estimator so it survives
     * session save/reload without needing to rerun cross-validation.
     */
    private CVReport persistedCvReport;

    private List<EdgeStrengthPair> persistedEdgeStrengthResults = new ArrayList<>();

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
            throw new IllegalStateException(
                    "resimulate() must be called before runCrossValidate().");
        estimator.crossValidate(k);
        persistedCvReport = estimator.getCvReport();
    }

    // ── edge-strength persistence ─────────────────────────────────────────────

    /**
     * Appends one completed edge-strength result to the persisted list.
     * Called from the panel's SwingWorker {@code process()} as each parent
     * finishes, so partial runs are saved even if the editor is closed early.
     *
     * @param edge    marginal edge-strength result
     * @param partial partial (residual) edge-strength result; may be null
     */
    public void addEdgeStrengthResult(EdgeStrengthResult edge,
                                      PartialEdgeStrengthResult partial) {
        persistedEdgeStrengthResults.add(new EdgeStrengthPair(edge, partial));
    }

    /**
     * Removes all persisted edge-strength rows whose child matches
     * {@code childName}. Call this before re-computing a child's parents so
     * stale rows are not duplicated on reload.
     *
     * @param childName name of the child node being removeEdgeStrengthResultsForChildrecomputed
     */
    public void removeEdgeStrengthResultsForChild(String childName) {
        persistedEdgeStrengthResults
                .removeIf(p -> p.edge().childName.equals(childName));
    }

    public void clearEdgeStrengthResults() {
        persistedEdgeStrengthResults.clear();
    }

    /**
     * Returns an unmodifiable view of all persisted edge-strength results,
     * ordered oldest-first (the table model reverses this to newest-first).
     *
     * @return live unmodifiable list; never null
     */
    public List<EdgeStrengthPair> getEdgeStrengthResults() {
        return Collections.unmodifiableList(persistedEdgeStrengthResults);
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
     * Returns the CV report from the most recent {@link #runCrossValidate}
     * call, preferring the live estimator copy and falling back to the
     * persisted copy after a session reload.
     *
     * @return CV report, or null if cross-validation has not yet been run
     */
    public CVReport getCvReport() {
        if (estimator != null && estimator.getCvReport() != null) {
            return estimator.getCvReport();
        }
        return persistedCvReport;
    }

    public PartialEdgeStrengthResult computePartialEdgeStrength(
            String parentName, String childName, int k) {
        if (estimator == null)
            throw new IllegalStateException(
                    "resimulate() must be called before computePartialEdgeStrength().");
        return estimator.computePartialEdgeStrength(parentName, childName, k);
    }

    /** @return the sample size used in the most recent resimulate() call */
    public int getSampleSize() { return sampleSize; }

    /** @return the underlying library estimator (for advanced / library use) */
    public NNEstimator getEstimator() { return estimator; }

    // ── GraphSource ───────────────────────────────────────────────────────────

    public Graph getGraph() { return inputGraph; }

    // ── serialization ─────────────────────────────────────────────────────────

    /**
     * Ensures {@link #persistedEdgeStrengthResults} is never null when
     * deserializing a session file that was saved before this field existed
     * (i.e. with {@code serialVersionUID = 23L}).
     */
    @Serial
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (persistedEdgeStrengthResults == null) {
            persistedEdgeStrengthResults = new ArrayList<>();
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private NNEstimatorParams buildParams() {
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = System.nanoTime();
        return p;
    }

    // ── nested types ──────────────────────────────────────────────────────────

    /**
     * Immutable pair of marginal and partial edge-strength results for one
     * parent→child edge. Stored in {@link #persistedEdgeStrengthResults} and
     * used by the panel to repopulate the Edge Strength table on reopen.
     *
     * <p>Must be {@link Serializable} because instances live inside the
     * persisted {@link NNEstimatorModel}.
     */
    public record EdgeStrengthPair(EdgeStrengthResult edge,
                                   PartialEdgeStrengthResult partial)
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}