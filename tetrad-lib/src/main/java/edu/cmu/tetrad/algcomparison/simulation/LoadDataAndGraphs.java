package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.ContinuousTabularDataset;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.MixedTabularDataset;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class LoadDataAndGraphs implements Simulation {
    static final long serialVersionUID = 23L;
    private String path;
    private List<Graph> graphs = new ArrayList<>();
    private List<DataSet> dataSets = new ArrayList<>();
    private List<String> usedParameters = new ArrayList<>();
    private String description = "";

    public LoadDataAndGraphs(String path) {
        this.path = path;
    }

    @Override
    public void createData(Parameters parameters) {
        this.dataSets = new ArrayList<>();

        if (new File(path + "/data").exists()) {
            int numDataSets = new File(path + "/data").listFiles().length;

            try {
                for (int i = 0; i < numDataSets; i++) {
                    try {
                        File file2 = new File(path + "/graph/graph." + (i + 1) + ".txt");
                        System.out.println("Loading graph from " + file2.getAbsolutePath());
                        this.graphs.add(GraphUtils.loadGraphTxt(file2));
                    } catch (Exception e) {
                        this.graphs.add(null);
                    }

                    GraphUtils.circleLayout(this.graphs.get(i), 225, 200, 150);

                    File file1 = new File(path + "/data/data." + (i + 1) + ".txt");

                    System.out.println("Loading data from " + file1.getAbsolutePath());

                    DataReader dataReader = new DataReader() ;
                    dataReader.setVariablesSupplied(true);
                    dataReader.setDelimiter(DelimiterType.TAB);
                    dataReader.setMaxIntegralDiscrete(parameters.getInt("maxDistinctValuesDiscrete"));

                    // Header in first row or not

                    // Set comment marker
                    dataReader.setCommentMarker("//");

                    dataReader.setMissingValueMarker("*");

                    DataSet ds = dataReader.parseTabular(file1);

//                    DataReader reader = new DataReader();
//                    reader.setVariablesSupplied(true);
//                    reader.setMaxIntegralDiscrete(parameters.getInt("maxDistinctValuesDiscrete"));
//                    ContinuousTabularDataset dataset = (ContinuousTabularDataset) dataReader.readInData();
//                    DoubleDataBox box = new DoubleDataBox(dataset.getData());
//                    List<Node> variables = new ArrayList<>();
//                    for (String s : dataset.getVariables()) variables.add(new ContinuousVariable(s));
//                    BoxDataSet _dataSet = new BoxDataSet(box, variables);
                    dataSets.add(ds);
                }

                File file = new File(path, "parameters.txt");
                BufferedReader r = new BufferedReader(new FileReader(file));

                String line;

                line = r.readLine();

                if (line != null) this.description = line;

                while ((line = r.readLine()) != null) {
                    if (line.contains(" = ")) {
                        String[] tokens = line.split(" = ");
                        String key = tokens[0];
                        String value = tokens[1].trim();

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
        return "Load data sets and graphs from a directory" + (!("".equals(description)) ? ": " + description : "");

//        try {
//            File file = new File(path, "parameters.txt");
//            BufferedReader r = new BufferedReader(new FileReader(file));
//
//            StringBuilder b = new StringBuilder();
//            b.append("Load data sets and graphs from a directory.").append("\n\n");
//            String line;
//
//            while ((line = r.readLine()) != null) {
//                if (line.trim().isEmpty()) continue;
//                b.append(line).append("\n");
//            }
//
//            return b.toString();
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
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
        boolean continuous = false;
        boolean discrete = false;
        boolean mixed = false;

        for (DataSet dataSet : dataSets) {
            if (dataSet.isContinuous()) continuous = true;
            if (dataSet.isDiscrete()) discrete = true;
            if (dataSet.isMixed()) mixed = true;
        }

        if (mixed) return DataType.Mixed;
        else if (continuous && discrete) return DataType.Mixed;
        else if (continuous) return DataType.Continuous;
        else if (discrete) return DataType.Discrete;

        return DataType.Mixed;
    }
}
