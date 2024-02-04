package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * SEM the discretize.
 *
 * @author josephramsey
 */
public class SemThenDiscretize implements Simulation {
    private static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<Graph> graphs = new ArrayList<>();
    private List<DataSet> dataSets = new ArrayList<>();
    private DataType dataType;
    private List<Node> shuffledOrder;

    public SemThenDiscretize(RandomGraph randomGraph) {
        this.randomGraph = randomGraph;
        this.dataType = DataType.Mixed;
    }

    public SemThenDiscretize(RandomGraph randomGraph, DataType dataType) {
        this.randomGraph = randomGraph;
        this.dataType = dataType;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        double percentDiscrete = parameters.getDouble(Params.PERCENT_DISCRETE);

        boolean discrete = parameters.getString("dataType").equals("discrete");
        boolean continuous = parameters.getString("dataType").equals("continuous");

        if (discrete && percentDiscrete != 100.0) {
            throw new IllegalArgumentException("To simulate discrete data, 'percentDiscrete' must be set to 0.0.");
        } else if (continuous && percentDiscrete != 0.0) {
            throw new IllegalArgumentException("To simulate continuoue data, 'percentDiscrete' must be set to 100.0.");
        }

        if (discrete) this.dataType = DataType.Discrete;
        if (continuous) this.dataType = DataType.Continuous;

        this.dataSets = new ArrayList<>();
        this.shuffledOrder = null;

        Graph graph = this.randomGraph.createGraph(parameters);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            this.graphs.add(graph);

            DataSet dataSet = simulate(graph, parameters);

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            dataSet.setName("" + (i + 1));
            this.dataSets.add(dataSet);
        }
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    @Override
    public String getDescription() {
        return "Simulation SEM data then discretizing some variables, using " +
                this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = this.randomGraph.getParameters();
        parameters.add(Params.NUM_CATEGORIES);
        parameters.add(Params.PERCENT_DISCRETE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SEED);
        return parameters;
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    @Override
    public DataType getDataType() {
        return this.dataType;
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet continuousData = im.simulateData(parameters.getInt(Params.SAMPLE_SIZE), false);

        if (this.shuffledOrder == null) {
            List<Node> shuffledNodes = new ArrayList<>(continuousData.getVariables());
            RandomUtil.shuffle(shuffledNodes);
            this.shuffledOrder = shuffledNodes;
        }

        Discretizer discretizer = new Discretizer(continuousData);

        for (int i = 0; i < this.shuffledOrder.size() * parameters.getDouble(Params.PERCENT_DISCRETE) * 0.01; i++) {
            discretizer.equalIntervals(continuousData.getVariable(this.shuffledOrder.get(i).getName()),
                    parameters.getInt(Params.NUM_CATEGORIES));
        }

        return discretizer.discretize();
    }
}
