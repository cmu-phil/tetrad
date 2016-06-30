package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousCyclicSemSimulation implements Simulation {
    private Graph graph;
    private DataSet dataSet;
    private int numDataSets;

    public ContinuousCyclicSemSimulation(int numDataSets) {
        this.numDataSets = numDataSets;
    }

    public DataSet getDataSet(int index, Map<String, Number> parameters) {
        this.graph = GraphUtils.cyclicGraph2(parameters.get("numMeasures").intValue(),
                parameters.get("numEdges").intValue());
        SemPm pm = new SemPm(graph);

        SemImInitializationParams params = new SemImInitializationParams();
        params.setCoefRange(.2, .9);
        params.setCoefSymmetric(true);
        SemIm im = new SemIm(pm, params);
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
        return "Cyclic SEM simulation";
    }

    @Override
    public boolean isMixed() {
        return false;
    }

    @Override
    public int getNumDataSets() {
        return numDataSets;
    }
}
