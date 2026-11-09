package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.AdequacyParams;
import edu.cmu.tetrad.sem.AdequacyReport;
import edu.cmu.tetrad.sem.TrainedDagAdequacy;
import edu.cmu.tetrad.sem.TrainedDagSimulatorGNM;
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
public final class DagFactorizationCompare extends DataWrapper implements SessionModel {

    // ---- inputs ----
    /**
     * The input graph (DAG) used to factorize the joint distribution.
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
     * The sample size used for resimulation.
     */
    private int sampleSize;
    /**
     * The data set resimulated from the DAG-trained model.
     */
    private DataSet simulatedData;

    /**
     * Constructs a new DagFactorizationCompare from the given data and graph, and immediately resimulates a data set
     * with the same sample size as the observed data.
     *
     * @param dataWrapper  the data wrapper; its first data model must be a DataSet
     * @param graphWrapper the graph wrapper supplying the DAG
     * @param parameters   the parameters
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if no graph is provided or the first data model is not a DataSet
     */
    public DagFactorizationCompare(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters parameters) {
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

        resimulate(this.sampleSize);
    }

    /**
     * Trains a simulator on the observed data using the input DAG and simulates a new data set of the given sample
     * size. The observed and simulated data sets are then set as this wrapper's data model list, with the simulated
     * data selected.
     *
     * @param sampleSize the number of rows to simulate
     */
    public void resimulate(int sampleSize) {
        this.sampleSize = sampleSize;
        this.simulatedData = simulateWithGNM(this.inputData, this.inputGraph, this.sampleSize);

        // Optionally: expose both datasets from this wrapper (handy for downstream tooling)
        // If your DataWrapper already has a setter, use it; otherwise remove this block.
        try {
            simulatedData.setName("Simulated");
            inputData.setName("Observed");
            DataModelList list = new DataModelList();
            list.add(inputData);
            list.add(simulatedData);
             list.setSelectedModel(simulatedData);
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
     * Returns the most recently simulated data set.
     *
     * @return the simulated data set
     */
    public DataSet getSimulatedData() {
        return simulatedData;
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
     * Returns the sample size used for resimulation.
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

    /**
     * Train + simulate using the same mechanism as the Simulation editor’s TrainedDagSimulatorGNM path.
     * <p>
     * You should replace this stub with your actual TrainedDagSimulatorGNM calls.
     */
    private DataSet simulateWithGNM(DataSet observed, Graph dag, int sampleSize) {
        TrainedDagSimulatorGNM.Params params = new TrainedDagSimulatorGNM.Params();
        params.seed = System.nanoTime();
        TrainedDagSimulatorGNM sim = new TrainedDagSimulatorGNM(observed, dag, params);
        sim.fit();
//        edu.cmu.tetrad.sem.TrainedDagSimulatorGNM.SimResult result = sim.simulate(sampleSize);
//        return result.toDataSet();

        TrainedDagSimulatorGNM.SimResult simData = sim.simulate(sampleSize);

        AdequacyReport report =
                TrainedDagAdequacy.mmd2(
                        observed,
                        simData.toDataSet(),
                        sim,
                        new AdequacyParams());

        System.out.println(report.toText());

        return simData.toDataSet();
    }
//
//    @Override
//    public DataModelList getDataModelList() {
//        DataModelList list = new DataModelList();
//        if (simulatedData != null) list.add(simulatedData);
//        else list.add(inputData);
//        return list;
//    }
}