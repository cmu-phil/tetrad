package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.utils.HasParameterValues;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author josephramsey
 */
public class LoadMadelynData implements Simulation, HasParameterValues {
    static final long serialVersionUID = 23L;
    private final String directory;
    private final String suffix;
    private final int structure;
    private final List<String> usedParameters = new ArrayList<>();
    private final Parameters parametersValues = new Parameters();
    private Graph graph;
    private List<DataSet> dataSets = new ArrayList<>();

    public LoadMadelynData(String directory, String suffix, int structure) {
        this.directory = directory;
        this.suffix = suffix;
        this.structure = structure;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        this.dataSets = new ArrayList<>();

        for (int run = 1; run <= 10; run++) {
            File file = new File(this.directory + "/structure_" + this.structure + "_coeff" + run + "_" + this.suffix + ".txt");

            System.out.println("Loading data from " + file.getAbsolutePath());

            try {
                DataSet dataSet = SimpleDataLoader.loadContinuousData(file, "//", '\"',
                        "*", true, Delimiter.TAB);
                this.dataSets.add(dataSet);

                if (!(dataSet.isContinuous())) {
                    throw new IllegalArgumentException("Not a continuous data set: " + dataSet.getName());
                }
            } catch (Exception e) {
                System.out.println("Couldn't parse " + file.getAbsolutePath());
            }
        }

        File parent = new File(new File(this.directory).getParent());

        File file2 = new File(parent + "/structure_" + this.structure + "_graph.txt");
        System.out.println("Loading graph from " + file2.getAbsolutePath());
        this.graph = GraphSaveLoadUtils.loadGraphTxt(file2);
        LayoutUtil.circleLayout(this.graph, 225, 200, 150);

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
            b.append("Load data sets and graphs from a directory.").append("\n\n");
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
}
