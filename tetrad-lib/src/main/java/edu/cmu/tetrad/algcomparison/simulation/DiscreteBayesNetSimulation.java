package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class DiscreteBayesNetSimulation implements Simulation {
    private Graph graph;
    private DataSet dataSet;
    private int numDataSets;

    public DiscreteBayesNetSimulation(int numDataSets) {
        this.numDataSets = numDataSets;
    }

    public DataSet getDataSet(int index, Parameters parameters) {
        this.graph = GraphUtils.randomGraphRandomForwardEdges(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getInt("numEdges"),
                parameters.getInt("maxDegree"),
                parameters.getInt("maxIndegree"),
                parameters.getInt("maxOutdegree"),
                parameters.getInt("connected") == 1);
        int numCategories = parameters.getInt("numCategories");
        BayesPm pm = new BayesPm(graph, numCategories, numCategories);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        this.dataSet = im.simulateData(parameters.getInt("sampleSize"), false);
        return this.dataSet;
    }

    public Graph getDag() {
        return graph;
    }

    public DataSet getData() {
        return dataSet;
    }

    public String toString() {
        return "Bayes net simulation";
    }

    public boolean isMixed() {
        return false;
    }

    @Override
    public int getNumDataSets() {
        return numDataSets;
    }
}
