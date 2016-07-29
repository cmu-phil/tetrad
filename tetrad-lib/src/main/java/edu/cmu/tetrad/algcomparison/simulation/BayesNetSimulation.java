package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class BayesNetSimulation implements Simulation {
    private RandomGraph randomGraph;
    private List<DataSet> dataSets;
    private Graph graph;

    public BayesNetSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = randomGraph.createGraph(parameters);

        dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));
            DataSet dataSet = simulate(graph, parameters);
            dataSets.add(dataSet);
        }
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }


    @Override
    public Graph getTrueGraph() {
        return graph;
    }

    @Override
    public String getDescription() {
        return "Bayes net simulation using " + randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = randomGraph.getParameters();
        parameters.add("numRuns");
        parameters.add("sampleSize");
        return parameters;
    }

    @Override
    public int getNumDataSets() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }


    private DataSet simulate(Graph graph, Parameters parameters) {
        int numCategories = parameters.getInt("numCategories");
        BayesPm pm = new BayesPm(graph, numCategories, numCategories);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        return im.simulateData(parameters.getInt("sampleSize"), false);
    }
}
