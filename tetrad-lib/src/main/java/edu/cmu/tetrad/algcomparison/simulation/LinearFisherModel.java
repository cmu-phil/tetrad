package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesData;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Linear Fisher Model.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LinearFisherModel implements Simulation, TakesData {

    private static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private final List<DataModel> shocks;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();

    /**
     * <p>Constructor for LinearFisherModel.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     */
    public LinearFisherModel(RandomGraph graph) {
        this.randomGraph = graph;
        this.shocks = null;
    }

    /**
     * <p>Constructor for LinearFisherModel.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     * @param shocks a {@link java.util.List} object
     */
    public LinearFisherModel(RandomGraph graph, List<DataModel> shocks) {
        this.randomGraph = graph;
        this.shocks = shocks;

        if (shocks != null) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "The initial dataset you've provided will be used as initial shocks"
                            + "\nfor a Fisher model.");

            for (DataModel _shocks : shocks) {
                if (_shocks == null) {
                    throw new NullPointerException("Dataset containing shocks must not be null.");
                }
                DataSet dataSet = (DataSet) _shocks;
                if (!dataSet.isContinuous()) {
                    throw new IllegalArgumentException("Dataset containing shocks must be continuous tabular.");
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
//        if (parameters.getLong(Params.SEED) != -1L) {
//            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
//        }

        boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();
        Graph graph = this.randomGraph.createGraph(parameters);

        System.out.println("degree = " + GraphUtils.getDegree(graph));

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            if (this.shocks != null && this.shocks.size() > 0) {
                parameters.set(Params.NUM_MEASURES, this.shocks.get(0).getVariables().size());
            }

            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            if (this.shocks != null && this.shocks.size() > 0) {
                graph.setNodes(this.shocks.get(0).getVariables());
            }

            this.graphs.add(graph);

            int[] tiers = new int[graph.getNodes().size()];
            for (int j = 0; j < tiers.length; j++) {
                tiers[j] = j;
            }

            LargeScaleSimulation simulator = new LargeScaleSimulation(
                    graph, graph.getNodes(), tiers);
            simulator.setCoefRange(
                    parameters.getDouble(Params.COEF_LOW),
                    parameters.getDouble(Params.COEF_HIGH));
            simulator.setVarRange(
                    parameters.getDouble(Params.VAR_LOW),
                    parameters.getDouble(Params.VAR_HIGH));
            simulator.setIncludePositiveCoefs(parameters.getBoolean(Params.INCLUDE_POSITIVE_COEFS));
            simulator.setIncludeNegativeCoefs(parameters.getBoolean(Params.INCLUDE_NEGATIVE_COEFS));
            simulator.setSelfLoopCoef(parameters.getDouble(Params.SELF_LOOP_COEF));
            simulator.setMeanRange(
                    parameters.getDouble(Params.MEAN_LOW),
                    parameters.getDouble(Params.MEAN_HIGH));
            simulator.setErrorsNormal(parameters.getBoolean(Params.ERRORS_NORMAL));
            simulator.setVerbose(parameters.getBoolean(Params.VERBOSE));

            DataSet dataSet;

            if (this.shocks == null) {
                dataSet = simulator.simulateDataFisher(
                        parameters.getInt(Params.INTERVAL_BETWEEN_SHOCKS),
                        parameters.getInt(Params.INTERVAL_BETWEEN_RECORDINGS),
                        parameters.getInt(Params.SAMPLE_SIZE),
                        parameters.getDouble(Params.FISHER_EPSILON),
                        saveLatentVars
                );
            } else {
                DataSet _shocks = (DataSet) this.shocks.get(i);

                dataSet = simulator.simulateDataFisher(
                        _shocks.getDoubleData().toArray(),
                        parameters.getInt(Params.INTERVAL_BETWEEN_SHOCKS),
                        parameters.getDouble(Params.FISHER_EPSILON)
                );
            }

            double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE);

            if (variance > 0) {
                for (int k = 0; k < dataSet.getNumRows(); k++) {
                    for (int j = 0; j < dataSet.getNumColumns(); j++) {
                        double d = dataSet.getDouble(k, j);
                        double delta = RandomUtil.getInstance().nextNormal(0, FastMath.sqrt(variance));
                        dataSet.setDouble(k, j, d + delta);
                    }
                }
            }

            dataSet.setName("" + (i + 1));

            if (parameters.getBoolean(Params.STANDARDIZE)) {
                dataSet = DataTransforms.standardizeData(dataSet);
            }

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataTransforms.shuffleColumns(dataSet);
            }

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            this.dataSets.add(saveLatentVars ? dataSet : DataTransforms.restrictToMeasured(dataSet));
        }
    }

    /** {@inheritDoc} */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /** {@inheritDoc} */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Linear Fisher model simulation";
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>(this.randomGraph.getParameters());

        if (this.shocks != null) {
            parameters.remove(Params.NUM_MEASURES);
            parameters.remove(Params.NUM_LATENTS);
        }

        parameters.add(Params.COEF_LOW);
        parameters.add(Params.COEF_HIGH);
        parameters.add(Params.VAR_LOW);
        parameters.add(Params.VAR_HIGH);
        parameters.add(Params.VERBOSE);
        parameters.add(Params.INCLUDE_POSITIVE_COEFS);
        parameters.add(Params.INCLUDE_NEGATIVE_COEFS);
        parameters.add(Params.ERRORS_NORMAL);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.NUM_CATEGORIES);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.INTERVAL_BETWEEN_SHOCKS);
        parameters.add(Params.INTERVAL_BETWEEN_RECORDINGS);
        parameters.add(Params.SELF_LOOP_COEF);
        parameters.add(Params.FISHER_EPSILON);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.MEASUREMENT_VARIANCE);
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.STANDARDIZE);
//        parameters.add(Params.SEED);

        return parameters;
    }

    /** {@inheritDoc} */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }
}
