package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.algcomparison.SimulationPath;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

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
    }

    public Graph getDag() {
        return null;
    }

    public DataSet getDataSet(int i, Map<String, Number> parameters) {
        try {
            File file = new File(path);
            System.out.println("Loading data from " + file.getAbsolutePath());
            DataReader reader = new DataReader();
            reader.setVariablesSupplied(false);
            this.dataSet = reader.parseTabular(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return dataSet;
    }

    public String toString() {
        return "Load single file to run.";
    }

    public boolean isMixed() {
        return false;
    }

    @Override
    public int getNumDataSets() {
        return numDataSets;
    }

    public String getPath() {
        return path;
    }
}
