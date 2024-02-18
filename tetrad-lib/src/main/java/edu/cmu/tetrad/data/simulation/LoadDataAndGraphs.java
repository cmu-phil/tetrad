package edu.cmu.tetrad.data.simulation;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Load data sets and graphs from a directory.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LoadDataAndGraphs implements Simulation {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The path.
     */
    private final String path;

    /**
     * The graphs.
     */
    private final List<Graph> graphs = new ArrayList<>();

    /**
     * The used parameters.
     */
    private final List<String> usedParameters = new ArrayList<>();

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The description.
     */
    private String description = "";

    /**
     * The stdout.
     */
    private transient PrintStream stdout = System.out;

    /**
     * <p>Constructor for LoadDataAndGraphs.</p>
     *
     * @param path a {@link java.lang.String} object
     */
    public LoadDataAndGraphs(String path) {
        this.path = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
//        if (!newModel && !dataSets.isEmpty()) return;
        if (!this.dataSets.isEmpty()) return;

        this.dataSets = new ArrayList<>();

        File path = new File(this.path);

        if (path.exists()) {
            int numDataSets = Objects.requireNonNull(new File(path, "/data").listFiles()).length;

            try {
                for (int i = 0; i < numDataSets; i++) {
                    try {
                        File file2 = new File(path + "/graph/graph." + (i + 1) + ".txt");
                        this.stdout.println("Loading graph from " + file2.getAbsolutePath());
                        this.graphs.add(GraphSaveLoadUtils.loadGraphTxt(file2));
                    } catch (Exception e) {
                        this.graphs.add(null);
                    }

                    LayoutUtil.defaultLayout(this.graphs.get(i));

                    File file1 = new File(path + "/data/data." + (i + 1) + ".txt");

                    this.stdout.println("Loading data from " + file1.getAbsolutePath());

                    DataSet ds = SimpleDataLoader.loadContinuousData(file1, "//", '\"',
                            "*", true, Delimiter.TAB, false);

                    this.dataSets.add(ds);
                }

                File file = new File(path, "parameters.txt");
                BufferedReader r = new BufferedReader(new FileReader(file));

                String line;

                line = r.readLine();

                if (line != null) {
                    this.description = line;
                }

                while ((line = r.readLine()) != null) {
                    if (line.contains(" = ")) {
                        String[] tokens = line.split(" = ");
                        String key = tokens[0];
                        String value = tokens[1].trim();

                        this.usedParameters.add(key);
                        try {
                            double _value = Double.parseDouble(value);
                            parameters.set(key, _value);
                        } catch (NumberFormatException e) {
                            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                                parameters.set(key, Boolean.valueOf(value));
                            } else {
                                parameters.set(key, value);
                            }
                        }
                    }
                }

                parameters.set(Params.NUM_RUNS, numDataSets);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Load data sets and graphs from a directory" + (!("".equals(this.description)) ? ": " + this.description : "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return this.usedParameters;
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
        boolean continuous = false;
        boolean discrete = false;
        boolean mixed = false;

        for (DataSet dataSet : this.dataSets) {
            if (dataSet.isContinuous()) {
                continuous = true;
            }
            if (dataSet.isDiscrete()) {
                discrete = true;
            }
            if (dataSet.isMixed()) {
                mixed = true;
            }
        }

        if (mixed) {
            return DataType.Mixed;
        } else if (continuous && discrete) {
            return DataType.Mixed;
        } else if (continuous) {
            return DataType.Continuous;
        } else if (discrete) {
            return DataType.Discrete;
        }

        return DataType.Mixed;
    }

    /**
     * <p>Setter for the field <code>stdout</code>.</p>
     *
     * @param stdout a {@link java.io.PrintStream} object
     */
    public void setStdout(PrintStream stdout) {
        this.stdout = stdout;
    }

}
