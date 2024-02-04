package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.csb.mgm.MixedUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A version of the Lee and Hastic simulation which is guaranteed ot generate a discrete data set.
 *
 * @author josephramsey
 */
public class LeeHastieSimulation implements Simulation {

    private static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private DataType dataType;
    private List<Node> shuffledOrder;

    public LeeHastieSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
//        if (parameters.getLong(Params.SEED) != -1L) {
//            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
//        }

        double percentDiscrete = parameters.getDouble(Params.PERCENT_DISCRETE);

        boolean discrete = parameters.getString(Params.DATA_TYPE).equals("discrete");
        boolean continuous = parameters.getString(Params.DATA_TYPE).equals("continuous");

        if (discrete && percentDiscrete != 100.0) {
            throw new IllegalArgumentException("To simulate discrete data, 'percentDiscrete' must be set to 0.0.");
        } else if (continuous && percentDiscrete != 0.0) {
            throw new IllegalArgumentException("To simulate continuoue data, 'percentDiscrete' must be set to 100.0.");
        }

        if (discrete) {
            this.dataType = DataType.Discrete;
        }
        if (continuous) {
            this.dataType = DataType.Continuous;
        }

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
            dataSet.setName("" + (i + 1));

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataTransforms.shuffleColumns(dataSet);
            }

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            this.dataSets.add(dataSet);
        }
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Lee & Hastie simulation using " + this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = this.randomGraph.getParameters();
        parameters.add(Params.MIN_CATEGORIES);
        parameters.add(Params.MAX_CATEGORIES);
        parameters.add(Params.PERCENT_DISCRETE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);

        parameters.add(Params.VERBOSE);
//        parameters.add(Params.SEED);

        return parameters;
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return this.dataType;
    }

    private DataSet simulate(Graph dag, Parameters parameters) {
        HashMap<String, Integer> nd = new HashMap<>();

        List<Node> nodes = dag.getNodes();

        List<Node> shuffledNodes = new ArrayList<>(nodes);
        RandomUtil.shuffle(shuffledNodes);

        if (this.shuffledOrder == null) {
            this.shuffledOrder = shuffledNodes;
        }

        for (int i = 0; i < this.shuffledOrder.size(); i++) {
            if (i < this.shuffledOrder.size() * parameters.getDouble(Params.PERCENT_DISCRETE) * 0.01) {
                int minNumCategories = parameters.getInt(Params.MIN_CATEGORIES);
                int maxNumCategories = parameters.getInt(Params.MAX_CATEGORIES);
                int value = pickNumCategories(minNumCategories, maxNumCategories);
                nd.put(this.shuffledOrder.get(i).getName(), value);
            } else {
                nd.put(this.shuffledOrder.get(i).getName(), 0);
            }
        }

        Graph graph = MixedUtils.makeMixedGraph(dag, nd);

        GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(graph, "Split(-1.0,-.0,.0,1.0)");
        GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);

        boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);
        DataSet ds = im.simulateDataAvoidInfinity(parameters.getInt(Params.SAMPLE_SIZE), saveLatentVars);

        return MixedUtils.makeMixedData(ds, nd);
    }

    private int pickNumCategories(int min, int max) {
        return min + RandomUtil.getInstance().nextInt(max - min + 1);
    }
}
