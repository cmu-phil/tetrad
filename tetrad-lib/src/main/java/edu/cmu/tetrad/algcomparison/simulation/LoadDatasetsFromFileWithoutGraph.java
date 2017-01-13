package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.statistic.utils.SimulationPath;
import edu.cmu.tetrad.algcomparison.utils.ParameterValues;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jdramsey
 */
public class LoadDatasetsFromFileWithoutGraph implements Simulation, SimulationPath, ParameterValues {
    static final long serialVersionUID = 23L;
    private List<DataSet> dataSets = new ArrayList<>();
    private int numDataSets = 1;
    private String path;
    private Map<String, Object> parameterValues = new HashMap<>();

    public LoadDatasetsFromFileWithoutGraph(String path) {
        this.dataSets = null;
        this.path = path;
    }

    @Override
    public void createData(Parameters parameters) {
        File[] files = new File(path).listFiles();

        for (File file : files) {
            try {
                DataReader reader = new DataReader();
                reader.setDelimiter(DelimiterType.TAB);
                reader.setVariablesSupplied(true);
                DataSet dataSet = reader.parseTabular(file);
                dataSets.add(dataSet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public Graph getTrueGraph(int index) {
        return null;
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    @Override
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

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Map<String, Object> paremeterValues() {
        return parameterValues;
    }
}
