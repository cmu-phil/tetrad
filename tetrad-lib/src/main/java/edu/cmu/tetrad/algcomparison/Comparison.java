///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.ElapsedTime;
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
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.*;
import org.reflections.Reflections;

import java.io.*;
import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;

/**
 * Script to do a comparison of a list of algorithms using a list of statistics
 * and a list of parameters and their values.
 *
 * @author jdramsey
 * @author Daniel Malinsky
 */
public class Comparison {

    public enum ComparisonGraph {
        true_DAG, CPDAG_of_the_true_DAG, PAG_of_the_true_DAG
    }

    private boolean[] graphTypeUsed;
    private PrintStream out;
    private boolean tabDelimitedTables;
    private boolean saveGraphs;
    private boolean showSimulationIndices;
    private boolean showAlgorithmIndices;
    private boolean showUtilities;
    private boolean sortByUtility;
    private String dataPath;
    private String resultsPath;
    private boolean saveCPDAGs;
    private boolean savePags;
    private ComparisonGraph comparisonGraph = ComparisonGraph.true_DAG;

    public void compareFromFiles(String filePath, Algorithms algorithms) {
        compareFromFiles(filePath, filePath, algorithms);
    }

    /**
     * Compares algorithms.
     *
     * @param dataPath    Path to the directory where data and graph files have
     *                    been saved.
     * @param resultsPath Path to the file where the results should be stored.
     * @param algorithms  The list of algorithms to be compared.
     */
    public void compareFromFiles(String dataPath, String resultsPath, Algorithms algorithms) {
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

    }

    public void generateReportFromExternalAlgorithms(String dataPath, String resultsPath, Algorithms algorithms,
                                                     Statistics statistics, Parameters parameters) {
        generateReportFromExternalAlgorithms(dataPath, resultsPath, "Comparison.txt", algorithms,
                statistics, parameters);
    }

    public void generateReportFromExternalAlgorithms(String dataPath, String resultsPath, String outputFileName, Algorithms algorithms,
                                                     Statistics statistics, Parameters parameters) {

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

    public void compareFromSimulations(String resultsPath, Simulations simulations, Algorithms algorithms,
                                       Statistics statistics, Parameters parameters) {
        compareFromSimulations(resultsPath, simulations, "Comparison.txt", algorithms, statistics, parameters);
    }

    /**
     * Compares algorithms.
     *
     * @param resultsPath Path to the file where the output should be printed.
     * @param simulations The list of simulationWrapper that is used to generate
     *                    graphs and data for the comparison.
     * @param algorithms  The list of algorithms to be compared.
     * @param statistics  The list of statistics on which to compare the
     *                    algorithm, and their utility weights.
     */
    public void compareFromSimulations(String resultsPath, Simulations simulations, String outputFileName, Algorithms algorithms,
                                       Statistics statistics, Parameters parameters) {
        this.resultsPath = resultsPath;

        PrintStream stdout = (PrintStream) parameters.get("printStream", System.out);

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
                    stdout.println("Type mismatch: " + algorithmWrapper.getDescription()
                            + " / " + simulationWrapper.getDescription());
                }

                if (algorithmWrapper.getAlgorithm() instanceof ExternalAlgorithm) {
                    ExternalAlgorithm external = (ExternalAlgorithm) algorithmWrapper.getAlgorithm();
                    external.setSimIndex(simulationWrappers.indexOf(external.getSimulation()));
                }

                algorithmSimulationWrappers.add(new AlgorithmSimulationWrapper(
                        algorithmWrapper, simulationWrapper));
            }
        }

        // Run all of the algorithms and compile statistics.
        double[][][][] allStats = calcStats(algorithmSimulationWrappers, algorithmWrappers, simulationWrappers,
                statistics, numRuns, stdout);

        // Print out the preliminary information for statistics types, etc.
        this.out.println();
        this.out.println("Statistics:");
        this.out.println();

        for (Statistic stat : statistics.getStatistics()) {
            this.out.println(stat.getAbbreviation() + " = " + stat.getDescription());
        }

        this.out.println();

        {
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

            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.StandardDeviation, newOrder, algorithmSimulationWrappers, algorithmWrappers,
                    simulationWrappers, utilities, parameters);

            statTables = calcStatTables(allStats, Mode.WorstCase, numTables, algorithmSimulationWrappers,
                    numStats, statistics);

            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.WorstCase, newOrder, algorithmSimulationWrappers, algorithmWrappers,
                    simulationWrappers, utilities, parameters);

            statTables = calcStatTables(allStats, Mode.MedianCase, numTables, algorithmSimulationWrappers,
                    numStats, statistics);

            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.MedianCase, newOrder, algorithmSimulationWrappers, algorithmWrappers,
                    simulationWrappers, utilities, parameters);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }
        }

        for (int i = 0; i < simulations.getSimulations().size(); i++) {
            saveToFiles(resultsPath + "/simulation" + (i + 1), simulations.getSimulations().get(i), parameters);
        }

        this.out.close();
    }

    /**
     * Saves simulationWrapper data.
     *
     * @param dataPath   The path to the directory where the simulationWrapper
     *                   data should be saved.
     * @param simulation The simulate used to generate the graphs and data.
     * @param parameters The parameters to be used in the simulationWrapper.
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

                simulationWrapper.createData(simulationWrapper.getSimulationSpecificParameters(), false);

                File subdir = dir;
                if (simulationWrappers.size() > 1) {
                    index++;

                    subdir = new File(dir, "" + index);
                    subdir.mkdirs();
                }

                File dir1 = new File(subdir, "graph");
                File dir2 = new File(subdir, "data");

                dir1.mkdirs();
                dir2.mkdirs();

                File dir3 = null;

                if (isSaveCPDAGs()) {
                    dir3 = new File(subdir, "cpdags");
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

                    GraphUtils.saveGraph(graph, file2, false);

                    if (isSaveData()) {
                        File file = new File(dir2, "data." + (j + 1) + ".txt");
                        Writer out = new FileWriter(file);
                        DataModel dataModel = simulationWrapper.getDataModel(j);
                        DataWriter.writeRectangularData((DataSet) dataModel, out, '\t');
                        out.close();
                    }

                    if (isSaveCPDAGs()) {
                        File file3 = new File(dir3, "cpdag." + (j + 1) + ".txt");
                        GraphUtils.saveGraph(SearchGraphUtils.cpdagForDag(graph), file3, false);
                    }

                    if (isSavePags()) {
                        File file4 = new File(dir4, "pag." + (j + 1) + ".txt");
                        GraphUtils.saveGraph(new DagToPag(graph).convert(), file4, false);
                    }

                }

                PrintStream out = new PrintStream(new FileOutputStream(new File(subdir, "parameters.txt")));
                out.println(simulationWrapper.getDescription());
                out.println(simulationWrapper.getSimulationSpecificParameters());
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves simulationWrapper data.
     *
     * @param dataPath   The path to the directory where the simulationWrapper
     *                   data should be saved.
     * @param simulation The simulate used to generate the graphs and data.
     * @param parameters The parameters to be used in the simulationWrapper.
     */
    public void saveToFilesSingleSimulation(String dataPath, Simulation simulation, Parameters parameters) {
        File dir0 = new File(dataPath);
        File dir = new File(dir0, "save");

        deleteFilesThenDirectory(dir);
        dir.mkdirs();

        try {
            PrintStream _out = new PrintStream(new FileOutputStream(new File(dir, "parameters.txt")));
            _out.println(simulation.getDescription());
            _out.println(parameters);
            _out.close();

            int numDataSets = simulation.getNumDataModels();
            if (numDataSets <= 0) {

                File dir1 = new File(dir, "graph");
                File dir2 = new File(dir, "data");

                dir1.mkdirs();
                dir2.mkdirs();

                return;
            }

            File dir1 = new File(dir, "graph");
            File dir2 = new File(dir, "data");

            dir1.mkdirs();
            dir2.mkdirs();

            File dir3 = null;

            if (isSaveCPDAGs()) {
                dir3 = new File(dir, "cpdags");
                dir3.mkdirs();
            }

            File dir4 = null;

            if (isSavePags()) {
                dir4 = new File(dir, "pags");
                dir4.mkdirs();
            }

            for (int j = 0; j < simulation.getNumDataModels(); j++) {
                File file2 = new File(dir1, "graph." + (j + 1) + ".txt");
                Graph graph = simulation.getTrueGraph(j);

                GraphUtils.saveGraph(graph, file2, false);

                File file = new File(dir2, "data." + (j + 1) + ".txt");
                Writer out = new FileWriter(file);
                DataModel dataModel = simulation.getDataModel(j);
                DataWriter.writeRectangularData((DataSet) dataModel, out, '\t');
                out.close();

                if (isSaveCPDAGs()) {
                    File file3 = new File(dir3, "cpdag." + (j + 1) + ".txt");
                    GraphUtils.saveGraph(SearchGraphUtils.cpdagForDag(graph), file3, false);
                }

                if (isSavePags()) {
                    File file4 = new File(dir4, "pag." + (j + 1) + ".txt");
                    GraphUtils.saveGraph(new DagToPag(graph).convert(), file4, false);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    public void configuration(String path) {
        try {
            new File(path).mkdirs();

            PrintStream out = new PrintStream(new FileOutputStream(new File(path, "Configuration.txt")));

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
                            out.println("\t" + clazz.getSimpleName() + " can take an external graph from some other algorithm as input");
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
                                BdeuScore.class.newInstance());
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
                                     Statistics statistics, int numRuns, PrintStream stdout) {
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
                        statistics, numGraphTypes, allStats, run, stdout);
//                task.compute();
                tasks.add(task);
            }
        }

        for (AlgorithmTask task : tasks) {
            task.compute();
        }

        return allStats;
    }

    public boolean isShowSimulationIndices() {
        return this.showSimulationIndices;
    }

    public void setShowSimulationIndices(boolean showSimulationIndices) {
        this.showSimulationIndices = showSimulationIndices;
    }

    public boolean isShowAlgorithmIndices() {
        return this.showAlgorithmIndices;
    }

    public void setShowAlgorithmIndices(boolean showAlgorithmIndices) {
        this.showAlgorithmIndices = showAlgorithmIndices;
    }

    /**
     * @return True iff a column of utilities marked "W" should be shown in the
     * output.
     */
    public boolean isShowUtilities() {
        return this.showUtilities;
    }

    /**
     * @param showUtilities True iff a column of utilities marked "W" should be
     *                      shown in the output.
     */
    public void setShowUtilities(boolean showUtilities) {
        this.showUtilities = showUtilities;
    }

    /**
     * @return True iff the output should be sorted by utility.
     */
    public boolean isSortByUtility() {
        return this.sortByUtility;
    }

    /**
     * @param sortByUtility true iff the output should be sorted by utility.
     */
    public void setSortByUtility(boolean sortByUtility) {
        this.sortByUtility = sortByUtility;
    }

    /**
     * @return True if CPDAGs should be saved out.
     */
    public boolean isSaveCPDAGs() {
        return this.saveCPDAGs;
    }

    /**
     * @param saveCPDAGs True if CPDAGs should be saved out.
     */
    public void setSaveCPDAGs(boolean saveCPDAGs) {
        this.saveCPDAGs = saveCPDAGs;
    }

    /**
     * @return True if CPDAGs should be saved out.
     */
    public boolean isSavePags() {
        return this.savePags;
    }

    public void setSavePags(boolean savePags) {
        this.savePags = savePags;
    }

    /**
     * @return True if CPDAGs should be saved out.
     */
    public boolean isSaveData() {
        return true;
    }

    /**
     * @return True iff tables should be tab delimited (e.g. for easy pasting
     * into Excel).
     */
    public boolean isTabDelimitedTables() {
        return this.tabDelimitedTables;
    }

    /**
     * @param tabDelimitedTables True iff tables should be tab delimited (e.g.
     *                           for easy pasting into Excel).
     */
    public void setTabDelimitedTables(boolean tabDelimitedTables) {
        this.tabDelimitedTables = tabDelimitedTables;
    }

    /**
     * @param saveGraphs True if all graphs should be saved to files.
     */
    public void setSaveGraphs(boolean saveGraphs) {
        this.saveGraphs = saveGraphs;
    }

    /**
     * @return True if all graphs should be saved to files.
     */
    public boolean isSaveGraphs() {
        return this.saveGraphs;
    }

    /**
     * The type of graph the results are compared to.
     */
    public ComparisonGraph getComparisonGraph() {
        return this.comparisonGraph;
    }

    /**
     * The type of graph the results are compared to.
     */
    public void setComparisonGraph(ComparisonGraph comparisonGraph) {
        if (comparisonGraph == null) {
            throw new NullPointerException("Null compare graph.");
        }
        this.comparisonGraph = comparisonGraph;
    }

    private class AlgorithmTask extends RecursiveTask<Boolean> {

        private final List<AlgorithmSimulationWrapper> algorithmSimulationWrappers;
        private final List<AlgorithmWrapper> algorithmWrappers;
        private final List<SimulationWrapper> simulationWrappers;
        private final Statistics statistics;
        private final int numGraphTypes;
        private final double[][][][] allStats;
        private final Run run;
        private final PrintStream stdout;

        public AlgorithmTask(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                             List<AlgorithmWrapper> algorithmWrappers, List<SimulationWrapper> simulationWrappers,
                             Statistics statistics, int numGraphTypes, double[][][][] allStats, Run run, PrintStream stdout) {
            this.algorithmSimulationWrappers = algorithmSimulationWrappers;
            this.simulationWrappers = simulationWrappers;
            this.algorithmWrappers = algorithmWrappers;
            this.statistics = statistics;
            this.numGraphTypes = numGraphTypes;
            this.allStats = allStats;
            this.run = run;
            this.stdout = stdout;
        }

        @Override
        protected Boolean compute() {
            doRun(this.algorithmSimulationWrappers, this.algorithmWrappers,
                    this.simulationWrappers, this.statistics, this.numGraphTypes, this.allStats, this.run, this.stdout);
            return true;
        }
    }

    private void printParameters(List<String> names, Parameters parameters, PrintStream out) {
        out.println("Comparison.printParameters");
        ParamDescriptions descriptions = ParamDescriptions.getInstance();

        for (String name : names) {
            ParamDescription description = descriptions.get(name);
            Object defaultValue = description.getDefaultValue();
            Object value = parameters.get(name);

            if (defaultValue instanceof Double) {
                out.println(description.getShortDescription() + " = " + value.toString());
            } else if (defaultValue instanceof Integer) {
                out.println(description.getShortDescription() + " = " + value.toString());
            } else if (defaultValue instanceof Boolean) {
                boolean b = (Boolean) value;
                out.println(description.getShortDescription() + " = " + (b ? "Yes" : "No"));
            } else if (defaultValue instanceof String) {
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
                       int numGraphTypes, double[][][][] allStats, Run run, PrintStream stdout) {
        stdout.println();
        stdout.println("Run " + (run.getRunIndex() + 1));
        stdout.println();

        AlgorithmSimulationWrapper algorithmSimulationWrapper = algorithmSimulationWrappers.get(run.getAlgSimIndex());
        AlgorithmWrapper algorithmWrapper = algorithmSimulationWrapper.getAlgorithmWrapper();
        SimulationWrapper simulationWrapper = algorithmSimulationWrapper.getSimulationWrapper();
        DataModel data = simulationWrapper.getDataModel(run.getRunIndex());
        Graph trueGraph = simulationWrapper.getTrueGraph(run.getRunIndex());

        stdout.println((run.getAlgSimIndex() + 1) + ". " + algorithmWrapper.getDescription()
                + " simulationWrapper: " + simulationWrapper.getDescription());

        long start = System.currentTimeMillis();
        Graph graphOut;

        try {
            Algorithm algorithm = algorithmWrapper.getAlgorithm();
            Simulation simulation = simulationWrapper.getSimulation();

            if (algorithm instanceof HasKnowledge && simulation instanceof HasKnowledge) {
                ((HasKnowledge) algorithm).setKnowledge(((HasKnowledge) simulation).getKnowledge());
            }

            if (algorithmWrapper.getAlgorithm() instanceof ExternalAlgorithm) {
                ExternalAlgorithm external = (ExternalAlgorithm) algorithmWrapper.getAlgorithm();
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
                Collections.shuffle(indices);

                List<DataModel> dataModels = new ArrayList<>();
                int randomSelectionSize = algorithmWrapper.getAlgorithmSpecificParameters().getInt(
                        "randomSelectionSize");
                for (int i = 0; i < Math.min(numDataModels, randomSelectionSize); i++) {
                    dataModels.add(simulationWrapper.getSimulation().getDataModel(indices.get(i)));
                }

                Parameters _params = algorithmWrapper.getAlgorithmSpecificParameters();
                graphOut = ((MultiDataSetAlgorithm) algorithm).search(dataModels, _params);
            } else {
                boolean copyData = true;
                DataModel dataModel = copyData ? data.copy() : data;
                Parameters _params = algorithmWrapper.getAlgorithmSpecificParameters();
                graphOut = algorithm.search(dataModel, _params);
            }
        } catch (Exception e) {
            stdout.println("Could not run " + algorithmWrapper.getDescription());
            e.printStackTrace();
            return;
        }

        int simIndex = simulationWrappers.indexOf(simulationWrapper) + 1;
        int algIndex = algorithmWrappers.indexOf(algorithmWrapper) + 1;

        long stop = System.currentTimeMillis();

        long elapsed = stop - start;

        saveGraph(this.resultsPath, graphOut, run.getRunIndex(), simIndex, algorithmWrapper, elapsed, stdout);

        if (trueGraph != null) {
            graphOut = GraphUtils.replaceNodes(graphOut, trueGraph.getNodes());
        }

        if (algorithmWrapper.getAlgorithm() instanceof ExternalAlgorithm) {
            ExternalAlgorithm extAlg = (ExternalAlgorithm) algorithmWrapper.getAlgorithm();
            extAlg.setSimIndex(simulationWrappers.indexOf(simulationWrapper));
            extAlg.setSimulation(simulationWrapper.getSimulation());
            extAlg.setPath(this.resultsPath);
            elapsed = extAlg.getElapsedTime(data, simulationWrapper.getSimulationSpecificParameters());
        }

        synchronized (this) {

            Graph[] est = new Graph[numGraphTypes];

            Graph comparisonGraph;

            if (this.comparisonGraph == ComparisonGraph.true_DAG) {
                comparisonGraph = new EdgeListGraph(trueGraph);
            } else if (this.comparisonGraph == ComparisonGraph.CPDAG_of_the_true_DAG) {
                comparisonGraph = SearchGraphUtils.cpdagForDag(new EdgeListGraph(trueGraph));
            } else if (this.comparisonGraph == ComparisonGraph.PAG_of_the_true_DAG) {
                comparisonGraph = new DagToPag(new EdgeListGraph(trueGraph)).convert();
            } else {
                throw new IllegalArgumentException("Unrecognized graph type.");
            }

//        Graph comparisonGraph = trueGraph == null ? null : algorithmSimulationWrapper.getComparisonGraph(trueGraph);
            est[0] = new EdgeListGraph(graphOut);
            this.graphTypeUsed[0] = true;

            if (data.isMixed()) {
                est[1] = getSubgraph(est[0], true, true, simulationWrapper.getDataModel(run.getRunIndex()));
                est[2] = getSubgraph(est[0], true, false, simulationWrapper.getDataModel(run.getRunIndex()));
                est[3] = getSubgraph(est[0], false, false, simulationWrapper.getDataModel(run.getRunIndex()));

                this.graphTypeUsed[1] = true;
                this.graphTypeUsed[2] = true;
                this.graphTypeUsed[3] = true;
            }

            Graph[] truth = new Graph[numGraphTypes];

            truth[0] = new EdgeListGraph(comparisonGraph);

            if (data.isMixed()) {
                truth[1] = getSubgraph(comparisonGraph, true, true, simulationWrapper.getDataModel(run.getRunIndex()));
                truth[2] = getSubgraph(comparisonGraph, true, false, simulationWrapper.getDataModel(run.getRunIndex()));
                truth[3] = getSubgraph(comparisonGraph, false, false, simulationWrapper.getDataModel(run.getRunIndex()));
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

                    if (_stat instanceof ElapsedTime) {
                        stat = elapsed / 1000.0;
                    } else {
                        stat = _stat.getValue(truth[u], est[u], data);
                    }

                    allStats[u][run.getAlgSimIndex()][statIndex][run.getRunIndex()] = stat;
                }
            }

        }
    }

    private void saveGraph(String resultsPath, Graph graph, int i, int simIndex,
                           AlgorithmWrapper algorithmWrapper, long elapsed, PrintStream stdout) {
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
            stdout.println("Saving graph to " + file.getAbsolutePath());
            out.println(graph);
            out.close();

            PrintStream outElapsed = new PrintStream(fileElapsed);
//            stdout.println("Saving graph to " + file.getAbsolutePath());
            outElapsed.println(elapsed);
            outElapsed.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private enum Mode {
        Average, StandardDeviation, WorstCase, MedianCase
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
                    } else if (mode == Mode.WorstCase) {
                        statTables[u][i][j] = StatUtils.min(allStats[u][i][j]);
                    } else if (mode == Mode.StandardDeviation) {
                        statTables[u][i][j] = StatUtils.sd(allStats[u][i][j]);
                    } else if (mode == Mode.MedianCase) {
                        statTables[u][i][j] = StatUtils.median(allStats[u][i][j]);
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
        } else if (mode == Mode.MedianCase) {
            this.out.println("MEDIAN CASE");
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
            table.setTabDelimited(isTabDelimitedTables());

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
                                Math.abs(stat) < Math.pow(10, -smallNf.getMaximumFractionDigits()) && stat != 0 ? smallNf.format(stat) : nf.format(stat));
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

            Iterator<Statistic> it2 = statistics.getStatistics().iterator();

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

    private static class AlgorithmWrapper implements Algorithm {

        static final long serialVersionUID = 23L;
        private final Algorithm algorithm;
        private final Parameters parameters;
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

    private static class AlgorithmSimulationWrapper implements Algorithm {

        static final long serialVersionUID = 23L;
        private final SimulationWrapper simulationWrapper;
        private final AlgorithmWrapper algorithmWrapper;

        public AlgorithmSimulationWrapper(AlgorithmWrapper algorithm, SimulationWrapper simulation) {
            this.algorithmWrapper = algorithm;
            this.simulationWrapper = simulation;
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

    private static class SimulationWrapper implements Simulation {
        static final long serialVersionUID = 23L;
        private final Simulation simulation;
        private List<Graph> graphs;
        private List<DataModel> dataModels;
        private Parameters parameters;

        public SimulationWrapper(Simulation simulation, Parameters parameters) {
            this.simulation = simulation;

            // There is no harm in allowing the simulation code to add parameters here; they can
            // be displayed in the output table if desired. jdramsey 20170118
            this.parameters = new Parameters(parameters);
        }

        @Override
        public void createData(Parameters parameters, boolean newModel) {
            this.simulation.createData(parameters, newModel);
            this.graphs = new ArrayList<>();
            this.dataModels = new ArrayList<>();
            for (int i = 0; i < this.simulation.getNumDataModels(); i++) {
                this.graphs.add(this.simulation.getTrueGraph(i));
                this.dataModels.add(this.simulation.getDataModel(i));
            }
        }

        @Override
        public int getNumDataModels() {
            if (this.dataModels == null) {
                return 0;
            }
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
        public synchronized DataModel getDataModel(int index) {
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

        public void setParameters(Parameters parameters) {
            this.parameters = new Parameters(parameters);
        }

        public Parameters getSimulationSpecificParameters() {
            return this.parameters;
        }
    }

    private static class Run {

        private final int algSimIndex;
        private final int runIndex;
        private final int index;
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
}
