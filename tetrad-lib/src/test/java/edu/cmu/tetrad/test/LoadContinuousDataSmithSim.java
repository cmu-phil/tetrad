package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.utils.HasParameterValues;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.Delimiter;

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
    private final String path;
    private Graph graph;
    private List<DataSet> dataSets = new ArrayList<>();
    private final List<String> usedParameters = new ArrayList<>();
    private final Parameters parametersValues = new Parameters();

    public LoadContinuousDataSmithSim(String path, int index) {
        this.path = path;
        this.index = index;
        String structure = new File(path).getName();
        this.parametersValues.set("Structure", structure + " " + index);
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (!this.dataSets.isEmpty()) return;
        this.dataSets = new ArrayList<>();

        File dir2 = new File(this.path + "/models");

        if (dir2.exists()) {
            File[] files = dir2.listFiles();

            for (File file : files) {
                if (!file.getName().endsWith(".txt")) continue;
                if (!file.getName().contains("sim" + this.index + ".")) continue;

                System.out.println("Loading graph from " + file.getAbsolutePath());
                this.graph = readGraph(file);

                GraphUtils.circleLayout(this.graph, 225, 200, 150);

                break;
            }
        }

        File dir = new File(this.path + "/data");

        if (dir.exists()) {
            File[] files = dir.listFiles();

            for (File file : files) {
                if (!file.getName().endsWith(".txt")) continue;
                if (!file.getName().contains("sim" + this.index + ".")) continue;
                System.out.println("Loading data from " + file.getAbsolutePath());
                try {
                    DataSet dataSet = DataUtils.loadContinuousData(file, "//", '\"',
                            "*", true, Delimiter.TAB);

                    if (dataSet.getVariables().size() > this.graph.getNumNodes()) {
                        List<Node> nodes = new ArrayList<>();
                        for (int i = 0; i < this.graph.getNumNodes(); i++) nodes.add(dataSet.getVariable(i));
                        dataSet = dataSet.subsetColumns(nodes);
                    }

                    this.dataSets.add(dataSet);
                } catch (Exception e) {
                    System.out.println("Couldn't parse " + file.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        }


        if (parameters.get("numRuns") != null) {
            parameters.set("numRuns", parameters.get("numRuns"));
        } else {
            parameters.set("numRuns", this.dataSets.size());
        }

        System.out.println();
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graph;
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    public String getDescription() {
        try {
            StringBuilder b = new StringBuilder();
            b.append("Smith sim " + this.index).append("\n\n");
            return b.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> getParameters() {
        return this.usedParameters;
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public Parameters getParameterValues() {
        return this.parametersValues;
    }


    public Graph readGraph(File file) {
        try {
            DataSet data = DataUtils.loadContinuousData(file, "//", '\"',
                    "*", true, Delimiter.TAB);
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