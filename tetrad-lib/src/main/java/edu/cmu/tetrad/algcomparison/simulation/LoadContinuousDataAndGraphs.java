package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * @author jdramsey
 */
public class LoadContinuousDataAndGraphs implements Simulation {
    static final long serialVersionUID = 23L;
    private String path;
    private List<Graph> graphs = new ArrayList<>();
    private List<DataSet> dataSets = new ArrayList<>();
    private List<String> usedParameters = new ArrayList<>();

    public LoadContinuousDataAndGraphs(String path) {
        this.path = path;
    }

    @Override
    public void createData(Parameters parameters) {
        this.dataSets = new ArrayList<>();

        if (new File(path + "/data").exists()) {
            int numDataSets = new File(path + "/data").listFiles().length;

            try {
                for (int i = 0; i < numDataSets; i++) {
                    File file2 = new File(path + "/graph/graph." + (i + 1) + ".txt");
                    System.out.println("Loading graph from " + file2.getAbsolutePath());
                    this.graphs.add(GraphUtils.loadGraphTxt(file2));

                    edu.cmu.tetrad.graph.GraphUtils.circleLayout(this.graphs.get(i), 225, 200, 150);

                    File file1 = new File(path + "/data/data." + (i + 1) + ".txt");

                    System.out.println("Loading data from " + file1.getAbsolutePath());
                    DataReader reader = new DataReader();
                    reader.setVariablesSupplied(true);
                    dataSets.add(reader.parseTabular(file1));
                }

                File paramFile = new File(path, "parameters.txt");
                System.out.println("Loading parameters from " + paramFile.getAbsolutePath());
                BufferedReader r = new BufferedReader(new FileReader(paramFile));

                String line;

                while ((line = r.readLine()) != null) {
                    if (line.contains(" = ")) {
                        String[] tokens = line.split(" = ");
                        String key = tokens[0];
                        String value = tokens[1];

                        usedParameters.add(key);
                        try {
                            double _value = Double.parseDouble(value);
                            parameters.set(key, _value);
                        } catch (NumberFormatException e) {
                        	if(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")){
                                parameters.set(key, Boolean.valueOf(value));
                        	}else{
                                parameters.set(key, value);
                        	}
                        }
                        System.out.println(key + " : " + value);
                    }
                }

                parameters.set("numRuns", numDataSets);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Graph getTrueGraph(int index) {
        return graphs.get(index);
    }

    @Override
    public DataModel getDataModel(int index) {
        return dataSets.get(index);
    }

    @Override
    public String getDescription() {
        try {
            File file = new File(path, "parameters.txt");
            BufferedReader r = new BufferedReader(new FileReader(file));

            StringBuilder b = new StringBuilder();
            b.append("Load data sets and graphs from a directory.").append("\n\n");
            String line;

            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                b.append(line).append("\n");
            }

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
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }
}
