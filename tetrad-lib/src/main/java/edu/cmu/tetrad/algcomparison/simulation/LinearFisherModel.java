package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.LinearSimulations;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jdramsey
 */
public class LinearFisherModel implements Simulation {
    static final long serialVersionUID = 23L;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private RandomGraph randomGraph;
    private List<Node> shuffledOrder;

    public LinearFisherModel(RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters) {
        dataSets = new ArrayList<>();
        graphs = new ArrayList<>();
        Graph graph = randomGraph.createGraph(parameters);

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean("differentGraphs") && i > 0) {
                graph = randomGraph.createGraph(parameters);
            }

            graphs.add(graph);

            int[] tiers = new int[graph.getNodes().size()];
            for (int j = 0; j < tiers.length; j++) tiers[j] = j;

            LinearSimulations simulator = new LinearSimulations(
                    graph, graph.getNodes(), tiers);
            simulator.setCoefRange(
                    parameters.getDouble("coefLow"),
                    parameters.getDouble("coefHigh"));
            simulator.setVarRange(
                    parameters.getDouble("varLow"),
                    parameters.getDouble("varHigh"));
            simulator.setVerbose(parameters.getBoolean("verbose"));

            DataSet dataSet = simulator.simulateDataFisher(
                    parameters.getInt("sampleSize"),
                    parameters.getInt("intervalBetweenShocks"),
                    parameters.getDouble("fisherEpsilon"));
            dataSet.setName("" + (i + 1));

            if (parameters.getDouble("percentDiscrete") > 0.0) {
                if (this.shuffledOrder == null) {
                    List<Node> shuffledNodes = new ArrayList<>(dataSet.getVariables());
                    Collections.shuffle(shuffledNodes);
                    this.shuffledOrder = shuffledNodes;
                }

                Discretizer discretizer = new Discretizer(dataSet);

                for (int k = 0; k < shuffledOrder.size() * parameters.getDouble("percentDiscrete") * 0.01; k++) {
                    discretizer.equalIntervals(dataSet.getVariable(shuffledOrder.get(k).getName()),
                            parameters.getInt("numCategories"));
                }

                String name = dataSet.getName();
                dataSet = discretizer.discretize();
                dataSet.setName(name);
            }

            dataSets.add(dataSet);
        }
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    @Override
    public Graph getTrueGraph(int index) {
        return graphs.get(index);
    }

    @Override
    public String getDescription() {
        return "Large scale SEM simulation";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.addAll(randomGraph.getParameters());
        parameters.add("coefLow");
        parameters.add("coefHigh");
        parameters.add("varLow");
        parameters.add("varHigh");
        parameters.add("verbose");
        parameters.add("numRuns");
        parameters.add("percentDiscrete");
        parameters.add("numCategories");
        parameters.add("differentGraphs");
        parameters.add("sampleSize");
        parameters.add("intervalBetweenShocks");
        parameters.add("fisherEpsilon");
        return parameters;
    }

    @Override
    public int getNumDataSets() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }
}
