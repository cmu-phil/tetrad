package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.graph.LagGraphParams;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.util.*;

/**
 * A version of the Lee and Hastic simulation which is guaranteed to generate a discrete data set.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@Experimental
public class BooleanGlassSimulation implements Simulation {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph.
     */
    private final RandomGraph randomGraph;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The graph.
     */
    private Graph graph = new EdgeListGraph();

    /**
     * <p>Constructor for BooleanGlassSimulation.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     */
    public BooleanGlassSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    /**
     * Lays out the nodes in the graph from top to bottom.
     *
     * @param graph The graph to lay out.
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
                    System.out.println("Couldn't find " + null);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates a new data set.
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.graph = this.randomGraph.createGraph(parameters);

        LagGraphParams params = new LagGraphParams(parameters);

        params.setIndegree(2);
        params.setMlag(1);

        RandomActiveLagGraph _graph = new RandomActiveLagGraph(params);
        BooleanGlassGenePm pm = new BooleanGlassGenePm(_graph);
        BooleanGlassGeneIm im = new BooleanGlassGeneIm(pm, parameters);
        DataModelList data = im.simulateData();

        List<DataSet> dataSets = new ArrayList<>();

        for (DataModel model : data) {
            dataSets.add((DataSet) model);
        }

        this.dataSets = dataSets;

        List<String> factors = new ArrayList<>(_graph.getFactors());

        Map<String, Node> nodes = new HashMap<>();

        for (String factor : factors) {
            nodes.put(factor, new ContinuousVariable(factor));
        }

        TimeLagGraph graph = new TimeLagGraph();
        graph.setMaxLag(_graph.getMaxLag());

        for (String factor : factors) {
            graph.addNode(nodes.get(factor));
        }

        for (String factor : factors) {
            for (Object o : _graph.getParents(factor)) {
                LaggedFactor laggedFactor = (LaggedFactor) o;
                String _factor = laggedFactor.getFactor();
                int lag = laggedFactor.getLag();
                Node node1 = graph.getNode(_factor + ":" + lag);
                Node node2 = graph.getNode(factor);
                graph.addDirectedEdge(node1, node2);
            }
        }

        BooleanGlassSimulation.topToBottomLayout(graph);

        this.graph = graph;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the true graph.
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graph;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the simulated data set.
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the simulation.
     */
    @Override
    public String getDescription() {
        return "Boolean Glass Simulation " + this.randomGraph.getDescription();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters for the simulation.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("lagGraphVarsPerInd");
        parameters.add("lagGraphMlag");
        parameters.add("lagGraphIndegree");
        parameters.add("numDishes");
        parameters.add("includeDishAndChipColumns");
        parameters.add("numChipsPerDish");
        parameters.add("numCellsPerDish");
        parameters.add("stepsGenerated");
        parameters.add("firstStepStored");
        parameters.add("interval");
        parameters.add("rawDataSaved");
        parameters.add("measuredDataSaved");
        parameters.add("initSync");
        parameters.add("antilogCalculated");
        parameters.add("dishDishVariability");
        parameters.add("sampleSampleVariability");
        parameters.add("chipChipVariability");
        parameters.add("pixelDigitalization");
        parameters.add(Params.SEED);

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
     * <p>
     * Returns the number of true graphs.
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type of the simulation.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

}
