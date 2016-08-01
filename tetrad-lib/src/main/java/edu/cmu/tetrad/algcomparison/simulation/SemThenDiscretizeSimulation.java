package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Graph;
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
    private final RandomGraph randomGraph;
    private Graph graph;
    private List<DataSet> dataSets;
    private DataType dataType;

    public SemThenDiscretizeSimulation(RandomGraph randomGraph) {
        this.randomGraph = randomGraph;
        this.dataType = DataType.Mixed;
    }

    public SemThenDiscretizeSimulation(RandomGraph randomGraph, DataType dataType) {
        this.randomGraph = randomGraph;
        this.dataType = dataType;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = randomGraph.createGraph(parameters);

        double percentDiscrete = parameters.getDouble("percentDiscrete", 0);

        boolean discrete = parameters.getString("dataType", "continuous").equals("discrete");
        boolean continuous = parameters.getString("dataType", "continuous").equals("continuous");

        if (discrete && percentDiscrete != 100.0) {
            throw new IllegalArgumentException("To simulate discrete data, 'percentDiscrete' must be set to 0.0.");
        } else if (continuous && percentDiscrete != 0.0) {
            throw new IllegalArgumentException("To simulate continuoue data, 'percentDiscrete' must be set to 100.0.");
        }

        if (discrete) this.dataType = DataType.Discrete;
        if (continuous) this.dataType = DataType.Continuous;

        dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns", 1); i++) {
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
        return "Simulation SEM data then discretizing some variables, using " +
                randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = randomGraph.getParameters();
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
        DataSet continuousData = im.simulateData(parameters.getInt("sampleSize", 1000), false);

        List<Node> shuffledNodes = new ArrayList<>(continuousData.getVariables());
        Collections.shuffle(shuffledNodes);

        Discretizer discretizer = new Discretizer(continuousData);

        for (int i = 0; i < shuffledNodes.size() * parameters.getDouble("percentDiscrete", 0) * 0.01; i++) {
            discretizer.equalIntervals(shuffledNodes.get(i), parameters.getInt("numCategories", 2));
        }

        return discretizer.discretize();
    }
}
