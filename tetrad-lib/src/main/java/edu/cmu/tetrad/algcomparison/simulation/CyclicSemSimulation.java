package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class CyclicSemSimulation implements Simulation {
    private Graph graph;
    private List<DataSet> dataSets;

    @Override
    public void createData(Parameters parameters) {
        this.graph = GraphUtils.cyclicGraph2(parameters.getInt("numMeasures"),
                parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2);

        dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            SemPm pm = new SemPm(graph);
            SemImInitializationParams params = new SemImInitializationParams();
            params.setCoefRange(.2, .9);
            params.setCoefSymmetric(true);
            SemIm im = new SemIm(pm, params);
            dataSets.add(im.simulateData(parameters.getInt("sampleSize"), false));
        }
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    @Override
    public Graph getTrueGraph() {
        return graph;
    }

    @Override
    public String getDescription() {
        return "Linear, Gaussian SEM simulation";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numMeasures");
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
