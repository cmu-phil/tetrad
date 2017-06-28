package edu.cmu.tetrad.algcomparison.joe_examples;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class ExternalAlgorithm implements Algorithm {
    static final long serialVersionUID = 23L;
    private final String extDir;
    private String path;
    private List<String> usedParameters = new ArrayList<>();
    private Simulation simulation;

    public ExternalAlgorithm(String extDir) {
        this.extDir = extDir;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        int index = -1;

        try {

            for (int i = 0; i < getNumDataModels(); i++) {
                if (dataSet.equals(simulation.getDataModel(i))) {
                    index = i + 1;
                    break;
                }
            }

            if (index == -1) {
                throw new IllegalArgumentException("Not a dataset for this simulation.");
            }

            DataReader reader = new DataReader();
            reader.setVariablesSupplied(true);
            File file3 = new File(path, extDir +"/graph." + index + ".txt");
            DataSet dataSet2 = reader.parseTabular(file3);
            System.out.println("Loading graph from " + file3.getAbsolutePath());
            Graph graph = GraphUtils.loadGraphPcAlgMatrix(dataSet2);
            GraphUtils.circleLayout(graph, 225, 200, 150);

            return graph;
        } catch (IOException e) {
            e.printStackTrace();
        }

        throw new IllegalArgumentException("Couldn't find a graph at a " + path + "/" + extDir + "/graph." + index + ".txt");
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph graph1 = SearchGraphUtils.patternForDag(new EdgeListGraph(graph));

        System.out.println("Comparison graph" + graph1);

        return graph1;
    }

    public String getDescription() {
        return "Load data from " + path + "/" + extDir;
    }

    @Override
    public List<String> getParameters() {
        return usedParameters;
    }

    public int getNumDataModels() {
        return simulation.getNumDataModels();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    public Simulation setSimulation(Simulation simulation) {
        return this.simulation = simulation;
    }

    public void setPath(String path) {
        this.path = path;
    }

}
