/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.ExternalAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.ElapsedCpuTime;
import edu.cmu.tetrad.algcomparison.statistic.ParameterColumn;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.HasParameterValues;
import edu.cmu.tetrad.algcomparison.utils.HasParameters;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.data.simulation.LoadDataAndGraphs;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.util.FastMath;
import org.reflections.Reflections;

import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Nov 14, 2017 12:00:31 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class TimeoutComparison {

    /**
     * The date format.
     */
    private static final DateFormat DF = new SimpleDateFormat("EEE, MMMM dd, yyyy hh:mm:ss a");
    /**
     * The graph type used.
     */
    private boolean[] graphTypeUsed;
    /**
     * The out.
     */
    private PrintStream out;
    /**
     * Whether to output the tables in tab-delimited format.
     */
    private boolean tabDelimitedTables;
    /**
     * Whether to save the graphs.
     */
    private boolean saveGraphs;
    /**
     * Whether to copy the data (or using the original).
     */
    private boolean copyData;
    /**
     * Whether to show the simulation indices.
     */
    private boolean showSimulationIndices;
    /**
     * Whether to show the algorithm indices.
     */
    private boolean showAlgorithmIndices;
    /**
     * Whether to show the utilities.
     */
    private boolean showUtilities;
    /**
     * Whether to sort by utility.
     */
    private boolean sortByUtility;
    /**
     * The data path.
     */
    private String dataPath;
    /**
     * The results path.
     */
    private String resultsPath;
    /**
     * Whether to parallelize the process.
     */
    private boolean parallelized = true;
    /**
     * Whether to save CPDAGs.
     */
    private boolean saveCPDAGs;
    /**
     * Whether to save PAGs.
     */
    private boolean savePags;
    /**
     * The directories for the saved graphs.
     */
    private ArrayList<String> dirs;
    /**
     * The comparison graph.
     */
    private ComparisonGraph comparisonGraph = ComparisonGraph.true_DAG;

    /**
     * Represents a comparison of two time values for timeout purposes.
     * <p>
     * The TimeoutComparison class can be used to compare two time values and determine if they have exceeded a
     * specified timeout period.
     * </p>
     */
    public TimeoutComparison() {
    }

    /**
     * <p>compareFromFiles.</p>
     *
     * @param filePath   a {@link java.lang.String} object
     * @param algorithms a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithms} object
     * @param statistics a {@link edu.cmu.tetrad.algcomparison.statistic.Statistics} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     * @param timeout    a long
     * @param unit       a {@link java.util.concurrent.TimeUnit} object
     */
    public void compareFromFiles(String filePath, Algorithms algorithms,
                                 Statistics statistics, Parameters parameters, long timeout, TimeUnit unit) {
        compareFromFiles(filePath, filePath, algorithms, statistics, parameters, timeout, unit);
    }

    /**
     * Compares algorithms.
     *
     * @param dataPath    Path to the directory where data and graph files have been saved.
     * @param resultsPath Path to the file where the results should be stored.
     * @param algorithms  The list of algorithms to be compared.
     * @param statistics  The list of statistics on which to compare the algorithm, and their utility weights.
     * @param parameters  The list of parameters and their values.
     * @param timeout     a long
     * @param unit        a {@link java.util.concurrent.TimeUnit} object
     */
    public void compareFromFiles(String dataPath, String resultsPath, Algorithms algorithms,
                                 Statistics statistics, Parameters parameters, long timeout, TimeUnit unit) {
        for (Algorithm algorithm : algorithms.getAlgorithms()) {
            if (algorithm instanceof ExternalAlgorithm) {
                throw new IllegalArgumentException("Not expecting any implementations of ExternalAlgorithm here.");
            }
        }

        this.dataPath = dataPath;
        this.resultsPath = resultsPath;

        Simulations simulations = new Simulations();

        File file = new File(this.dataPath, "save");
        File[] dirs = file.listFiles();

        if (dirs == null) {
            throw new NullPointerException("No files in " + file.getAbsolutePath());
        }

        this.dirs = new ArrayList<>();

        int count = 0;

        for (File dir : dirs) {
            if (dir.getName().contains("DS_Store")) {
                continue;
            }
            count++;
        }

        for (int i = 1; i <= count; i++) {
            File _dir = new File(dataPath, "save/" + i);
            simulations.add(new LoadDataAndGraphs(_dir.getAbsolutePath()));
            this.dirs.add(_dir.getAbsolutePath());
        }

        compareFromSimulations(this.resultsPath, simulations, algorithms, statistics, parameters, timeout, unit);
    }

    /**
     * <p>generateReportFromExternalAlgorithms.</p>
     *
     * @param dataPath    a {@link java.lang.String} object
     * @param resultsPath a {@link java.lang.String} object
     * @param algorithms  a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithms} object
     * @param statistics  a {@link edu.cmu.tetrad.algcomparison.statistic.Statistics} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     * @param timeout     a long
     * @param unit        a {@link java.util.concurrent.TimeUnit} object
     */
    public void generateReportFromExternalAlgorithms(String dataPath, String resultsPath, Algorithms algorithms,
                                                     Statistics statistics, Parameters parameters, long timeout, TimeUnit unit) {
        generateReportFromExternalAlgorithms(dataPath, resultsPath, "Comparison.txt", algorithms,
                statistics, parameters, timeout, unit);
    }

    /**
     * <p>generateReportFromExternalAlgorithms.</p>
     *
     * @param dataPath       a {@link java.lang.String} object
     * @param resultsPath    a {@link java.lang.String} object
     * @param outputFileName a {@link java.lang.String} object
     * @param algorithms     a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithms} object
     * @param statistics     a {@link edu.cmu.tetrad.algcomparison.statistic.Statistics} object
     * @param parameters     a {@link edu.cmu.tetrad.util.Parameters} object
     * @param timeout        a long
     * @param unit           a {@link java.util.concurrent.TimeUnit} object
     */
    public void generateReportFromExternalAlgorithms(String dataPath, String resultsPath, String outputFileName, Algorithms algorithms,
                                                     Statistics statistics, Parameters parameters, long timeout, TimeUnit unit) {

        this.saveGraphs = false;
        this.dataPath = dataPath;
        this.resultsPath = resultsPath;

        for (Algorithm algorithm : algorithms.getAlgorithms()) {
            if (!(algorithm instanceof ExternalAlgorithm)) {
                throw new IllegalArgumentException(
                        "Expecting all algorithms to implement ExternalAlgorithm.");
            }
        }

        Simulations simulations = new Simulations();

        File file = new File(this.dataPath, "save");
        File[] dirs = file.listFiles();

        if (dirs == null) {
            throw new NullPointerException("No files in " + file.getAbsolutePath());
        }

        this.dirs = new ArrayList<>();

        int count = 0;

        for (File dir : dirs) {
            if (dir.getName().contains("DS_Store")) {
                continue;
            }
            count++;
        }

        for (int i = 1; i <= count; i++) {
            File _dir = new File(dataPath, "save/" + i);
            simulations.add(new LoadDataAndGraphs(_dir.getAbsolutePath()));
            this.dirs.add(_dir.getAbsolutePath());
        }

        compareFromSimulations(this.resultsPath, simulations, outputFileName, algorithms, statistics, parameters, timeout, unit);
    }

    /**
     * <p>compareFromSimulations.</p>
     *
     * @param resultsPath a {@link java.lang.String} object
     * @param simulations a {@link edu.cmu.tetrad.algcomparison.simulation.Simulations} object
     * @param algorithms  a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithms} object
     * @param statistics  a {@link edu.cmu.tetrad.algcomparison.statistic.Statistics} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     * @param timeout     a long
     * @param unit        a {@link java.util.concurrent.TimeUnit} object
     */
    public void compareFromSimulations(String resultsPath, Simulations simulations, Algorithms algorithms,
                                       Statistics statistics, Parameters parameters, long timeout, TimeUnit unit) {
        compareFromSimulations(resultsPath, simulations, "Comparison.txt", algorithms, statistics, parameters, timeout, unit);
    }

    /**
     * Compares algorithms.
     *
     * @param resultsPath    Path to the file where the output should be printed.
     * @param simulations    The list of simulationWrapper that is used to generate graphs and data for the comparison.
     * @param algorithms     The list of algorithms to be compared.
     * @param statistics     The list of statistics on which to compare the algorithm, and their utility weights.
     * @param outputFileName a {@link java.lang.String} object
     * @param parameters     a {@link edu.cmu.tetrad.util.Parameters} object
     * @param timeout        a long
     * @param unit           a {@link java.util.concurrent.TimeUnit} object
     */
    public void compareFromSimulations(String resultsPath, Simulations simulations, String outputFileName, Algorithms algorithms,
                                       Statistics statistics, Parameters parameters, long timeout, TimeUnit unit) {
        this.resultsPath = resultsPath;

        // Create output file.
        try {
            File dir = new File(resultsPath);
            dir.mkdirs();
            File file = new File(dir, outputFileName);
            this.out = new PrintStream(new FileOutputStream(file));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.out.println(new Date());

        // Set up simulations--create data and graphs, read in parameters. The parameters
        // are set in the parameters object.
        List<SimulationWrapper> simulationWrappers = new ArrayList<>();

        int numRuns = parameters.getInt("numRuns");

        for (Simulation simulation : simulations.getSimulations()) {
            List<SimulationWrapper> wrappers = getSimulationWrappers(simulation, parameters);

            for (SimulationWrapper wrapper : wrappers) {
                wrapper.createData(wrapper.getSimulationSpecificParameters(), false);
                simulationWrappers.add(wrapper);
            }
        }

        // Set up the algorithms.
        List<AlgorithmWrapper> algorithmWrappers = new ArrayList<>();

        for (Algorithm algorithm : algorithms.getAlgorithms()) {
            List<Integer> _dims = new ArrayList<>();
            List<String> varyingParameters = new ArrayList<>();

            List<String> parameters1 = algorithm.getParameters();

            for (String name : parameters1) {
                if (parameters.getNumValues(name) > 1) {
                    _dims.add(parameters.getNumValues(name));
                    varyingParameters.add(name);
                }
            }

            if (varyingParameters.isEmpty()) {
                algorithmWrappers.add(new AlgorithmWrapper(algorithm, parameters));
            } else {

                int[] dims = new int[_dims.size()];
                for (int i = 0; i < _dims.size(); i++) {
                    dims[i] = _dims.get(i);
                }

                CombinationGenerator gen = new CombinationGenerator(dims);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    AlgorithmWrapper wrapper = new AlgorithmWrapper(algorithm, parameters);

                    for (int h = 0; h < dims.length; h++) {
                        String parameter = varyingParameters.get(h);
                        Object[] values = parameters.getValues(parameter);
                        Object value = values[choice[h]];
                        wrapper.setValue(parameter, value);
                    }

                    algorithmWrappers.add(wrapper);
                }
            }
        }

        // Create the algorithm-simulation wrappers for every combination of algorithm and
        // simulation.
        List<AlgorithmSimulationWrapper> algorithmSimulationWrappers = new ArrayList<>();

        for (SimulationWrapper simulationWrapper : simulationWrappers) {
            for (AlgorithmWrapper algorithmWrapper : algorithmWrappers) {
                DataType algDataType = algorithmWrapper.getDataType();
                DataType simDataType = simulationWrapper.getDataType();
                if (!(algDataType == DataType.Mixed || (algDataType == simDataType))) {
                    System.out.println("Type mismatch: " + algorithmWrapper.getDescription()
                                       + " / " + simulationWrapper.getDescription());
                }

                if (algorithmWrapper.getAlgorithm() instanceof ExternalAlgorithm external) {
                    external.setSimIndex(simulationWrappers.indexOf(external.getSimulation()));
                }

                algorithmSimulationWrappers.add(new AlgorithmSimulationWrapper(
                        algorithmWrapper, simulationWrapper));
            }
        }

        // Run all of the algorithms and compile statistics.
        double[][][][] allStats = calcStats(algorithmSimulationWrappers, algorithmWrappers, simulationWrappers,
                statistics, numRuns, timeout, unit);

        // Print out the preliminary information for statistics types, etc.
        if (allStats != null) {
            this.out.println();
            this.out.println("Statistics:");
            this.out.println();

            for (Statistic stat : statistics.getStatistics()) {
                this.out.println(stat.getAbbreviation() + " = " + stat.getDescription());
            }
        }

        this.out.println();

        if (allStats != null) {
            int numTables = allStats.length;
            int numStats = allStats[0][0].length - 1;

            double[][][] statTables = calcStatTables(allStats, Mode.Average, numTables, algorithmSimulationWrappers,
                    numStats, statistics);
            double[] utilities = calcUtilities(statistics, algorithmSimulationWrappers, statTables[0]);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            int[] newOrder;

            if (isSortByUtility()) {
                newOrder = sort(algorithmSimulationWrappers, utilities);
            } else {
                newOrder = new int[algorithmSimulationWrappers.size()];
                for (int q = 0; q < algorithmSimulationWrappers.size(); q++) {
                    newOrder[q] = q;
                }
            }

            this.out.println("Simulations:");
            this.out.println();

//            if (simulationWrappers.size() == 1) {
//                out.println(simulationWrappers.get(0).getDescription());
//            } else {
            int i = 0;

            for (SimulationWrapper simulation : simulationWrappers) {
                this.out.print("Simulation " + (++i) + ": ");
                this.out.println(simulation.getDescription());
                this.out.println();

                printParameters(simulation.getParameters(), simulation.getSimulationSpecificParameters(), this.out);

                this.out.println();
            }
//            }

            this.out.println("Algorithms:");
            this.out.println();

            for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                AlgorithmSimulationWrapper wrapper = algorithmSimulationWrappers.get(t);

                if (wrapper.getSimulationWrapper() == simulationWrappers.get(0)) {
                    this.out.println((t + 1) + ". " + wrapper.getAlgorithmWrapper().getDescription());
                }
            }

            if (isSortByUtility()) {
                this.out.println();
                this.out.println("Sorting by utility, high to low.");
            }

            if (isShowUtilities()) {
                this.out.println();
                this.out.println("Weighting of statistics:");
                this.out.println();
                this.out.println("U = ");

                for (Statistic stat : statistics.getStatistics()) {
                    String statName = stat.getAbbreviation();
                    double weight = statistics.getWeight(stat);
                    if (weight != 0.0) {
                        this.out.println("    " + weight + " * f(" + statName + ")");
                    }
                }

                this.out.println();
                this.out.println("...normed to range between 0 and 1.");

                this.out.println();
                this.out.println("Note that f for each statistic is a function that maps the statistic to the ");
                this.out.println("interval [0, 1], with higher being better.");
            }

            this.out.println();
            this.out.println("Graphs are being compared to the " + this.comparisonGraph.toString().replace("_", " ") + ".");

            this.out.println();

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            // Print all of the tables.
            printStats(statTables, statistics, Mode.Average, newOrder, algorithmSimulationWrappers,
                    algorithmWrappers, simulationWrappers, utilities, parameters);

            statTables = calcStatTables(allStats, Mode.StandardDeviation, numTables,
                    algorithmSimulationWrappers, numStats, statistics);

            printStats(statTables, statistics, Mode.StandardDeviation, newOrder, algorithmSimulationWrappers, algorithmWrappers,
                    simulationWrappers, utilities, parameters);

            statTables = calcStatTables(allStats, Mode.WorstCase, numTables, algorithmSimulationWrappers,
                    numStats, statistics);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.WorstCase, newOrder, algorithmSimulationWrappers, algorithmWrappers,
                    simulationWrappers, utilities, parameters);
        }

        this.out.close();
    }

    /**
     * Saves simulationWrapper data.
     *
     * @param dataPath   The path to the directory where the simulationWrapper data should be saved.
     * @param simulation The simulate used to generate the graphs and data.
     * @param parameters The parameters to be used in the simulationWrapper.
     */
    public void saveToFiles(String dataPath, Simulation simulation, Parameters parameters) {
        List<SimulationWrapper> simulationWrappers = getSimulationWrappers(simulation, parameters);

        File dir0 = new File(dataPath);
        File dir;
        final int i = 0;

        dir = new File(dir0, "save");

        deleteFilesThenDirectory(dir);

        try {
            int index = 0;

            for (SimulationWrapper simulationWrapper : simulationWrappers) {
                for (String param : simulationWrapper.getParameters()) {
                    parameters.set(param, simulationWrapper.getValue(param));
                }

                simulationWrapper.createData(simulationWrapper.getSimulationSpecificParameters(), false);
                index++;

                File subdir = new File(dir, "" + index);
                subdir.mkdirs();

                File dir1 = new File(subdir, "graph");
                File dir2 = new File(subdir, "data");

                dir1.mkdirs();
                dir2.mkdirs();

                File dir3 = null;

                if (isSaveCPDAGs()) {
                    dir3 = new File(subdir, "patterns");
                    dir3.mkdirs();
                }

                File dir4 = null;

                if (isSavePags()) {
                    dir4 = new File(subdir, "pags");
                    dir4.mkdirs();
                }

                for (int j = 0; j < simulationWrapper.getNumDataModels(); j++) {
                    File file2 = new File(dir1, "graph." + (j + 1) + ".txt");
                    Graph graph = simulationWrapper.getTrueGraph(j);

                    GraphSaveLoadUtils.saveGraph(graph, file2, false);

                    File file = new File(dir2, "data." + (j + 1) + ".txt");
                    Writer out = new FileWriter(file);
                    DataModel dataModel = simulationWrapper.getDataModel(j);
                    DataWriter.writeRectangularData((DataSet) dataModel, out, '\t');
                    out.close();

                    if (isSaveCPDAGs()) {
                        File file3 = new File(dir3, "pattern." + (j + 1) + ".txt");
                        GraphSaveLoadUtils.saveGraph(GraphTransforms.dagToCpdag(graph), file3, false);
                    }

                    if (isSavePags()) {
                        File file4 = new File(dir4, "pag." + (j + 1) + ".txt");
                        GraphSaveLoadUtils.saveGraph(GraphTransforms.dagToPag(graph), file4, false);
                    }
                }

                PrintStream out = new PrintStream(Files.newOutputStream(new File(subdir, "parameters.txt").toPath()));
                out.println(simulationWrapper.getDescription());
//                out.println();
                out.println(simulationWrapper.getSimulationSpecificParameters());
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>configuration.</p>
     *
     * @param path a {@link java.lang.String} object
     */
    public void configuration(String path) {
        try {
            new File(path).mkdirs();

            PrintStream out = new PrintStream(Files.newOutputStream(new File(path, "Configuration.txt").toPath()));

            Parameters allParams = new Parameters();

            List<Class> algorithms = new ArrayList<>(getClasses(Algorithm.class));

            List<Class> statistics = new ArrayList<>(getClasses(Statistic.class));

            List<Class> independenceWrappers = new ArrayList<>(getClasses(IndependenceWrapper.class));

            List<Class> scoreWrappers = new ArrayList<>(getClasses(ScoreWrapper.class));

            List<Class> simulations = new ArrayList<>(getClasses(Simulation.class));

            out.println("Available Algorithms:");
            out.println();
            out.println("Algorithms that take an independence test (using an example independence test):");
            out.println();

            for (Class clazz : new ArrayList<>(algorithms)) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor[] constructors = clazz.getConstructors();

                for (Constructor constructor : constructors) {
                    if (constructor.getParameterTypes().length == 1
                        && constructor.getParameterTypes()[0] == IndependenceWrapper.class) {
                        Algorithm algorithm = (Algorithm) constructor.newInstance(
                                FisherZ.class.newInstance());
                        out.println(clazz.getSimpleName() + ": " + algorithm.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(algorithm.getParameters(), allParams, out);
                        }
                        if (TakesExternalGraph.class.isAssignableFrom(clazz)) {
                            out.println("\t" + clazz.getSimpleName() + " can take an initial graph from some other algorithm as input");
                        }
                    }
                }
            }

            out.println();
            out.println("Algorithms that take a score (using an example score):");
            out.println();

            for (Class clazz : new ArrayList<>(algorithms)) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor[] constructors = clazz.getConstructors();

                for (Constructor constructor : constructors) {
                    if (constructor.getParameterTypes().length == 1
                        && constructor.getParameterTypes()[0] == ScoreWrapper.class) {
                        Algorithm algorithm = (Algorithm) constructor.newInstance(
                                BdeuScore.class.getDeclaredConstructor().newInstance());
                        out.println(clazz.getSimpleName() + ": " + algorithm.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(algorithm.getParameters(), allParams, out);
                        }
                    }

                }
            }

            out.println();
            out.println("Algorithms with blank constructor:");
            out.println();

            for (Class clazz : new ArrayList<>(algorithms)) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor[] constructors = clazz.getConstructors();

                for (Constructor constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        Algorithm algorithm = (Algorithm) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + algorithm.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(algorithm.getParameters(), allParams, out);
                        }
                    }
                }
            }

            out.println();
            out.println("Available Statistics:");
            out.println();

            for (Class clazz : statistics) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor[] constructors = clazz.getConstructors();

                for (Constructor constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        Statistic statistic = (Statistic) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + statistic.getDescription());
                    }
                }
            }

            out.println();
            out.println("Available Independence Tests:");
            out.println();

            for (Class clazz : independenceWrappers) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor[] constructors = clazz.getConstructors();

                for (Constructor constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        IndependenceWrapper independence = (IndependenceWrapper) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + independence.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(independence.getParameters(), allParams, out);
                        }
                    }
                }
            }

            out.println();
            out.println("Available Scores:");
            out.println();

            for (Class clazz : scoreWrappers) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor[] constructors = clazz.getConstructors();

                for (Constructor constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        ScoreWrapper score = (ScoreWrapper) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + score.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(score.getParameters(), allParams, out);
                        }
                    }
                }
            }

            out.println();
            out.println("Available Simulations:");
            out.println();

            for (Class clazz : simulations) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor[] constructors = clazz.getConstructors();

                for (Constructor constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        Simulation simulation = (Simulation) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + simulation.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(simulation.getParameters(), allParams, out);
                        }
                    }
                }
            }

            out.println();

            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Class> getClasses(Class type) {
        Reflections reflections = new Reflections();
        Set<Class> allClasses = reflections.getSubTypesOf(type);
        return new ArrayList<>(allClasses);
    }

    private List<SimulationWrapper> getSimulationWrappers(Simulation simulation, Parameters parameters) {
        List<SimulationWrapper> simulationWrappers = new ArrayList<>();

        List<Integer> _dims = new ArrayList<>();
        List<String> varyingParams = new ArrayList<>();

        List<String> parameters1 = simulation.getParameters();
        for (String param : parameters1) {
            int numValues = parameters.getNumValues(param);
            if (numValues > 1) {
                _dims.add(numValues);
                varyingParams.add(param);
            }
        }

        if (varyingParams.isEmpty()) {
            simulationWrappers.add(new SimulationWrapper(simulation, parameters));
        } else {

            int[] dims = new int[_dims.size()];
            for (int i = 0; i < _dims.size(); i++) {
                dims[i] = _dims.get(i);
            }

            CombinationGenerator gen = new CombinationGenerator(dims);
            int[] choice;

            while ((choice = gen.next()) != null) {
                SimulationWrapper wrapper = new SimulationWrapper(simulation, parameters);

                for (int h = 0; h < dims.length; h++) {
                    String param = varyingParams.get(h);
                    Object[] values = parameters.getValues(param);
                    Object value = values[choice[h]];
                    wrapper.setValue(param, value);
                }

                simulationWrappers.add(wrapper);
            }
        }

        return simulationWrappers;
    }

    private double[][][][] calcStats(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                                     List<AlgorithmWrapper> algorithmWrappers, List<SimulationWrapper> simulationWrappers,
                                     Statistics statistics, int numRuns, long timeout, TimeUnit unit) {
        final int numGraphTypes = 4;

        this.graphTypeUsed = new boolean[4];

        double[][][][] allStats = new double[4][algorithmSimulationWrappers.size()][statistics.size() + 1][numRuns];

        List<AlgorithmTask> tasks = new ArrayList<>();
        int index = 0;

        for (int algSimIndex = 0; algSimIndex < algorithmSimulationWrappers.size(); algSimIndex++) {
            for (int runIndex = 0; runIndex < numRuns; runIndex++) {
                AlgorithmSimulationWrapper algorithmSimulationWrapper = algorithmSimulationWrappers.get(algSimIndex);
                Run run = new Run(algSimIndex, runIndex, index++, algorithmSimulationWrapper);
                AlgorithmTask task = new AlgorithmTask(algorithmSimulationWrappers,
                        algorithmWrappers, simulationWrappers,
                        statistics, numGraphTypes, allStats, run);
                tasks.add(task);
            }
        }

        ForkJoinPool pool = new ForkJoinPool(1);
        tasks.forEach(task -> {
            Future<Void> future = pool.submit(task);
            try {
                future.get(timeout, unit);
                this.out.printf("%s: Run %d: Task is successfully completed.%n", dateTimeNow(), task.run.index + 1);
            } catch (ExecutionException exception) {
                this.out.printf("%s: Run %d: Execution error.%n", dateTimeNow(), task.run.index + 1);
            } catch (InterruptedException exception) {
                this.out.printf("%s: Run %d: Task has been interrupted.%n", dateTimeNow(), task.run.index + 1);
                Thread.currentThread().interrupt();
            } catch (TimeoutException exception) {
                this.out.printf("%s: Run %d: Task has been timed out.%n", dateTimeNow(), task.run.index + 1);
            } finally {
                if (!future.isDone()) {
                    this.out.printf("%s: Run %d: Cancel task.%n", dateTimeNow(), task.run.index + 1);
                    future.cancel(true);
                }
            }
        });

        shutdownAndAwaitTermination(pool);

        return allStats;
    }

    private String dateTimeNow() {
        return TimeoutComparison.DF.format(new Date(MillisecondTimes.timeMillis()));
    }

    private void shutdownAndAwaitTermination(ForkJoinPool pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.err.println("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * <p>isShowSimulationIndices.</p>
     *
     * @return a boolean
     */
    public boolean isShowSimulationIndices() {
        return this.showSimulationIndices;
    }

    /**
     * <p>Setter for the field <code>showSimulationIndices</code>.</p>
     *
     * @param showSimulationIndices a boolean
     */
    public void setShowSimulationIndices(boolean showSimulationIndices) {
        this.showSimulationIndices = showSimulationIndices;
    }

    /**
     * <p>isShowAlgorithmIndices.</p>
     *
     * @return a boolean
     */
    public boolean isShowAlgorithmIndices() {
        return this.showAlgorithmIndices;
    }

    /**
     * <p>Setter for the field <code>showAlgorithmIndices</code>.</p>
     *
     * @param showAlgorithmIndices a boolean
     */
    public void setShowAlgorithmIndices(boolean showAlgorithmIndices) {
        this.showAlgorithmIndices = showAlgorithmIndices;
    }

    /**
     * <p>isShowUtilities.</p>
     *
     * @return True iff a column of utilities marked "W" should be shown in the output.
     */
    public boolean isShowUtilities() {
        return this.showUtilities;
    }

    /**
     * <p>Setter for the field <code>showUtilities</code>.</p>
     *
     * @param showUtilities True iff a column of utilities marked "W" should be shown in the output.
     */
    public void setShowUtilities(boolean showUtilities) {
        this.showUtilities = showUtilities;
    }

    /**
     * <p>isSortByUtility.</p>
     *
     * @return True iff the output should be sorted by utility.
     */
    public boolean isSortByUtility() {
        return this.sortByUtility;
    }

    /**
     * <p>Setter for the field <code>sortByUtility</code>.</p>
     *
     * @param sortByUtility true iff the output should be sorted by utility.
     */
    public void setSortByUtility(boolean sortByUtility) {
        this.sortByUtility = sortByUtility;
    }

    /**
     * <p>isParallelized.</p>
     *
     * @return a boolean
     */
    public boolean isParallelized() {
        return this.parallelized;
    }

    /**
     * <p>Setter for the field <code>parallelized</code>.</p>
     *
     * @param parallelized a boolean
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * <p>isSaveCPDAGs.</p>
     *
     * @return True if CPDAGs should be saved out.
     */
    public boolean isSaveCPDAGs() {
        return this.saveCPDAGs;
    }

    /**
     * <p>Setter for the field <code>saveCPDAGs</code>.</p>
     *
     * @param saveCPDAGs True if CPDAGs should be saved out.
     */
    public void setSaveCPDAGs(boolean saveCPDAGs) {
        this.saveCPDAGs = saveCPDAGs;
    }

    /**
     * <p>isSavePags.</p>
     *
     * @return True if patterns should be saved out.
     */
    public boolean isSavePags() {
        return this.savePags;
    }

    /**
     * <p>Setter for the field <code>savePags</code>.</p>
     *
     * @param savePags True if patterns should be saved out.
     */
    public void setSavePags(boolean savePags) {
        this.savePags = savePags;
    }

    /**
     * <p>isTabDelimitedTables.</p>
     *
     * @return True iff tables should be tab delimited (e.g. for easy pasting into Excel).
     */
    public boolean isTabDelimitedTables() {
        return this.tabDelimitedTables;
    }

    /**
     * <p>Setter for the field <code>tabDelimitedTables</code>.</p>
     *
     * @param tabDelimitedTables True iff tables should be tab delimited (e.g. for easy pasting into Excel).
     */
    public void setTabDelimitedTables(boolean tabDelimitedTables) {
        this.tabDelimitedTables = tabDelimitedTables;
    }

    /**
     * <p>isSaveGraphs.</p>
     *
     * @return True if all graphs should be saved to files.
     */
    public boolean isSaveGraphs() {
        return this.saveGraphs;
    }

    /**
     * <p>Setter for the field <code>saveGraphs</code>.</p>
     *
     * @param saveGraphs True if all graphs should be saved to files.
     */
    public void setSaveGraphs(boolean saveGraphs) {
        this.saveGraphs = saveGraphs;
    }

    /**
     * <p>isCopyData.</p>
     *
     * @return True if data should be copied before analyzing it.
     */
    public boolean isCopyData() {
        return this.copyData;
    }

    /**
     * <p>Setter for the field <code>copyData</code>.</p>
     *
     * @param copyData True if data should be copied before analyzing it.
     */
    public void setCopyData(boolean copyData) {
        this.copyData = copyData;
    }

    /**
     * The type of graph the results are compared to.
     *
     * @return a {@link edu.cmu.tetrad.algcomparison.TimeoutComparison.ComparisonGraph} object
     */
    public ComparisonGraph getComparisonGraph() {
        return this.comparisonGraph;
    }

    /**
     * The type of graph the results are compared to.
     *
     * @param comparisonGraph a {@link edu.cmu.tetrad.algcomparison.TimeoutComparison.ComparisonGraph} object
     */
    public void setComparisonGraph(ComparisonGraph comparisonGraph) {
        if (comparisonGraph == null) {
            throw new NullPointerException("Null compare graph.");
        }
        this.comparisonGraph = comparisonGraph;
    }

    private void printParameters(List<String> names, Parameters parameters, PrintStream out) {
        ParamDescriptions descriptions = ParamDescriptions.getInstance();

        for (String name : names) {
            ParamDescription description = descriptions.get(name);
            Object value = parameters.get(name);

            if (value instanceof Double) {
                out.println(description.getShortDescription() + " = " + value);
            } else if (value instanceof Integer) {
                out.println(description.getShortDescription() + " = " + value);
            } else if (value instanceof Boolean) {
                boolean b = (Boolean) value;
                out.println(description.getShortDescription() + " = " + (b ? "Yes" : "No"));
            } else if (value instanceof String) {
                out.println(description.getShortDescription() + " = " + value);
            }
        }
    }

    private void deleteFilesThenDirectory(File dir) {
        if (dir == null) {
            return;
        }

        String[] entries = dir.list();

        if (entries == null) {
            return;
        }

        for (String s : entries) {
            File currentFile = new File(dir.getPath(), s);

            if (currentFile.isDirectory()) {
                deleteFilesThenDirectory(currentFile);
            } else {
                currentFile.delete();
            }
        }

        dir.delete();
    }

    private void doRun(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                       List<AlgorithmWrapper> algorithmWrappers, List<SimulationWrapper> simulationWrappers,
                       Statistics statistics,
                       int numGraphTypes, double[][][][] allStats, Run run) {
        System.out.println();
        System.out.println("Run " + (run.getRunIndex() + 1));
        System.out.println();

        AlgorithmSimulationWrapper algorithmSimulationWrapper = algorithmSimulationWrappers.get(run.getAlgSimIndex());
        AlgorithmWrapper algorithmWrapper = algorithmSimulationWrapper.getAlgorithmWrapper();
        SimulationWrapper simulationWrapper = algorithmSimulationWrapper.getSimulationWrapper();
        DataModel data = simulationWrapper.getDataModel(run.getRunIndex());
        Graph trueGraph = simulationWrapper.getTrueGraph(run.getRunIndex());

        System.out.println((run.getAlgSimIndex() + 1) + ". " + algorithmWrapper.getDescription()
                           + " simulationWrapper: " + simulationWrapper.getDescription());

        long start = MillisecondTimes.timeMillis();
        Graph out;

        try {
            Algorithm algorithm = algorithmWrapper.getAlgorithm();
            Simulation simulation = simulationWrapper.getSimulation();

            if (algorithm instanceof HasKnowledge && simulation instanceof HasKnowledge) {
                ((HasKnowledge) algorithm).setKnowledge(((HasKnowledge) simulation).getKnowledge());
            }

            if (algorithmWrapper.getAlgorithm() instanceof ExternalAlgorithm external) {
                external.setSimulation(simulationWrapper.getSimulation());
                external.setPath(this.resultsPath);
                external.setSimIndex(simulationWrappers.indexOf(simulationWrapper));
            }

            if (algorithm instanceof MultiDataSetAlgorithm) {
                List<Integer> indices = new ArrayList<>();
                int numDataModels = simulationWrapper.getSimulation().getNumDataModels();
                for (int i = 0; i < numDataModels; i++) {
                    indices.add(i);
                }
                RandomUtil.shuffle(indices);

                List<DataModel> dataModels = new ArrayList<>();
                int randomSelectionSize = algorithmWrapper.getAlgorithmSpecificParameters().getInt(
                        "randomSelectionSize");
                for (int i = 0; i <
                                FastMath.min(numDataModels, randomSelectionSize); i++) {
                    dataModels.add(simulationWrapper.getSimulation().getDataModel(indices.get(i)));
                }

                Parameters _params = algorithmWrapper.getAlgorithmSpecificParameters();
                out = ((MultiDataSetAlgorithm) algorithm).search(dataModels, _params);
            } else {
                DataModel dataModel = this.copyData ? data.copy() : data;
                Parameters _params = algorithmWrapper.getAlgorithmSpecificParameters();
                out = algorithm.search(dataModel, _params);
            }
        } catch (Exception e) {
            System.out.println("Could not run " + algorithmWrapper.getDescription());
            e.printStackTrace();
            return;
        }

        int simIndex = simulationWrappers.indexOf(simulationWrapper) + 1;
        int algIndex = algorithmWrappers.indexOf(algorithmWrapper) + 1;

        long stop = MillisecondTimes.timeMillis();

        long elapsed = stop - start;

        saveGraph(this.resultsPath, out, run.getRunIndex(), simIndex, algIndex, algorithmWrapper, elapsed);

        if (trueGraph != null) {
            out = GraphUtils.replaceNodes(out, trueGraph.getNodes());
        }

        if (algorithmWrapper.getAlgorithm() instanceof ExternalAlgorithm extAlg) {
            extAlg.setSimIndex(simulationWrappers.indexOf(simulationWrapper));
            extAlg.setSimulation(simulationWrapper.getSimulation());
            extAlg.setPath(this.resultsPath);
            elapsed = extAlg.getElapsedTime(data, simulationWrapper.getSimulationSpecificParameters());
        }

        Graph[] est = new Graph[numGraphTypes];

        Graph comparisonGraph;

        if (this.comparisonGraph == ComparisonGraph.true_DAG) {
            comparisonGraph = new EdgeListGraph(trueGraph);
        } else if (this.comparisonGraph == ComparisonGraph.CPDAG_of_the_true_DAG) {
            Graph dag = new EdgeListGraph(trueGraph);
            comparisonGraph = GraphTransforms.dagToCpdag(dag);
        } else if (this.comparisonGraph == ComparisonGraph.PAG_of_the_true_DAG) {
            Graph trueGraph1 = new EdgeListGraph(trueGraph);
            comparisonGraph = GraphTransforms.dagToPag(trueGraph1);
        } else {
            throw new IllegalArgumentException("Unrecognized graph type.");
        }

//        Graph comparisonGraph = trueGraph == null ? null : algorithmSimulationWrapper.getComparisonGraph(trueGraph);
        est[0] = out;
        this.graphTypeUsed[0] = true;

        if (data.isMixed()) {
            est[1] = getSubgraph(out, true, true, data);
            est[2] = getSubgraph(out, true, false, data);
            est[3] = getSubgraph(out, false, false, data);

            this.graphTypeUsed[1] = true;
            this.graphTypeUsed[2] = true;
            this.graphTypeUsed[3] = true;
        }

        Graph[] truth = new Graph[numGraphTypes];

        truth[0] = comparisonGraph;

        if (data.isMixed() && comparisonGraph != null) {
            truth[1] = getSubgraph(comparisonGraph, true, true, data);
            truth[2] = getSubgraph(comparisonGraph, true, false, data);
            truth[3] = getSubgraph(comparisonGraph, false, false, data);
        }

        if (comparisonGraph != null) {
            for (int u = 0; u < numGraphTypes; u++) {
                if (!this.graphTypeUsed[u]) {
                    continue;
                }

                int statIndex = -1;

                for (Statistic _stat : statistics.getStatistics()) {
                    statIndex++;

                    if (_stat instanceof ParameterColumn) {
                        continue;
                    }

                    double stat;

                    if (_stat instanceof ElapsedCpuTime) {
                        stat = elapsed / 1000.0;
                    } else {
                        stat = _stat.getValue(truth[u], est[u], null);
                    }

                    allStats[u][run.getAlgSimIndex()][statIndex][run.getRunIndex()] = stat;
                }
            }
        }
    }

    private void saveGraph(String resultsPath, Graph graph, int i, int simIndex, int algIndex,
                           AlgorithmWrapper algorithmWrapper, long elapsed) {
        if (!this.saveGraphs) {
            return;
        }

        try {
            String description = algorithmWrapper.getDescription().replace(" ", "_");

            File file;
            File fileElapsed;

            File dir = new File(resultsPath, "results/" + description + "/" + simIndex);
            dir.mkdirs();

            File dirElapsed = new File(resultsPath, "elapsed/" + description + "/" + simIndex);
            dirElapsed.mkdirs();

            if (resultsPath != null) {
                file = new File(dir, "graph." + (i + 1) + ".txt");
                fileElapsed = new File(dirElapsed, "graph." + (i + 1) + ".txt");
            } else {
                throw new IllegalArgumentException("Results path not provided.");
            }

            PrintStream out = new PrintStream(file);
            System.out.println("Saving graph to " + file.getAbsolutePath());
            out.println(graph);
            out.close();

            PrintStream outElapsed = new PrintStream(fileElapsed);
//            System.out.println("Saving graph to " + file.getAbsolutePath());
            outElapsed.println(elapsed);
            outElapsed.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String getHeader(int u) {
        String header;

        switch (u) {
            case 0:
                header = "All edges";
                break;
            case 1:
                header = "Discrete-discrete";
                break;
            case 2:
                header = "Discrete-continuous";
                break;
            case 3:
                header = "Continuous-continuous";
                break;
            default:
                throw new IllegalStateException();
        }
        return header;
    }

    private double[][][] calcStatTables(double[][][][] allStats, Mode mode, int numTables,
                                        List<AlgorithmSimulationWrapper> wrappers, int numStats, Statistics statistics) {
        double[][][] statTables = new double[numTables][wrappers.size()][numStats + 1];

        for (int u = 0; u < numTables; u++) {
            for (int i = 0; i < wrappers.size(); i++) {
                for (int j = 0; j < numStats; j++) {
                    if (statistics.getStatistics().get(j) instanceof ParameterColumn) {
                        String statName = statistics.getStatistics().get(j).getAbbreviation();
                        SimulationWrapper simulationWrapper = wrappers.get(i).getSimulationWrapper();
                        AlgorithmWrapper algorithmWrapper = wrappers.get(i).getAlgorithmWrapper();
                        double stat = Double.NaN;

                        List<String> parameterNames = simulationWrapper.getParameters();
                        Parameters parameters = simulationWrapper.getSimulationSpecificParameters();

                        for (String name : parameterNames) {
                            if (name.equals(statName)) {
                                if (parameters.get(name) instanceof Boolean) {
                                    boolean b = parameters.getBoolean(name);
                                    stat = b ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                                } else {
                                    stat = parameters.getDouble(name);
                                }

                                break;
                            }
                        }

                        if (Double.isNaN(stat)) {
                            List<String> _parameterNames = algorithmWrapper.getParameters();
                            Parameters _parameters = algorithmWrapper.parameters;

                            for (String name : _parameterNames) {
                                if (name.equals(statName)) {
                                    try {
                                        stat = _parameters.getDouble(name);
                                    } catch (Exception e) {
                                        boolean b = _parameters.getBoolean(name);
                                        stat = b ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                                    }

                                    break;
                                }
                            }
                        }

                        statTables[u][i][j] = stat;
                    } else if (mode == Mode.Average) {
                        statTables[u][i][j] = StatUtils.mean(allStats[u][i][j]);
                    } else if (mode == Mode.WorstCase) {
                        statTables[u][i][j] = StatUtils.min(allStats[u][i][j]);
                    } else if (mode == Mode.StandardDeviation) {
                        statTables[u][i][j] = StatUtils.sd(allStats[u][i][j]);
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }

        return statTables;
    }

    private void printStats(double[][][] statTables, Statistics statistics, Mode mode, int[] newOrder,
                            List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                            List<AlgorithmWrapper> algorithmWrappers,
                            List<SimulationWrapper> simulationWrappers, double[] utilities,
                            Parameters parameters) {

        if (mode == Mode.Average) {
            this.out.println("AVERAGE STATISTICS");
        } else if (mode == Mode.StandardDeviation) {
            this.out.println("STANDARD DEVIATIONS");
        } else if (mode == Mode.WorstCase) {
            this.out.println("WORST CASE");
        } else {
            throw new IllegalStateException();
        }

        int numTables = statTables.length;
        int numStats = statistics.size();

        NumberFormat nf = new DecimalFormat("0.00");
        NumberFormat smallNf = new DecimalFormat("0.00E0");

        this.out.println();

        for (int u = 0; u < numTables; u++) {
            if (!this.graphTypeUsed[u]) {
                continue;
            }

            int rows = algorithmSimulationWrappers.size() + 1;
            int cols = (isShowSimulationIndices() ? 1 : 0) + (isShowAlgorithmIndices() ? 1 : 0) + numStats
                       + (isShowUtilities() ? 1 : 0);

            TextTable table = new TextTable(rows, cols);
            table.setDelimiter(isTabDelimitedTables() ? TextTable.Delimiter.TAB : TextTable.Delimiter.JUSTIFIED);

            int initialColumn = 0;

            if (isShowSimulationIndices()) {
                table.setToken(0, initialColumn, "Sim");

                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    Simulation simulation = algorithmSimulationWrappers.get(newOrder[t]).
                            getSimulationWrapper();
                    table.setToken(t + 1, initialColumn, "" + (simulationWrappers.indexOf(simulation) + 1));
                }

                initialColumn++;
            }

            if (isShowAlgorithmIndices()) {
                table.setToken(0, initialColumn, "Alg");

                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    AlgorithmWrapper algorithm = algorithmSimulationWrappers.get(newOrder[t]).getAlgorithmWrapper();
                    table.setToken(t + 1, initialColumn, "" + (algorithmWrappers.indexOf(algorithm) + 1));
                }

                initialColumn++;
            }

            for (int statIndex = 0; statIndex < numStats; statIndex++) {
                String statLabel = statistics.getStatistics().get(statIndex).getAbbreviation();
                table.setToken(0, initialColumn + statIndex, statLabel);
            }

            if (isShowUtilities()) {
                table.setToken(0, initialColumn + numStats, "U");
            }

            for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                for (int statIndex = 0; statIndex < numStats; statIndex++) {
                    Statistic statistic = statistics.getStatistics().get(statIndex);
                    AlgorithmWrapper algorithmWrapper = algorithmSimulationWrappers.get(newOrder[t]).getAlgorithmWrapper();
                    SimulationWrapper simulationWrapper = algorithmSimulationWrappers.get(newOrder[t]).getSimulationWrapper();

                    Algorithm algorithm = algorithmWrapper.getAlgorithm();
                    Simulation simulation = simulationWrapper.getSimulation();

                    if (algorithm instanceof HasParameterValues) {
                        parameters.putAll(((HasParameterValues) algorithm).getParameterValues());
                    }

                    if (simulation instanceof HasParameterValues) {
                        parameters.putAll(((HasParameterValues) simulation).getParameterValues());
                    }

                    String abbreviation = statistic.getAbbreviation();

                    Object[] o = parameters.getValues(abbreviation);

                    if (o.length == 1 && o[0] instanceof String) {
                        table.setToken(t + 1, initialColumn + statIndex, (String) o[0]);
                        continue;
                    }

                    double stat = statTables[u][newOrder[t]][statIndex];

                    if (stat == 0.0) {
                        table.setToken(t + 1, initialColumn + statIndex, "-");
                    } else if (stat == Double.POSITIVE_INFINITY) {
                        table.setToken(t + 1, initialColumn + statIndex, "Yes");
                    } else if (stat == Double.NEGATIVE_INFINITY) {
                        table.setToken(t + 1, initialColumn + statIndex, "No");
                    } else if (Double.isNaN(stat)) {
                        table.setToken(t + 1, initialColumn + statIndex, "*");
                    } else {
                        table.setToken(t + 1, initialColumn + statIndex,
                                FastMath.abs(stat) < FastMath.pow(10, -smallNf.getMaximumFractionDigits()) && stat != 0 ? smallNf.format(stat) : nf.format(stat));
                    }
                }

                if (isShowUtilities()) {
                    table.setToken(t + 1, initialColumn + numStats, nf.format(utilities[newOrder[t]]));
                }
            }

            this.out.println(getHeader(u));
            this.out.println();
            this.out.println(table);
        }
    }

    private double[] calcUtilities(Statistics statistics, List<AlgorithmSimulationWrapper> wrappers,
                                   double[][] stats) {

        // Calculate utilities for the first table.
        double[] utilities = new double[wrappers.size()];

        for (int t = 0; t < wrappers.size(); t++) {
            int j = -1;

            Iterator it2 = statistics.getStatistics().iterator();

            double sum = 0.0;
            double max = 0.0;

            while (it2.hasNext()) {
                Statistic stat = (Statistic) it2.next();
                j++;

                double weight = statistics.getWeight(stat);

                if (weight != 0.0) {
                    sum += weight * stat.getNormValue(stats[t][j]);
                    max += weight;
                }
            }

            utilities[t] = sum / max;
        }

        return utilities;
    }

    private int[] sort(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                       double[] utilities) {
        List<Integer> order = new ArrayList<>();
        for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
            order.add(t);
        }

        double[] _utilities = Arrays.copyOf(utilities, utilities.length);
        double low = StatUtils.min(utilities);
        for (int t = 0; t < _utilities.length; t++) {
            low--;
            if (Double.isNaN(_utilities[t])) {
                _utilities[t] = low;
            }
        }

        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -Double.compare(_utilities[o1], _utilities[o2]);
            }
        });

        int[] newOrder = new int[algorithmSimulationWrappers.size()];
        for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
            newOrder[t] = order.get(t);
        }

        return newOrder;
    }

    private Graph getSubgraph(Graph graph, boolean discrete1, boolean discrete2, DataModel DataModel) {
        if (discrete1 && discrete2) {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = DataModel.getVariable(edge.getNode1().getName());
                Node node2 = DataModel.getVariable(edge.getNode2().getName());

                if (node1 instanceof DiscreteVariable
                    && node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        } else if (!discrete1 && !discrete2) {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = DataModel.getVariable(edge.getNode1().getName());
                Node node2 = DataModel.getVariable(edge.getNode2().getName());

                if (node1 instanceof ContinuousVariable
                    && node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        } else {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = DataModel.getVariable(edge.getNode1().getName());
                Node node2 = DataModel.getVariable(edge.getNode2().getName());

                if (node1 instanceof DiscreteVariable
                    && node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }

                if (node1 instanceof ContinuousVariable
                    && node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        }
    }

    /**
     * An enum of graph types to compare.
     */
    public enum ComparisonGraph {

        /**
         * The true dag.
         */
        true_DAG,
        /**
         * The cpdag of the true dag.
         */
        CPDAG_of_the_true_DAG,
        /**
         * The pag of the true dag.
         */
        PAG_of_the_true_DAG
    }

    private enum Mode {
        Average, StandardDeviation, WorstCase
    }

    private static class AlgorithmWrapper implements Algorithm {

        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The algorithm to be wrapped.
         */
        private final Algorithm algorithm;

        /**
         * The parameters for the algorithm.
         */
        private final Parameters parameters;

        /**
         * The parameters that have been overridden.
         */
        private final List<String> overriddenParameters = new ArrayList<>();

        public AlgorithmWrapper(Algorithm algorithm, Parameters parameters) {
            this.algorithm = algorithm;
            this.parameters = new Parameters(parameters);
        }

        @Override
        public Graph search(DataModel DataModel, Parameters parameters) {
            return this.algorithm.search(DataModel, this.parameters);
        }

        @Override
        public Graph getComparisonGraph(Graph graph) {
            return this.algorithm.getComparisonGraph(graph);
        }

        @Override
        public String getDescription() {
            StringBuilder description = new StringBuilder();
            description.append(this.algorithm.getDescription());

            if (this.overriddenParameters.size() > 0) {
                for (String name : new ArrayList<>(this.overriddenParameters)) {
                    description.append(", ").append(name).append(" = ").append(this.parameters.get(name));
                }
            }

            return description.toString();
        }

        @Override
        public DataType getDataType() {
            return this.algorithm.getDataType();
        }

        @Override
        public List<String> getParameters() {
            return this.algorithm.getParameters();
        }

        public void setValue(String name, Object value) {
            if (!(value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException();
            }

            this.parameters.set(name, value);
            this.overriddenParameters.add(name);
        }

        public Algorithm getAlgorithm() {
            return this.algorithm;
        }

        public Parameters getAlgorithmSpecificParameters() {
            return this.parameters;
        }
    }

    /**
     * A wrapper for an algorithm and a simulation.
     */
    private static class AlgorithmSimulationWrapper implements Algorithm {

        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The simulation to be wrapped.
         */
        private final SimulationWrapper simulationWrapper;

        /**
         * The algorithm to be wrapped.
         */
        private final AlgorithmWrapper algorithmWrapper;

        /**
         * The parameters for the algorithm and simulation.
         */
        List<String> parameters = new ArrayList<>();

        public AlgorithmSimulationWrapper(AlgorithmWrapper algorithm, SimulationWrapper simulation) {
            this.algorithmWrapper = algorithm;
            this.simulationWrapper = simulation;
            this.parameters.addAll(this.algorithmWrapper.getParameters());
            this.parameters.addAll(this.simulationWrapper.getParameters());
        }

        @Override
        public Graph search(DataModel DataModel, Parameters parameters) {
            return this.algorithmWrapper.getAlgorithm().search(DataModel, parameters);
        }

        @Override
        public Graph getComparisonGraph(Graph graph) {
            return this.algorithmWrapper.getComparisonGraph(graph);
        }

        @Override
        public String getDescription() {
            throw new IllegalArgumentException();
        }

        @Override
        public DataType getDataType() {
            return this.algorithmWrapper.getDataType();
        }

        @Override
        public List<String> getParameters() {
            List<String> params = new ArrayList<>(this.simulationWrapper.getParameters());
            params.addAll(this.algorithmWrapper.getParameters());
            return params;
        }

        public SimulationWrapper getSimulationWrapper() {
            return this.simulationWrapper;
        }

        public AlgorithmWrapper getAlgorithmWrapper() {
            return this.algorithmWrapper;
        }
    }

    /**
     * A wrapper for a simulation.
     */
    private static class SimulationWrapper implements Simulation {

        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The simulation to be wrapped.
         */
        private final Simulation simulation;

        /**
         * The list of graphs.
         */
        private List<Graph> graphs;

        /**
         * The list of data models.
         */
        private List<DataModel> dataModels;

        /**
         * The parameters for the simulation.
         */
        private Parameters parameters;

        public SimulationWrapper(Simulation simulation, Parameters parameters) {
            this.simulation = simulation;

            // There is no harm in allowing the simulation code to add parameters here; they can
            // be displayed in the output table if desired. jdramsey 20170118
            this.parameters = new Parameters(parameters);
        }

        @Override
        public void createData(Parameters parameters, boolean newModel) {
            this.simulation.createData(parameters, false);
            this.graphs = new ArrayList<>();
            this.dataModels = new ArrayList<>();
            for (int i = 0; i < this.simulation.getNumDataModels(); i++) {
                this.graphs.add(this.simulation.getTrueGraph(i));
                this.dataModels.add(this.simulation.getDataModel(i));
            }
        }

        @Override
        public int getNumDataModels() {
            return this.dataModels.size();
        }

        @Override
        public Graph getTrueGraph(int index) {
            if (this.graphs.get(index) == null) {
                return null;
            } else {
                return new EdgeListGraph(this.graphs.get(index));
            }
        }

        @Override
        public DataModel getDataModel(int index) {
            return this.dataModels.get(index);
        }

        @Override
        public DataType getDataType() {
            return this.simulation.getDataType();
        }

        @Override
        public String getDescription() {
            return this.simulation.getDescription();
        }

        @Override
        public List<String> getParameters() {
            return this.simulation.getParameters();
        }

        @Override
        public Class<? extends RandomGraph> getRandomGraphClass() {
            return simulation.getRandomGraphClass();
        }

        @Override
        public Class<? extends Simulation> getSimulationClass() {
            return simulation.getSimulationClass();
        }

        public void setParameters(Parameters parameters) {
            this.parameters = new Parameters(parameters);
        }

        public void setValue(String name, Object value) {
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException();
            }

            this.parameters.set(name, value);
        }

        public Object getValue(String name) {
            Object[] values = this.parameters.getValues(name);

            if (values == null || values.length == 0) {
                throw new NullPointerException("Expecting parameter to be defined: " + name);
            }

            return values[0];
        }

        public Simulation getSimulation() {
            return this.simulation;
        }

        public Parameters getSimulationSpecificParameters() {
            return this.parameters;
        }
    }

    /**
     * A wrapper for a run.
     */
    private static class Run {

        /**
         * The index of the algorithm-simulation wrapper to be used.
         */
        private final int algSimIndex;

        /**
         * The index of the run to be used.
         */
        private final int runIndex;

        /**
         * The index to be used.
         */
        private final int index;

        /**
         * The algorithm-simulation wrapper to be used.
         */
        private final AlgorithmSimulationWrapper wrapper;

        public Run(int algSimIndex, int runIndex, int index, AlgorithmSimulationWrapper wrapper) {
            this.runIndex = runIndex;
            this.algSimIndex = algSimIndex;
            this.index = index;
            this.wrapper = wrapper;
        }

        public int getAlgSimIndex() {
            return this.algSimIndex;
        }

        public int getRunIndex() {
            return this.runIndex;
        }

        public int getIndex() {
            return this.index;
        }

        public AlgorithmSimulationWrapper getWrapper() {
            return this.wrapper;
        }
    }

    /**
     * A wrapper for a simulation and an algorithm.
     */
    private class AlgorithmTask implements Callable<Void> {
        /**
         * The algorithm-simulation wrappers to be used.
         */
        private final List<AlgorithmSimulationWrapper> algorithmSimulationWrappers;

        /**
         * The algorithm wrappers to be used.
         */
        private final List<AlgorithmWrapper> algorithmWrappers;

        /**
         * The simulation wrappers to be used.
         */
        private final List<SimulationWrapper> simulationWrappers;

        /**
         * The statistics to be used.
         */
        private final Statistics statistics;

        /**
         * The number of graph types to be used.
         */
        private final int numGraphTypes;

        /**
         * The array of statistics to be used.
         */
        private final double[][][][] allStats;

        /**
         * The run to be used.
         */
        private final Run run;

        public AlgorithmTask(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                             List<AlgorithmWrapper> algorithmWrappers, List<SimulationWrapper> simulationWrappers,
                             Statistics statistics, int numGraphTypes, double[][][][] allStats, Run run) {
            this.algorithmSimulationWrappers = algorithmSimulationWrappers;
            this.simulationWrappers = simulationWrappers;
            this.algorithmWrappers = algorithmWrappers;
            this.statistics = statistics;
            this.numGraphTypes = numGraphTypes;
            this.allStats = allStats;
            this.run = run;
        }

        @Override
        public Void call() throws Exception {
            doRun(this.algorithmSimulationWrappers, this.algorithmWrappers,
                    this.simulationWrappers, this.statistics, this.numGraphTypes, this.allStats, this.run);
            return null;
        }

    }

}
