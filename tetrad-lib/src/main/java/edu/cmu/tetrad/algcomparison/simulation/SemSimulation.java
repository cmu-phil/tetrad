package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

/**
 * @author josephramsey
 */
public class SemSimulation implements Simulation {

    private static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private SemPm pm;
    private SemIm im;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private List<SemIm> ims = new ArrayList<>();
    private long seed = -1L;

    public SemSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    public SemSimulation(SemPm pm) {
        SemGraph graph = pm.getGraph();
        graph.setShowErrorTerms(false);
        this.randomGraph = new SingleGraph(graph);
        this.pm = pm;
    }

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

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        Graph graph = this.randomGraph.createGraph(parameters);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();
        this.ims = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            this.graphs.add(graph);

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
            this.dataSets.add(dataSet);
        }
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    @Override
    public String getDescription() {
        return "Linear, Gaussian SEM simulation using " + this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

//        if (this.im == null) {
        parameters.addAll(SemIm.getParameterNames());
//        }

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

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);

        SemPm pm = this.pm;
        SemIm im = this.im;

        if (pm == null) {
            pm = new SemPm(graph);
            this.pm = pm;
        }

        // If an im is already available, then we should use that im without creating new
        // random parameter values.-JR 20231005
        long seed = parameters.getLong(Params.SEED);

        if (im != null) {

            // If the seed is set and has not changed, then we should call RandomUtil
            // methods the same number of times as we would have if we had created a new im.
            if (seed != -1 && seed == this.seed) {
                for (int i = 0; i < this.im.getNumRandomCalls(); i++) {
                    RandomUtil.getInstance().nextNormal(0, 1);
                }
            }

            this.im = im;
        } else {

            // Otherwise, we should create a new im with new random parameter values.
            // -JR 20231005
            im = new SemIm(pm, parameters);
            this.im = im;
        }

        this.seed = seed;

        // Need this in case the SEM IM is given externally.
        this.im.setParams(parameters);

        this.ims.add(im);
        return this.im.simulateData(parameters.getInt(Params.SAMPLE_SIZE), saveLatentVars);
    }

    public List<SemIm> getSemIms() {
        return ims;
    }
}
