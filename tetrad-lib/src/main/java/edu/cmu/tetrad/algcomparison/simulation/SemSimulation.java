package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.algorithms.graphs.GraphGenerator;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
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
public class SemSimulation implements Simulation {
    private final GraphGenerator graphGenerator;
    private Graph graph;
    private List<DataSet> dataSets;

    public SemSimulation(GraphGenerator graphGenerator) {
        this.graphGenerator = graphGenerator;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = graphGenerator.getGraph(parameters);

        dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));
            SemPm pm = new SemPm(graph);
            SemImInitializationParams params = new SemImInitializationParams();
            params.setVarRange(parameters.getDouble("varLow"), parameters.getDouble("varHigh"));
            SemIm im = new SemIm(pm);
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
        List<String> parameters = graphGenerator.getParameters();
        parameters.add("numRuns");
        parameters.add("sampleSize");
        parameters.add("variance");
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
