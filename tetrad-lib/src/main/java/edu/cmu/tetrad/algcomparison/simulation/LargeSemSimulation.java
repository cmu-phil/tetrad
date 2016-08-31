package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
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

    public LargeSemSimulation(RandomGraph graph) {
    }

    @Override
    public void createData(Parameters parameters) {
        int numCases = parameters.getInt("sampleSize");

        List<Node> vars = new ArrayList<>();

        String numVars = "numMeasures";
        int numEdges = (int) (parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2.0);

        for (int i = 0; i < parameters.getInt(numVars, 10); i++) {
            vars.add(new ContinuousVariable("X" + (i + 1)));
        }

        dataSets = new ArrayList<>();
        graphs = new ArrayList<>();
        Graph graph = randomGraph(vars, numEdges);

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean("differentGraphs") && i > 0) {
                graph = randomGraph(vars, numEdges);
            }

            graphs.add(graph);

            edu.cmu.tetrad.sem.LargeSemSimulator simulator = new LargeSemSimulator(graph);
            DataSet dataSet = simulator.simulateDataAcyclic(numCases);
            dataSet.setName("" + (i + 1));
            dataSets.add(dataSet);
        }
    }

    private Graph randomGraph(List<Node> vars, int numEdges) {
        return GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges,
                    30, 15, 15, false, true);
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

        parameters.add("numMeasures");
        parameters.add("avgDegree");
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
