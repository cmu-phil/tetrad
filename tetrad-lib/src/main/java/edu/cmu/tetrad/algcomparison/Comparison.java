///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;

/**
 * Script to do a comparison of a list of algorithms using a list of statistics and a list of parameters and their
 * values.
 *
 * @author josephramsey
 * @author danielmalinsky
 * @version $Id: $Id
 */
public class Comparison implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The number of threads to use.
     */
    private int numThreads = 1;

    /**
     * The graph type used.
     */
    private boolean[] graphTypeUsed;

    /**
     * The output stream.
     */
    private transient PrintStream out;

    /**
     * Whether to save the graphs.
     */
    private boolean saveGraphs;

    /**
     * Whether to show the simulation indices.
     */
    private boolean showSimulationIndices;

    /**
     * Whether to show the algorithm indices.
     */
    private boolean showAlgorithmIndices;

    /**
     * Whether to show the utility calculations.
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
     * The result path.
     */
    private String resultsPath;

    /**
     * Whether to save the data.
     */
    private boolean saveData = false;

    /**
     * Whether to save the CPDAGs.
     */
    private boolean saveCPDAGs = false;

    /**
     * Whether to save the PAGs.
     */
    private boolean savePags = false;

    /**
     * The comparison graph type.
     */
    private ComparisonGraph comparisonGraph = ComparisonGraph.true_DAG;
    /**
     * Indicates whether the tables are tab-delimited.
     */
    private boolean tabDelimitedTables = false;
    /**
     * The output stream for local output. Could be null.
     */
    private transient PrintStream localOut = null;

    /**
     * Initializes a new instance of the Comparison class.
     * <p>
     * By default, the saveGraphs property is set to true. The showSimulationIndices, showAlgorithmIndices,
     * showUtilities, and sortByUtility properties are all set to false.
     * <p>
     * Usage: Comparison comparison = new Comparison();
     */
    public Comparison() {
        this.saveGraphs = true;
        this.showSimulationIndices = false;
        this.showAlgorithmIndices = false;
        this.showUtilities = false;
        this.sortByUtility = false;
    }

    /**
     * Sets the number of threads to be used for the concurrent execution.
     *
     * @param numThreads the number of threads to be set
     */
    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    /**
     * <p>compareFromFiles.</p>
     *
     * @param filePath   a {@link java.lang.String} object
     * @param algorithms a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithms} object
     * @param statistics a {@link edu.cmu.tetrad.algcomparison.statistic.Statistics} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public void compareFromFiles(String filePath, Algorithms algorithms, Statistics statistics, Parameters parameters) {
        compareFromFiles(filePath, filePath, algorithms, statistics, parameters);
    }

    /**
     * Compares algorithms.
     *
     * @param dataPath    Path to the directory where data and graph files have been saved.
     * @param resultsPath Path to the file where the results should be stored.
     * @param algorithms  The list of algorithms to be compared.
     * @param statistics  a {@link edu.cmu.tetrad.algcomparison.statistic.Statistics} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public void compareFromFiles(String dataPath, String resultsPath, Algorithms algorithms, Statistics statistics, Parameters parameters) {
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
        }

        compareFromSimulations(resultsPath, simulations, algorithms, statistics, parameters);
    }

    /**
     * Generates a report from external algorithms.
     *
     * @param dataPath    - The path to the data file.
     * @param resultsPath - The path to where the report will be saved.
     * @param algorithms  - The algorithms to compare.
     * @param statistics  - The statistics to include in the report.
     * @param parameters  - The parameters to use for the algorithms.
     */
    public void generateReportFromExternalAlgorithms(String dataPath, String resultsPath, Algorithms algorithms, Statistics statistics, Parameters parameters) {
        generateReportFromExternalAlgorithms(dataPath, resultsPath, "Comparison.txt", algorithms, statistics, parameters);
    }

    /**
     * Generates a report from external algorithms based on the given parameters.
     *
     * @param dataPath       The path to the data files.
     * @param resultsPath    The path to save the results.
     * @param outputFileName The name of the output file.
     * @param algorithms     The collection of algorithms to compare.
     * @param statistics     The statistics to include in the report.
     * @param parameters     The additional parameters for the algorithms.
     * @throws IllegalArgumentException if any algorithm in the collection is not an instance of ExternalAlgorithm.
     * @throws NullPointerException     if there are no files in the specified data path.
     */
    public void generateReportFromExternalAlgorithms(String dataPath, String resultsPath, String outputFileName, Algorithms algorithms, Statistics statistics, Parameters parameters) {

        this.saveGraphs = false;
        this.dataPath = dataPath;
        this.resultsPath = resultsPath;

        for (Algorithm algorithm : algorithms.getAlgorithms()) {
            if (!(algorithm instanceof ExternalAlgorithm)) {
                throw new IllegalArgumentException("Expecting all algorithms to implement ExternalAlgorithm.");
            }
        }

        Simulations simulations = new Simulations();

        File file = new File(this.dataPath, "save");
        File[] dirs = file.listFiles();

        if (dirs == null) {
            throw new NullPointerException("No files in " + file.getAbsolutePath());
        }

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
        }

        compareFromSimulations(this.resultsPath, simulations, outputFileName, algorithms, statistics, parameters);
    }

    /**
     * Compare simulation results using the provided parameters and write the comparison results to a file.
     *
     * @param resultsPath The path to write the comparison results file.
     * @param simulations The simulations to compare results from.
     * @param algorithms  The algorithms used in the simulations.
     * @param statistics  The statistics used in the simulations.
     * @param parameters  The parameters used in the simulations.
     */
    public void compareFromSimulations(String resultsPath, Simulations simulations, Algorithms algorithms, Statistics statistics, Parameters parameters) {
        compareFromSimulations(resultsPath, simulations, "Comparison.txt", algorithms, statistics, parameters);
    }

    /**
     * Compares the results obtained from simulations.
     *
     * @param resultsPath    The path to the directory containing the simulation results.
     * @param simulations    The simulations to compare the results from.
     * @param outputFileName The name of the output file.
     * @param algorithms     The algorithms used in the simulations.
     * @param statistics     The statistics used in the simulations.
     * @param parameters     The parameters used in the simulations.
     */
    public void compareFromSimulations(String resultsPath, Simulations simulations, String outputFileName,
                                       Algorithms algorithms, Statistics statistics, Parameters parameters) {
        compareFromSimulations(resultsPath, simulations, outputFileName, System.out, algorithms, statistics, parameters);
    }

    /**
     * Compares the results of different simulations and algorithms.
     *
     * @param resultsPath    the path to the results directory
     * @param simulations    the simulations object containing the simulation data
     * @param outputFileName the name of the output file
     * @param localOut       the local output stream
     * @param algorithms     the algorithms object containing the algorithm data
     * @param statistics     the statistics object containing the statistics data
     * @param parameters     the parameters object containing the parameter data
     */
    public void compareFromSimulations(String resultsPath, Simulations simulations, String outputFileName, PrintStream localOut,
                                       Algorithms algorithms, Statistics statistics, Parameters parameters) {
        this.resultsPath = resultsPath;

        if (localOut != null) {
            this.localOut = localOut;
        }

        setNumThreads(parameters.getInt(Params.NUM_THREADS));

        PrintStream stdout = (PrintStream) parameters.get("printStream", System.out);

        // Create output file.
        try {
            File dir = new File(resultsPath);
            if (!dir.mkdirs()) {
                // Ignore
            }
            File file = new File(dir, outputFileName);
            this.out = new PrintStream(Files.newOutputStream(file.toPath()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        println(new Date().toString());
        println("Results are being saved to " + resultsPath);

        // Set up simulations--create data and graphs, read in parameters. The parameters
        // are set in the parameter object.
        List<SimulationWrapper> simulationWrappers = new ArrayList<>();

        int numRuns = parameters.getInt("numRuns");

        for (Simulation simulation : simulations.getSimulations()) {
            List<SimulationWrapper> wrappers = getSimulationWrappers(simulation, parameters);

            for (SimulationWrapper wrapper : wrappers) {
                wrapper.createData(wrapper.getSimulationSpecificParameters(), true);
                simulationWrappers.add(wrapper);
            }
        }

        // Set up the algorithms.
        List<AlgorithmWrapper> algorithmWrappers = new ArrayList<>();

        for (Algorithm algorithm : algorithms.getAlgorithms()) {
            List<Integer> _dims = new ArrayList<>();
            List<String> varyingParameters = new ArrayList<>();

            List<String> parameters1 = new ArrayList<>(Params.getAlgorithmParameters(algorithm));
            parameters1.addAll(Params.getTestParameters(algorithm));
            parameters1.addAll(Params.getScoreParameters(algorithm));

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
                    stdout.println("Type mismatch: " + algorithmWrapper.getDescription() + " / " + simulationWrapper.getDescription());
                }

                if (algorithmWrapper.getAlgorithm() instanceof ExternalAlgorithm external) {
                    external.setSimIndex(simulationWrappers.indexOf(simulationWrapper));
                }

                algorithmSimulationWrappers.add(new AlgorithmSimulationWrapper(algorithmWrapper, simulationWrapper));
            }
        }

        // Run all the algorithms and compile statistics.
        double[][][][] allStats;

        try {
            allStats = calcStats(algorithmSimulationWrappers, simulationWrappers, statistics, numRuns, stdout);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        {
            int numTables = allStats.length;
            int numStats = allStats[0][0].length - 1;

            double[][][] statTables = calcStatTables(allStats, Mode.Average, numTables, algorithmSimulationWrappers, numStats, statistics);
            double[] utilities = calcUtilities(statistics, algorithmSimulationWrappers, statTables[0]);

            // Add utilities to the table as the last column.
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

            println();
            println("Simulations:");
            println();

            int i = 0;

            for (SimulationWrapper simulation : simulationWrappers) {
                println("Simulation " + (++i) + ": ");
                println(simulation.getDescription());
                println();

                printParameters(simulation.getParameters(), simulation.getSimulationSpecificParameters());

                println();
            }

            println("Algorithms:");
            println();

            for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                AlgorithmSimulationWrapper wrapper = algorithmSimulationWrappers.get(t);

                if (wrapper.getSimulationWrapper() == simulationWrappers.get(0)) {
                    println((t + 1) + ". " + wrapper.getAlgorithmWrapper().getDescription());
                }
            }


            // Print out the preliminary information for statistics types, etc.
            println();
            println("Statistics:");
            println();

            for (Statistic stat : statistics.getStatistics()) {
                println(stat.getAbbreviation() + " = " + stat.getDescription());
            }

            println();

            if (isSortByUtility()) {
                println();
                println("Sorting by utility, high to low.");
            }

            if (isShowUtilities()) {
                println();
                println("Weighting of statistics:");
                println();
                println("U = ");

                for (Statistic stat : statistics.getStatistics()) {
                    String statName = stat.getAbbreviation();
                    double weight = statistics.getWeight(stat);
                    if (weight != 0.0) {
                        println("    " + weight + " * f(" + statName + ")");
                    }
                }

                println();
                println("...normed to range between 0 and 1.");

                println();
                println("Note that f for each statistic is a function that maps the statistic to the ");
                println("interval [0, 1], with higher being better.");
            }

            println("Graphs are being compared to the " + this.comparisonGraph.toString().replace("_", " ") + ".");
            println("All statistics are individually summarized over " + numRuns + " runs using the indicated statistic.");

            println();

            statTables = calcStatTables(allStats, Mode.Average, numTables, algorithmSimulationWrappers, numStats, statistics);

            // Add utilities to the table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            // Print all the tables.
            printStats(statTables, statistics, Mode.Average, newOrder, algorithmSimulationWrappers, algorithmWrappers, simulationWrappers, utilities, parameters);


            statTables = calcStatTables(allStats, Mode.StandardDeviation, numTables, algorithmSimulationWrappers, numStats, statistics);

            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.StandardDeviation, newOrder, algorithmSimulationWrappers, algorithmWrappers, simulationWrappers, utilities, parameters);

            statTables = calcStatTables(allStats, Mode.MinValue, numTables, algorithmSimulationWrappers, numStats, statistics);

            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.MinValue, newOrder, algorithmSimulationWrappers, algorithmWrappers, simulationWrappers, utilities, parameters);

            statTables = calcStatTables(allStats, Mode.MaxValue, numTables, algorithmSimulationWrappers, numStats, statistics);

            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.MaxValue, newOrder, algorithmSimulationWrappers, algorithmWrappers, simulationWrappers, utilities, parameters);

            statTables = calcStatTables(allStats, Mode.MedianValue, numTables, algorithmSimulationWrappers, numStats, statistics);

            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.MedianValue, newOrder, algorithmSimulationWrappers, algorithmWrappers, simulationWrappers, utilities, parameters);
        }

        for (int i = 0; i < simulations.getSimulations().size(); i++) {
            saveToFiles(resultsPath + "/simulation" + (i + 1), simulations.getSimulations().get(i), parameters);
        }

        close();
    }

    private void println(String s) {
        this.out.println(s);

        if (localOut != null) {
            localOut.println(s);
        }
    }

    private void println() {
        this.out.println();

        if (localOut != null) {
            localOut.println();
        }
    }

    private void close() {
        this.out.close();

        if (localOut != null) {
            localOut.close();
        }
    }

    /**
     * Saves the simulation data to file in the specified data path.
     *
     * @param dataPath   the path where the data files will be saved
     * @param simulation the simulation object containing the data to be saved
     * @param parameters the parameters used in the simulation
     */
    public void saveToFiles(String dataPath, Simulation simulation, Parameters parameters) {

        File dir0 = new File(dataPath);
        File dir;

        dir = new File(dir0, "save");

        deleteFilesThenDirectory(dir);

        try {
            List<SimulationWrapper> simulationWrappers = getSimulationWrappers(simulation, parameters);

            int index = 0;

            for (SimulationWrapper simulationWrapper : simulationWrappers) {
                for (String param : simulationWrapper.getParameters()) {
                    parameters.set(param, simulationWrapper.getValue(param));
                }

                if (simulation.getNumDataModels() == 0) {
                    simulationWrapper.createData(simulationWrapper.getSimulationSpecificParameters(), true);
                }

                File subdir = dir;
                if (simulationWrappers.size() > 1) {
                    index++;

                    subdir = new File(dir, "" + index);
                    if (!subdir.mkdirs()) {
//                        TetradLogger.getInstance().forceLogMessage("Directory already exists: " + subdir);
                    }
                }

                File dir1 = new File(subdir, "graph");
                File dir2 = new File(subdir, "data");

                if (!dir1.mkdirs()) {
//                    TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir1);
                }
                if (!dir2.mkdirs()) {
//                    TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir2);
                }

                File dir3 = null;

                if (isSaveCPDAGs()) {
                    dir3 = new File(subdir, "cpdags");
                    if (!dir3.mkdirs()) {
//                        TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir3);
                    }
                }

                File dir4 = null;

                if (isSavePags()) {
                    dir4 = new File(subdir, "pags");
                    if (!dir4.mkdirs()) {
//                        TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir4);
                    }
                }

                for (int j = 0; j < simulationWrapper.getNumDataModels(); j++) {
                    File file2 = new File(dir1, "graph." + (j + 1) + ".txt");
                    Graph graph = simulationWrapper.getTrueGraph(j);

                    GraphSaveLoadUtils.saveGraph(graph, file2, false);

                    if (isSaveData()) {
                        File file = new File(dir2, "data." + (j + 1) + ".txt");
                        Writer out = new FileWriter(file);
                        DataModel dataModel = simulationWrapper.getDataModel(j);
                        DataWriter.writeRectangularData((DataSet) dataModel, out, '\t');
                        out.close();
                    }

                    if (isSaveCPDAGs()) {
                        File file3 = new File(dir3, "cpdag." + (j + 1) + ".txt");
                        GraphSaveLoadUtils.saveGraph(GraphTransforms.cpdagForDag(graph), file3, false);
                    }

                    if (isSavePags()) {
                        File file4 = new File(dir4, "pag." + (j + 1) + ".txt");
                        GraphSaveLoadUtils.saveGraph(GraphTransforms.dagToPag(graph), file4, false);
                    }

                }

                PrintStream out = new PrintStream(Files.newOutputStream(new File(subdir, "parameters.txt").toPath()));
                out.println(simulationWrapper.getDescription());
                out.println(simulationWrapper.getSimulationSpecificParameters());
                out.close();
            }
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("IO Exception: " + e.getMessage());
        }
    }

    /**
     * Saves the results of a single simulation to files.
     *
     * @param dataPath   The path to the directory where the files will be saved.
     * @param simulation The simulation object containing the data and graphs.
     * @param parameters The parameters used for the simulation.
     */
    public void saveToFilesSingleSimulation(String dataPath, Simulation simulation, Parameters parameters) {
        File dir0 = new File(dataPath);
        File dir = new File(dir0, "save");
        setNumThreads(1);

        deleteFilesThenDirectory(dir);
        if (!dir.mkdirs()){
//            TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir);
        }

        try {
            PrintStream _out = new PrintStream(Files.newOutputStream(new File(dir, "parameters.txt").toPath()));
            _out.println(simulation.getDescription());
            _out.println(parameters);
            _out.close();

            int numDataSets = simulation.getNumDataModels();
            if (numDataSets <= 0) {

                File dir1 = new File(dir, "graph");
                File dir2 = new File(dir, "data");

                if (!dir1.mkdirs()) {
//                    TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir1);
                }
                if (!dir2.mkdirs()) {
//                    TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir2);
                }

                return;
            }

            File dir1 = new File(dir, "graph");
            File dir2 = new File(dir, "data");

            if (!dir1.mkdirs()) {
//                TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir1);
            }
            if (!dir2.mkdirs()) {
//                TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir2);
            }

            File dir3 = null;

            if (isSaveCPDAGs()) {
                dir3 = new File(dir, "cpdags");
                if (!dir3.mkdirs()) {
//                    TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir3);
                }
            }

            File dir4 = null;

            if (isSavePags()) {
                dir4 = new File(dir, "pags");
                if (!dir4.mkdirs()) {
//                    TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir4);
                }
            }

            for (int j = 0; j < simulation.getNumDataModels(); j++) {
                File file2 = new File(dir1, "graph." + (j + 1) + ".txt");
                Graph graph = simulation.getTrueGraph(j);

                GraphSaveLoadUtils.saveGraph(graph, file2, false);

                File file = new File(dir2, "data." + (j + 1) + ".txt");
                Writer out = new FileWriter(file);
                DataModel dataModel = simulation.getDataModel(j);
                DataWriter.writeRectangularData((DataSet) dataModel, out, '\t');
                out.close();

                if (isSaveCPDAGs()) {
                    File file3 = new File(dir3, "cpdag." + (j + 1) + ".txt");
                    GraphSaveLoadUtils.saveGraph(GraphTransforms.cpdagForDag(graph), file3, false);
                }

                if (isSavePags()) {
                    File file4 = new File(dir4, "pag." + (j + 1) + ".txt");
                    GraphSaveLoadUtils.saveGraph(GraphTransforms.dagToPag(graph), file4, false);
                }
            }
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("IO Exception: " + e.getMessage());
        }
    }

    /**
     * Generates a configuration file containing information about available algorithms, statistics, independence tests,
     * scores, and simulations.
     *
     * @param path The path to save the configuration file.
     */
    public void configuration(String path) {
        try {
            if (!new File(path).mkdirs())
                TetradLogger.getInstance().forceLogMessage("Path already exists: " + new File(path));

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

            for (Class<?> clazz : new ArrayList<>(algorithms)) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor<?>[] constructors = clazz.getConstructors();

                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterTypes().length == 1 && constructor.getParameterTypes()[0] == IndependenceWrapper.class) {
                        Algorithm algorithm = (Algorithm) constructor.newInstance(FisherZ.class.getDeclaredConstructor().newInstance());
                        out.println(clazz.getSimpleName() + ": " + algorithm.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(algorithm.getParameters(), allParams);
                        }
                        if (TakesExternalGraph.class.isAssignableFrom(clazz)) {
                            out.println("\t" + clazz.getSimpleName() + " can take an external graph from some other algorithm as input");
                        }
                    }
                }
            }

            out.println();
            out.println("Algorithms that take a score (using an example score):");
            out.println();

            for (Class<?> clazz : new ArrayList<>(algorithms)) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor<?>[] constructors = clazz.getConstructors();

                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterTypes().length == 1 && constructor.getParameterTypes()[0] == ScoreWrapper.class) {
                        Algorithm algorithm = (Algorithm) constructor.newInstance(BdeuScore.class.getDeclaredConstructor().newInstance());
                        out.println(clazz.getSimpleName() + ": " + algorithm.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(algorithm.getParameters(), allParams);
                        }
                    }

                }
            }

            out.println();
            out.println("Algorithms with blank constructor:");
            out.println();

            for (Class<?> clazz : new ArrayList<>(algorithms)) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor<?>[] constructors = clazz.getConstructors();

                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        Algorithm algorithm = (Algorithm) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + algorithm.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(algorithm.getParameters(), allParams);
                        }
                    }
                }
            }

            out.println();
            out.println("Available Statistics:");
            out.println();

            for (Class<?> clazz : statistics) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor<?>[] constructors = clazz.getConstructors();

                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        Statistic statistic = (Statistic) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + statistic.getDescription());
                    }
                }
            }

            out.println();
            out.println("Available Independence Tests:");
            out.println();

            for (Class<?> clazz : independenceWrappers) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor<?>[] constructors = clazz.getConstructors();

                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        IndependenceWrapper independence = (IndependenceWrapper) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + independence.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(independence.getParameters(), allParams);
                        }
                    }
                }
            }

            out.println();
            out.println("Available Scores:");
            out.println();

            for (Class<?> clazz : scoreWrappers) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor<?>[] constructors = clazz.getConstructors();

                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        ScoreWrapper score = (ScoreWrapper) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + score.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(score.getParameters(), allParams);
                        }
                    }
                }
            }

            out.println();
            out.println("Available Simulations:");
            out.println();

            for (Class<?> clazz : simulations) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor<?>[] constructors = clazz.getConstructors();

                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterTypes().length == 0) {
                        Simulation simulation = (Simulation) constructor.newInstance();
                        out.println(clazz.getSimpleName() + ": " + simulation.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(simulation.getParameters(), allParams);
                        }
                    }
                }
            }

            out.println();

            out.close();
        } catch (Exception e) {
            TetradLogger.getInstance().forceLogMessage("Exception: " + e.getMessage());
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

    /**
     * Calculates statistics for algorithm simulations.
     *
     * @param algorithmSimulationWrappers A list of AlgorithmSimulationWrapper objects representing the algorithm
     *                                    simulations.
     * @param simulationWrappers          A list of SimulationWrapper objects representing the simulations.
     * @param statistics                  The Statistics object containing statistical measures to be computed.
     * @param numRuns                     The number of runs for each simulation.
     * @param stdout                      The PrintStream object for printing the progress.
     * @return A 4-dimensional array of doubles containing the computed statistics. The dimensions are as follows: -
     * first dimension: graph types (four types in total) - second dimension: algorithmSimulationWrappers size - third
     * dimension: statistics size + one (additional slot for storing total statistics) - fourth dimension: numRuns
     */
    private double[][][][] calcStats(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                                     List<SimulationWrapper> simulationWrappers, Statistics statistics,
                                     int numRuns, PrintStream stdout) {
        final int numGraphTypes = 4;

        this.graphTypeUsed = new boolean[4];

        double[][][][] allStats = new double[4][algorithmSimulationWrappers.size()][statistics.size() + 1][numRuns];

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int algSimIndex = 0; algSimIndex < algorithmSimulationWrappers.size(); algSimIndex++) {
            for (int runIndex = 0; runIndex < numRuns; runIndex++) {
                Run run = new Run(algSimIndex, runIndex);
                Callable<Boolean> task = new AlgorithmTask(algorithmSimulationWrappers, simulationWrappers, statistics, numGraphTypes, allStats, run, stdout);
                tasks.add(task);
            }
        }

        ForkJoinPool pool = new ForkJoinPool(numThreads);

        try {
            pool.invokeAll(tasks);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        return allStats;
    }

    /**
     * Checks if the simulation indices are currently being shown.
     *
     * @return true if the simulation indices are being shown, false otherwise
     */
    public boolean isShowSimulationIndices() {
        return this.showSimulationIndices;
    }

    /**
     * Sets whether to show simulation indices or not.
     *
     * @param showSimulationIndices true to show simulation indices, false otherwise
     */
    public void setShowSimulationIndices(boolean showSimulationIndices) {
        this.showSimulationIndices = showSimulationIndices;
    }

    /**
     * Indicates whether the algorithm indices should be shown.
     *
     * @return {@code true} if the algorithm indices should be shown, {@code false} otherwise.
     */
    public boolean isShowAlgorithmIndices() {
        return this.showAlgorithmIndices;
    }

    /**
     * Sets whether to show algorithm indices.
     *
     * @param showAlgorithmIndices true to show algorithm indices, false otherwise
     */
    public void setShowAlgorithmIndices(boolean showAlgorithmIndices) {
        this.showAlgorithmIndices = showAlgorithmIndices;
    }

    /**
     * Checks if the utilities are currently being shown.
     *
     * @return true if the utilities are being shown, false otherwise.
     */
    public boolean isShowUtilities() {
        return this.showUtilities;
    }

    /**
     * Sets the value of the showUtilities property.
     *
     * @param showUtilities the new value for the showUtilities property
     */
    public void setShowUtilities(boolean showUtilities) {
        this.showUtilities = showUtilities;
    }

    /**
     * Returns whether utility does the sorting.
     *
     * @return {@code true} if utility does the sorting, {@code false} otherwise.
     */
    public boolean isSortByUtility() {
        return this.sortByUtility;
    }

    /**
     * Set the flag to determine if utility should do the sorting.
     *
     * @param sortByUtility a flag indicating whether to sort by utility or not
     */
    public void setSortByUtility(boolean sortByUtility) {
        this.sortByUtility = sortByUtility;
    }

    /**
     * Checks if the CPDAGs are saved.
     *
     * @return true if the CPDAGs are saved, false otherwise.
     */
    public boolean isSaveCPDAGs() {
        return this.saveCPDAGs;
    }

    /**
     * Sets whether to save CPDAGs.
     *
     * @param saveCPDAGs indicates whether to save CPDAGs or not
     */
    public void setSaveCPDAGs(boolean saveCPDAGs) {
        this.saveCPDAGs = saveCPDAGs;
    }

    /**
     * Checks if the "savePags" variable is true or false.
     *
     * @return true if the "savePags" variable is true, false otherwise.
     */
    public boolean isSavePags() {
        return this.savePags;
    }

    /**
     * Sets the value of 'savePags' flag. This method is used to set the value of the 'savePags' flag, which indicates
     * whether the pags should be saved or not.
     *
     * @param savePags a boolean value indicating whether to save the pags or not.
     */
    public void setSavePags(boolean savePags) {
        this.savePags = savePags;
    }

    /**
     * Returns the status of whether data is being saved or not.
     *
     * @return {@code true} if data is being saved, {@code false} otherwise.
     */
    public boolean isSaveData() {
        return saveData;
    }

    /**
     * Sets the flag indicating whether to save data.
     *
     * @param saveData true if data should be saved, false otherwise
     */
    public void setSaveData(boolean saveData) {
        this.saveData = saveData;
    }

    /**
     * Sets whether to save graphs.
     *
     * @param saveGraphs true to save graphs, false otherwise
     */
    public void setSaveGraphs(boolean saveGraphs) {
        this.saveGraphs = saveGraphs;
    }

    /**
     * Returns the ComparisonGraph instance.
     *
     * @return the ComparisonGraph instance
     */
    public ComparisonGraph getComparisonGraph() {
        return this.comparisonGraph;
    }

    /**
     * Sets the comparison graph.
     *
     * @param comparisonGraph the comparison graph to be set
     * @throws NullPointerException if the comparison graph is null
     */
    public void setComparisonGraph(ComparisonGraph comparisonGraph) {
        if (comparisonGraph == null) {
            throw new NullPointerException("Null compare graph.");
        }
        this.comparisonGraph = comparisonGraph;
    }

    private void printParameters(List<String> names, Parameters parameters) {
        println("Parameters:");
        ParamDescriptions descriptions = ParamDescriptions.getInstance();

        for (String name : names) {
            ParamDescription description = descriptions.get(name);
            Object defaultValue = description.getDefaultValue();
            Object value = parameters.get(name);

            if (defaultValue instanceof Double) {
                println(description.getShortDescription() + " = " + value.toString());
            } else if (defaultValue instanceof Integer) {
                println(description.getShortDescription() + " = " + value.toString());
            } else if (defaultValue instanceof Boolean) {
                boolean b = (Boolean) value;
                println(description.getShortDescription() + " = " + (b ? "Yes" : "No"));
            } else if (defaultValue instanceof String) {
                println(description.getShortDescription() + " = " + value);
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
                if (!currentFile.delete())
                    TetradLogger.getInstance().forceLogMessage("File could not be deleted: " + currentFile);
            }
        }

        if (!dir.delete()) TetradLogger.getInstance().forceLogMessage("Directory could not be deleted: " + dir);
    }

    private void doRun(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers, List<SimulationWrapper> simulationWrappers, Statistics statistics,
                       int numGraphTypes, double[][][][] allStats, Run run, PrintStream stdout) {
       if (Thread.currentThread().isInterrupted()) {
           return;
       }

        stdout.println();
        stdout.println("Run " + (run.runIndex() + 1));
        stdout.println();

        AlgorithmSimulationWrapper algorithmSimulationWrapper = algorithmSimulationWrappers.get(run.algSimIndex());
        AlgorithmWrapper algorithmWrapper = algorithmSimulationWrapper.getAlgorithmWrapper();
        SimulationWrapper simulationWrapper = algorithmSimulationWrapper.getSimulationWrapper();

        DataModel data = simulationWrapper.getDataModel(run.runIndex());
        Graph trueGraph = simulationWrapper.getTrueGraph(run.runIndex());

        stdout.println((run.algSimIndex() + 1) + ". " + algorithmWrapper.getDescription() + " simulationWrapper: " + simulationWrapper.getDescription());

        long start = MillisecondTimes.cpuTimeMillis();
        Graph graphOut;

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
                int randomSelectionSize = algorithmWrapper.getAlgorithmSpecificParameters().getInt("randomSelectionSize");
                for (int i = 0; i < FastMath.min(numDataModels, randomSelectionSize); i++) {
                    dataModels.add(simulationWrapper.getSimulation().getDataModel(indices.get(i)));
                }

                Parameters _params = algorithmWrapper.getAlgorithmSpecificParameters();
                graphOut = ((MultiDataSetAlgorithm) algorithm).search(dataModels, _params);
            } else {
                Parameters _params = algorithmWrapper.getAlgorithmSpecificParameters();
                graphOut = algorithm.search(data, _params);
            }
        } catch (Exception e) {
            TetradLogger.getInstance().forceLogMessage("Could not run " + algorithmWrapper.getDescription());
            return;
        }

        int simIndex = simulationWrappers.indexOf(simulationWrapper) + 1;

        long stop = MillisecondTimes.cpuTimeMillis();

        long elapsed = (stop - start);

        saveGraph(this.resultsPath, graphOut, run.runIndex(), simIndex, algorithmWrapper, elapsed, stdout);

        if (trueGraph != null) {
            graphOut = GraphUtils.replaceNodes(graphOut, trueGraph.getNodes());


            Graph[] est = new Graph[numGraphTypes];

            Graph comparisonGraph;

            if (this.comparisonGraph == ComparisonGraph.true_DAG) {
                comparisonGraph = new EdgeListGraph(trueGraph);
            } else if (this.comparisonGraph == ComparisonGraph.CPDAG_of_the_true_DAG) {
                comparisonGraph = GraphTransforms.cpdagForDag(trueGraph);
            } else if (this.comparisonGraph == ComparisonGraph.PAG_of_the_true_DAG) {
                comparisonGraph = GraphTransforms.dagToPag(trueGraph);
            } else {
                throw new IllegalArgumentException("Unrecognized graph type.");
            }

            est[0] = new EdgeListGraph(graphOut);
            this.graphTypeUsed[0] = true;

            if (data.isMixed()) {
                est[1] = getSubgraph(est[0], true, true, simulationWrapper.getDataModel(run.runIndex()));
                est[2] = getSubgraph(est[0], true, false, simulationWrapper.getDataModel(run.runIndex()));
                est[3] = getSubgraph(est[0], false, false, simulationWrapper.getDataModel(run.runIndex()));

                this.graphTypeUsed[1] = true;
                this.graphTypeUsed[2] = true;
                this.graphTypeUsed[3] = true;
            }

            Graph[] truth = new Graph[numGraphTypes];

            truth[0] = new EdgeListGraph(comparisonGraph);

            if (data.isMixed()) {
                truth[1] = getSubgraph(comparisonGraph, true, true, simulationWrapper.getDataModel(run.runIndex()));
                truth[2] = getSubgraph(comparisonGraph, true, false, simulationWrapper.getDataModel(run.runIndex()));
                truth[3] = getSubgraph(comparisonGraph, false, false, simulationWrapper.getDataModel(run.runIndex()));
            }

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
                        stat = _stat.getValue(truth[u], est[u], data);
                    }

                    synchronized (this) {
                        allStats[u][run.algSimIndex()][statIndex][run.runIndex()] = stat;
                    }
                }
            }

        }

        if (algorithmWrapper.getAlgorithm() instanceof ExternalAlgorithm extAlg) {
            extAlg.setSimIndex(simulationWrappers.indexOf(simulationWrapper));
            extAlg.setSimulation(simulationWrapper.getSimulation());
            extAlg.setPath(this.resultsPath);
        }
    }

    private void saveGraph(String resultsPath, Graph graph, int i, int simIndex, AlgorithmWrapper algorithmWrapper, long elapsed, PrintStream stdout) {
        if (!this.saveGraphs) {
            return;
        }

        try {
            String description = algorithmWrapper.getDescription().replace(" ", "_");

            File file;
            File fileElapsed;

            File dir = new File(resultsPath, "results/" + description + "/" + simIndex);
            if (!dir.mkdirs()) {
//                TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dir);
            }

            File dirElapsed = new File(resultsPath, "elapsed/" + description + "/" + simIndex);
            if (!dirElapsed.mkdirs()) {
//                TetradLogger.getInstance().forceLogMessage("Directory already exists: " + dirElapsed);
            }

            if (resultsPath != null) {
                file = new File(dir, "graph." + (i + 1) + ".txt");
                fileElapsed = new File(dirElapsed, "graph." + (i + 1) + ".txt");
            } else {
                throw new IllegalArgumentException("Results path not provided.");
            }

            PrintStream out = new PrintStream(file);
            stdout.println("Saving graph to " + file.getAbsolutePath());
            out.println(graph);
            out.close();

            PrintStream outElapsed = new PrintStream(fileElapsed);
            outElapsed.println(elapsed);
            outElapsed.close();
        } catch (FileNotFoundException e) {
            TetradLogger.getInstance().forceLogMessage("File not found exception: " + e.getMessage());
        }
    }

    private String getHeader(int u) {
        return switch (u) {
            case 0 -> "All edges";
            case 1 -> "Discrete-discrete";
            case 2 -> "Discrete-continuous";
            case 3 -> "Continuous-continuous";
            default -> throw new IllegalStateException();
        };
    }

    private double[][][] calcStatTables(double[][][][] allStats, Mode mode, int numTables, List<AlgorithmSimulationWrapper> wrappers, int numStats, Statistics statistics) {
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
                            List<String> _parameterNames = new ArrayList<>(Params.getAlgorithmParameters(algorithmWrapper.getAlgorithm()));
                            _parameterNames.addAll(Params.getScoreParameters(algorithmWrapper.getAlgorithm()));
                            _parameterNames.addAll(Params.getTestParameters(algorithmWrapper.getAlgorithm()));

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
                        double mean = StatUtils.mean(allStats[u][i][j]);
                        statTables[u][i][j] = mean;
                    } else if (mode == Mode.MinValue) {
                        statTables[u][i][j] = StatUtils.min(allStats[u][i][j]);
                    } else if (mode == Mode.MaxValue) {
                        statTables[u][i][j] = StatUtils.max(allStats[u][i][j]);
                    } else if (mode == Mode.StandardDeviation) {
                        statTables[u][i][j] = StatUtils.sd(allStats[u][i][j]);
                    } else if (mode == Mode.MedianValue) {
                        statTables[u][i][j] = StatUtils.median(allStats[u][i][j]);
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }

        return statTables;
    }

    private void printStats(double[][][] statTables, Statistics statistics, Mode mode, int[] newOrder, List<AlgorithmSimulationWrapper> algorithmSimulationWrappers, List<AlgorithmWrapper> algorithmWrappers, List<SimulationWrapper> simulationWrappers, double[] utilities, Parameters parameters) {

        if (mode == Mode.Average) {
            println("AVERAGE VALUE");
        } else if (mode == Mode.StandardDeviation) {
            println("STANDARD DEVIATION");
        } else if (mode == Mode.MinValue) {
            println("MIN VALUE");
        } else if (mode == Mode.MaxValue) {
            println("MAX VALUE");
        } else if (mode == Mode.MedianValue) {
            println("MEDIAN VALUE");
        } else {
            throw new IllegalStateException();
        }

        int numTables = statTables.length;
        int numStats = statistics.size();

        NumberFormat nf = new DecimalFormat("0.00");
        NumberFormat smallNf = new DecimalFormat("0.00E0");

        println();

        for (int u = 0; u < numTables; u++) {
            if (!this.graphTypeUsed[u]) {
                continue;
            }

            int rows = algorithmSimulationWrappers.size() + 1;
            int cols = (isShowSimulationIndices() ? 1 : 0) + (isShowAlgorithmIndices() ? 1 : 0) + numStats + (isShowUtilities() ? 1 : 0);

            TextTable table = new TextTable(rows, cols);
            table.setDelimiter(tabDelimitedTables ? TextTable.Delimiter.TAB : TextTable.Delimiter.JUSTIFIED);

            int initialColumn = 0;

            if (isShowSimulationIndices()) {
                table.setToken(0, initialColumn, "Sim");

                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    Simulation simulation = algorithmSimulationWrappers.get(newOrder[t]).getSimulationWrapper();
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

                    if (stat == Double.POSITIVE_INFINITY) {
                        table.setToken(t + 1, initialColumn + statIndex, "Yes");
                    } else if (stat == Double.NEGATIVE_INFINITY) {
                        table.setToken(t + 1, initialColumn + statIndex, "No");
                    } else if (Double.isNaN(stat)) {
                        table.setToken(t + 1, initialColumn + statIndex, "*");
                    } else {
                        table.setToken(t + 1, initialColumn + statIndex, FastMath.abs(stat) < FastMath.pow(10, -smallNf.getMaximumFractionDigits()) && stat != 0 ? smallNf.format(stat) : nf.format(stat));
                    }
                }

                if (isShowUtilities()) {
                    table.setToken(t + 1, initialColumn + numStats, nf.format(utilities[newOrder[t]]));
                }
            }

            println(getHeader(u));
            println();
            println(table.toString());
        }
    }

    private double[] calcUtilities(Statistics statistics, List<AlgorithmSimulationWrapper> wrappers, double[][] stats) {

        // Calculate utilities for the first table.
        double[] utilities = new double[wrappers.size()];

        for (int t = 0; t < wrappers.size(); t++) {
            int j = -1;

            Iterator<Statistic> it2 = statistics.getStatistics().iterator();

            double sum = 0.0;
            double max = 0.0;

            while (it2.hasNext()) {
                Statistic stat = it2.next();
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

    private int[] sort(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers, double[] utilities) {
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

        order.sort((o1, o2) -> {
            double u1 = _utilities[o1];
            double u2 = _utilities[o2];
            if (Double.isNaN(u1)) {
                u1 = 0.0;
            }
            if (Double.isNaN(u2)) {
                u2 = 0.0;
            }
            return -Double.compare(u1, u2);
        });

        int[] newOrder = new int[algorithmSimulationWrappers.size()];
        for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
            newOrder[t] = order.get(t);
        }

        return newOrder;
    }

    private synchronized Graph getSubgraph(Graph graph, boolean discrete1, boolean discrete2, DataModel DataModel) {
        if (discrete1 && discrete2) {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = DataModel.getVariable(edge.getNode1().getName());
                Node node2 = DataModel.getVariable(edge.getNode2().getName());

                if (node1 instanceof DiscreteVariable && node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        } else if (!discrete1 && !discrete2) {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = DataModel.getVariable(edge.getNode1().getName());
                Node node2 = DataModel.getVariable(edge.getNode2().getName());

                if (node1 instanceof ContinuousVariable && node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        } else {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = DataModel.getVariable(edge.getNode1().getName());
                Node node2 = DataModel.getVariable(edge.getNode2().getName());

                if (node1 instanceof DiscreteVariable && node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }

                if (node1 instanceof ContinuousVariable && node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        }
    }

    /**
     * Sets the flag indicating whether tab-delimited tables should be used.
     *
     * @param tabDelimitedTables true if tab-delimited tables should be used; false otherwise
     */
    public void setTabDelimitedTables(boolean tabDelimitedTables) {
        this.tabDelimitedTables = tabDelimitedTables;
    }

    /**
     * An enum of comparison graphs types.
     */
    public enum ComparisonGraph {

        /**
         * Constant for the CPDAG of the true DAG.
         */
        CPDAG_of_the_true_DAG,

        /**
         * Constant for the PAG of the true DAG.
         */
        PAG_of_the_true_DAG,

        /**
         * Constant for the true DAG.
         */
        true_DAG

    }

    /**
     * The Mode class represents different calculation modes for a given dataset.
     */
    private enum Mode {
        /**
         * Represents the average calculation mode.
         */
        Average,

        /**
         * Represents the calculation mode for finding the maximum value in a dataset.
         */
        MaxValue,

        /**
         * The MedianValue class represents a calculation mode for finding the median value in a dataset.
         */
        MedianValue,

        /**
         * Represents the calculation mode for finding the minimum value in a dataset.
         */
        MinValue,

        /**
         * Represents the standard deviation calculation mode.
         */
        StandardDeviation

    }

    /**
     * An algorithm wrapper.
     */
    private static class AlgorithmWrapper implements Algorithm {

        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The algorithm.
         */
        private final Algorithm algorithm;

        /**
         * The parameters.
         */
        private final Parameters parameters;

        /**
         * The overridden parameters.
         */
        private final List<String> overriddenParameters = new ArrayList<>();

        public AlgorithmWrapper(Algorithm algorithm, Parameters parameters) {
            this.algorithm = algorithm;
            this.parameters = new Parameters(parameters);
        }

        @Override
        public Graph search(DataModel data, Parameters parameters) {
            return this.algorithm.search(data, this.parameters);
        }

        @Override
        public Graph getComparisonGraph(Graph graph) {
            return this.algorithm.getComparisonGraph(graph);
        }

        @Override
        public String getDescription() {
            StringBuilder description = new StringBuilder();
            description.append(this.algorithm.getDescription());

            if (!this.overriddenParameters.isEmpty()) {
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

    private static class AlgorithmSimulationWrapper implements Algorithm {

        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The simulation wrapper.
         */
        private final SimulationWrapper simulationWrapper;

        /**
         * The algorithm wrapper.
         */
        private final AlgorithmWrapper algorithmWrapper;

        public AlgorithmSimulationWrapper(AlgorithmWrapper algorithm, SimulationWrapper simulation) {
            this.algorithmWrapper = algorithm;
            this.simulationWrapper = simulation;
        }

        /**
         * <p>search.</p>
         *
         * @param data       a {@link edu.cmu.tetrad.data.DataModel} object
         * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
         * @return a {@link edu.cmu.tetrad.graph.Graph} object
         */
        @Override
        public Graph search(DataModel data, Parameters parameters) {
            return this.algorithmWrapper.getAlgorithm().search(data, parameters);
        }

        /**
         * <p>getComparisonGraph.</p>
         *
         * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
         * @return a {@link edu.cmu.tetrad.graph.Graph} object
         */
        @Override
        public Graph getComparisonGraph(Graph graph) {
            return this.algorithmWrapper.getComparisonGraph(graph);
        }

        /**
         * <p>getDescription.</p>
         *
         * @return a {@link java.lang.String} object
         */
        @Override
        public String getDescription() {
            throw new IllegalArgumentException();
        }

        /**
         * <p>getDataType.</p>
         *
         * @return a {@link edu.cmu.tetrad.data.DataType} object
         */
        @Override
        public DataType getDataType() {
            return this.algorithmWrapper.getDataType();
        }

        /**
         * <p>getParameters.</p>
         *
         * @return a {@link java.util.List} object
         */
        @Override
        public List<String> getParameters() {
            List<String> params = new ArrayList<>(this.simulationWrapper.getParameters());
            params.addAll(this.algorithmWrapper.getParameters());
            return params;
        }

        /**
         * <p>getAlgorithmWrapper.</p>
         *
         * @return a simulation wrapper
         */
        public SimulationWrapper getSimulationWrapper() {
            return this.simulationWrapper;
        }

        /**
         * <p>getAlgorithmWrapper.</p>
         *
         * @return an algorithm wrapper
         */
        public AlgorithmWrapper getAlgorithmWrapper() {
            return this.algorithmWrapper;
        }
    }

    /**
     * A simulation wrapper.
     */
    private static class SimulationWrapper implements Simulation {
        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The simulation.
         */
        private final Simulation simulation;
        /**
         * The parameters.
         */
        private final Parameters parameters;
        /**
         * The graphs.
         */
        private List<Graph> graphs;
        /**
         * The data models.
         */
        private List<DataModel> dataModels;

        /**
         * {@inheritDoc}
         */
        public SimulationWrapper(Simulation simulation, Parameters parameters) {
            if (simulation == null) {
                throw new NullPointerException("Simulation cannot be null.");
            }

            this.simulation = simulation;

            // There is no harm in allowing the simulation code to add parameters here; they can
            // be displayed in the output table if desired. Jdramsey 20170118
            this.parameters = new Parameters(parameters);

            this.graphs = new ArrayList<>();
            this.dataModels = new ArrayList<>();
            for (int i = 0; i < this.simulation.getNumDataModels(); i++) {
                this.graphs.add(this.simulation.getTrueGraph(i));
                this.dataModels.add(this.simulation.getDataModel(i));
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void createData(Parameters parameters, boolean newModel) {
            if (newModel) {
                this.simulation.createData(parameters, newModel);
            }

            this.graphs = new ArrayList<>();
            this.dataModels = new ArrayList<>();
            for (int i = 0; i < this.simulation.getNumDataModels(); i++) {
                this.graphs.add(this.simulation.getTrueGraph(i));
                this.dataModels.add(this.simulation.getDataModel(i));
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumDataModels() {
            if (this.dataModels == null) {
                return 0;
            }
            return this.dataModels.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Graph getTrueGraph(int index) {
            if (this.graphs.get(index) == null) {
                return null;
            } else {
                return new EdgeListGraph(this.graphs.get(index));
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized DataModel getDataModel(int index) {
            return this.dataModels.get(index);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DataType getDataType() {
            return this.simulation.getDataType();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDescription() {
            return this.simulation.getDescription();
        }

        /**
         * {@inheritDoc}
         */
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
            return getSimulationClass();
        }

        /**
         * Set the value of a parameter.
         *
         * @param name  The name of the parameter.
         * @param value The value of the parameter.
         * @throws IllegalArgumentException if the value is not an instance of Number.
         */
        public void setValue(String name, Object value) {
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException();
            }

            this.parameters.set(name, value);
        }

        /**
         * {@inheritDoc}
         */
        public Object getValue(String name) {
            Object[] values = this.parameters.getValues(name);

            if (values == null || values.length == 0) {
                throw new NullPointerException("Expecting parameter to be defined: " + name);
            }

            return values[0];
        }

        /**
         * {@inheritDoc}
         */
        public Simulation getSimulation() {
            return this.simulation;
        }

        /**
         * {@inheritDoc}
         */
        public Parameters getSimulationSpecificParameters() {
            return this.parameters;
        }
    }

    /**
     * A run.
     *
     * @param algSimIndex The algorithm simulation index.
     * @param runIndex    The run index.
     */
    private record Run(int algSimIndex, int runIndex) {

        /**
         * Constructs a new run.
         *
         * @param algSimIndex the algorithm simulation index
         * @param runIndex    the run index
         */
        private Run {
        }

        /**
         * <p>Getter for the field <code>algSimIndex</code>.</p>
         *
         * @return the algorithm simulation index
         */
        @Override
        public int algSimIndex() {
            return this.algSimIndex;
        }

        /**
         * <p>Getter for the field <code>runIndex</code>.</p>
         *
         * @return the run index
         */
        @Override
        public int runIndex() {
            return this.runIndex;
        }

    }

    /**
     * A task for running an algorithm.
     */
    private class AlgorithmTask implements Callable<Boolean> {

        /**
         * The algorithm simulation wrappers.
         */
        private final List<AlgorithmSimulationWrapper> algorithmSimulationWrappers;

        /**
         * The simulation wrappers.
         */
        private final List<SimulationWrapper> simulationWrappers;

        /**
         * The statistics.
         */
        private final Statistics statistics;

        /**
         * The number of graph types.
         */
        private final int numGraphTypes;

        /**
         * The statistics.
         */
        private final double[][][][] allStats;

        /**
         * The run.
         */
        private final Run run;

        /**
         * The standard output.
         */
        private transient final PrintStream stdout;

        /**
         * Constructs a new algorithm task.
         *
         * @param algorithmSimulationWrappers the algorithm simulation wrappers
         * @param simulationWrappers          the simulation wrappers
         * @param statistics                  the statistics
         * @param numGraphTypes               the number of graph types
         * @param allStats                    the statistics
         * @param run                         the run
         * @param stdout                      the standard output
         */
        public AlgorithmTask(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                             List<SimulationWrapper> simulationWrappers, Statistics statistics,
                             int numGraphTypes, double[][][][] allStats, Run run, PrintStream stdout) {
            this.algorithmSimulationWrappers = algorithmSimulationWrappers;
            this.simulationWrappers = simulationWrappers;
            this.statistics = statistics;
            this.numGraphTypes = numGraphTypes;
            this.allStats = allStats;
            this.run = run;
            this.stdout = stdout;
        }

        /**
         * Does a run.
         *
         * @return true.
         */
        @Override
        public Boolean call() {
            doRun(this.algorithmSimulationWrappers, this.simulationWrappers, this.statistics, this.numGraphTypes, this.allStats, this.run, this.stdout);
            return true;
        }
    }
}
