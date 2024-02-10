package edu.cmu.tetrad.data.simulation;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.statistic.utils.SimulationPath;
import edu.cmu.tetrad.algcomparison.utils.ParameterValues;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Load data sets and graphs from a directory.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LoadDataFromFileWithoutGraph implements Simulation, SimulationPath, ParameterValues {
    @Serial
    private static final long serialVersionUID = 23L;

    /** The path. */
    private final String path;

    /** The parameter values. */
    private final Map<String, Object> parameterValues = new HashMap<>();

    /** The data set. */
    private DataSet dataSet;

    /**
     * <p>Constructor for LoadDataFromFileWithoutGraph.</p>
     *
     * @param path a {@link java.lang.String} object
     */
    public LoadDataFromFileWithoutGraph(String path) {
        this.dataSet = null;
        this.path = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        try {
            File file = new File(this.path);
            System.out.println("Loading data from " + file.getAbsolutePath());
            this.dataSet = SimpleDataLoader.loadContinuousData(file, "//", '\"',
                    "*", true, Delimiter.TAB, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getTrueGraph(int index) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSet;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Load single file to run.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDataModels() {
        return 1;
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
    public String getPath() {
        return this.path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> parameterValues() {
        return this.parameterValues;
    }
}
