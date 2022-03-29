package edu.cmu.tetrad.data.simulation;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author jdramsey
 */
public class LoadDataAndGraphs implements Simulation {

    static final long serialVersionUID = 23L;
    private final String path;
    private final List<Graph> graphs = new ArrayList<>();
    private List<DataSet> dataSets = new ArrayList<>();
    private final List<String> usedParameters = new ArrayList<>();
    private String description = "";

    private transient PrintStream stdout = System.out;

    public LoadDataAndGraphs(String path) {
        this.path = path;
    }

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
                        this.graphs.add(GraphUtils.loadGraphTxt(file2));
                    } catch (Exception e) {
                        this.graphs.add(null);
                    }

                    GraphUtils.circleLayout(this.graphs.get(i), 225, 200, 150);

                    File file1 = new File(path + "/data/data." + (i + 1) + ".txt");

                    this.stdout.println("Loading data from " + file1.getAbsolutePath());

                    DataSet ds = DataUtils.loadContinuousData(file1, "//", '\"',
                            "*", true, Delimiter.TAB);

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

    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Load data sets and graphs from a directory" + (!("".equals(this.description)) ? ": " + this.description : "");

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
        return this.usedParameters;
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

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

    public void setStdout(PrintStream stdout) {
        this.stdout = stdout;
    }

}
