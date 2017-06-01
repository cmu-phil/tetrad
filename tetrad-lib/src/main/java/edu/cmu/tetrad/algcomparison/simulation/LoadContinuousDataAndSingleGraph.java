package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.utils.HasParameterValues;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class LoadContinuousDataAndSingleGraph implements Simulation, HasParameterValues {
    static final long serialVersionUID = 23L;
    private String path;
    private Graph graph = null;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<String> usedParameters = new ArrayList<>();
    private Parameters parametersValues = new Parameters();

    public LoadContinuousDataAndSingleGraph(String path) {
        this.path = path;
        String structure = new File(path).getName();
        parametersValues.set("Structure", structure);
    }

    @Override
    public void createData(Parameters parameters) {
        this.dataSets = new ArrayList<>();

        File dir = new File(path + "/data_noise");

        if (dir.exists()) {
            File[] files = dir.listFiles();

            for (File file : files) {
                if (!file.getName().endsWith(".txt")) continue;
                System.out.println("Loading data from " + file.getAbsolutePath());
                DataReader reader = new DataReader();
                reader.setVariablesSupplied(true);
                try {
                    DataSet dataSet = reader.parseTabular(file);
                    dataSets.add(dataSet);
                } catch (Exception e) {
                    System.out.println("Couldn't parse " + file.getAbsolutePath());
                }
            }
        }

        File dir2 = new File(path + "/graph");

        if (dir2.exists()) {
            File[] files = dir2.listFiles();

            if (files.length != 1) {
                throw new IllegalArgumentException("Expecting exactly one graph file.");
            }

            File file = files[0];

            System.out.println("Loading graph from " + file.getAbsolutePath());
            this.graph = GraphUtils.loadGraphTxt(file);

//            if (!graph.isAdjacentTo(graph.getNode("X3"), graph.getNode("X4"))) {
//                graph.addUndirectedEdge(graph.getNode("X3"), graph.getNode("X4"));
//            }

            GraphUtils.circleLayout(this.graph, 225, 200, 150);
        }

        if (parameters.get("numRuns") != null) {
            parameters.set("numRuns", parameters.get("numRuns"));
        } else {
            parameters.set("numRuns", dataSets.size());
        }

        System.out.println();
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graph;
    }

    @Override
    public DataModel getDataModel(int index) {
        return dataSets.get(index);
    }

    public String getDescription() {
        try {
            StringBuilder b = new StringBuilder();
            b.append("Load data sets and graphs from a directory.").append("\n\n");
            return b.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> getParameters() {
        return usedParameters;
    }

    @Override
    public int getNumDataModels() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public Parameters getParameterValues() {
        return parametersValues;
    }
}
