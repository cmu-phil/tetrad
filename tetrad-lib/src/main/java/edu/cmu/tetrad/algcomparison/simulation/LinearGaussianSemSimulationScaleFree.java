package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class LinearGaussianSemSimulationScaleFree implements Simulation {
    private Graph graph;
    private DataSet dataSet;
    private int numDataSets;

    public LinearGaussianSemSimulationScaleFree(int numDataSets) {
        this.numDataSets = numDataSets;
    }

    public DataSet getDataSet(int index, Map<String, Number> parameters) {
        this.graph = GraphUtils.scaleFreeGraph(
                parameters.get("numMeasures").intValue(),
                parameters.get("numLatents").intValue(),
                parameters.get("scaleFreeAlpha").doubleValue(),
                parameters.get("scaleFreeBeta").doubleValue(),
                parameters.get("scaleFreeDeltaIn").doubleValue(),
                parameters.get("scaleFreeDeltaOut").doubleValue()
        );
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        this.dataSet = im.simulateData(parameters.get("sampleSize").intValue(), false);
        return this.dataSet;
    }

    public Graph getDag() {
        return graph;
    }

    public DataSet getData() {
        return dataSet;
    }

    public String toString() {
        return "Linear, Gaussian SEM simulation using scale-free graphs";
    }

    public boolean isMixed() {
        return false;
    }

    @Override
    public int getNumDataSets() {
        return numDataSets;
    }
}

