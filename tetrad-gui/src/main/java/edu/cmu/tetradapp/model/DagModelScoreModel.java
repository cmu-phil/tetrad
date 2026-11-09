package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetrad.util.TMath;

import java.util.Objects;

/**
 * Compare-box model:
 * - Input: (DataWrapper, GraphWrapper, Parameters)
 * - Output: holds observed data and a resimulated dataset whose joint is trained/factorized by the DAG.
 * <p>
 * This is intentionally lightweight compared to Simulation/SimulationModel editors.
 */
public final class DagModelScoreModel extends DataWrapper implements SessionModel {

    // ---- inputs ----
    /**
     * The input graph (DAG) to be scored.
     */
    private final Graph inputGraph;
    /**
     * The observed input data set.
     */
    private final DataSet inputData;
    /**
     * The parameters for this model.
     */
    private final Parameters parameters;

    // ---- state/output ----
    /**
     * The sample size.
     */
    private int sampleSize;

    /**
     * Constructs a new DagModelScoreModel from the given data and graph. The sample size defaults to the number of
     * rows in the observed data.
     *
     * @param dataWrapper  the data wrapper; its first data model must be a DataSet
     * @param graphWrapper the graph wrapper supplying the DAG
     * @param parameters   the parameters
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if no graph is provided or the first data model is not a DataSet
     */
    public DagModelScoreModel(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters parameters) {
        Objects.requireNonNull(dataWrapper, "dataWrapper");
        Objects.requireNonNull(graphWrapper, "graphWrapper");
        this.parameters = Objects.requireNonNull(parameters, "parameters");

        this.inputGraph = graphWrapper.getGraph();
        if (this.inputGraph == null) {
            throw new IllegalArgumentException("No graph provided for DAG factorization compare.");
        }

        DataModel dm = dataWrapper.getDataModelList().isEmpty() ? null : dataWrapper.getDataModelList().getFirst();
        if (!(dm instanceof DataSet ds)) {
            throw new IllegalArgumentException("A DataSet is required for DAG factorization compare.");
        }
        this.inputData = ds;

        // Default sample size = observed sample size (as you requested)
        this.sampleSize = TMath.max(1, inputData.getNumRows());
    }

    /**
     * Sets the sample size and resets this wrapper's data model list to contain only the observed input data. No data
     * are actually simulated.
     *
     * @param sampleSize the sample size
     */
    public void resimulate(int sampleSize) {
        this.sampleSize = sampleSize;

        // Optionally: expose both datasets from this wrapper (handy for downstream tooling)
        // If your DataWrapper already has a setter, use it; otherwise remove this block.
        try {
            DataModelList list = new DataModelList();
            list.add(inputData);
            setDataModelList(list);
        } catch (Throwable ignored) {
            // If your DataWrapper doesn’t allow setting the list, that’s fine.
            // The editor can use the explicit accessors below.
        }
    }

    // -------------------------
    // Accessors used by editor
    // -------------------------

    /**
     * Returns the input graph.
     *
     * @return the input graph
     */
    public Graph getInputGraph() {
        return inputGraph;
    }

    /**
     * Returns the observed input data set.
     *
     * @return the observed input data set
     */
    public DataSet getInputData() {
        return inputData;
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Returns the sample size.
     *
     * @return the sample size
     */
    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * Set desired n (does not resimulate until resimulate() is called).
     *
     * @param n the desired sample size; values less than 1 are set to 1
     */
    public void setSampleSize(int n) {
        this.sampleSize = TMath.max(1, n);
    }

    // -------------------------
    // GraphSource
    // -------------------------
    /**
     * Returns the input graph.
     *
     * @return the input graph
     */
    public Graph getGraph() {
        return inputGraph;
    }
}