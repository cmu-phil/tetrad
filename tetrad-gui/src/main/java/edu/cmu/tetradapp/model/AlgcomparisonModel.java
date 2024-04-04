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
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * The AlgcomparisonModel class is a session model that allows for running comparisons of algorithms. It provides
 * methods for selecting algorithms, simulations, statistics, and parameters , and then running the comparison.
 *
 * @author josephramsey
 */
public class AlgcomparisonModel implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * A private final variable that holds a Parameters object.
     */
    private final Parameters parameters;
    /**
     * This variable holds a list of statNames.
     *
     * <p>
     * The statNames list is a private instance variable of type List&lt;String&gt;.
     * </p>
     */
    private transient List<String> statNames;
    /**
     * A private transient final variable that holds a list of simulation names.
     */
    private transient List<String> simNames;
    /**
     * A private transient final variable that holds a list of simulation classes.
     */
    private transient List<Class<? extends Simulation>> simulationClasses;
    /**
     * A private transient final variable that holds a list of statistic classes.
     */
    private transient List<Class<? extends Statistic>> statisticsClasses;
    /**
     * A private final transient variable that holds a list of algorithm classes.
     */
    private transient List<Class<? extends Algorithm>> algorithmClasses;
    /**
     * A private final transient variable that holds a list of algorithm names.
     */
    private transient List<String> algNames;
    /**
     * The selected parameters for the AlgcomparisonModel.
     */
    private transient List<String> selectedParameters;
    /**
     * The list of selected simulations in the AlgcomparisonModel. This list holds Simulation objects, which are
     * implementations of the Simulation interface. It is a transient field, meaning it is not serialized when the
     * object is saved.
     */
    private transient LinkedList<Simulation> selectedSimulations;
    /**
     * The selected algorithms for the AlgcomparisonModel.
     */
    private transient LinkedList<Algorithm> selectedAlgorithms;
    /**
     * The selected statistics for the AlgcomparisonModel.
     */
    private transient LinkedList<Statistic> selectedStatistics;
    /**
     * The name of the AlgcomparisonModel.
     */
    private String name = "Algcomparison";
    /**
     * The results path for the AlgcomparisonModel.
     */
    private final String resultsPath = Preferences.userRoot().get("edu.cmu.tetrad.resultsPath", System.getProperty("user.home") + "/comparison-results");
    /**
     * A private final transient variable that holds a map of simulation names to simulation classes.
     */
    private Map<String, Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation>> simulationMap;

    /**
     * Constructs a new AlgcomparisonModel with the specified parameters.
     *
     * @param parameters The parameters to be set.
     */
    public AlgcomparisonModel(Parameters parameters) {
        this.parameters = parameters;
        initializeIfNull();
    }

    @NotNull
    private static List<Class<? extends Algorithm>> findAlgorithmClasses() {
        Set<Class<? extends Algorithm>> _algorithms = findImplementations("edu.cmu.tetrad.algcomparison.algorithm",
                Algorithm.class);
        final List<Class<? extends Algorithm>> algorithmClasses = new ArrayList<>(_algorithms);
        algorithmClasses.sort(Comparator.comparing(Class::getName));
        return algorithmClasses;
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

    /**
     * Runs the comparison of simulations, algorithms, and statistics.
     *
     * @param localOut The output stream to write the comparison results.
     */
    public void runComparison(java.io.PrintStream localOut) {
        initializeIfNull();

        Simulations simulations = new Simulations();
        for (Simulation simulation : this.selectedSimulations) simulations.add(simulation);

        Algorithms algorithms = new Algorithms();
        for (Algorithm algorithm : this.selectedAlgorithms) algorithms.add(algorithm);

        Statistics statistics = new Statistics();
        for (Statistic statistic : this.selectedStatistics) statistics.add(statistic);

        Comparison comparison = new Comparison();

        // Making a copy of the parameters to send to Comparison since Comparison iterates
        // over the parameters and modifies them.
        /**
         * The output file name for the AlgcomparisonModel.
         */
        String outputFileName = "Comparison";
        comparison.compareFromSimulations(resultsPath, simulations, outputFileName, localOut,
                algorithms, statistics, new Parameters(parameters));
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

    /**
     * A private instance variable that holds a list of possible parameters.
     *
     * @return A list of possible parameters.
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Add a simulation to the list of selected simulations.
     *
     * @param simulation The simulation to add.
     */
    public void addSimulation(Simulation simulation) {
        initializeIfNull();
        selectedSimulations.add(simulation);
    }

    /**
     * Remove the last simulation from the list of selected simulations.
     */
    public void removeLastSimulation() {
        initializeIfNull();
        if (!selectedSimulations.isEmpty()) {
            selectedSimulations.removeLast();
        }
    }

    /**
     * Add an algorithm to the list of selected algorithms.
     *
     * @param algorithm The algorithm to add.
     */
    public void addAlgorithm(Algorithm algorithm) {
        initializeIfNull();
        selectedAlgorithms.add(algorithm);
    }

    /**
     * Remove the last algorithm from the list of selected algorithms.
     */
    public void removeLastAlgorithm() {
        initializeIfNull();
        if (!selectedAlgorithms.isEmpty()) {
            selectedAlgorithms.removeLast();
        }
    }

    /**
     * Add a statistic to the list of selected statistics.
     *
     * @param statistic The statistic to add.
     */
    public void addStatistic(Statistic statistic) {
        initializeIfNull();
        selectedStatistics.add(statistic);
    }

    /**
     * Remove the last statistic from the list of selected statistics.
     */
    public void removeLastStatistic() {
        initializeIfNull();
        if (!selectedStatistics.isEmpty()) {
            selectedStatistics.removeLast();
        }
    }

    /**
     * Returns the list of statistics classes.
     */
    public List<Class<? extends Statistic>> getStatisticsClasses() {
        initializeIfNull();
        return new ArrayList<>(statisticsClasses);
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
        initializeIfNull();
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

    private void initializeIfNull() {
        if (selectedSimulations == null || selectedAlgorithms == null || selectedStatistics == null
            || selectedParameters == null) {
            initializeSimulationsEtc();
        }

        if (this.selectedParameters == null) {
            this.selectedParameters = new LinkedList<>();
        }

        if (simulationClasses == null || algorithmClasses == null || statisticsClasses == null) {
            initializeClasses();
        }

        if (algNames == null || statNames == null || simNames == null) {
            initializeNames();
        }
    }

    private void initializeSimulationsEtc() {
        this.selectedSimulations = new LinkedList<>();
        this.selectedAlgorithms = new LinkedList<>();
        this.selectedStatistics = new LinkedList<>();
        this.selectedParameters = new LinkedList<>();
    }

    private void initializeClasses() {
        simulationClasses = findSimulationClasses();
        algorithmClasses = findAlgorithmClasses();
        statisticsClasses = findStatisticsClasses();
    }

    private void initializeNames() {
        algNames = getAlgorithmNamesFromAnnotations(algorithmClasses);
        statNames = getStatisticsNamesFromImplemenations(statisticsClasses);
        simNames = getSimulationNamesFromImplemenations(simulationClasses);

        this.algNames.sort(String.CASE_INSENSITIVE_ORDER);
        this.statNames.sort(String.CASE_INSENSITIVE_ORDER);
        this.simNames.sort(String.CASE_INSENSITIVE_ORDER);
    }

    @NotNull
    private List<Class<? extends Simulation>> findSimulationClasses() {
        Set<Class<? extends Simulation>> _simulations = findImplementations("edu.cmu.tetrad.algcomparison.simulation",
                Simulation.class);
        final List<Class<? extends Simulation>> simulationClasses = new ArrayList<>(_simulations);
        simulationClasses.sort(Comparator.comparing(Class::getName));
        return simulationClasses;
    }

    private List<Class<? extends Statistic>> findStatisticsClasses() {
        Set<Class<? extends Statistic>> _statistics = findImplementations("edu.cmu.tetrad.algcomparison.statistic",
                Statistic.class);
        final List<Class<? extends Statistic>> statisticsClasses = new ArrayList<>(_statistics);
        statisticsClasses.sort(Comparator.comparing(Class::getName));
        return statisticsClasses;
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

        /**
         * A private final transient variable that holds a map of statistic names to statistic classes.
         */
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

        /**
         * A private final transient variable that holds a map of algorithm names to algorithm classes.
         */

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

}



