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
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.LoadContinuousDataAndGraphs;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.ElapsedTime;
import edu.cmu.tetrad.algcomparison.statistic.ParameterColumn;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.algcomparison.statistic.utils.SimulationPath;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.HasParameters;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import org.reflections.Reflections;

import java.io.*;
import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;

/**
 * Script to do a comparison of a list of algorithms using a list of statistics and a list
 * of parameters and their values.
 *
 * @author jdramsey
 * @author Daniel Malinsky
 */
public class Comparison {
    private boolean[] graphTypeUsed;
    private PrintStream out;
    private boolean tabDelimitedTables = false;
    private boolean saveGraphs = false;
    private boolean copyData = false;
    private boolean showSimulationIndices = false;
    private boolean showAlgorithmIndices = false;
    private boolean showUtilities = false;
    private boolean sortByUtility = false;
    private String filePath = null;
    private boolean parallelized = true;

    /**
     * Compares algorithms.
     *
     * @param filePath   Path to the directory where files have been saved.
     * @param filePath   Path to the file where the output should be printed.
     * @param algorithms The list of algorithms to be compared.
     * @param statistics The list of statistics on which to compare the algorithm, and their utility weights.
     * @param parameters The list of parameters and their values.
     */
    public void compareFromFiles(String filePath, Algorithms algorithms,
                                 Statistics statistics, Parameters parameters) {
        this.filePath = filePath;

        Simulations simulations = new Simulations();

        File file = new File(filePath, "save");
        File[] dirs = file.listFiles();

        if (dirs == null) {
            throw new NullPointerException("No files in " + file.getAbsolutePath());
        }

        for (File dir : dirs) {
            simulations.add(new LoadContinuousDataAndGraphs(dir.getAbsolutePath()));
        }

        compareFromSimulations(filePath, simulations, algorithms, statistics, parameters);
    }

    /**
     * Compares algorithms.
     *
     * @param filePath    Path to the file where the output should be printed.
     * @param simulations The list of simulationWrapper that is used to generate graphs and data for the comparison.
     * @param algorithms  The list of algorithms to be compared.
     * @param statistics  The list of statistics on which to compare the algorithm, and their utility weights.
     */
    public void compareFromSimulations(String filePath, Simulations simulations, Algorithms algorithms,
                                       Statistics statistics, Parameters parameters) {
        this.filePath = filePath;

        // Create output file.
        try {
            File dir = new File(filePath);
            dir.mkdirs();
            File file = new File(dir, "Comparison.txt");
            this.out = new PrintStream(new FileOutputStream(file));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        out.println(new Date());

        // Set up simulations--create data and graphs, read in parameters. The parameters
        // are set in the parameters object.
        List<SimulationWrapper> simulationWrappers = new ArrayList<>();

        int numRuns = -1;

        for (Simulation simulation : simulations.getSimulations()) {
            List<SimulationWrapper> wrappers = getSimulationWrappers(simulation, parameters);

            for (SimulationWrapper wrapper : wrappers) {
                wrapper.createData(wrapper.getSimulationSpecificParameters());

                int _numRuns = wrapper.getSimulationSpecificParameters().getInt("numRuns");

                if (numRuns == -1) {
                    numRuns = _numRuns;
                } else if (numRuns != _numRuns) {
                    throw new IllegalArgumentException("The simulation all need to have the same number of runs.");
                }

                simulationWrappers.add(wrapper);
            }
        }

        // Set up the algorithms.
        List<AlgorithmWrapper> algorithmWrappers = new ArrayList<>();

        for (Algorithm algorithm : algorithms.getAlgorithms()) {
            List<Integer> _dims = new ArrayList<>();
            List<String> varyingParameters = new ArrayList<>();

            for (String name : algorithm.getParameters()) {
                if (parameters.getNumValues(name) > 1) {
                    _dims.add(parameters.getNumValues(name));
                    varyingParameters.add(name);
                }
            }

            if (varyingParameters.isEmpty()) {
                algorithmWrappers.add(new AlgorithmWrapper(algorithm, parameters));
            } else {

                int[] dims = new int[_dims.size()];
                for (int i = 0; i < _dims.size(); i++) dims[i] = _dims.get(i);

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
                if (algDataType == DataType.Mixed || (algDataType == simDataType)) {
                    algorithmSimulationWrappers.add(new AlgorithmSimulationWrapper(
                            algorithmWrapper, simulationWrapper));
                } else {
                    System.out.println("Type mismatch; skipping algorithm/simulation " + algorithmWrapper.getDescription()
                            + " / " + simulationWrapper.getDescription());
                }
            }
        }


        // Run all of the algorithms and compile statistics.
        double[][][][] allStats = calcStats(algorithmSimulationWrappers, statistics, numRuns);

        // Print out the preliminary information for statistics types, etc.
        if (allStats != null) {
            out.println();
            out.println("Statistics:");
            out.println();

            for (Statistic stat : statistics.getStatistics()) {
                out.println(stat.getAbbreviation() + " = " + stat.getDescription());
            }
        }

        out.println();
        out.println("Parameters:");
        out.println(parameters);
        out.println();

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

            out.println("Simulation:");
            out.println();

            if (simulationWrappers.size() == 1) {
                out.println(simulationWrappers.get(0).getDescription());
            } else {
                int i = 0;

                for (SimulationWrapper simulation : simulationWrappers) {
                    out.println("Simulation " + (++i) + ":");
                    out.println(simulation.getDescription());

                    for (String param : simulation.getParameters()) {
                        out.println(param + " = " + simulation.getValue(param));
                    }

                    out.println();
                }
            }

            out.println("Algorithms:");
            out.println();

            for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                AlgorithmSimulationWrapper wrapper = algorithmSimulationWrappers.get(t);

                if (wrapper.getSimulationWrapper() == simulationWrappers.get(0)) {
                    out.println((t + 1) + ". " + wrapper.getAlgorithmWrapper().getDescription());
                }
            }

            out.println();
            out.println("Weighting of statistics:");
            out.println();
            out.println("U = ");

            for (Statistic stat : statistics.getStatistics()) {
                String statName = stat.getAbbreviation();
                double weight = statistics.getWeight(stat);
                if (weight != 0.0) {
                    out.println("    " + weight + " * f(" + statName + ")");
                }
            }

            out.println();
            out.println("Note that f for each statistic is a function that maps the statistic to the ");
            out.println("interval [0, 1], with higher being better.");
            out.println();

            out.println();

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            // Print all of the tables.
            printStats(statTables, statistics, Mode.Average, newOrder, algorithmSimulationWrappers, algorithmWrappers,
                    simulationWrappers, utilities);

            statTables = calcStatTables(allStats, Mode.StandardDeviation, numTables,
                    algorithmSimulationWrappers, numStats, statistics);

            printStats(statTables, statistics, Mode.StandardDeviation, newOrder, algorithmSimulationWrappers, algorithmWrappers,
                    simulationWrappers, utilities);

            statTables = calcStatTables(allStats, Mode.WorstCase, numTables, algorithmSimulationWrappers,
                    numStats, statistics);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.WorstCase, newOrder, algorithmSimulationWrappers, algorithmWrappers,
                    simulationWrappers, utilities);
        }

        out.close();
    }

    /**
     * Saves simulationWrapper data.
     *
     * @param path       The path to the directory where the simulationWrapper data should be saved.
     * @param simulation The simulate used to generate the graphs and data.
     * @param parameters The parameters to be used in the simulationWrapper.
     */
    public void saveToFiles(String path, Simulation simulation, Parameters parameters) {
        List<SimulationWrapper> simulationWrappers = getSimulationWrappers(simulation, parameters);

        File dir0 = new File(path);
        File dir;
        int i = 0;

        dir = new File(dir0, "save");
//
//        do {
//            dir = new File(dir0, "Simulation" + (++i));
//        } while (dir.exists());

//        if (dir.exists()) {
//            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                    "A file already exists named 'Simulation' in directory '" + dir0.getPath() + "'; \n" +
//                            "please remove it first or move it out of the way.");
//        }

        deleteFilesThenDirectory(dir);

        try {
            int index = 0;

            for (SimulationWrapper simulationWrapper : simulationWrappers) {
                for (String param : simulationWrapper.getParameters()) {
                    parameters.set(param, simulationWrapper.getValue(param));
                }

                simulationWrapper.createData(simulationWrapper.getSimulationSpecificParameters());
                index++;

                File subdir = new File(dir, "" + index);
                subdir.mkdirs();

                File dir1 = new File(subdir, "graph");
                File dir2 = new File(subdir, "data");
                dir1.mkdirs();
                dir2.mkdirs();

                for (int j = 0; j < simulationWrapper.getNumDataSets(); j++) {
                    File file2 = new File(dir1, "graph." + (j + 1) + ".txt");
                    GraphUtils.saveGraph(simulationWrapper.getTrueGraph(j), file2, false);

                    File file = new File(dir2, "data." + (j + 1) + ".txt");
                    Writer out = new FileWriter(file);
                    DataSet dataSet = simulationWrapper.getDataSet(j);
                    DataWriter.writeRectangularData(dataSet, out, '\t');
                    out.close();
                }

                PrintStream out = new PrintStream(new FileOutputStream(new File(subdir, "parameters.txt")));
                out.println(simulationWrapper.getDescription());
//                out.println();
                out.println(simulationWrapper.getSimulationSpecificParameters());
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteFilesThenDirectory(File dir) {
        if (dir == null) return;

        String[] entries = dir.list();

        if (entries == null) return;

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

    /**
     *
     */
    public void configuration(String path) {
        try {
            new File(path).mkdirs();

            PrintStream out = new PrintStream(new FileOutputStream(new File(path, "Configuration.txt")));

            Parameters allParams = new Parameters();

            List<Class> algorithms = new ArrayList<>();
            List<Class> statistics = new ArrayList<>();
            List<Class> independenceWrappers = new ArrayList<>();
            List<Class> scoreWrappers = new ArrayList<>();
            List<Class> simulations = new ArrayList<>();

            algorithms.addAll(getClasses(Algorithm.class));

            statistics.addAll(getClasses(Statistic.class));

            independenceWrappers.addAll(getClasses(IndependenceWrapper.class));

            scoreWrappers.addAll(getClasses(ScoreWrapper.class));

            simulations.addAll(getClasses(Simulation.class));

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
                            printParameters(algorithm, out, allParams);
                        }
                        if (TakesInitialGraph.class.isAssignableFrom(clazz)) {
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
                                BdeuScore.class.newInstance());
                        out.println(clazz.getSimpleName() + ": " + algorithm.getDescription());
                        if (HasParameters.class.isAssignableFrom(clazz)) {
                            printParameters(algorithm, out, allParams);
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
                            printParameters(algorithm, out, allParams);
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
                            printParameters(independence, out, allParams);
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
                            printParameters(score, out, allParams);
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
                            printParameters(simulation, out, allParams);
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

    private void printParameters(HasParameters hasParameters, PrintStream out, Parameters allParams) {
        List<String> paramDescriptions = new ArrayList<>(hasParameters.getParameters());
        if (paramDescriptions.isEmpty()) return;
        out.print("\tParameters: ");

        for (int i = 0; i < paramDescriptions.size(); i++) {
            out.print(paramDescriptions.get(i));
            out.print(" = ");
            Object[] values = allParams.getValues(paramDescriptions.get(i));
            if (values == null || values.length == 0) {
                out.print("no default");

                if (i < paramDescriptions.size() - 1) {
                    out.print("; ");
                    if ((i + 1) % 4 == 0) out.print("\n\t\t");
                }

                continue;
            }

            for (int j = 0; j < values.length; j++) {
                out.print(values[j]);
                if (j < values.length - 1) out.print(",");
            }

            if (i < paramDescriptions.size() - 1) {
                out.print("; ");
                if ((i + 1) % 4 == 0) out.print("\n\t\t");
            }
        }

        out.println();
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

        for (String params : simulation.getParameters()) {
            if (parameters.getNumValues(params) > 1) {
                _dims.add(parameters.getNumValues(params));
                varyingParams.add(params);
            }
        }

        if (varyingParams.isEmpty()) {
            simulationWrappers.add(new SimulationWrapper(simulation, parameters));
        } else {

            int[] dims = new int[_dims.size()];
            for (int i = 0; i < _dims.size(); i++) dims[i] = _dims.get(i);

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


    private double[][][][] calcStats(final List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                                     Statistics statistics, int numRuns) {
        int numGraphTypes = 4;

        graphTypeUsed = new boolean[4];

        double[][][][] allStats = new double[4][algorithmSimulationWrappers.size()][statistics.size() + 1][numRuns];

        List<AlgorithmTask> tasks = new ArrayList<>();
        int index = 0;

        for (int algSimIndex = 0; algSimIndex < algorithmSimulationWrappers.size(); algSimIndex++) {
            for (int runIndex = 0; runIndex < numRuns; runIndex++) {
                AlgorithmSimulationWrapper algorithmSimulationWrapper = algorithmSimulationWrappers.get(algSimIndex);
                Run run = new Run(algSimIndex, runIndex, index++, algorithmSimulationWrapper);
                AlgorithmTask task = new AlgorithmTask(algorithmSimulationWrappers, statistics, numGraphTypes, allStats, run);
                tasks.add(task);
            }
        }

        if (!isParallelized()) {
            for (AlgorithmTask task : tasks) {
                task.compute();
            }
        } else {
            class Task extends RecursiveTask<Boolean> {
                List<AlgorithmTask> tasks;

                public Task(List<AlgorithmTask> tasks) {
                    this.tasks = tasks;
                }

                @Override
                protected Boolean compute() {
                    Queue<AlgorithmTask> tasks = new ArrayDeque<>();

                    for (AlgorithmTask task : this.tasks) {
                        tasks.add(task);
                        task.fork();

                        for (AlgorithmTask _task : new ArrayList<>(tasks)) {
                            if (_task.isDone()) {
                                _task.join();
                                tasks.remove(_task);
                            }
                        }

                        while (tasks.size() > Runtime.getRuntime().availableProcessors()) {
                            AlgorithmTask _task = tasks.poll();
                            _task.join();
                        }
                    }

                    for (AlgorithmTask task : tasks) {
                        task.join();
                    }

                    return true;
                }
            }

            Task task = new Task(tasks);

            ForkJoinPoolInstance.getInstance().getPool().invoke(task);
        }

        return allStats;
    }

    public boolean isShowSimulationIndices() {
        return showSimulationIndices;
    }

    public void setShowSimulationIndices(boolean showSimulationIndices) {
        this.showSimulationIndices = showSimulationIndices;
    }


    public boolean isShowAlgorithmIndices() {
        return showAlgorithmIndices;
    }

    public void setShowAlgorithmIndices(boolean showAlgorithmIndices) {
        this.showAlgorithmIndices = showAlgorithmIndices;
    }


    /**
     * @return True iff a column of utilities marked "W" should be shown
     * in the output.
     */
    public boolean isShowUtilities() {
        return showUtilities;
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
        return sortByUtility;
    }

    /**
     * @param sortByUtility true iff the output should be sorted by utility.
     */
    public void setSortByUtility(boolean sortByUtility) {
        this.sortByUtility = sortByUtility;
    }

    public boolean isParallelized() {
        return parallelized;
    }

    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    private class AlgorithmTask extends RecursiveTask<Boolean> {
        private List<AlgorithmSimulationWrapper> algorithmSimulationWrappers;
        private Statistics statistics;
        private int numGraphTypes;
        private double[][][][] allStats;
        private final Run run;

        public AlgorithmTask(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                             Statistics statistics, int numGraphTypes, double[][][][] allStats, Run run) {
            this.algorithmSimulationWrappers = algorithmSimulationWrappers;
            this.statistics = statistics;
            this.numGraphTypes = numGraphTypes;
            this.allStats = allStats;
            this.run = run;
        }

        @Override
        protected Boolean compute() {
            doRun(algorithmSimulationWrappers, statistics, numGraphTypes, allStats, run);
            return true;
        }
    }

    private void doRun(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers, Statistics statistics,
                       int numGraphTypes, double[][][][] allStats, Run run) {
        System.out.println();
        System.out.println("Run " + (run.getRunIndex() + 1));
        System.out.println();

        AlgorithmSimulationWrapper algorithmSimulationWrapper = algorithmSimulationWrappers.get(run.getAlgSimIndex());
        AlgorithmWrapper algorithmWrapper = algorithmSimulationWrapper.getAlgorithmWrapper();
        SimulationWrapper simulationWrapper = algorithmSimulationWrapper.getSimulationWrapper();
        DataSet data = simulationWrapper.getDataSet(run.getRunIndex());
        Graph trueGraph = simulationWrapper.getTrueGraph(run.getRunIndex());

        System.out.println((run.getAlgSimIndex() + 1) + ". " + algorithmWrapper.getDescription()
                + " simulationWrapper: " + simulationWrapper.getDescription());

        long start = System.currentTimeMillis();
        Graph out;

        try {
            Algorithm algorithm = algorithmWrapper.getAlgorithm();
            Simulation simulation = simulationWrapper.getSimulation();

            if (algorithm instanceof HasKnowledge && simulation instanceof HasKnowledge) {
                ((HasKnowledge) algorithm).setKnowledge(((HasKnowledge) simulation).getKnowledge());
            }

            if (algorithm instanceof MultiDataSetAlgorithm) {
                List<Integer> indices = new ArrayList<>();
                int numDataSets = simulationWrapper.getSimulation().getNumDataSets();
                for (int i = 0; i < numDataSets; i++) indices.add(i);
                Collections.shuffle(indices);

                List<DataSet> dataSets = new ArrayList<>();
                int randomSelectionSize = algorithmWrapper.getAlgorithmSpecificParameters().getInt(
                        "randomSelectionSize");
                for (int i = 0; i < Math.min(numDataSets, randomSelectionSize); i++) {
                    dataSets.add(simulationWrapper.getSimulation().getDataSet(indices.get(i)));
                }

                Parameters _params = algorithmWrapper.getAlgorithmSpecificParameters();
                out = ((MultiDataSetAlgorithm) algorithm).search(dataSets, _params);
            } else {
                DataSet dataSet = copyData ? data.copy() : data;
                Parameters _params = algorithmWrapper.getAlgorithmSpecificParameters();
                out = algorithm.search(dataSet, _params);
            }
        } catch (Exception e) {
            System.out.println("Could not run " + algorithmWrapper.getDescription());
            e.printStackTrace();
            return;
        }

        String path = null;

        if (simulationWrapper.getSimulation() instanceof SimulationPath) {
            path = ((SimulationPath) simulationWrapper.getSimulation()).getPath();
        }

        printGraph(path, out, run.getRunIndex(), algorithmWrapper);

        long stop = System.currentTimeMillis();

        long elapsed = stop - start;

        if (trueGraph != null) {
            out = GraphUtils.replaceNodes(out, trueGraph.getNodes());
        }

        Graph[] est = new Graph[numGraphTypes];

        Graph comparisonGraph = trueGraph == null ? null : algorithmSimulationWrapper.getComparisonGraph(trueGraph);

        est[0] = out;
        graphTypeUsed[0] = true;

        if (data.isMixed()) {
            est[1] = getSubgraph(out, true, true, data);
            est[2] = getSubgraph(out, true, false, data);
            est[3] = getSubgraph(out, false, false, data);

            graphTypeUsed[1] = true;
            graphTypeUsed[2] = true;
            graphTypeUsed[3] = true;
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
                if (!graphTypeUsed[u]) continue;

                int statIndex = -1;

                for (Statistic _stat : statistics.getStatistics()) {
                    statIndex++;

                    if (_stat instanceof ParameterColumn) continue;

                    double stat;

                    if (_stat instanceof ElapsedTime) {
                        stat = elapsed / 1000.0;
                    } else {
                        stat = _stat.getValue(truth[u], est[u]);
                    }

                    allStats[u][run.getAlgSimIndex()][statIndex][run.getRunIndex()] = stat;
                }
            }
        }
    }

    private void printGraph(String path, Graph graph, int i, AlgorithmWrapper algorithmWrapper) {
        if (!saveGraphs) {
            return;
        }

        try {
            String description = algorithmWrapper.getDescription();
            File file;

            File dir = new File(filePath, "output_graphs");
            dir.mkdirs();

            if (path != null) {
                file = new File(dir, path + "." + description + ".graph" + "." + (i + 1) + ".txt");
            } else {
                file = new File(dir, description + ".graph" + "." + (i + 1) + ".txt");
            }

            PrintStream out = new PrintStream(file);
            System.out.println("Printing graph to " + file.getAbsolutePath());
            out.println(graph);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return True iff tables should be tab delimited (e.g. for easy pasting into Excel).
     */
    public boolean isTabDelimitedTables() {
        return tabDelimitedTables;
    }

    /**
     * @param tabDelimitedTables True iff tables should be tab delimited (e.g. for easy
     *                           pasting into Excel).
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
        return saveGraphs;
    }

    /**
     * @return True if data should be copied before analyzing it.
     */
    public boolean isCopyData() {
        return copyData;
    }

    /**
     * @param copyData True if data should be copied before analyzing it.
     */
    public void setCopyData(boolean copyData) {
        this.copyData = copyData;
    }

    private enum Mode {
        Average, StandardDeviation, WorstCase
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
                                stat = parameters.getDouble(name);
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
                            List<SimulationWrapper> simulationWrappers, double[] utilities) {

        if (mode == Mode.Average) {
            out.println("AVERAGE STATISTICS");
        } else if (mode == Mode.StandardDeviation) {
            out.println("STANDARD DEVIATIONS");
        } else if (mode == Mode.WorstCase) {
            out.println("WORST CASE");
        } else {
            throw new IllegalStateException();
        }

        int numTables = statTables.length;
        int numStats = statistics.size();

        NumberFormat nf = new DecimalFormat("0.00");
        NumberFormat smallNf = new DecimalFormat("0.00E0");

        out.println();

        for (int u = 0; u < numTables; u++) {
            if (!graphTypeUsed[u]) continue;

            int rows = algorithmSimulationWrappers.size() + 1;
            int cols = (isShowSimulationIndices() ? 1 : 0) + (isShowAlgorithmIndices() ? 1 : 0) + numStats
                    + (isShowUtilities() ? 1 : 0);

            TextTable table = new TextTable(rows, cols);
            table.setTabDelimited(isTabDelimitedTables());

            int initialColumn = 0;

            if (isShowAlgorithmIndices()) {
                table.setToken(0, initialColumn, "Alg");

                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    AlgorithmWrapper algorithm = algorithmSimulationWrappers.get(newOrder[t]).getAlgorithmWrapper();
                    table.setToken(t + 1, initialColumn, "" + (algorithmWrappers.indexOf(algorithm) + 1));
                }

                initialColumn++;
            }

            if (isShowSimulationIndices()) {
                table.setToken(0, initialColumn, "Sim");

                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    Simulation simulation = algorithmSimulationWrappers.get(newOrder[t]).
                            getSimulationWrapper();
                    table.setToken(t + 1, initialColumn, "" + (simulationWrappers.indexOf(simulation) + 1));
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
                    double stat = statTables[u][newOrder[t]][statIndex];

                    if (stat == Double.POSITIVE_INFINITY) {
                        table.setToken(t + 1, initialColumn + statIndex, "Yes");
                    } else if (stat == Double.NEGATIVE_INFINITY) {
                        table.setToken(t + 1, initialColumn + statIndex, "No");
                    } else {
                        table.setToken(t + 1, initialColumn + statIndex,
                                Math.abs(stat) < 0.1 ? smallNf.format(stat) : nf.format(stat));
                    }
                }

                if (isShowUtilities()) {
                    table.setToken(t + 1, initialColumn + numStats, nf.format(utilities[newOrder[t]]));
                }
            }

            out.println(getHeader(u));
            out.println();
            out.println(table);
        }
    }

    private double[] calcUtilities(Statistics statistics, List<AlgorithmSimulationWrapper> wrappers,
                                   double[][] stats) {

        // Calculate utilities for the first table.
        double[] utilities = new double[wrappers.size()];

        for (int t = 0; t < wrappers.size(); t++) {
            double sum = 0.0;
            int j = -1;

            Iterator it2 = statistics.getStatistics().iterator();
            int count = 0;

            while (it2.hasNext()) {
                Statistic stat = (Statistic) it2.next();
                j++;

                double weight = statistics.getWeight(stat);

                if (weight != 0.0) {
                    sum += weight * stat.getNormValue(stats[t][j]);
                    count++;
                }
            }

            utilities[t] = sum / count;
        }

        return utilities;
    }

    private int[] sort(final List<AlgorithmSimulationWrapper> algorithmSimulationWrappers, final double[] utilities) {
        List<Integer> order = new ArrayList<>();
        for (int t = 0; t < algorithmSimulationWrappers.size(); t++) order.add(t);

        final double[] _utilities = Arrays.copyOf(utilities, utilities.length);
        double low = StatUtils.min(utilities);
        for (int t = 0; t < _utilities.length; t++) {
            low--;
            if (Double.isNaN(_utilities[t])) _utilities[t] = low;
        }

        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -Double.compare(_utilities[o1], _utilities[o2]);
            }
        });

        int[] newOrder = new int[algorithmSimulationWrappers.size()];
        for (int t = 0; t < algorithmSimulationWrappers.size(); t++) newOrder[t] = order.get(t);

        return newOrder;
    }

    private Graph getSubgraph(Graph graph, boolean discrete1, boolean discrete2, DataSet dataSet) {
        if (discrete1 && discrete2) {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = dataSet.getVariable(edge.getNode1().getName());
                Node node2 = dataSet.getVariable(edge.getNode2().getName());

                if (node1 instanceof DiscreteVariable &&
                        node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        } else if (!discrete1 && !discrete2) {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = dataSet.getVariable(edge.getNode1().getName());
                Node node2 = dataSet.getVariable(edge.getNode2().getName());

                if (node1 instanceof ContinuousVariable &&
                        node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        } else {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = dataSet.getVariable(edge.getNode1().getName());
                Node node2 = dataSet.getVariable(edge.getNode2().getName());

                if (node1 instanceof DiscreteVariable &&
                        node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }

                if (node1 instanceof ContinuousVariable &&
                        node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        }
    }

    private class AlgorithmWrapper implements Algorithm {
        static final long serialVersionUID = 23L;
        private Algorithm algorithm;
        private Parameters parameters;
        private List<String> overriddenParameters = new ArrayList<>();

        public AlgorithmWrapper(Algorithm algorithm, Parameters parameters) {
            this.algorithm = algorithm;
            this.parameters = new Parameters(parameters);
        }

        @Override
        public Graph search(DataModel dataSet, Parameters parameters) {
            return algorithm.search(dataSet, this.parameters);
        }

        @Override
        public Graph getComparisonGraph(Graph graph) {
            return algorithm.getComparisonGraph(graph);
        }

        @Override
        public String getDescription() {
            StringBuilder description = new StringBuilder();
            description.append(algorithm.getDescription());

            if (overriddenParameters.size() > 0) {
                for (String name : new ArrayList<>(overriddenParameters)) {
                    description.append(", ").append(name).append(" = ").append(parameters.get(name));
                }
            }

            return description.toString();
        }

        @Override
        public DataType getDataType() {
            return algorithm.getDataType();
        }

        @Override
        public List<String> getParameters() {
            return algorithm.getParameters();
        }

        public void setValue(String name, Object value) {
            if (!(value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException();
            }

            parameters.set(name, value);
            this.overriddenParameters.add(name);
        }

        public Algorithm getAlgorithm() {
            return algorithm;
        }

        public Parameters getAlgorithmSpecificParameters() {
            return this.parameters;
        }
    }

    private class AlgorithmSimulationWrapper implements Algorithm {
        static final long serialVersionUID = 23L;
        private SimulationWrapper simulationWrapper;
        private AlgorithmWrapper algorithmWrapper;
        List<String> parameters = new ArrayList<>();

        public AlgorithmSimulationWrapper(AlgorithmWrapper algorithm, SimulationWrapper simulation) {
            this.algorithmWrapper = algorithm;
            this.simulationWrapper = simulation;
            parameters.addAll(algorithmWrapper.getParameters());
            parameters.addAll(simulationWrapper.getParameters());
        }

        @Override
        public Graph search(DataModel dataSet, Parameters parameters) {
            return algorithmWrapper.getAlgorithm().search(dataSet, parameters);
        }

        @Override
        public Graph getComparisonGraph(Graph graph) {
            return algorithmWrapper.getComparisonGraph(graph);
        }

        @Override
        public String getDescription() {
            throw new IllegalArgumentException();
        }

        @Override
        public DataType getDataType() {
            return algorithmWrapper.getDataType();
        }

        @Override
        public List<String> getParameters() {
            List<String> params = new ArrayList<>(simulationWrapper.getParameters());
            params.addAll(algorithmWrapper.getParameters());
            return params;
        }

        public SimulationWrapper getSimulationWrapper() {
            return simulationWrapper;
        }

        public AlgorithmWrapper getAlgorithmWrapper() {
            return algorithmWrapper;
        }
    }

    private class SimulationWrapper implements Simulation {
        static final long serialVersionUID = 23L;
        private Simulation simulation;
        private List<Graph> graphs;
        private List<DataSet> dataSets;
        private Parameters parameters;

        public SimulationWrapper(Simulation simulation, Parameters parameters) {
            this.simulation = simulation;
            this.parameters = new Parameters(parameters);
        }

        @Override
        public void createData(Parameters parameters) {
            simulation.createData(parameters);
            this.graphs = new ArrayList<>();
            this.dataSets = new ArrayList<>();
            for (int i = 0; i < simulation.getNumDataSets(); i++) {
                this.graphs.add(simulation.getTrueGraph(i));
                this.dataSets.add(simulation.getDataSet(i));
            }
        }

        @Override
        public int getNumDataSets() {
            return dataSets.size();
        }

        @Override
        public Graph getTrueGraph(int index) {
            if (graphs == null) return null;
            else return new EdgeListGraph(graphs.get(index));
        }

        @Override
        public DataSet getDataSet(int index) {
            return dataSets.get(index);
        }

        @Override
        public DataType getDataType() {
            return simulation.getDataType();
        }

        @Override
        public String getDescription() {
            return simulation.getDescription();
        }

        @Override
        public List<String> getParameters() {
            return simulation.getParameters();
        }

        public void setValue(String name, Object value) {
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException();
            }

            parameters.set(name, value);
        }

        public Object getValue(String name) {
            Object[] values = parameters.getValues(name);

            if (values == null || values.length == 0) {
                throw new NullPointerException("Expecting parameter to be defined: " + name);
            }

            return values[0];
        }

        public Simulation getSimulation() {
            return simulation;
        }

        public void setParameters(Parameters parameters) {
            this.parameters = new Parameters(parameters);
        }

        public Parameters getSimulationSpecificParameters() {
            return parameters;
        }
    }

    private class Run {
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
            return algSimIndex;
        }

        public int getRunIndex() {
            return runIndex;
        }

        public int getIndex() {
            return index;
        }

        public AlgorithmSimulationWrapper getWrapper() {
            return wrapper;
        }
    }
}




