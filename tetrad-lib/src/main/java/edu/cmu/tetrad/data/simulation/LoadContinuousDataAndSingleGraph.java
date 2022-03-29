package edu.cmu.tetrad.data.simulation;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.utils.HasParameterValues;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
@Experimental
public class LoadContinuousDataAndSingleGraph implements Simulation, HasParameterValues {
    static final long serialVersionUID = 23L;
    private final String path;
    private Graph graph;
    private List<DataSet> dataSets = new ArrayList<>();
    private final List<String> usedParameters = new ArrayList<>();
    private final Parameters parametersValues = new Parameters();

    public LoadContinuousDataAndSingleGraph(String path) {
        this.path = path;
        String structure = new File(path).getName();
        this.parametersValues.set("structure", structure);
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        this.dataSets = new ArrayList<>();

        File dir = new File(this.path + "/data_noise");

        if (dir.exists()) {
            File[] files = dir.listFiles();

            assert files != null;
            for (File file : files) {
                if (!file.getName().endsWith(".txt")) continue;
                System.out.println("Loading data from " + file.getAbsolutePath());
                try {
                    DataSet data = DataUtils.loadContinuousData(file, "//", '\"',
                            "*", true, Delimiter.TAB);
                    this.dataSets.add(data);
                } catch (Exception e) {
                    System.out.println("Couldn't parse " + file.getAbsolutePath());
                }
            }
        }

        File dir2 = new File(this.path + "/graph");

        if (dir2.exists()) {
            File[] files = dir2.listFiles();

            assert files != null;
            if (files.length != 1) {
                throw new IllegalArgumentException("Expecting exactly one graph file.");
            }

            File file = files[0];

            System.out.println("Loading graph from " + file.getAbsolutePath());
            this.graph = GraphUtils.loadGraphTxt(file);

            GraphUtils.circleLayout(this.graph, 225, 200, 150);
        }

        if (parameters.get(Params.NUM_RUNS) != null) {
            parameters.set(Params.NUM_RUNS, parameters.get(Params.NUM_RUNS));
        } else {
            parameters.set(Params.NUM_RUNS, this.dataSets.size());
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
            return "Load data sets and graphs from a directory." + "\n\n";
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
