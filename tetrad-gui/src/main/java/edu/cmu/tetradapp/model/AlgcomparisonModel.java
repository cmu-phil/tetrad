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
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.io.PrintStream;
import java.io.Serial;
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

    private List<Class<? extends Simulation>> simulations;
    private List<Class<? extends Algorithm>> algorithms ;
    private List<Class<? extends Statistic>> statistics;
    /**
     * A private instance variable that holds a list of possible Parameter objects.
     */
    private final Parameters parameters = new Parameters();
    private Simulations selectedSimulations = new Simulations();
    private Algorithms selectedAlgorithms = new Algorithms();
    private Statistics selectedStatistics = new Statistics();
    private List<String> selectedParameters = new LinkedList<>();

    private String name = "Algcomparison";

    private String resultsPath = Preferences.userRoot().get("edu.cmu.tetrad.resultsPath", System.getProperty("user.home") + "/comparison-results");

    private String outputFileName = "Comparison";

    private PrintStream localOut = null;

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
     * @param selectedSimulations The selected simulations to be set.
     * @throws IllegalArgumentException if the selected simulations is null, empty, or not in the list of simulations.
     */
    public void setSelectedSimulations(Simulations selectedSimulations) {
        if (selectedSimulations == null) {
            throw new IllegalArgumentException("Selected simulation must not be null.");
        }

        if (selectedSimulations.getSimulations().isEmpty()) {
            throw new IllegalArgumentException("Selected simulation must not be empty.");
        }

        for (edu.cmu.tetrad.algcomparison.simulation.Simulation simulation : selectedSimulations.getSimulations()) {
            if (!(simulations.contains(simulation))) {
                throw new IllegalArgumentException("Selected simulation must be in the list of simulations.");
            }
        }

        this.selectedSimulations = selectedSimulations;
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
     * Finds and returns a set of classes that implement a given interface within a specified package.
     *
     * @param packageName The name of the package to search in.
     * @param interfaceClazz The interface class to find implementations of.
     * @return A set of classes that implement the specified interface.
     */
    private static <T> Set<Class<? extends T>> findImplementations(String packageName, Class<T> interfaceClazz) {
        Reflections reflections = new Reflections(packageName, Scanners.SubTypes);
        Set<Class<? extends T>> subTypesOf = reflections.getSubTypesOf(interfaceClazz);
        return subTypesOf;
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
    public List<String> getSimulationsNames() {
        List<String> simulationsNames = new ArrayList<>();

        for (Class<? extends Simulation> simulation : simulations) {
            String[] split = simulation.getName().split("\\.");
            String name = split[split.length - 1];
            simulationsNames.add(name);
        }

        simulationsNames.sort(String.CASE_INSENSITIVE_ORDER);

        return simulationsNames;
    }

    /**
     * A private instance variable that holds a list of possible Algorithm objects.
     */
    public List<String> getAlgorithmsNames() {
        List<String> algorithmsNames = new ArrayList<>();

        for (Class<? extends Algorithm> algorithm : algorithms) {
            String[] split = algorithm.getName().split("\\.");
            String name = split[split.length - 1];
            algorithmsNames.add(name);
        }

        algorithmsNames.sort(String.CASE_INSENSITIVE_ORDER);

        return algorithmsNames;
    }

    /**
     * A private instance variable that holds a list of possible Statistic objects.
     */
    public List<String> getStatisticsNames() {
        List<String> statisticsNames = new ArrayList<>();

        for (Class<? extends Statistic> statistic : statistics) {
            String[] split = statistic.getName().split("\\.");
            String name = split[split.length - 1];
            statisticsNames.add(name);
        }

        statisticsNames.sort(String.CASE_INSENSITIVE_ORDER);

        return statisticsNames;
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



