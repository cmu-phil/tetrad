package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class LargeSemSimulation implements Simulation {
    static final long serialVersionUID = 23L;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private RandomGraph randomGraph;

    public LargeSemSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters) {
        int numCases = parameters.getInt("sampleSize");

        List<Node> vars = new ArrayList<>();

        String numVars = "numMeasures";
//        int numEdges = (int) (parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2.0);

        for (int i = 0; i < parameters.getInt(numVars, 10); i++) {
            vars.add(new ContinuousVariable("X" + (i + 1)));
        }

        dataSets = new ArrayList<>();
        graphs = new ArrayList<>();
        Graph graph = randomGraph.createGraph(parameters);
        graph = GraphUtils.replaceNodes(graph, vars);
//        Graph graph = randomGraph(vars, numEdges);

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean("differentGraphs") && i > 0) {
                graph = randomGraph.createGraph(parameters);
                graph = GraphUtils.replaceNodes(graph, vars);
//                graph = randomGraph(vars, numEdges);
            }

            graphs.add(graph);

            int[] tiers = new int[graph.getNodes().size()];
            for (int j = 0; j < tiers.length; j++) tiers[j] = j;

            edu.cmu.tetrad.sem.LargeSemSimulator simulator = new LargeSemSimulator(graph, vars, tiers);
            simulator.setCoefRange(parameters.getDouble("coefLow"), parameters.getDouble("coefHigh"));
            simulator.setVarRange(parameters.getDouble("varLow"), parameters.getDouble("varHigh"));
            simulator.setVerbose(parameters.getBoolean("verbose"));
            DataSet dataSet = simulator.simulateDataFixPoint(numCases);
            dataSet.setName("" + (i + 1));
            dataSets.add(dataSet);
        }
    }

//    private Graph randomGraph(List<Node> vars, int numEdges) {
//        return GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges,
//                30, 15, 15, false, true);
//    }

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

        parameters.add("numMeasures");
        parameters.add("avgDegree");
        parameters.add("coefLow");
        parameters.add("coefHigh");
        parameters.add("varLow");
        parameters.add("varHigh");
        parameters.add("verbose");
        parameters.add("numRuns");
        parameters.add("differentGraphs");
        parameters.add("sampleSize");
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
