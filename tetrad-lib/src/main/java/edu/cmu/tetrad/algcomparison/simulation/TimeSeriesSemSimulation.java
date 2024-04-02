package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Time series SEM simulation.
 *
 * @author josephramsey
 * @author danielmalinsky
 * @version $Id: $Id
 */
public class TimeSeriesSemSimulation implements Simulation, HasKnowledge {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph.
     */
    private final RandomGraph randomGraph;

    /**
     * The data sets.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The knowledge.
     */
    private Knowledge knowledge;

    /**
     * <p>Constructor for TimeSeriesSemSimulation.</p>
     *
     * @param randomGraph a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     */
    public TimeSeriesSemSimulation(RandomGraph randomGraph) {
        if (randomGraph == null) {
            throw new NullPointerException();
        }
        this.randomGraph = randomGraph;
    }

    /**
     * <p>topToBottomLayout.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     */
    public static void topToBottomLayout(TimeLagGraph graph) {

        final int xStart = 65;
        final int yStart = 50;
        final int xSpace = 100;
        final int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int x = xStart - xSpace;

        for (Node node : lag0Nodes) {
            x += xSpace;
            int y = yStart - ySpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                y += ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
//        if (parameters.getLong(Params.SEED) != -1L) {
//            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
//        }

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        Graph graph = this.randomGraph.createGraph(parameters);
        graph = TsUtils.graphToLagGraph(graph, parameters.getInt(Params.NUM_LAGS));
        TimeSeriesSemSimulation.topToBottomLayout((TimeLagGraph) graph);
        this.knowledge = TsUtils.getKnowledge(graph);

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
                graph = TsUtils.graphToLagGraph(graph, 2);
                TimeSeriesSemSimulation.topToBottomLayout((TimeLagGraph) graph);
            }

            this.graphs.add(graph);

            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm, parameters);

            int sampleSize = parameters.getInt(Params.SAMPLE_SIZE);

            boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);
            DataSet dataSet = im.simulateData(sampleSize, saveLatentVars);

            int numLags = ((TimeLagGraph) graph).getMaxLag();

            dataSet = TsUtils.createLagData(dataSet, numLags);

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            dataSet.setName("" + (i + 1));
            dataSet.setKnowledge(this.knowledge.copy());
            this.dataSets.add(dataSet);

        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Linear, Gaussian Dynamic SEM (1-lag SVAR) simulation";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortName() {
        return "Time Series SEM Simulation";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.NUM_LAGS);

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        parameters.addAll(SemIm.getParameterNames());

        parameters.add(Params.STANDARDIZE);
        parameters.add(Params.MEASUREMENT_VARIANCE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);
//        parameters.add(Params.SEED);

        return parameters;

    }

    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

}
