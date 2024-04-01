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
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.io.PrintStream;
import java.io.Serial;
import java.lang.reflect.Constructor;
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
    private final Comparison comparison = new Comparison();
    private final List<String> statNames;
    private final List<String> simNames;
    private final List<Class<? extends Simulation>> simulations;
    private final List<Class<? extends Algorithm>> algorithms;
    private final List<Class<? extends Statistic>> statistics;
    /**
     * A private instance variable that holds a list of possible Parameter objects.
     */
    private final Parameters parameters = new Parameters();
    private List<String> algNames;
    private Simulations selectedSimulations = new Simulations();
    private Algorithms selectedAlgorithms = new Algorithms();
    private Statistics selectedStatistics = new Statistics();
    private List<String> selectedParameters = new LinkedList<>();

    private String name = "Algcomparison";

    private String resultsPath = Preferences.userRoot().get("edu.cmu.tetrad.resultsPath", System.getProperty("user.home") + "/comparison-results");

    private String outputFileName = "Comparison";

    private PrintStream localOut = null;
    private Map<String, Class<? extends Simulation>> simulationMap;
    private Map<String, Class<? extends Statistic>> statisticsMap;
    private Map<String, Class<? extends Algorithm>> algorithmMap;

    public AlgcomparisonModel(Parameters parameters) {

        Set<Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation>> _simulations = findImplementations("edu.cmu.tetrad.algcomparison.simulation",
                edu.cmu.tetrad.algcomparison.simulation.Simulation.class);
        simulations = new ArrayList<>(_simulations);

        Set<Class<? extends Algorithm>> _algorithms = findImplementations("edu.cmu.tetrad.algcomparison.algorithm",
                Algorithm.class);

        this.algorithms = new ArrayList<>(_algorithms);

        Set<Class<? extends Statistic>> _statistics = findImplementations("edu.cmu.tetrad.algcomparison.statistic",
                Statistic.class);

        this.statistics = new ArrayList<>(_statistics);

        this.simulations.sort(Comparator.comparing(Class::getName));
        this.algorithms.sort(Comparator.comparing(Class::getName));
        this.statistics.sort(Comparator.comparing(Class::getName));

        algNames = getAlgorithmNamesFromAnnotations(algorithms);
        statNames = getStatisticsNamesFromImplemenations(statistics);
        simNames = getSimulationNamesFromImplemenations(simulations);

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
        Set<Class<? extends T>> subTypesOf = reflections.getSubTypesOf(interfaceClazz);
        return subTypesOf;
    }

    public void doComparison() {
        comparison.compareFromSimulations(resultsPath, selectedSimulations, outputFileName, localOut,
                selectedAlgorithms, selectedStatistics, parameters);
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
        return selectedSimulations;
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

        Class<? extends Simulation> simulation = simulationMap.get(name);

        if (!(simulations.contains(simulation))) {
            throw new IllegalArgumentException("Selected simulation must be in the list of simulations.");
        }

        try {

            RandomGraph graph = new RandomForward();

            Simulation _simulation = simulation.getConstructor(RandomGraph.class).newInstance(graph);

            this.selectedSimulations = new Simulations();
            this.selectedSimulations.getSimulations().add(_simulation);
            DataType dataType = _simulation.getDataType();

            List<Class<? extends Algorithm>> algorithms = new ArrayList<>();

            for (Class<? extends Algorithm> algorithm : this.algorithms) {
                try {
                    Constructor<? extends Algorithm> constructor = algorithm.getConstructor();

                    Algorithm _algorithm = constructor.newInstance();

                    if (_algorithm instanceof TakesIndependenceWrapper) {
                        TakesIndependenceWrapper takesIndependenceWrapper = (TakesIndependenceWrapper) _algorithm;
                        takesIndependenceWrapper.setIndependenceWrapper(null);
                    }

                    if (_algorithm.getDataType() == dataType) {
                        algorithms.add(algorithm);
                    }
                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                         IllegalAccessException e) {
//                    e.printStackTrace();
                }
            }

            List<String> algorithmNames = getAlgorithmNamesFromAnnotations(algorithms);
            algorithmNames.sort(String.CASE_INSENSITIVE_ORDER);

            System.out.println(algorithmNames);

            this.algNames = algorithmNames;
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            throw new IllegalArgumentException("Selected simulation must have a constructor that takes a RandomGraph.");
        }
    }

    /**
     * A private instance variable that holds a list of selected Algorithm objects.
     */
    public Algorithms getSelectedAlgorithms() {
        return selectedAlgorithms;
    }

    /**
     * Sets the selected algorithms in the AlgcomparisonModel.
     *
     * @param selectedAlgorithms The selected algorithms to be set.
     * @throws IllegalArgumentException If the selected algorithms is null, empty, or not in the list of algorithms.
     */
    public void setSelectedAlgorithms(Algorithms selectedAlgorithms) {
        if (selectedAlgorithms == null) {
            throw new IllegalArgumentException("Selected algorithms must not be null.");
        }

        if (selectedAlgorithms.getAlgorithms().isEmpty()) {
            throw new IllegalArgumentException("Selected algorithms must not be empty.");
        }

        for (Algorithm algorithm : selectedAlgorithms.getAlgorithms()) {
            if (!(algorithms.contains(algorithm))) {
                throw new IllegalArgumentException("Selected algorithm must be in the list of algorithms.");
            }
        }

        this.selectedAlgorithms = selectedAlgorithms;
    }

    /**
     * A private instance variable that holds a list of selected Statistic objects.
     */
    public Statistics getSelectedStatistics() {
        return selectedStatistics;
    }

    /**
     * Sets the selected statistics in the AlgcomparisonModel.
     *
     * @param selectedStatistics The selected statistics to be set.
     * @throws IllegalArgumentException if the selected statistics is null, empty, or not in the list of statistics.
     */
    public void setSelectedStatistics(Statistics selectedStatistics) {
        if (selectedStatistics == null) {
            throw new IllegalArgumentException("Selected statistics must not be null.");
        }

        if (selectedStatistics.getStatistics().isEmpty()) {
            throw new IllegalArgumentException("Selected statistics must not be empty.");
        }

        for (Statistic statistic : selectedStatistics.getStatistics()) {
            if (!(statistics.contains(statistic))) {
                throw new IllegalArgumentException("Selected statistic must be in the list of statistics.");
            }
        }

        this.selectedStatistics = selectedStatistics;
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

    /**
     * Sets the PrintStream to be used for local output. This is used in the interface to display output to the user.
     *
     * @param localOut the PrintStream to be set for local output.
     */
    public void setLocalOut(PrintStream localOut) {
        this.localOut = localOut;
    }
}



