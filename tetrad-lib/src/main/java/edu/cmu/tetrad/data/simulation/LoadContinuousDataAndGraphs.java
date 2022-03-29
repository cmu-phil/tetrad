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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class LoadContinuousDataAndGraphs implements Simulation {
    static final long serialVersionUID = 23L;
    private final String path;
    private final List<Graph> graphs = new ArrayList<>();
    private List<DataSet> dataSets = new ArrayList<>();
    private final List<String> usedParameters = new ArrayList<>();

    public LoadContinuousDataAndGraphs(final String path) {
        this.path = path;
    }

    @Override
    public void createData(final Parameters parameters, final boolean newModel) {
//        if (!newModel && !dataSets.isEmpty()) return;

        this.dataSets = new ArrayList<>();

        if (new File(this.path + "/data").exists()) {
            final int numDataSets = new File(this.path + "/data").listFiles().length;

            try {
                for (int i = 0; i < numDataSets; i++) {
                    final File file2 = new File(this.path + "/graph/graph." + (i + 1) + ".txt");
                    System.out.println("Loading graph from " + file2.getAbsolutePath());
                    this.graphs.add(GraphUtils.loadGraphTxt(file2));

                    edu.cmu.tetrad.graph.GraphUtils.circleLayout(this.graphs.get(i), 225, 200, 150);

                    final File file1 = new File(this.path + "/data/data." + (i + 1) + ".txt");

                    System.out.println("Loading data from " + file1.getAbsolutePath());
                    final DataSet data = DataUtils.loadContinuousData(file1, "//", '\"' ,
                            "*", true, Delimiter.TAB);
                    this.dataSets.add(data);
                }

                final File paramFile = new File(this.path, "parameters.txt");
                System.out.println("Loading parameters from " + paramFile.getAbsolutePath());
                final BufferedReader r = new BufferedReader(new FileReader(paramFile));

                String line;

                while ((line = r.readLine()) != null) {
                    if (line.contains(" = ")) {
                        final String[] tokens = line.split(" = ");
                        final String key = tokens[0];
                        final String value = tokens[1];

                        this.usedParameters.add(key);
                        try {
                            final double _value = Double.parseDouble(value);
                            parameters.set(key, _value);
                        } catch (final NumberFormatException e) {
                            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                                parameters.set(key, Boolean.valueOf(value));
                            } else {
                                parameters.set(key, value);
                            }
                        }
                        System.out.println(key + " : " + value);
                    }
                }

                parameters.set(Params.NUM_RUNS, numDataSets);
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Graph getTrueGraph(final int index) {
        return this.graphs.get(index);
    }

    @Override
    public DataModel getDataModel(final int index) {
        return this.dataSets.get(index);
    }

    @Override
    public String getDescription() {
        try {
            final File file = new File(this.path, "parameters.txt");
            final BufferedReader r = new BufferedReader(new FileReader(file));

            final StringBuilder b = new StringBuilder();
            b.append("Load data sets and graphs from a directory.").append("\n\n");
            String line;

            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                b.append(line).append("\n");
            }

            return b.toString();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
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
        return DataType.Continuous;
    }
}
