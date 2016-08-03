package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author jdramsey
 */
public class BayesNetSimulation implements Simulation {
    private RandomGraph randomGraph;
    private BayesPm pm;
    private BayesIm im;
    private List<DataSet> dataSets;
    private Graph graph;

    public BayesNetSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    public BayesNetSimulation(BayesPm pm) {
        this.randomGraph = new SingleGraph(pm.getDag());
        this.pm = pm;
    }

    public BayesNetSimulation(BayesIm im ) {
        this.randomGraph = new SingleGraph(im.getDag());
        this.im = im;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = randomGraph.createGraph(parameters);

        dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns", 1); i++) {
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
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = randomGraph.getParameters();
        parameters.put("minCategories", 2);
        parameters.put("maxCategories", 2);
        parameters.put("numRuns", 1);
        parameters.put("sampleSize", 1000);
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
        BayesIm im = this.im;

        if (im == null) {
            BayesPm pm = this.pm;

            if (pm == null) {
                int minCategories = parameters.getInt("minCategories", 2);
                int maxCategories = parameters.getInt("maxCategories", 4);
                pm = new BayesPm(graph, minCategories, maxCategories);
            }

            im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        }

        return im.simulateData(parameters.getInt("sampleSize", 1000), false);
    }
}
