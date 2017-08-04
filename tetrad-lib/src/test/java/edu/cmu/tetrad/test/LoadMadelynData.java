package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
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
public class LoadMadelynData implements Simulation, HasParameterValues {
    static final long serialVersionUID = 23L;
    private String directory;
    private String suffix;
    private int structure;
    private Graph graph = null;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<String> usedParameters = new ArrayList<>();
    private Parameters parametersValues = new Parameters();

    public LoadMadelynData(String directory, String suffix, int structure) {
        this.directory = directory;
        this.suffix = suffix;
        this.structure = structure;
    }

    @Override
    public void createData(Parameters parameters) {
        this.dataSets = new ArrayList<>();

        for (int run = 1; run <= 10; run++) {
            File file = new File(directory + "/structure_" + structure + "_coeff" + run + "_" + suffix + ".txt");

            System.out.println("Loading data from " + file.getAbsolutePath());
            DataReader reader = new DataReader();
            reader.setVariablesSupplied(true);

            try {
                DataSet dataSet = reader.parseTabular(file);
                dataSets.add(dataSet);

                if (!(dataSet.isContinuous())) {
                    throw new IllegalArgumentException("Not a continuous data set: " + dataSet.getName());
                }
            } catch (Exception e) {
                System.out.println("Couldn't parse " + file.getAbsolutePath());
            }
        }

        File parent = new File(new File(directory).getParent());

        File file2 = new File(parent + "/structure_" + structure + "_graph.txt");
        System.out.println("Loading graph from " + file2.getAbsolutePath());
        this.graph = GraphUtils.loadGraphTxt(file2);
        GraphUtils.circleLayout(this.graph, 225, 200, 150);

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
