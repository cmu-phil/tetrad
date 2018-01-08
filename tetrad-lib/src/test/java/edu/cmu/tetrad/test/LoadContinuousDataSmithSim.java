package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.utils.HasParameterValues;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class LoadContinuousDataSmithSim implements Simulation, HasParameterValues {
    static final long serialVersionUID = 23L;
    private final int index;
    private String path;
    private Graph graph = null;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<String> usedParameters = new ArrayList<>();
    private Parameters parametersValues = new Parameters();

    public LoadContinuousDataSmithSim(String path, int index) {
        this.path = path;
        this.index = index;
        String structure = new File(path).getName();
        parametersValues.set("Structure", structure + " " + index);
    }

    @Override
    public void createData(Parameters parameters) {
        this.dataSets = new ArrayList<>();

        File dir2 = new File(path + "/models");

        if (dir2.exists()) {
            File[] files = dir2.listFiles();

            for (File file : files) {
                if (!file.getName().endsWith(".txt")) continue;
                if (!file.getName().contains("sim" + index + ".")) continue;

                System.out.println("Loading graph from " + file.getAbsolutePath());
                this.graph = readGraph(file);
//            this.graph = GraphUtils.loadGraphTxt(file);

//            if (!graph.isAdjacentTo(graph.getNode("X3"), graph.getNode("X4"))) {
//                graph.addUndirectedEdge(graph.getNode("X3"), graph.getNode("X4"));
//            }

                GraphUtils.circleLayout(this.graph, 225, 200, 150);

                break;
            }
        }

        File dir = new File(path + "/data");

        if (dir.exists()) {
            File[] files = dir.listFiles();

            for (File file : files) {
                if (!file.getName().endsWith(".txt")) continue;
                if (!file.getName().contains("sim" + index + ".")) continue;
                System.out.println("Loading data from " + file.getAbsolutePath());
                try {
                    DataReader reader = new DataReader();
//                    reader.setVariablesSupplied(false);
//                    reader.setDelimiter(DelimiterType.COMMA);
                    DataSet dataSet;// = reader.parseTabular(file);

//                    if (dataSet.getVariable().size() == 1) {
                    DataReader reader2 = new DataReader();
                    reader2.setVariablesSupplied(false);
                    reader2.setDelimiter(DelimiterType.WHITESPACE);
                    reader2.setDelimiter(DelimiterType.COMMA);
                    dataSet = reader2.parseTabular(file);
//                    }

                    if (dataSet.getVariables().size() > graph.getNumNodes()) {
                        List<Node> nodes = new ArrayList<>();
                        for (int i = 0; i < graph.getNumNodes(); i++) nodes.add(dataSet.getVariable(i));
                        dataSet = dataSet.subsetColumns(nodes);
                    }

                    dataSets.add(dataSet);
                } catch (Exception e) {
                    System.out.println("Couldn't parse " + file.getAbsolutePath());
                    e.printStackTrace();
                }
            }
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
            b.append("Smith sim " + index).append("\n\n");
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


    public Graph readGraph(File file) {
        try {
            DataReader reader = new DataReader();
            reader.setVariablesSupplied(false);
            reader.setDelimiter(DelimiterType.COMMA);

            DataSet data = reader.parseTabular(file);
            List<Node> variables = data.getVariables();

            List<Node> _variables = new ArrayList<>();
            for (int i = 0; i < variables.size(); i++) {
                _variables.add(new ContinuousVariable(variables.get(i).getName()));
            }

            Graph graph = new EdgeListGraph(_variables);

            for (int i = 0; i < _variables.size(); i++) {
                for (int j = 0; j < _variables.size(); j++) {
                    if (i == j) continue;

                    if (data.getDouble(i, j) != 0) {
                        graph.addDirectedEdge(_variables.get(i), _variables.get(j));
                    }
                }
            }

            return graph;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}