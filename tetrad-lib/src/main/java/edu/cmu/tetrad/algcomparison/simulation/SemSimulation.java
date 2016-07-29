package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class SemSimulation implements Simulation {
    private final RandomGraph randomGraph;
    private Graph graph;
    private List<DataSet> dataSets;

    public SemSimulation(RandomGraph randomGraph) {
        this.randomGraph = randomGraph;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = randomGraph.createGraph(parameters);

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
        return "Linear, Gaussian SEM simulation using "+ randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = randomGraph.getParameters();
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
