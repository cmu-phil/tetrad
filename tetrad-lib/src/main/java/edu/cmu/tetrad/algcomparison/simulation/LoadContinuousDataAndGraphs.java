package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class LoadContinuousDataAndGraphs implements Simulation {
    private String path;
    private List<DataSet> dataSets;
    private List<Graph> graphs;

    public LoadContinuousDataAndGraphs(String filesPath, Parameters parameters) {
        this.path = filesPath;
        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        if (new File(filesPath + "/data").exists()) {
            int numDataSets = new File(filesPath + "/data").listFiles().length;

            try {
                for (int i = 0; i < numDataSets; i++) {
                    File file1 = new File(filesPath + "/data/data." + (i + 1));

                    System.out.println("Loading data from " + file1.getAbsolutePath());
                    DataReader reader = new DataReader();
                    reader.setVariablesSupplied(true);
                    dataSets.add(reader.parseTabular(file1));

                    File file2 = new File(filesPath + "/graph/graph." + (i + 1));
                    System.out.println("Loading graph from " + file2.getAbsolutePath());
                    Graph graph = GraphUtils.loadGraphTxt(file2);
                    graphs.add(graph);
                }

                File file = new File(path, "parameters.txt");
                BufferedReader r = new BufferedReader(new FileReader(file));

                String line;

                while ((line = r.readLine()) != null) {
                    if (line.contains(" = ")) {
                        String[] tokens = line.split(" = ");
                        String key = tokens[0];
                        String value = tokens[1];
                        parameters.put(key, Double.parseDouble(value));
                    }
                }

                parameters.put("numRuns", numDataSets);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Graph getTrueGraph(int index) {
        return graphs.get(index);
    }

    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    public String getDescription() {
        try {
            File file = new File(path, "parameters.txt");
            BufferedReader r = new BufferedReader(new FileReader(file));

            StringBuilder b = new StringBuilder();
            b.append("Load data sets and graphs from a directory.").append("\n");
            String line;

            while ((line = r.readLine()) != null) {
                b.append(line).append("\n");
            }

            return b.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
