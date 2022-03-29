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
    private final String path;
    private final String prefix;
    private Graph graph;
    private List<ICovarianceMatrix> covs = new ArrayList<>();
    private final List<String> usedParameters = new ArrayList<>();
    private final Parameters parametersValues = new Parameters();

    public LoadContinuousDataAndSingleGraphKun(final String path, final String prefix) {
        this.path = path;
        this.prefix = prefix;
    }

    @Override
    public void createData(final Parameters parameters, final boolean newModel) {
        this.covs = new ArrayList<>();

        final File dir = new File(this.path);

        if (dir.exists()) {
            for (int i = 1; i <= 20; i++) {
                final File f = new File(this.path, this.prefix + i + ".txt");
                try {
                    this.covs.add(DataUtils.parseCovariance(f, "//", DelimiterType.WHITESPACE, '\"', "*"));
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }

        final File graphFile = new File("/Users/user/Downloads//graph/graph1.txt");
        this.graph = GraphUtils.loadGraphTxt(graphFile);

    }

    @Override
    public Graph getTrueGraph(final int index) {
        return this.graph;
    }

    @Override
    public DataModel getDataModel(final int index) {
        return this.covs.get(index);
    }

    public String getDescription() {
        try {
            final StringBuilder b = new StringBuilder();
            b.append("Load data sets and graphs from a directory.").append("\n\n");
            return b.toString();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> getParameters() {
        return this.usedParameters;
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
        return this.parametersValues;
    }
}
