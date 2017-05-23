package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.utils.HasParameterValues;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class LoadContinuousDataAndSingleGraphKun implements Simulation, HasParameterValues {
    static final long serialVersionUID = 23L;
    private String path;
    private String prefix;
    private Graph graph = null;
    private List<ICovarianceMatrix> covs = new ArrayList<>();
    private List<String> usedParameters = new ArrayList<>();
    private Parameters parametersValues = new Parameters();

    public LoadContinuousDataAndSingleGraphKun(String path, String prefix) {
        this.path = path;
        this.prefix = prefix;
    }

    @Override
    public void createData(Parameters parameters) {
        this.covs = new ArrayList<>();

        File dir = new File(path);

        if (dir.exists()) {
            for (int i = 1; i <= 20; i++) {
                File f = new File(path, prefix + i + ".txt");
                DataReader reader = new DataReader();
                try {
                    covs.add(reader.parseCovariance(f));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        File graphFile = new File("/Users/user/Downloads//graph/graph1.txt");
        this.graph = GraphUtils.loadGraphTxt(graphFile);

    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graph;
    }

    @Override
    public DataModel getDataModel(int index) {
        return covs.get(index);
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
        return 20;
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
