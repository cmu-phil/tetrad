package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.DataType;
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
    private Graph graph;
    private List<DataSet> dataSets;

    public void simulate(Parameters parameters) {
        dataSets = new ArrayList<>();
        this.graph = GraphUtils.randomGraphRandomForwardEdges(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getInt("numEdges"),
                parameters.getInt("maxDegree"),
                parameters.getInt("maxIndegree"),
                parameters.getInt("maxOutdegree"),
                parameters.getInt("connected") == 1);

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);
            dataSets.add(im.simulateData(parameters.getInt("sampleSize"), false));
        }
    }

    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    public Graph getTrueGraph() {
        return graph;
    }

    public String getDescription() {
        return "Linear, Gaussian SEM simulation";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numMeasures");
        parameters.add("numLatents");
        parameters.add("numEdges");
        parameters.add("maxDegree");
        parameters.add("maxIndegree");
        parameters.add("maxOutdegree");
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
        return DataType.Continuous;
    }
}
