package edu.cmu.tetrad.algcomparison.simulation;

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

    public DiscreteBayesNetSimulation() {
    }

    public void simulate(Map<String, Number> parameters) {
        this.graph = GraphUtils.randomGraphRandomForwardEdges(
                parameters.get("numMeasures").intValue(),
                parameters.get("numLatents").intValue(),
                parameters.get("numEdges").intValue(),
                parameters.get("maxDegree").intValue(),
                parameters.get("maxIndegree").intValue(),
                parameters.get("maxOutdegree").intValue(),
                parameters.get("connected").intValue() == 1);
        int numCategories = parameters.get("numCategories").intValue();
        BayesPm pm = new BayesPm(graph, numCategories, numCategories);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        this.dataSet = im.simulateData(parameters.get("sampleSize").intValue(), false);
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

    public boolean isContinuous() {
        return false;
    }
}
