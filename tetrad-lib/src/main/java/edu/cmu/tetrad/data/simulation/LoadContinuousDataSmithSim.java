package edu.cmu.tetrad.data.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.utils.HasParameterValues;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Load data sets and graphs from a directory.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@Experimental
public class LoadContinuousDataSmithSim implements Simulation, HasParameterValues {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The path.
     */
    private final String path;

    /**
     * The used parameters.
     */
    private final List<String> usedParameters = new ArrayList<>();

    /**
     * The parameters values.
     */
    private final Parameters parametersValues = new Parameters();

    /**
     * The graph.
     */
    private Graph graph;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * <p>Constructor for LoadContinuousDataSmithSim.</p>
     *
     * @param path a {@link java.lang.String} object
     */
    public LoadContinuousDataSmithSim(String path) {
        this.path = path;
        String structure = new File(path).getName();
        this.parametersValues.set("structure", structure);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (!this.dataSets.isEmpty()) return;

        this.dataSets = new ArrayList<>();

        File dir = new File(this.path + "/data");

        if (dir.exists()) {
            File[] files = dir.listFiles();

            assert files != null;
            for (File file : files) {
                if (!file.getName().endsWith(".txt")) continue;
                System.out.println("Loading data from " + file.getAbsolutePath());
                try {
                    DataSet dataSet = SimpleDataLoader.loadContinuousData(file, "//", '\"',
                            "*", true, Delimiter.TAB, false);
                    this.dataSets.add(dataSet);
                } catch (Exception e) {
                    System.out.println("Couldn't parse " + file.getAbsolutePath());
                }
            }
        }

        File dir2 = new File(this.path + "/graph");

        if (dir2.exists()) {
            File[] files = dir2.listFiles();

            assert files != null;
            for (File file : files) {
                if (!file.getName().endsWith(".txt")) continue;

                System.out.println("Loading graph from " + file.getAbsolutePath());
                this.graph = readGraph(file);

                LayoutUtil.defaultLayout(this.graph);

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

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * <p>getDescription.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDescription() {
        try {
            return "Load data sets and graphs from a directory." + "\n\n";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return this.usedParameters;
    }

    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return null;
    }

    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Parameters getParameterValues() {
        return this.parametersValues;
    }


    /**
     * <p>readGraph.</p>
     *
     * @param file a {@link java.io.File} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph readGraph(File file) {
        try {
            DataSet data = SimpleDataLoader.loadContinuousData(file, "//", '\"',
                    "*", true, Delimiter.TAB, false);
            List<Node> variables = data.getVariables();
            Graph graph = new EdgeListGraph(variables);

            for (int i = 0; i < variables.size(); i++) {
                for (int j = 0; j < variables.size(); j++) {
                    if (i == j) continue;

                    if (data.getDouble(i, j) != 0) {
                        graph.addDirectedEdge(variables.get(i), variables.get(j));
                    }
                }
            }

            return graph;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
