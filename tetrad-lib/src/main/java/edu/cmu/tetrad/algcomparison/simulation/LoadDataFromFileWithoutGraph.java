package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.algcomparison.statistic.utilities.SimulationPath;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class LoadDataFromFileWithoutGraph implements Simulation, SimulationPath {
    private DataSet dataSet;
    private int numDataSets = 1;
    private String path;

    public LoadDataFromFileWithoutGraph(String path) {
        this.dataSet = null;
        this.path = path;

        try {
            File file = new File(path);
            System.out.println("Loading data from " + file.getAbsolutePath());
            DataReader reader = new DataReader();
            reader.setVariablesSupplied(false);
            this.dataSet = reader.parseTabular(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void simulate(Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    public Graph getTrueGraph() {
        return null;
    }

    public DataSet getDataSet(int index) {
        return dataSet;
    }

    public String getDescription() {
        return "Load single file to run.";
    }

    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }

    @Override
    public int getNumDataSets() {
        return numDataSets;
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    public String getPath() {
        return path;
    }
}
