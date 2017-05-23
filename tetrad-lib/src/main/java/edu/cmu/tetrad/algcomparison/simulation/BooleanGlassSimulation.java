package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
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
 * A version of the Lee & Hastic simulation which is guaranteed ot generate a discrete
 * data set.
 *
 * @author jdramsey
 */
public class BooleanGlassSimulation implements Simulation {
    static final long serialVersionUID = 23L;
    private RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private Graph graph = new EdgeListGraph();

    public BooleanGlassSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = randomGraph.createGraph(parameters);

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

        topToBottomLayout(graph);

        this.graph = graph;
    }

    public static void topToBottomLayout(TimeLagGraph graph) {

        int xStart = 65;
        int yStart = 50;
        int xSpace = 100;
        int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

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

    @Override
    public Graph getTrueGraph(int index) {
        return graph;
    }

    @Override
    public DataModel getDataModel(int index) {
        return dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Boolean Glass Simulation " + randomGraph.getDescription();
    }

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

        return parameters;
    }

    @Override
    public int getNumDataModels() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

}
