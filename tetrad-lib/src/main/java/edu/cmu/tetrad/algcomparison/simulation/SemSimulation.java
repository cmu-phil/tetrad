package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * SEM simulation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemSimulation implements Simulation {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph.
     */
    private final RandomGraph randomGraph;

    /**
     * The SEM PM.
     */
    private SemPm pm;

    /**
     * The SEM IM.
     */
    private SemIm im;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The graphs.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * The SEM IMs.
     */
    private List<SemIm> ims = new ArrayList<>();

    /**
     * The seed.
     */
    private long seed = -1L;

    /**
     * <p>Constructor for SemSimulation.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     */
    public SemSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    /**
     * <p>Constructor for SemSimulation.</p>
     *
     * @param pm a {@link edu.cmu.tetrad.sem.SemPm} object
     */
    public SemSimulation(SemPm pm) {
        SemGraph graph = pm.getGraph();
        graph.setShowErrorTerms(false);
        this.randomGraph = new SingleGraph(graph);
        this.pm = pm;
    }

    /**
     * <p>Constructor for SemSimulation.</p>
     *
     * @param im a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemSimulation(SemIm im) {
        SemGraph graph = im.getSemPm().getGraph();
        graph.setShowErrorTerms(false);
        Graph graph2 = new EdgeListGraph(graph);
        this.randomGraph = new SingleGraph(graph2);
        this.im = new SemIm(im);
        this.pm = new SemPm(im.getSemPm());
        this.ims = new ArrayList<>();
        this.ims.add(im);
    }

    /**
     * Creates data sets for simulation based on the given parameters.
     *
     * @param parameters The parameters to use in the simulation.
     * @param newModel   If true, a new model is created. If false, the model is reused.
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();
        this.ims = new ArrayList<>();

        int numRuns = parameters.getInt(Params.NUM_RUNS);

        Graph graph = this.randomGraph.createGraph(parameters);

        for (int i = 0; i < numRuns; i++) {
            boolean differentGraphs = parameters.getBoolean(Params.DIFFERENT_GRAPHS);

            if (differentGraphs && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            DataSet dataSet = simulate(graph, parameters);

            if (parameters.getBoolean(Params.STANDARDIZE)) {
                dataSet = DataTransforms.standardizeData(dataSet);
            }

            double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE);

            if (variance > 0) {
                for (int k = 0; k < dataSet.getNumRows(); k++) {
                    for (int j = 0; j < dataSet.getNumColumns(); j++) {
                        double d = dataSet.getDouble(k, j);
                        double norm = RandomUtil.getInstance().nextNormal(0, FastMath.sqrt(variance));
                        dataSet.setDouble(k, j, d + norm);
                    }
                }
            }

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataTransforms.shuffleColumns(dataSet);
            }

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            dataSet.setName("" + (i + 1));
            this.graphs.add(graph);
            this.dataSets.add(dataSet);
        }
    }

    /**
     * Retrieves the data model at the specified index from the list of data sets.
     *
     * @param index The index of the desired simulated data set.
     * @return The data model at the specified index from the list of data sets.
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * Retrieves the true graph at the specified index.
     *
     * @param index The index of the desired true graph.
     * @return The true graph at the specified index.
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * Returns the description of the simulation.
     *
     * @return Returns a one-line description of the simulation, to be printed at the beginning of the report.
     */
    @Override
    public String getDescription() {
        return "Linear, Gaussian SEM simulation using " + this.randomGraph.getDescription();
    }

    /**
     * Retrieves the parameters used by this method.
     *
     * @return A list of String names of parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        parameters.addAll(SemIm.getParameterNames());
        parameters.add(Params.MEASUREMENT_VARIANCE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.STANDARDIZE);
        parameters.add(Params.SIMULATION_ERROR_TYPE);
        parameters.add(Params.SIMULATION_PARAM1);
        parameters.add(Params.SIMULATION_PARAM2);
        parameters.add(Params.SEED);

        return parameters;
    }

    /**
     * Returns the number of data models.
     *
     * @return The number of data sets to simulate.
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * Returns the data type of the data set.
     *
     * @return The type of the data set (continuous, discrete, mixed, graph, covariance or all).
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        // Need this in case the SEM IM is given externally.
        im.setParams(parameters);

        this.ims.add(im);
        return im.simulateData(parameters.getInt(Params.SAMPLE_SIZE), saveLatentVars);
    }

    /**
     * Retrieves the list of SemIm objects.
     *
     * @return The list of SemIm objects.
     */
    public List<SemIm> getSemIms() {
        return ims;
    }
}
