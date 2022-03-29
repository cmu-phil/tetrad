package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.gene.tetrad.gene.graph.LagGraphParams;
import edu.cmu.tetrad.gene.tetrad.gene.graph.RandomActiveLagGraph;
import edu.cmu.tetrad.gene.tetrad.gene.history.LaggedFactor;
import edu.cmu.tetrad.gene.tetradapp.model.BooleanGlassGeneIm;
import edu.cmu.tetrad.gene.tetradapp.model.BooleanGlassGenePm;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import edu.cmu.tetrad.util.Parameters;

import java.util.*;

/**
 * A version of the Lee & Hastic simulation which is guaranteed to generate a discrete
 * data set.
 *
 * @author jdramsey
 */
@Experimental
public class BooleanGlassSimulation implements Simulation {
    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private Graph graph = new EdgeListGraph();

    public BooleanGlassSimulation(final RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(final Parameters parameters, final boolean newModel) {
//        if (!newModel && !dataSets.isEmpty()) return;

        this.graph = this.randomGraph.createGraph(parameters);

        final LagGraphParams params = new LagGraphParams(parameters);

        params.setIndegree(2);
        params.setMlag(1);

        final RandomActiveLagGraph _graph = new RandomActiveLagGraph(params);
        final BooleanGlassGenePm pm = new BooleanGlassGenePm(_graph);
        final BooleanGlassGeneIm im = new BooleanGlassGeneIm(pm, parameters);
        final DataModelList data = im.simulateData();

        final List<DataSet> dataSets = new ArrayList<>();

        for (final DataModel model : data) {
            dataSets.add((DataSet) model);
        }

        this.dataSets = dataSets;

        final List<String> factors = new ArrayList<>(_graph.getFactors());

        final Map<String, Node> nodes = new HashMap<>();

        for (final String factor : factors) {
            nodes.put(factor, new ContinuousVariable(factor));
        }

        final TimeLagGraph graph = new TimeLagGraph();
        graph.setMaxLag(_graph.getMaxLag());

        for (final String factor : factors) {
            graph.addNode(nodes.get(factor));
        }

        for (final String factor : factors) {
            for (final Object o : _graph.getParents(factor)) {
                final LaggedFactor laggedFactor = (LaggedFactor) o;
                final String _factor = laggedFactor.getFactor();
                final int lag = laggedFactor.getLag();
                final Node node1 = graph.getNode(_factor + ":" + lag);
                final Node node2 = graph.getNode(factor);
                graph.addDirectedEdge(node1, node2);
            }
        }

        topToBottomLayout(graph);

        this.graph = graph;
    }

    public static void topToBottomLayout(final TimeLagGraph graph) {

        final int xStart = 65;
        final int yStart = 50;
        final int xSpace = 100;
        final int ySpace = 100;
        final List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(final Node o1, final Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int x = xStart - xSpace;

        for (final Node node : lag0Nodes) {
            x += xSpace;
            int y = yStart - ySpace;
            final TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                y += ySpace;
                final Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }
    }

    @Override
    public Graph getTrueGraph(final int index) {
        return this.graph;
    }

    @Override
    public DataModel getDataModel(final int index) {
        return this.dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Boolean Glass Simulation " + this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new ArrayList<>();
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

}
