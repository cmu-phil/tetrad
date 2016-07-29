package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.algorithms.graphs.GraphGenerator;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jdramsey
 */
public class SemThenDiscretizeSimulation implements Simulation {
    private final GraphGenerator graphGenerator;
    private Graph graph;
    private List<DataSet> dataSets;
    private DataType dataType;

    public SemThenDiscretizeSimulation(GraphGenerator graphGenerator) {
        this.graphGenerator = graphGenerator;
        this.dataType = DataType.Mixed;
    }

    public SemThenDiscretizeSimulation(GraphGenerator graphGenerator, DataType dataType) {
        this.graphGenerator = graphGenerator;
        this.dataType = dataType;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = graphGenerator.getGraph(parameters);

        double percentDiscrete = parameters.getDouble("percentDiscrete");

        boolean discrete = parameters.getString("dataType").equals("discrete");
        boolean continuous = parameters.getString("dataType").equals("continuous");

        if (discrete && percentDiscrete != 100.0) {
            throw new IllegalArgumentException("To simulate discrete data, 'percentDiscrete' must be set to 0.0.");
        } else if (continuous && percentDiscrete != 0.0) {
            throw new IllegalArgumentException("To simulate continuoue data, 'percentDiscrete' must be set to 100.0.");
        }

        if (discrete) this.dataType = dataType.Discrete;
        if (continuous) this.dataType = dataType.Continuous;

        dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));
            dataSets.add(simulate(graph, parameters));
        }
    }

    @Override
    public Graph getTrueGraph() {
        return graph;
    }

    @Override
    public String getDescription() {
        return "Simulation SEM data then discretizing some variables";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = graphGenerator.getParameters();
        parameters.add("numRuns");
        parameters.add("sampleSize");
        parameters.add("variance");
        parameters.add("numCategories");
        return parameters;
    }

    @Override
    public int getNumDataSets() {
        return dataSets.size();
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    @Override
    public DataType getDataType() {
        return dataType;
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet continuousData = im.simulateData(parameters.getInt("sampleSize"), false);

        List<Node> shuffledNodes = new ArrayList<>(continuousData.getVariables());
        Collections.shuffle(shuffledNodes);

        Discretizer discretizer = new Discretizer(continuousData);

        for (int i = 0; i < shuffledNodes.size() * parameters.getDouble("percentDiscrete") * 0.01; i++) {
            discretizer.equalIntervals(shuffledNodes.get(i), parameters.getInt("numCategories"));
        }

        return discretizer.discretize();
    }
}
