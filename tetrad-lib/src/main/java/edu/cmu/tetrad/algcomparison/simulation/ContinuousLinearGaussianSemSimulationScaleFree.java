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
public class ContinuousLinearGaussianSemSimulationScaleFree implements Simulation {
    private List<DataSet> dataSets;
    private Graph graph;

    public void simulate(Parameters parameters) {
        this.dataSets = new ArrayList<>();
        this.graph = GraphUtils.scaleFreeGraph(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getDouble("scaleFreeAlpha"),
                parameters.getDouble("scaleFreeBeta"),
                parameters.getDouble("scaleFreeDeltaIn"),
                parameters.getInt("scaleFreeDeltaOut")
        );

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
        parameters.add("scaleFreeAlpha");
        parameters.add("scaleFreeBeta");
        parameters.add("scaleFreeDeltaIn");
        parameters.add("scaleFreeDeltaOut");
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

