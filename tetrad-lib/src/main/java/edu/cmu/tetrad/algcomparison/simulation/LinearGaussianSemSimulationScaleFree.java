package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

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

    public DataSet getDataSet(int index, Parameters parameters) {
        this.graph = GraphUtils.scaleFreeGraph(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getDouble("scaleFreeAlpha"),
                parameters.getDouble("scaleFreeBeta"),
                parameters.getDouble("scaleFreeDeltaIn"),
                parameters.getInt("scaleFreeDeltaOut")
        );
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
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
        return "Linear, Gaussian SEM simulation using scale-free graphs";
    }

    public boolean isMixed() {
        return false;
    }

    @Override
    public int getNumDataSets() {
        return numDataSets;
    }

    @Override
    public DataType getDataType(Parameters parameters) {
        return DataType.Continuous;
    }
}

