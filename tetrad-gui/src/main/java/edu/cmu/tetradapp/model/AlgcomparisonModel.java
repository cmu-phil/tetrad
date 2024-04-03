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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * A model for the algcomaprison editor.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AlgcomparisonModel implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The comparison object. This is used to run a comparison of algorithms. The simulation may be varied; a variety of
     * algorithms may be selected depending on the type of the algorithm, and a variety of statistics may be selected. A
     * list of parameters will be given that depend on the simulation and the algorithms selected.
     */
    private final List<String> statNames;
    private final List<String> simNames;
    private final List<Class<? extends Simulation>> simulationClasses;
    private final List<Class<? extends Algorithm>> algorithmClasses;
    private final List<Class<? extends Statistic>> statisticsClasses;
    private final Parameters parameters = new Parameters();
    private List<String> algNames;
    private LinkedList<Simulation> selectedSimulations = new LinkedList<>();
    private LinkedList<Algorithm> selectedAlgorithms = new LinkedList<>();
    private LinkedList<Statistic> selectedStatistics = new LinkedList<>();
    private List<String> selectedParameters = new LinkedList<>();

    private String name = "Algcomparison";


    private String resultsPath = Preferences.userRoot().get("edu.cmu.tetrad.resultsPath", System.getProperty("user.home") + "/comparison-results");
    private String outputFileName = "Comparison";
    private Map<String, Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation>> simulationMap;
    private Map<String, Class<? extends Statistic>> statisticsMap;
    private Map<String, Class<? extends Algorithm>> algorithmMap;

    public AlgcomparisonModel(Parameters parameters) {

        Set<Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation>> _simulations = findImplementations("edu.cmu.tetrad.algcomparison.simulation",
                edu.cmu.tetrad.algcomparison.simulation.Simulation.class);
        simulationClasses = new ArrayList<>(_simulations);

        Set<Class<? extends Algorithm>> _algorithms = findImplementations("edu.cmu.tetrad.algcomparison.algorithm",
                Algorithm.class);

        this.algorithmClasses = new ArrayList<>(_algorithms);

        Set<Class<? extends Statistic>> _statistics = findImplementations("edu.cmu.tetrad.algcomparison.statistic",
                Statistic.class);

        this.statisticsClasses = new ArrayList<>(_statistics);

        this.simulationClasses.sort(Comparator.comparing(Class::getName));
        this.algorithmClasses.sort(Comparator.comparing(Class::getName));
        this.statisticsClasses.sort(Comparator.comparing(Class::getName));

        algNames = getAlgorithmNamesFromAnnotations(algorithmClasses);
        statNames = getStatisticsNamesFromImplemenations(statisticsClasses);
        simNames = getSimulationNamesFromImplemenations(simulationClasses);

        this.algNames.sort(String.CASE_INSENSITIVE_ORDER);
        this.statNames.sort(String.CASE_INSENSITIVE_ORDER);
        this.simNames.sort(String.CASE_INSENSITIVE_ORDER);

    }

    /**
     * Finds and returns a set of classes that implement a given interface within a specified package.
     *
     * @param packageName    The name of the package to search in.
     * @param interfaceClazz The interface class to find implementations of.
     * @return A set of classes that implement the specified interface.
     */
    private static <T> Set<Class<? extends T>> findImplementations(String packageName, Class<T> interfaceClazz) {
        Reflections reflections = new Reflections(packageName, Scanners.SubTypes);
        return reflections.getSubTypesOf(interfaceClazz);
    }

    public void runComparison(java.io.PrintStream localOut) {
        Simulations simulations = new Simulations();
        for (Simulation simulation : this.selectedSimulations) simulations.add(simulation);

        Algorithms algorithms = new Algorithms();
        for (Algorithm algorithm : this.selectedAlgorithms) algorithms.add(algorithm);

        Statistics statistics = new Statistics();
        for (Statistic statistic : this.selectedStatistics) statistics.add(statistic);

        Comparison comparison = new Comparison();
        comparison.compareFromSimulations(resultsPath, simulations, outputFileName, localOut,
                algorithms, statistics, parameters);
    }

    /**
     * Returns the name of the session model.
     *
     * @return The name of the session model.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the session model.
     *
     * @param name the name of the session model.
     * @throws IllegalArgumentException if the name is null or blank.
     */
    @Override
    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name must not be null.");
        }

        if (name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank.");
        }

        this.name = name;
    }

    /**
     * The currently selected simulation in the AlgcomparisonModel. A list of size one (enforced) that contains the
     * selected simulation.
     */
    public Simulations getSelectedSimulations() {
        Simulations simulations = new Simulations();
        for (Simulation simulation : this.selectedSimulations) simulations.add(simulation);

        return simulations;
    }

    /**
     * Sets the selected simulations in the AlgcomparisonModel.
     *
     * @param name The name of the selected simulation to be set.
     * @throws IllegalArgumentException if the selected simulations is null, empty, or not in the list of simulations.
     */
    public void setSelectedSimulation(String name) {
        if (!simulationMap.containsKey(name)) {
            throw new IllegalArgumentException("Selected simulation must be in the list of simulations.");
        }

        Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation> simulation = simulationMap.get(name);

        if (!(simulationClasses.contains(simulation))) {
            throw new IllegalArgumentException("Selected simulation must be in the list of simulations.");
        }

    }

    /**
     * A private instance variable that holds a list of selected Algorithm objects.
     */
    public Algorithms getSelectedAlgorithms() {
        Algorithms algorithms = new Algorithms();
        for (Algorithm algorithm : this.selectedAlgorithms) algorithms.add(algorithm);
        return algorithms;
    }

    /**
     * A private instance variable that holds a list of selected Statistic objects.
     */
    public Statistics getSelectedStatistics() {
        Statistics statistics = new Statistics();
        for (Statistic statistic : this.selectedStatistics) statistics.add(statistic);
        return statistics;
    }

    /**
     * A private instance variable that holds a list of selected Parameter objects.
     */
    public List<String> getSelectedParameters() {
        return new ArrayList<>(selectedParameters);
    }

    /**
     * For each algorithm class, use reflection to get the annotation for that class, and add the name of the algorithm
     * to a list of algorithm names.
     */
    private List<String> getAlgorithmNamesFromAnnotations(List<Class<? extends Algorithm>> algorithmClasses) {
        List<String> algorithmNames = new ArrayList<>();
        Map<String, Class<? extends Algorithm>> algorithmMap = new HashMap<>();

        for (Class<? extends Algorithm> algorithm : algorithmClasses) {
            edu.cmu.tetrad.annotation.Algorithm algAnnotation = algorithm.getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class);

            if (algAnnotation != null) {
                String _name = algAnnotation.name();
                algorithmNames.add(_name);
                algorithmMap.put(_name, algorithm);
            }
        }

        this.algorithmMap = algorithmMap;

        return algorithmNames;
    }

    private List<String> getStatisticsNamesFromImplemenations(List<Class<? extends Statistic>> algorithmClasses) {
        List<String> statisticsNames = new ArrayList<>();
        Map<String, Class<? extends Statistic>> statisticsMap = new HashMap<>();

        for (Class<? extends Statistic> statistic : algorithmClasses) {
            try {
                Statistic _statistic = statistic.getConstructor().newInstance();
                String abbreviation = _statistic.getAbbreviation();
                statisticsNames.add(abbreviation);
                statisticsMap.put(abbreviation, statistic);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                // Skip.
            }
        }

        this.statisticsMap = statisticsMap;

        return statisticsNames;
    }

    private List<String> getSimulationNamesFromImplemenations(List<Class<? extends Simulation>> algorithmClasses) {
        List<String> simulationNames = new ArrayList<>();
        Map<String, Class<? extends Simulation>> simulationMap = new HashMap<>();

        RandomGraph graph = new RandomForward();

        for (Class<? extends Simulation> statistic : algorithmClasses) {
            try {
                Simulation _statistic = statistic.getConstructor(RandomGraph.class).newInstance(graph);
                String shortName = _statistic.getShortName();
                simulationNames.add(shortName);
                simulationMap.put(shortName, statistic);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                // Skip.
            }
        }

        this.simulationMap = simulationMap;

        return simulationNames;
    }

    /**
     * Retrieves the results path.
     *
     * @return The results path.
     */
    public String getResultsPath() {
        return resultsPath;
    }

    /**
     * Sets the results path in the AlgcomparisonModel.
     *
     * @param resultsPath The results path to be set. Must not be null or blank.
     * @throws IllegalArgumentException If the results path is null or blank.
     */
    public void setResultsPath(String resultsPath) {
        if (resultsPath == null) {
            throw new IllegalArgumentException("Results path must not be null.");
        }

        if (resultsPath.isBlank()) {
            throw new IllegalArgumentException("Results path must not be blank.");
        }

        this.resultsPath = resultsPath;
    }

    /**
     * Returns the output file name.
     *
     * @return The output file name.
     */
    public String getOutputFileName() {
        return outputFileName;
    }

    /**
     * Sets the output file name.
     *
     * @param outputFileName The output file name to be set. Must not be null or blank.
     * @throws IllegalArgumentException If the output file name is null or blank.
     */
    public void setOutputFileName(String outputFileName) {
        if (outputFileName == null) {
            throw new IllegalArgumentException("Output file name must not be null.");
        }

        if (outputFileName.isBlank()) {
            throw new IllegalArgumentException("Output file name must not be blank.");
        }

        this.outputFileName = outputFileName;
    }

    /**
     * A list of possible simulations.
     */
    public List<String> getSimulationName() {
        return simNames;
    }

    /**
     * A private instance variable that holds a list of possible Algorithm objects.
     */
    public List<String> getAlgorithmsName() {
        return algNames;
    }

    /**
     * A private instance variable that holds a list of possible Statistic objects.
     */
    public List<String> getStatisticsNames() {
        return statNames;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public void addSimulation(Simulation simulation) {
        selectedSimulations.add(simulation);
    }

    public void removeLastSimulation() {
        selectedSimulations.removeLast();
    }

    public void addAlgorithm(Algorithm algorithm) {
        selectedAlgorithms.add(algorithm);
    }

    public void removeLastAlgorithm() {
        selectedAlgorithms.removeLast();
    }

    public void removeLastStatistic() {
        selectedStatistics.removeLast();
    }

    public void addStatistic(Statistic selectedItem) {
        selectedStatistics.add(selectedItem);
    }

    public List<Class<? extends Statistic>> getStatisticsClasses() {
        return statisticsClasses;
    }
}



