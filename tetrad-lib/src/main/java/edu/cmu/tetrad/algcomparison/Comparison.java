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

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithms.Algorithms;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.LoadContinuousDataAndGraphs;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.ElapsedTime;
import edu.cmu.tetrad.algcomparison.statistic.ParameterColumn;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.algcomparison.statistic.utils.SimulationPath;
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
 */
public class Comparison {
    private boolean[] graphTypeUsed;
    private PrintStream out;
    private boolean tabDelimitedTables = false;
    private boolean saveGraphs = false;

    /**
     * Compares algorithms.
     *
     * @param filePath   Path to the directory where files have been saved.
     * @param outFile    Path to the file where the output should be printed.
     * @param algorithms The list of algorithms to be compared.
     * @param statistics The list of statistics on which to compare the algorithms, and their utility weights.
     * @param parameters The list of parameters and their values.
     */
    public void compareAlgorithms(String filePath, String outFile, Algorithms algorithms,
                                  Statistics statistics, Parameters parameters) {
        Simulations simulations = new Simulations();

        File file = new File(filePath);
        File[] dirs = file.listFiles();

        for (File dir : dirs) {
            simulations.add(new LoadContinuousDataAndGraphs(dir.getAbsolutePath()));
        }

        compareAlgorithms(outFile, simulations, algorithms, statistics, parameters);
    }

    /**
     * Compares algorithms.
     *
     * @param outFile     Path to the file where the output should be printed.
     * @param simulations The list of simulation that is used to generate graphs and data for the comparison.
     * @param algorithms  The list of algorithms to be compared.
     * @param statistics  The list of statistics on which to compare the algorithms, and their utility weights.
     * @param parameters  The list of parameters and their values.
     */
    public void compareAlgorithms(String outFile, Simulations simulations, Algorithms algorithms,
                                  Statistics statistics, Parameters parameters) {
        Simulations _simulations = new Simulations();

        for (Simulation simulation : simulations.getSimulations()) {
            List<SimulationWrapper> wrappers = getSimulationWrappers(simulation, parameters);

            for (SimulationWrapper wrapper : wrappers) {
                for (String param : wrapper.getOverriddenParameters().keySet()) {
                    parameters.put(param, wrapper.getValue(param));
                }

                wrapper.createData(parameters);
                _simulations.add(wrapper);
            }
        }

        try {
            File comparison = new File(outFile);
            this.out = new PrintStream(new FileOutputStream(comparison));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        out.println(new Date());

        // Only consider the algorithms for the given data type. Mixed data types can go either way.
        // MGM algorithms won'algSimIndex run on continuous data or discrete data.
        List<AlgorithmWrapper> algorithmWrappers = new ArrayList<>();

        for (Algorithm algorithm : algorithms.getAlgorithms()) {
            List<String> algParameters = algorithm.getParameters();
            List<Integer> _dims = new ArrayList<>();
            List<String> varyingParameters = new ArrayList<>();

            for (String parameter : algParameters) {
                if (parameters.getNumValues(parameter) > 1) {
                    _dims.add(parameters.getNumValues(parameter));
                    varyingParameters.add(parameter);
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
                        String p = varyingParameters.get(h);
                        Object[] values = parameters.getValues(p);
                        Object value = values[choice[h]];
                        wrapper.setValue(p, value);
                    }

                    algorithmWrappers.add(wrapper);
                }
            }
        }

        List<AlgorithmSimulationWrapper> algorithmSimulationWrappers = new ArrayList<>();

        for (Simulation simulation : _simulations.getSimulations()) {
            for (AlgorithmWrapper algorithmWrapper : algorithmWrappers) {
                if (algorithmWrapper.getDataType() == simulation.getDataType()
                        || algorithmWrapper.getDataType() == DataType.Mixed) {

                    AlgorithmSimulationWrapper wrapper = new AlgorithmSimulationWrapper(
                            algorithmWrapper, simulation);

                    for (String param : algorithmWrapper.getOverriddenParameters()) {
                        wrapper.setValue(param, algorithmWrapper.getValue(param));
                    }

                    algorithmSimulationWrappers.add(wrapper);
                } else {
                    System.out.println("Type mismatch; skipping algorithm/simulation " + algorithmWrapper.getDescription());
                }
            }
        }

        double[][][][] allStats = calcStats(algorithmSimulationWrappers, statistics, parameters);

        if (allStats != null)

        {
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
                    numStats);
            double[] utilities = calcUtilities(statistics, algorithmSimulationWrappers, numStats, statTables[0]);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            int[] newOrder;

            if (statistics.isSortByUtility()) {
                newOrder = sort(algorithmSimulationWrappers, utilities);
            } else {
                newOrder = new int[algorithmSimulationWrappers.size()];
                for (int q = 0; q < algorithmSimulationWrappers.size(); q++) {
                    newOrder[q] = q;
                }
            }

            out.println("Simulation:");
            out.println();

            if (_simulations.getSimulations().size() == 1) {
                out.println(_simulations.getSimulations().get(0).getDescription());
            } else {
                int i = 0;

                for (Simulation simulation : _simulations.getSimulations()) {
                    out.println("Simulation " + (++i) + ":");
                    out.println(simulation.getDescription());

                    if (simulation instanceof SimulationWrapper) {
                        for (String param : ((SimulationWrapper) simulation).getOverriddenParameters().keySet()) {
                            out.println(param + " = " + ((SimulationWrapper) simulation).getValue(param));
                        }
                    }

                    out.println();
                }
            }

            out.println("Algorithms:");
            out.println();

            for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                AlgorithmSimulationWrapper wrapper = algorithmSimulationWrappers.get(t);

                if (wrapper.getSimulation() == _simulations.getSimulations().get(0)) {
                    out.println((t + 1) + ". " + wrapper.getDescription());
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

            printStats(statTables, statistics, Mode.Average, newOrder, algorithmWrappers, algorithmSimulationWrappers,
                    _simulations, utilities);

            statTables = calcStatTables(allStats, Mode.StandardDeviation, numTables,
                    algorithmSimulationWrappers, numStats);

            printStats(statTables, statistics, Mode.StandardDeviation, newOrder, algorithmWrappers,
                    algorithmSimulationWrappers,
                    _simulations, utilities);

            statTables = calcStatTables(allStats, Mode.WorstCase, numTables, algorithmSimulationWrappers,
                    numStats);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.WorstCase, newOrder, algorithmWrappers,
                    algorithmSimulationWrappers,
                    _simulations, utilities);
        }

        out.close();
    }

    /**
     * Saves simulation data.
     *
     * @param path       The path to the directory where the simulation data should be saved.
     * @param simulation The simulate used to generate the graphs and data.
     * @param parameters The parameters to be used in the simulation.
     */
    public void saveDataSetAndGraphs(String path, Simulation simulation, Parameters parameters) {
        List<SimulationWrapper> simulationWrappers = getSimulationWrappers(simulation, parameters);

        try {
            int index = 0;

            for (SimulationWrapper simulationWrapper : simulationWrappers) {
                for (String param : simulationWrapper.getOverriddenParameters().keySet()) {
                    parameters.setValue(param, simulationWrapper.getValue(param));
                }

                simulationWrapper.createData(parameters);
                index++;

                File dir = new File(path);
                dir.mkdirs();
                dir.delete();
                dir.mkdirs();

                File subdir = new File(dir, "" + index);
                subdir.mkdir();

                File dir1 = new File(subdir, "graph");
                File dir2 = new File(subdir, "data");
                dir1.mkdirs();
                dir2.mkdirs();

                File file2 = new File(dir1, "graph.txt");
                GraphUtils.saveGraph(simulationWrapper.getTrueGraph(), file2, false);

                for (int i = 0; i < simulationWrapper.getNumDataSets(); i++) {
                    File file = new File(dir2, "data." + (i + 1) + ".txt");
                    Writer out = new FileWriter(file);
                    DataSet dataSet = simulationWrapper.getDataSet(i);
                    DataWriter.writeRectangularData(dataSet, out, '\t');
                    out.close();
                }

                PrintStream out = new PrintStream(new FileOutputStream(new File(subdir, "parameters.txt")));
                out.println(simulationWrapper.getDescription());
                out.println();
                out.println(parameters);
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    public void configuration(String outFile) {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(new File(outFile)));

            Parameters allParams = new Parameters();

            List<Class> algorithms = new ArrayList<>();
            List<Class> statistics = new ArrayList<>();
            List<Class> independenceWrappers = new ArrayList<>();
            List<Class> scoreWrappers = new ArrayList<>();
            List<Class> simulations = new ArrayList<>();

            algorithms.addAll(getClasses(Algorithm.class));
//
            statistics.addAll(getClasses(Statistic.class));
//
            independenceWrappers.addAll(getClasses(IndependenceWrapper.class));
//
            scoreWrappers.addAll(getClasses(ScoreWrapper.class));
//
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

                for (int i = 0; i < constructors.length; i++) {
                    if (constructors[i].getParameterTypes().length == 1
                            && constructors[i].getParameterTypes()[0] == IndependenceWrapper.class) {
                        Algorithm algorithm = (Algorithm) constructors[i].newInstance(
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
            out.println("Algorithms that taks a score (using an example score):");
            out.println();

            for (Class clazz : new ArrayList<>(algorithms)) {
                if (Experimental.class.isAssignableFrom(clazz)) {
                    continue;
                }

                Constructor[] constructors = clazz.getConstructors();

                for (int i = 0; i < constructors.length; i++) {
                    if (constructors[i].getParameterTypes().length == 1
                            && constructors[i].getParameterTypes()[0] == ScoreWrapper.class) {
                        Algorithm algorithm = (Algorithm) constructors[i].newInstance(
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

                for (int i = 0; i < constructors.length; i++) {
                    if (constructors[i].getParameterTypes().length == 0) {
                        Algorithm algorithm = (Algorithm) constructors[i].newInstance();
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

                for (int i = 0; i < constructors.length; i++) {
                    if (constructors[i].getParameterTypes().length == 0) {
                        Statistic statistic = (Statistic) constructors[i].newInstance();
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

                for (int i = 0; i < constructors.length; i++) {
                    if (constructors[i].getParameterTypes().length == 0) {
                        IndependenceWrapper independence = (IndependenceWrapper) constructors[i].newInstance();
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

                for (int i = 0; i < constructors.length; i++) {
                    if (constructors[i].getParameterTypes().length == 0) {
                        ScoreWrapper score = (ScoreWrapper) constructors[i].newInstance();
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

                for (int i = 0; i < constructors.length; i++) {
                    if (constructors[i].getParameterTypes().length == 0) {
                        Simulation simulation = (Simulation) constructors[i].newInstance();
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
        List<String> parameters = hasParameters.getParameters();
        if (parameters.isEmpty()) return;
        out.print("\tParameters: ");

        for (int i = 0; i < parameters.size(); i++) {
            out.print(parameters.get(i));
            out.print(" = ");
            Object[] values = allParams.getValues(parameters.get(i));
            if (values == null || values.length == 0) {
                out.print("no default");

                if (i < parameters.size() - 1) {
                    out.print("; ");
                    if ((i + 1) % 4 == 0) out.print("\n\t\t");
                }

                continue;
            }

            for (int j = 0; j < values.length; j++) {
                out.print(values[j]);
                if (j < values.length - 1) out.print(",");
            }

            if (i < parameters.size() - 1) {
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
        List<String> varyingParameters = new ArrayList<>();

        for (String parameter : simulation.getParameters()) {
            if (parameters.getNumValues(parameter) > 1) {
                _dims.add(parameters.getNumValues(parameter));
                varyingParameters.add(parameter);
            }
        }

        if (varyingParameters.isEmpty()) {
            simulationWrappers.add(new SimulationWrapper(simulation));
        } else {

            int[] dims = new int[_dims.size()];
            for (int i = 0; i < _dims.size(); i++) dims[i] = _dims.get(i);

            CombinationGenerator gen = new CombinationGenerator(dims);
            int[] choice;

            while ((choice = gen.next()) != null) {
                SimulationWrapper wrapper = new SimulationWrapper(simulation);

                for (int h = 0; h < dims.length; h++) {
                    String p = varyingParameters.get(h);
                    Object[] values = parameters.getValues(p);
                    Object value = values[choice[h]];
                    wrapper.setValue(p, value);
                }

                simulationWrappers.add(wrapper);
            }
        }
        return simulationWrappers;
    }


    private double[][][][] calcStats(final List<AlgorithmSimulationWrapper> algorithmSimulationWrappers, Statistics statistics,
                                     Parameters parameters) {
        int numGraphTypes = 4;

        graphTypeUsed = new boolean[4];
        int numRuns = parameters.getInt("numRuns");

        double[][][][] allStats = new double[4][algorithmSimulationWrappers.size()][statistics.size() + 1][numRuns];

        List<Run> runs = new ArrayList<>();
        int index = 0;

        for (int algSimIndex = 0; algSimIndex < algorithmSimulationWrappers.size(); algSimIndex++) {
            for (int runIndex = 0; runIndex < numRuns; runIndex++) {
                AlgorithmSimulationWrapper algorithmSimulationWrapper = algorithmSimulationWrappers.get(algSimIndex);
                runs.add(new Run(algSimIndex, runIndex, index++, algorithmSimulationWrapper));
            }
        }

        List<AlgorithmTask> tasks = new ArrayList<>();

        for (Run run : runs) {
            tasks.add(new AlgorithmTask(algorithmSimulationWrappers, statistics, parameters,
                    numGraphTypes, allStats, run));
        }

        class Task extends RecursiveTask<Boolean> {
            List<AlgorithmTask> tasks;

            public Task(List<AlgorithmTask> tasks) {
                this.tasks = tasks;
            }

            @Override
            protected Boolean compute() {
                Queue<AlgorithmTask> tasks = new ArrayDeque<>();

                for (int i = 0; i < this.tasks.size(); i += 1) {
                    AlgorithmTask task = this.tasks.get(i);
                    tasks.add(task);
                    task.fork();

                    for (AlgorithmTask _task : new ArrayList<>(tasks)) {
                        if (_task.isDone()) {
                            _task.join();
                            tasks.remove(_task);
                        }
                    }

                    while (tasks.size() > 3) {
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

        return allStats;
    }

    private class AlgorithmTask extends RecursiveTask<Boolean> {
        private List<AlgorithmSimulationWrapper> algorithmSimulationWrappers;
        private Statistics statistics;
        private Parameters parameters;
        private int numGraphTypes;
        private double[][][][] allStats;
        private final Run run;

        public AlgorithmTask(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                             Statistics statistics, Parameters parameters,
                             int numGraphTypes, double[][][][] allStats, Run run) {
            this.algorithmSimulationWrappers = algorithmSimulationWrappers;
            this.statistics = statistics;
            this.parameters = parameters;
            this.numGraphTypes = numGraphTypes;
            this.allStats = allStats;
            this.run = run;
        }

        @Override
        protected Boolean compute() {
            doRun(algorithmSimulationWrappers, statistics, parameters, numGraphTypes,
                    allStats, run);
            return true;
        }
    }

    private void doRun(List<AlgorithmSimulationWrapper> algorithmSimulationWrappers, Statistics statistics,
                       Parameters parameters, int numGraphTypes, double[][][][] allStats, Run run) {
        System.out.println();
        System.out.println("Run " + (run.getRunIndex() + 1));
        System.out.println();

        AlgorithmSimulationWrapper algorithmSimulationWrapper = algorithmSimulationWrappers.get(run.getAlgSimIndex());
        AlgorithmWrapper algorithmWrapper = algorithmSimulationWrappers.get(run.getAlgSimIndex()).getAlgorithmWrapper();
        SimulationWrapper simulationWrapper = (SimulationWrapper) algorithmSimulationWrapper.getSimulation();
        Simulation simulation = algorithmSimulationWrapper.getSimulation();
        DataSet data = simulation.getDataSet(run.getRunIndex());
        Graph trueGraph = simulation.getTrueGraph();

        boolean isMixed = data.isMixed();

        System.out.println((run.getAlgSimIndex() + 1) + ". " + algorithmSimulationWrapper.getDescription()
                + " simulation: " + simulation.getDescription());

        long start = System.currentTimeMillis();
        Graph out;

        try {
            DataSet copy = data.copy();

            Parameters algParameters = algorithmSimulationWrapper.getAlgorithmWrapper().getAlgorithmSpecificParameters();

            for (String p : algorithmWrapper.getOverriddenParameters()) {
                algParameters.setValue(p, algorithmWrapper.getValue(p));
            }

            parameters.setOverriddenParameters(algorithmSimulationWrapper.getOverriddenParametersMap());

            out = algorithmSimulationWrapper.search(copy, algParameters);
        } catch (Exception e) {
            System.out.println("Could not run " + algorithmSimulationWrapper.getDescription());
            e.printStackTrace();
            return;
        }

        String path = null;

        if (simulationWrapper.getSimulation() instanceof SimulationPath) {
            path = ((SimulationPath) simulationWrapper.getSimulation()).getPath();
        }

        printGraph(path, out, run.getRunIndex(), algorithmSimulationWrapper);

        long stop = System.currentTimeMillis();

        long elapsed = stop - start;

        if (trueGraph != null) {
            out = GraphUtils.replaceNodes(out, trueGraph.getNodes());
        }

        Graph[] est = new Graph[numGraphTypes];

        Graph comparisonGraph = trueGraph == null ? null : algorithmSimulationWrapper.getComparisonGraph(trueGraph);

        est[0] = out;
        graphTypeUsed[0] = true;

        if (isMixed) {
            est[1] = getSubgraph(out, true, true, data);
            est[2] = getSubgraph(out, true, false, data);
            est[3] = getSubgraph(out, false, false, data);

            graphTypeUsed[1] = true;
            graphTypeUsed[2] = true;
            graphTypeUsed[3] = true;
        }

        Graph[] truth = new Graph[numGraphTypes];

        truth[0] = comparisonGraph;

        if (isMixed && comparisonGraph != null) {
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

                    double stat;

                    if (_stat instanceof ElapsedTime) {
                        stat = elapsed / 1000.0;
                    } else if (_stat instanceof ParameterColumn) {
                        stat = run.getWrapper().getAlgorithmWrapper().getAlgorithmSpecificParameters()
                                .getDouble(_stat.getAbbreviation());
                    } else {
                        stat = _stat.getValue(truth[u], est[u]);
                    }

                    if (!Double.isNaN(stat)) {
                        allStats[u][run.getAlgSimIndex()][statIndex][run.getRunIndex()] = stat;
                    }
                }
            }
        }
    }

    private void printGraph(String path, Graph graph, int i, Algorithm algorithm) {
        if (!saveGraphs) {
            return;
        }

        try {
            String description = algorithm.getDescription();
            File file;

            if (path != null) {
                File _path = new File(path);
                file = new File("comparison/" + _path.getName() + "." + description + ".graph" + "." + (i + 1) + ".txt");
            } else {
                file = new File("comparison/" + description + ".graph" + "." + (i + 1) + ".txt");
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
                                        List<AlgorithmSimulationWrapper> wrappers, int numStats) {
        double[][][] statTables = new double[numTables][wrappers.size()][numStats + 1];

        for (int u = 0; u < numTables; u++) {
            for (int i = 0; i < wrappers.size(); i++) {
                for (int j = 0; j < numStats; j++) {
                    if (mode == Mode.Average) {
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
                            List<AlgorithmWrapper> algorithmWrappers,
                            List<AlgorithmSimulationWrapper> algorithmSimulationWrappers,
                            Simulations simulations, double[] utilities) {

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

        boolean showSimulationIndices = simulations.getSimulations().size() > 1;
        boolean showAlgorithmIndices = algorithmWrappers.size() > 1;

        for (int u = 0; u < numTables; u++) {
            if (!graphTypeUsed[u]) continue;

            int rows = algorithmSimulationWrappers.size() + 1;
            int cols = (showSimulationIndices ? 1 : 0) + (showAlgorithmIndices ? 1 : 0) + numStats
                    + (statistics.isShowUtilities() ? 1 : 0);

            TextTable table = new TextTable(rows, cols);
            table.setTabDelimited(isTabDelimitedTables());

            int initialColumn = 0;

            if (showAlgorithmIndices) {
                table.setToken(0, initialColumn, "Alg");

                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    AlgorithmWrapper algorithm = algorithmSimulationWrappers.get(newOrder[t]).getAlgorithmWrapper();
                    table.setToken(t + 1, initialColumn, "" + (algorithmWrappers.indexOf(algorithm) + 1));
                }

                initialColumn++;
            }

            if (showSimulationIndices) {
                table.setToken(0, initialColumn, "Sim");

                for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                    Simulation simulation = algorithmSimulationWrappers.get(newOrder[t]).getSimulation();
                    List<Simulation> _simulations = simulations.getSimulations();
                    table.setToken(t + 1, initialColumn, "" + (_simulations.indexOf(simulation) + 1));
                }

                initialColumn++;
            }

            for (int statIndex = 0; statIndex < numStats; statIndex++) {
                String statLabel = statistics.getStatistics().get(statIndex).getAbbreviation();
                table.setToken(0, initialColumn + statIndex, statLabel);
            }

            if (statistics.isShowUtilities()) {
                table.setToken(0, initialColumn + numStats, "U");
            }

            for (int t = 0; t < algorithmSimulationWrappers.size(); t++) {
                for (int statIndex = 0; statIndex < numStats; statIndex++) {
                    double stat = statTables[u][newOrder[t]][statIndex];
                    table.setToken(t + 1, initialColumn + statIndex,
                            Math.abs(stat) < 0.1 ? smallNf.format(stat) : nf.format(stat));
                }

                table.setToken(t + 1, initialColumn + numStats, nf.format(utilities[newOrder[t]]));
            }


            out.println(getHeader(u));
            out.println();
            out.println(table);
        }
    }

    private double[] calcUtilities(Statistics statistics, List<AlgorithmSimulationWrapper> wrappers, int numStats,
                                   double[][] stats) {
        List<List<Double>> all = new ArrayList<>();

        for (int m = 0; m < numStats; m++) {
            ArrayList<Double> list = new ArrayList<>();

            try {
                for (int t = 0; t < wrappers.size(); t++) {
                    double _stat = stats[t][m];

                    if (!list.contains(_stat)) {
                        list.add(_stat);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Collections.sort(list);

            all.add(list);
        }

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
                    double _stat = stats[t][j];
                    double utility;

                    if (stat.getAbbreviation().equals("E") || stat.getAbbreviation().equals("SHD")) {
                        utility = all.get(j).indexOf(_stat) / (double) all.get(j).size();
                    } else {
                        utility = _stat;
                    }

                    sum += weight * utility;
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

        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -Double.compare(utilities[o1], utilities[o2]);
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
        private Algorithm algorithm;
        private Parameters parameters;
        private List<String> overriddenParameters = new ArrayList<>();

        public AlgorithmWrapper(Algorithm algorithm, Parameters parameters) {
            this.algorithm = algorithm;
            this.parameters = new Parameters(parameters);
        }

        @Override
        public Graph search(DataSet dataSet, Parameters parameters) {
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
                for (String parameter : overriddenParameters) {
                    description.append(", " + parameter + " = " + parameters.getValues(parameter)[0]);
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

        public List<String> getOverriddenParameters() {
            return new ArrayList<>(overriddenParameters);
        }

        public void setValue(String parameter, Object value) {
            parameters.setValue(parameter, value);
            this.overriddenParameters.add(parameter);
        }

        public Object getValue(String parameter) {
            return parameters.getValues(parameter)[0];
        }

        public Algorithm getAlgorithm() {
            return algorithm;
        }

        public Parameters getAlgorithmSpecificParameters() {
            return this.parameters;
        }
    }

    private class AlgorithmSimulationWrapper implements Algorithm {
        private Map<String, Object> overriddenParameters = new LinkedHashMap<>();
        private Simulation simulation;
        private AlgorithmWrapper algorithmWrapper;

        public AlgorithmSimulationWrapper(AlgorithmWrapper algorithm, Simulation simulation) {
            this.algorithmWrapper = algorithm;
            this.simulation = simulation;
        }

        @Override
        public Graph search(DataSet dataSet, Parameters parameters) {
            return algorithmWrapper.search(dataSet, parameters);
        }

        @Override
        public Graph getComparisonGraph(Graph graph) {
            return algorithmWrapper.getComparisonGraph(graph);
        }

        @Override
        public String getDescription() {
            return algorithmWrapper.getDescription();
        }

        @Override
        public DataType getDataType() {
            return algorithmWrapper.getDataType();
        }

        @Override
        public List<String> getParameters() {
            return algorithmWrapper.getParameters();
        }

        public void setValue(String parameter, Object value) {
            this.overriddenParameters.put(parameter, value);
        }

        public Object getValue(String parameter) {
            return overriddenParameters.get(parameter);
        }

        public Map<String, Object> getOverriddenParametersMap() {
            return overriddenParameters;
        }

        public Simulation getSimulation() {
            return simulation;
        }

        public AlgorithmWrapper getAlgorithmWrapper() {
            return algorithmWrapper;
        }
    }

    private class SimulationWrapper implements Simulation {
        private Simulation simulation;
        private Map<String, Object> overriddenParameters = new LinkedHashMap<>();
        private Graph graph;
        private List<DataSet> dataSets;

        public SimulationWrapper(Simulation simulation) {
            this.simulation = simulation;
        }

        @Override
        public void createData(Parameters parameters) {
            simulation.createData(parameters);
            this.graph = simulation.getTrueGraph();
            this.dataSets = new ArrayList<>();
            for (int i = 0; i < simulation.getNumDataSets(); i++) {
                this.dataSets.add(simulation.getDataSet(i));
            }
        }

        @Override
        public int getNumDataSets() {
            return dataSets.size();
        }

        @Override
        public Graph getTrueGraph() {
            if (graph == null) return null;
            else return new EdgeListGraph(graph);
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

        public Map<String, Object> getOverriddenParameters() {
            return overriddenParameters;
        }

        public void setValue(String parameter, Object value) {
            this.overriddenParameters.put(parameter, value);
        }

        public Object getValue(String parameter) {
            return overriddenParameters.get(parameter);
        }

        public Simulation getSimulation() {
            return simulation;
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




