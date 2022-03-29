package edu.cmu.tetrad.data.simulation;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.utils.HasParameterValues;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
@Experimental
public class LoadContinuousDataSmithSim implements Simulation, HasParameterValues {
    static final long serialVersionUID = 23L;
    private final String path;
    private Graph graph;
    private List<DataSet> dataSets = new ArrayList<>();
    private final List<String> usedParameters = new ArrayList<>();
    private final Parameters parametersValues = new Parameters();

    public LoadContinuousDataSmithSim(final String path) {
        this.path = path;
        final String structure = new File(path).getName();
        this.parametersValues.set("structure", structure);
    }

    @Override
    public void createData(final Parameters parameters, final boolean newModel) {
//        if (!newModel && !dataSets.isEmpty()) return;
        if (!this.dataSets.isEmpty()) return;

        this.dataSets = new ArrayList<>();

        final File dir = new File(this.path + "/data");

        if (dir.exists()) {
            final File[] files = dir.listFiles();

            for (final File file : files) {
                if (!file.getName().endsWith(".txt")) continue;
                System.out.println("Loading data from " + file.getAbsolutePath());
                try {
                    final DataSet dataSet = DataUtils.loadContinuousData(file, "//", '\"',
                            "*", true, Delimiter.TAB);
                    this.dataSets.add(dataSet);
                } catch (final Exception e) {
                    System.out.println("Couldn't parse " + file.getAbsolutePath());
                }
            }
        }

        final File dir2 = new File(this.path + "/graph");

        if (dir2.exists()) {
            final File[] files = dir2.listFiles();

            for (final File file : files) {
                if (!file.getName().endsWith(".txt")) continue;

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

        if (parameters.get(Params.NUM_RUNS) != null) {
            parameters.set(Params.NUM_RUNS, parameters.get(Params.NUM_RUNS));
        } else {
            parameters.set(Params.NUM_RUNS, this.dataSets.size());
        }

        System.out.println();
    }

    @Override
    public Graph getTrueGraph(final int index) {
        return this.graph;
    }

    @Override
    public DataModel getDataModel(final int index) {
        return this.dataSets.get(index);
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


    public Graph readGraph(final File file) {
        try {
            final DataSet data = DataUtils.loadContinuousData(file, "//", '\"',
                    "*", true, Delimiter.TAB);
            final List<Node> variables = data.getVariables();
            final Graph graph = new EdgeListGraph(variables);

            for (int i = 0; i < variables.size(); i++) {
                for (int j = 0; j < variables.size(); j++) {
                    if (i == j) continue;

                    if (data.getDouble(i, j) != 0) {
                        graph.addDirectedEdge(variables.get(i), variables.get(j));
                    }
                }
            }

            return graph;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
