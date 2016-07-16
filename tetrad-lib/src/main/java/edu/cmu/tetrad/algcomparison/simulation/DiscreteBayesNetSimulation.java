package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class DiscreteBayesNetSimulation implements Simulation {
    private List<DataSet> dataSets;
    private Graph graph;

    public DiscreteBayesNetSimulation(Parameters parameters) {
        this.graph = GraphUtils.randomGraphRandomForwardEdges(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getInt("numEdges"),
                parameters.getInt("maxDegree"),
                parameters.getInt("maxIndegree"),
                parameters.getInt("maxOutdegree"),
                parameters.getInt("connected") == 1);

       for (int i = 0; i < parameters.getInt("numRuns"); i++) {
           DataSet dataSet = simulate(graph, parameters);
           dataSets.add(dataSet);
       }
    }

    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        int numCategories = parameters.getInt("numCategories");
        BayesPm pm = new BayesPm(graph, numCategories, numCategories);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        return im.simulateData(parameters.getInt("sampleSize"), false);
    }

    public Graph getTrueGraph() {
        return graph;
    }

    public String getDescription() {
        return "Bayes net simulation";
    }

    @Override
    public int getNumDataSets() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }
}
