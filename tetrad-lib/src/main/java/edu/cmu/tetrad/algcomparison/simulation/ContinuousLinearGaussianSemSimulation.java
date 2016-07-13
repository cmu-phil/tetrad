package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousLinearGaussianSemSimulation implements Simulation {
    private int numDataSets;
    private List<DataSet> dataSets;
    private List<Graph> graphs;

    public ContinuousLinearGaussianSemSimulation(Parameters parameters) {
        this.numDataSets = numDataSets;

        dataSets = new ArrayList<>();
        graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            Graph graph = GraphUtils.randomGraphRandomForwardEdges(
                    parameters.getInt("numMeasures"),
                    parameters.getInt("numLatents"),
                    parameters.getInt("numEdges"),
                    parameters.getInt("maxDegree"),
                    parameters.getInt("maxIndegree"),
                    parameters.getInt("maxOutdegree"),
                    parameters.getInt("connected") == 1);
            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);
            dataSets.add(im.simulateData(parameters.getInt("sampleSize"), false));
            graphs.add(graph);
        }
    }

    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    public Graph getTrueGraph(int index) {
        return graphs.get(index);
    }

    public String getDescription() {
        return "Linear, Gaussian SEM simulation";
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
