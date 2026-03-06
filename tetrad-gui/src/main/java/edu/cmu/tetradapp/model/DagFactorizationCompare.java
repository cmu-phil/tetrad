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
    private final Graph inputGraph;
    private final DataSet inputData;
    private final Parameters parameters;

    // ---- state/output ----
    private int sampleSize;
    private DataSet simulatedData;

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

    public Graph getInputGraph() {
        return inputGraph;
    }

    public DataSet getInputData() {
        return inputData;
    }

    public DataSet getSimulatedData() {
        return simulatedData;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * Set desired n (does not resimulate until resimulate() is called).
     */
    public void setSampleSize(int n) {
        this.sampleSize = TMath.max(1, n);
    }

    // -------------------------
    // GraphSource
    // -------------------------
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