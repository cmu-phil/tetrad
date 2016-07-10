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
public class LinearGaussianSemSimulation implements Simulation {
    private Graph graph;
    private DataSet dataSet;
    private int numDataSets;

    public LinearGaussianSemSimulation(int numDataSets) {
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
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        this.dataSet = im.simulateData(parameters.getInt("sampleSize"), false);
        return this.dataSet;
    }

    public Graph getTrueGraph() {
        return graph;
    }

    public DataSet getData() {
        return dataSet;
    }

    public String getDescription() {
        return "Linear, Gaussian SEM simulation";
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
