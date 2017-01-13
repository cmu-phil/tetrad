package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class LoadContinuousDataAndSingleGraph implements Simulation {
    static final long serialVersionUID = 23L;
    private String path;
    private Graph graph = null;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<String> usedParameters = new ArrayList<>();

    public LoadContinuousDataAndSingleGraph(String path) {
        this.path = path;
    }

    @Override
    public void createData(Parameters parameters) {
        this.dataSets = new ArrayList<>();

        File dir = new File(path + "/data");

        if (dir.exists()) {
            try {
                File[] files = dir.listFiles();

                for (File file : files) {
                    System.out.println("Loading data from " + file.getAbsolutePath());
                    DataReader reader = new DataReader();
                    reader.setVariablesSupplied(true);
                    try {
                        DataSet dataSet = reader.parseTabular(file);
                        dataSets.add(dataSet);
                    } catch (Exception e) {
                        System.out.println("Couldn't parse " + file.getAbsolutePath());
                    }
                }

                File file2 = new File(path + "/graph/graph.txt");
                System.out.println("Loading graph from " + file2.getAbsolutePath());
                this.graph = GraphUtils.loadGraphTxt(file2);
                GraphUtils.circleLayout(this.graph, 225, 200, 150);

                File file = new File(path, "parameters.txt");
                BufferedReader r = new BufferedReader(new FileReader(file));

                String line;

                while ((line = r.readLine()) != null) {
                    if (line.contains(" = ")) {
                        String[] tokens = line.split(" = ");
                        String key = tokens[0];
                        String value = tokens[1];

                        try {
                            double _value = Double.parseDouble(value);
                            usedParameters.add(key);
                            parameters.set(key, _value);
                        } catch (NumberFormatException e) {
                            usedParameters.add(key);
                            parameters.set(key, value);
                        }
                    }
                }

                if (parameters.get("numRandomSelections") != null) {
                    parameters.set("numRuns", parameters.get("numRandomSelections"));
                } else {
                    parameters.set("numRuns", dataSets.size());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graph;
    }

    @Override
    public DataSet getDataSet(int index) {
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
    public int getNumDataSets() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }
}
