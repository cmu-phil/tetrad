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
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.simulation.SingleDatasetSimulation;
import edu.cmu.tetrad.algcomparison.statistic.ParameterColumn;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.ui.model.*;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.io.File;
import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * The GridSearchModel class is a session model that allows for running comparisons of algorithms. It provides
 * methods for selecting algorithms, simulations, statistics, and parameters, and then running the comparison.
 * <p>
 * The reference is here:
 * <p>
 * Ramsey, J. D., Malinsky, D., &amp; Bui, K. V. (2020). Algcomparison: Comparing the performance of graphical structure
 * learning algorithms with tetrad. Journal of Machine Learning Research, 21(238), 1-6.
 *
 * @author josephramsey
 */
public class GridSearchModel implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * A private final variable that holds a Parameters object.
     */
    private final Parameters parameters;
    /**
     * The results path for the GridSearchModel.
     */
    private final String resultsRoot = System.getProperty("user.home");
    private final Knowledge knowledge;
    /**
     * The suppliedData variable represents a dataset that can be used in place of a simulated dataset for analysis.
     * It can be set to null if no dataset is supplied.
     * <p>
     * Using a supplied dataset restricts the analysis to only those statistics that do not require a true graph.
     * <p>
     * Example usage:
     * DataSet dataset = new DataSet();
     * suppliedData = dataset;
     */
    private DataSet suppliedData = null;
    /**
     * The suppliedGraph variable represents a graph that can be supplied by the user. This graph will be given as an
     * option in the user interface.
     */
    private Graph suppliedGraph = null;
    /**
     * The list of statistic names.
     */
    private List<String> statNames;
    /**
     * The list of simulation names.
     */
    private List<String> simNames;
    /**
     * The list of simulation classes.
     */
    private List<Class<? extends Simulation>> simulationClasses;
    /**
     * The list of statistic classes.
     */
    private List<Class<? extends Statistic>> statisticsClasses;
    /**
     * The list of algorithm classes.
     */
    private List<Class<? extends Algorithm>> algorithmClasses;
    /**
     * The list of algorithm names.
     */
    private List<String> algNames;
    /**
     * The selected parameters for the GridSearchModel.
     */
    private List<String> selectedParameters;
    /**
     * The list of selected simulations in the GridSearchModel. This list holds Simulation objects, which are
     * implementations of the Simulation interface.
     */
    private LinkedList<SimulationSpec> selectedSimulations;
    /**
     * The selected algorithms for the GridSearchModel.
     */
    private LinkedList<AlgorithmSpec> selectedAlgorithms;
    /**
     * The selected table columns for the GridSearchModel.
     */
    private LinkedList<MyTableColumn> selectedTableColumns;
    /**
     * The last comparison text displayed.
     */
    private String lastComparisonText = "";
    /**
     * The last verbose output displayed.
     */
    private String lastVerboseOutputText = "";
    /**
     * The name of the GridSearchModel.
     */
    private String name = "Grid Search";

    /**
     * Constructs a new GridSearchModel with the specified parameters.
     *
     * @param parameters The parameters to be set.
     */
    public GridSearchModel(Parameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }

        this.parameters = parameters;
        this.knowledge = null;
        initializeIfNull();
    }

    public GridSearchModel(KnowledgeBoxModel knowledge, Parameters parameters) {
        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge must not be null.");
        }

        if (parameters == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }

        this.parameters = parameters;
        this.knowledge = knowledge.getKnowledge();
        initializeIfNull();
    }

    public GridSearchModel(GraphSource graphSource, Parameters parameters) {
        if (graphSource == null) {
            throw new IllegalArgumentException("Graph source must not be null.");
        }

        if (parameters == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }

        this.parameters = parameters;
        this.knowledge = null;
        this.suppliedGraph = graphSource.getGraph();
        initializeIfNull();
    }

    public GridSearchModel(GraphSource graphSource, KnowledgeBoxModel knowledge, Parameters parameters) {
        if (graphSource == null) {
            throw new IllegalArgumentException("Graph source must not be null.");
        }

        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge must not be null.");
        }

        if (parameters == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }

        this.parameters = parameters;
        this.knowledge = knowledge.getKnowledge();
        this.suppliedGraph = graphSource.getGraph();
        initializeIfNull();
    }

    public GridSearchModel(DataWrapper dataWrapper, Parameters parameters) {
        if (dataWrapper == null) {
            throw new IllegalArgumentException("Data wrapper must not be null.");
        }

        if (parameters == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }

        this.parameters = parameters;
        this.knowledge = null;
        this.suppliedData = (DataSet) dataWrapper.getSelectedDataModel();
        initializeIfNull();
    }

    public GridSearchModel(DataWrapper dataWrapper, KnowledgeBoxModel knowledge, Parameters parameters) {
        if (dataWrapper == null) {
            throw new IllegalArgumentException("Data wrapper must not be null.");
        }

        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge must not be null.");
        }

        if (parameters == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }

        this.parameters = parameters;
        this.knowledge = knowledge.getKnowledge();
        this.suppliedData = (DataSet) dataWrapper.getSelectedDataModel();
        initializeIfNull();
    }

    /**
     * Finds and returns a list of algorithm classes that implement the Algorithm interface.
     *
     * @return A list of algorithm classes.
     */
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

    public static void sortTableColumns(List<MyTableColumn> selectedTableColumns) {
        selectedTableColumns.sort((o1, o2) -> {
            if (o1.equals(o2)) {
                return 0;
            } else if (o1.getType() == MyTableColumn.ColumnType.PARAMETER
                    && o2.getType() == MyTableColumn.ColumnType.STATISTIC) {
                return -1;
            } else if (o1.getType() == MyTableColumn.ColumnType.STATISTIC
                    && o2.getType() == MyTableColumn.ColumnType.PARAMETER) {
                return 1;
            } else {
                return String.CASE_INSENSITIVE_ORDER.compare(o1.getColumnName(), o2.getColumnName());
            }
        });
    }

    /**
     * Retrieves all simulation parameters from a list of simulation objects.
     *
     * @param simulations the list of simulation objects
     * @return a set of all simulation parameters
     */
    @NotNull
    public static Set<String> getAllSimulationParameters(List<Simulation> simulations) {
        Set<String> paramNamesSet = new HashSet<>();

        for (Simulation simulation : simulations) {
            paramNamesSet.addAll(simulation.getParameters());
        }

        return paramNamesSet;
    }

    /**
     * Retrieves all algorithms parameters from a list of Algorithm objects.
     *
     * @param algorithms the list of Algorithm objects
     * @return a set of all algorithms parameters
     */
    @NotNull
    public static Set<String> getAllAlgorithmParameters(List<AlgorithmSpec> algorithms) {
        Set<String> paramNamesSet = new HashSet<>();

        for (AlgorithmSpec algorithm : algorithms) {
            paramNamesSet.addAll(algorithm.getAlgorithmImpl().getParameters());
        }

        return paramNamesSet;
    }

    @NotNull
    public static Set<String> getAllTestParameters(List<AlgorithmSpec> algorithms) {
        Set<String> paramNamesSet = new HashSet<>();

        for (AlgorithmSpec algorithm : algorithms) {
            Algorithm algorithmImpl = algorithm.getAlgorithmImpl();

            if (algorithmImpl instanceof TakesIndependenceWrapper) {
                paramNamesSet.addAll(((TakesIndependenceWrapper) algorithmImpl).getIndependenceWrapper().getParameters());
            }
        }

        return paramNamesSet;
    }

    public static Set<String> getAllScoreParameters(List<AlgorithmSpec> algorithms) {
        Set<String> paramNamesSet = new HashSet<>();

        for (AlgorithmSpec algorithm : algorithms) {
            Algorithm algorithmImpl = algorithm.getAlgorithmImpl();

            if (algorithmImpl instanceof UsesScoreWrapper) {
                paramNamesSet.addAll(((UsesScoreWrapper) algorithmImpl).getScoreWrapper().getParameters());
            }
        }

        return paramNamesSet;
    }

    @NotNull
    public static Set<String> getAllBootstrapParameters(List<AlgorithmSpec> algorithms) {
        Set<String> paramNamesSet = new HashSet<>();

        for (AlgorithmSpec algorithm : algorithms) {
            paramNamesSet.addAll(Params.getBootstrappingParameters(algorithm.getAlgorithmImpl()));
        }

        return paramNamesSet;
    }

    private static void setWeight(Statistics selectedStatistics, String abbr, double weight) {
        for (Statistic statistic : selectedStatistics.getStatistics()) {
            if (statistic.getAbbreviation().equals(abbr)) {
                selectedStatistics.setWeight(abbr, weight);
            }
        }
    }

    /**
     * Runs the comparison of simulations, algorithms, and statistics.
     *
     * @param localOut The output stream to write the comparison results.
     */
    public void runComparison(java.io.PrintStream localOut) {
        initializeIfNull();

        Simulations simulations = new Simulations();

        if (suppliedData != null) {
            simulations.add(new SingleDatasetSimulation(suppliedData));
        } else {
            for (SimulationSpec simulation : this.selectedSimulations) simulations.add(simulation.getSimulationImpl());
        }

        Algorithms algorithms = new Algorithms();
        for (AlgorithmSpec algorithm : this.selectedAlgorithms) algorithms.add(algorithm.getAlgorithmImpl());

        Comparison comparison = new Comparison();
        comparison.setSaveData(parameters.getBoolean("algcomparisonSaveData"));
        comparison.setSaveGraphs(parameters.getBoolean("algcomparisonSaveGraphs"));
        comparison.setSaveCPDAGs(parameters.getBoolean("algcomparisonSaveCPDAGs"));
        comparison.setSavePags(parameters.getBoolean("algcomparisonSavePAGs"));
        comparison.setShowAlgorithmIndices(parameters.getBoolean("algcomparisonShowAlgorithmIndices"));
        comparison.setShowSimulationIndices(parameters.getBoolean("algcomparisonShowSimulationIndices"));
        comparison.setSortByUtility(parameters.getBoolean("algcomparisonSortByUtility"));
        comparison.setShowUtilities(parameters.getBoolean("algcomparisonShowUtilities"));
        comparison.setSetAlgorithmKnowledge(parameters.getBoolean("algcomparisonSetAlgorithmKnowledge"));
        comparison.setParallelism(parameters.getInt("algcomparisonParallelism"));
        comparison.setKnowledge(knowledge);

        String string = parameters.getString("algcomparisonGraphType", "DAG");
        ComparisonGraphType type = ComparisonGraphType.valueOf(string);

        switch (type) {
            case DAG -> comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);
            case CPDAG -> comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);
            case PAG -> comparison.setComparisonGraph(Comparison.ComparisonGraph.PAG_of_the_true_DAG);
            default -> throw new IllegalArgumentException("Invalid value for comparison graph: " + type);
        }

        String resultsPath;

        for (int i = 1; ; i++) {
            String pathname = resultsRoot + "/comparison-results/comparison-" + i;
            File resultsDir = new File(pathname);
            if (!resultsDir.exists()) {
                if (!resultsDir.mkdirs()) {
                    throw new IllegalStateException("Could not create directory: " + resultsDir);
                }
                resultsPath = pathname;
                break;
            }
        }

        // Making a copy of the parameters to send to Comparison since Comparison iterates
        // over the parameters and modifies them.
        String outputFileName = "Comparison";
        comparison.compareFromSimulations(resultsPath, simulations, outputFileName, localOut,
                algorithms, getSelectedStatistics(), new Parameters(parameters));
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
    public void addSimulationSpec(SimulationSpec simulation) {
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
    public void addAlgorithm(AlgorithmSpec algorithm) {
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
     * Add a table column to the list of selected table columns.
     *
     * @param tableColumn The table column to add.
     */
    public void addTableColumn(MyTableColumn tableColumn) {
        if (selectedTableColumns.contains(tableColumn)) return;
        initializeIfNull();
        selectedTableColumns.add(tableColumn);
        GridSearchModel.sortTableColumns(selectedTableColumns);
    }

    /**
     * Remove the last statistic from the list of selected statistics.
     */
    public void removeLastTableColumn() {
        initializeIfNull();
        if (!selectedTableColumns.isEmpty()) {
            selectedTableColumns.removeLast();
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
     * The currently selected simulation in the GridSearchModel. A list of size one (enforced) that contains the
     * selected simulation.
     */
    public Simulations getSelectedSimulations() {
        initializeIfNull();
        Simulations simulations = new Simulations();
        if (suppliedData != null) {
            simulations.add(new SingleDatasetSimulation(suppliedData));
            return simulations;
        } else {
            for (SimulationSpec simulation : this.selectedSimulations) simulations.add(simulation.getSimulationImpl());
            return simulations;
        }
    }

    /**
     * A private instance variable that holds a list of selected Algorithm objects.
     */
    public List<AlgorithmSpec> getSelectedAlgorithms() {
        return selectedAlgorithms;
    }

    public List<MyTableColumn> getSelectedTableColumns() {
        GridSearchModel.sortTableColumns(selectedTableColumns);
        return new ArrayList<>(selectedTableColumns);
    }

    /**
     * Initializes the necessary variables if they are null.
     * <p>
     * This method checks if the selectedSimulations, selectedAlgorithms, selectedStatistics, and selectedParameters
     * variables are null. If any of them is null, it calls the initializeSimulationsEtc() method to initialize them.
     * <p>
     * It also checks if the selectedParameters variable is null. If it is null, it initializes it as an empty
     * LinkedList.
     * <p>
     * It then checks if the simulationClasses, algorithmClasses, and statisticsClasses variables are null. If any of
     * them is null, it calls the initializeClasses() method to initialize them.
     * <p>
     * Finally, it checks if the algNames, statNames, and simNames variables are null. If any of them is null, it calls
     * the initializeNames() method to initialize them.
     */
    private void initializeIfNull() {
        if (selectedSimulations == null || selectedAlgorithms == null || selectedTableColumns == null
                || selectedParameters == null) {
            initializeSimulationsEtc();
        }

        if (this.selectedParameters == null) {
            this.selectedParameters = new LinkedList<>();
        }

        initializeClasses();
        initializeNames();
    }

    /**
     * Initializes the necessary variables for simulations, algorithms, statistics, and parameters.
     * <p>
     * This method initializes the selectedSimulations, selectedAlgorithms, selectedStatistics, and selectedParameters
     * variables as new LinkedLists if they are null.
     */
    private void initializeSimulationsEtc() {
        this.selectedSimulations = new LinkedList<>();
        this.selectedAlgorithms = new LinkedList<>();
        this.selectedTableColumns = new LinkedList<>();
        this.selectedParameters = new LinkedList<>();
    }

    /**
     * Initializes the necessary simulation, algorithm, and statistics classes.
     */
    private void initializeClasses() {
        simulationClasses = findSimulationClasses();
        algorithmClasses = findAlgorithmClasses();
        statisticsClasses = findStatisticsClasses();
    }

    /**
     * Initializes the names of algorithms, statistics, and simulations.
     */
    private void initializeNames() {
        algNames = getAlgorithmNamesFromAnnotations(algorithmClasses);
        statNames = getStatisticsNamesFromImplementations(statisticsClasses);
        simNames = getSimulationNamesFromImplementations(simulationClasses);

        this.algNames.sort(String.CASE_INSENSITIVE_ORDER);
        this.statNames.sort(String.CASE_INSENSITIVE_ORDER);
        this.simNames.sort(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Finds and returns a list of simulation classes that implement the Simulation interface.
     *
     * @return A list of simulation classes.
     */
    @NotNull
    private List<Class<? extends Simulation>> findSimulationClasses() {
        Set<Class<? extends Simulation>> _simulations = findImplementations("edu.cmu.tetrad.algcomparison.simulation",
                Simulation.class);
        final List<Class<? extends Simulation>> simulationClasses = new ArrayList<>(_simulations);
        simulationClasses.sort(Comparator.comparing(Class::getName));
        return simulationClasses;
    }

    /**
     * Finds and returns a list of classes that implement the Statistic interface within the specified package.
     *
     * @return A list of Statistic classes.
     */
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

        for (Class<? extends Algorithm> algorithm : algorithmClasses) {
            edu.cmu.tetrad.annotation.Algorithm algAnnotation = algorithm.getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class);

            if (algAnnotation != null) {
                String _name = algAnnotation.name();
                algorithmNames.add(_name);
            }
        }

        return algorithmNames;
    }

    /**
     * Retrieves the abbreviations of statistics from a list of implementation classes.
     *
     * @param algorithmClasses The list of implementation classes of statistics.
     * @return The abbreviations of the statistics.
     */
    private List<String> getStatisticsNamesFromImplementations(List<Class<? extends Statistic>> algorithmClasses) {
        List<String> statisticsNames = new ArrayList<>();

        for (Class<? extends Statistic> statistic : algorithmClasses) {
            try {
                Statistic _statistic = statistic.getConstructor().newInstance();
                String abbreviation = _statistic.getAbbreviation();
                statisticsNames.add(abbreviation);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                // Skip.
            }
        }

        return statisticsNames;
    }

    /**
     * Retrieves the names of simulations from a list of implementation classes.
     *
     * @param algorithmClasses The list of implementation classes of simulations.
     * @return The names of the simulations.
     */
    private List<String> getSimulationNamesFromImplementations(List<Class<? extends Simulation>> algorithmClasses) {
        List<String> simulationNames = new ArrayList<>();

        RandomGraph graph = new RandomForward();

        for (Class<? extends Simulation> statistic : algorithmClasses) {
            try {
                Simulation _statistic = statistic.getConstructor(RandomGraph.class).newInstance(graph);
                String shortName = _statistic.getShortName();
                simulationNames.add(shortName);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                // Skip.
            }
        }

        return simulationNames;
    }

    public Statistics getSelectedStatistics() {
        List<MyTableColumn> selectedTableColumns = getSelectedTableColumns();

        Statistics selectedStatistics = new Statistics();
        List<Statistic> lastStatisticsUsed = new ArrayList<>();

        for (MyTableColumn column : selectedTableColumns) {
            if (column.getType() == MyTableColumn.ColumnType.STATISTIC) {
                try {
                    Statistic statistic = column.getStatistic().getConstructor().newInstance();
                    selectedStatistics.add(statistic);
                    lastStatisticsUsed.add(statistic);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException ex) {
                    System.out.println("Error creating statistic: " + ex.getMessage());
                }
            } else if (column.getType() == MyTableColumn.ColumnType.PARAMETER) {
                String parameter = column.getParameter();
                selectedStatistics.add(new ParameterColumn(parameter));
            }
        }

        for (Statistic statistic : selectedStatistics.getStatistics()) {
            double weight = 0;
            try {
                weight = parameters.getDouble("algcomparison." + statistic.getAbbreviation());
            } catch (Exception e) {
                // Skip.
            }
            selectedStatistics.setWeight(statistic.getAbbreviation(), weight);
        }

        setLastStatisticsUsed(lastStatisticsUsed);
        return selectedStatistics;
    }

    @NotNull
    public List<GridSearchModel.MyTableColumn> getAllTableColumns() {
        List<GridSearchModel.MyTableColumn> allTableColumns = new ArrayList<>();

        List<Simulation> simulations = getSelectedSimulations().getSimulations();
        List<AlgorithmSpec> algorithms = getSelectedAlgorithms();

        for (String name : getAllSimulationParameters(simulations)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            GridSearchModel.MyTableColumn column = new GridSearchModel.MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        for (String name : getAllAlgorithmParameters(algorithms)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            GridSearchModel.MyTableColumn column = new GridSearchModel.MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        for (String name : getAllTestParameters(algorithms)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            GridSearchModel.MyTableColumn column = new GridSearchModel.MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        for (String name : getAllScoreParameters(algorithms)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            GridSearchModel.MyTableColumn column = new GridSearchModel.MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        for (String name : getAllBootstrapParameters(algorithms)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            GridSearchModel.MyTableColumn column = new GridSearchModel.MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        List<Class<? extends Statistic>> statisticClasses = getStatisticsClasses();

        for (Class<? extends Statistic> statisticClass : statisticClasses) {
            try {
                Statistic statistic = statisticClass.getConstructor().newInstance();
                GridSearchModel.MyTableColumn column = new GridSearchModel.MyTableColumn(statistic.getAbbreviation(), statistic.getDescription(), statisticClass);
                allTableColumns.add(column);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException ex) {
                System.out.println("Error creating statistic: " + ex.getMessage());
            }
        }

        return allTableColumns;
    }

    private boolean paramSetByUser(String columnName) {
        ParamDescription paramDescription = ParamDescriptions.getInstance().get(columnName);
        Object defaultValue = paramDescription.getDefaultValue();
        Object[] values = parameters.getValues(columnName);
        boolean userDefault = values != null && values.length == 1 && values[0].equals(defaultValue);
        return !userDefault;
    }

    public List<String> getLastStatisticsUsed() {
        String[] lastStatisticsUsed = Preferences.userRoot().get("lastAlgcomparisonStatisticsUsed", "").split(";");
        return Arrays.asList(lastStatisticsUsed);
    }

    public void setLastStatisticsUsed(List<Statistic> lastStatisticsUsed) {
        StringBuilder sb = new StringBuilder();
        for (Statistic statistic : lastStatisticsUsed) {
            sb.append(statistic.getAbbreviation()).append(";");
        }

//        System.out.println("Setting last statistics used: " + sb);

        Preferences.userRoot().put("lastAlgcomparisonStatisticsUsed", sb.toString());
    }

    public String getLastIndependenceTest() {
        return Preferences.userRoot().get("lastAlgcomparisonIndependenceTestUsed", "");
    }


    public void setLastIndependenceTest(String name) {
        IndependenceTestModels independenceTestModels = IndependenceTestModels.getInstance();
        List<IndependenceTestModel> models = independenceTestModels.getModels();

        for (IndependenceTestModel model : models) {
            if (model.getName().equals(name)) {
                Preferences.userRoot().put("lastAlgcomparisonIndependenceTestUsed", name);
                return;
            }
        }

        throw new IllegalArgumentException("Independence test by that name not found: " + name);
    }

    public String getLastScore() {
        return Preferences.userRoot().get("lastAlgcomparisonScoreUsed", "");
    }

    public void setLastScore(String name) {
        ScoreModels scoreModels = ScoreModels.getInstance();
        List<ScoreModel> models = scoreModels.getModels();

        for (ScoreModel model : models) {
            if (model.getName().equals(name)) {
                Preferences.userRoot().put("lastAlgcomparisonScoreUsed", name);
                return;
            }
        }

        throw new IllegalArgumentException("Score by that name not found: " + name);
    }

    public String getLastGraphChoice() {
        return Preferences.userRoot().get("lastAlgcomparisonGraphChoice", "");
    }

    public void setLastGraphChoice(String name) {
        Preferences.userRoot().put("lastAlgcomparisonGraphChoice", name);
    }

    public String getLastAlgorithmChoice() {
        return Preferences.userRoot().get("lastAlgcomparisonAlgorithmChoice", "");
    }

    public void setLastAlgorithmChoice(String name) {
        Preferences.userRoot().put("lastAlgcomparisonAlgorithmChoice", name);
    }

    public Object getLastSimulationChoice() {
        return Preferences.userRoot().get("lastAlgcomparisonSimulationChoice", "");
    }

    public void setLastSimulationChoice(String selectedItem) {
        Preferences.userRoot().put("lastAlgcomparisonSimulationChoice", selectedItem);
    }

    /**
     * The user may supply a graph, which will be given as an option in the UI.
     */
    public Graph getSuppliedGraph() {
        return suppliedGraph;
    }

    /**
     * The last comparison text displayed.
     */
    public String getLastComparisonText() {
        return lastComparisonText == null ? "" : lastComparisonText;
    }

    public void setLastComparisonText(String lastComparisonText) {
        this.lastComparisonText = lastComparisonText;
    }

    /**
     * The last verbose output displayed.
     */
    public String getLastVerboseOutputText() {
        return lastVerboseOutputText == null ? "" : lastVerboseOutputText;
    }

    public void setLastVerboseOutputText(String lastVerboseOutputText) {
        this.lastVerboseOutputText = lastVerboseOutputText;
    }

    /**
     * If a dataset (such as an empirical dataset) is supplied, it will be used in place of simulated dataset
     * for analysis. In this case, only statistics not requiring a true graph can be used.
     */
    public DataSet getSuppliedData() {
        return suppliedData;
    }

    /**
     * This class represents the comparison graph type for graph-based comparison algorithms. ComparisonGraphType is an
     * enumeration type that represents different types of comparison graphs. The available types are DAG (Directed
     * Acyclic Graph), CPDAG (Completed Partially Directed Acyclic Graph), and PAG (Partially Directed Acyclic Graph).
     */
    public enum ComparisonGraphType {

        /**
         * Directed Acyclic Graph (DAG).
         */
        DAG,

        /**
         * Completed Partially Directed Acyclic Graph (CPDAG).
         */
        CPDAG,

        /**
         * Partially Directed Acyclic Graph (PAG).
         */
        PAG
    }

    public static class MyTableColumn implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;
        private final String columnName;
        private final String description;
        private final Class<? extends Statistic> statistic;
        private final String parameter;
        private final ColumnType type;
        private boolean setByUser = false;

        public MyTableColumn(String name, String description, Class<? extends Statistic> statistic) {
            this.columnName = name;
            this.description = description;
            this.statistic = statistic;
            this.parameter = null;
            this.type = ColumnType.STATISTIC;
        }

        public MyTableColumn(String name, String description, String parameter) {
            this.columnName = name;
            this.description = description;
            this.statistic = null;
            this.parameter = parameter;
            this.type = ColumnType.PARAMETER;
        }

        public String getDescription() {
            return description;
        }

        public String getColumnName() {
            return columnName;
        }

        public String getdescription() {
            return description;
        }

        public ColumnType getType() {
            return type;
        }

        public Class<? extends Statistic> getStatistic() {
            if (type != ColumnType.STATISTIC) throw new IllegalStateException("Not a statistic column");
            return statistic;
        }

        public String getParameter() {
            if (type != ColumnType.PARAMETER) throw new IllegalStateException("Not a parameter column");
            return parameter;
        }

        public boolean isSetByUser() {
            return setByUser;
        }

        public void setSetByUser(boolean setByUser) {
            this.setByUser = setByUser;
        }

        public int hashCode() {
            return columnName.hashCode();
        }

        public boolean equals(Object obj) {
            if (obj instanceof MyTableColumn other) {
                return columnName.equals(other.columnName);
            }
            return false;
        }

        public enum ColumnType {
            STATISTIC,
            PARAMETER
        }
    }

    public static class AlgorithmSpec implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;
        private final String name;
        private final AlgorithmModel algorithm;
        private final AnnotatedClass<TestOfIndependence> test;
        private final AnnotatedClass<Score> score;

        public AlgorithmSpec(String name, AlgorithmModel algorithm,
                             AnnotatedClass<TestOfIndependence> test, AnnotatedClass<Score> score) {
            this.name = name;
            this.algorithm = algorithm;
            this.test = test;
            this.score = score;
        }

        public String getName() {
            return name;
        }

        public AlgorithmModel getAlgorithm() {
            return algorithm;
        }

        public AnnotatedClass<TestOfIndependence> getTest() {
            return test;
        }

        public AnnotatedClass<Score> getScore() {
            return score;
        }

        public Algorithm getAlgorithmImpl() {
            try {
                IndependenceWrapper independenceWrapper = null;
                ScoreWrapper scoreWrapper = null;

                if (test != null) {
                    independenceWrapper = (IndependenceWrapper) test.clazz().getConstructor().newInstance();
                }

                if (score != null) {
                    scoreWrapper = (ScoreWrapper) score.clazz().getConstructor().newInstance();
                }

                Class<?> _algorithm = algorithm.getAlgorithm().clazz();
                Algorithm algorithmImpl = (Algorithm) _algorithm.getConstructor().newInstance();

                if (algorithmImpl instanceof TakesIndependenceWrapper && independenceWrapper != null) {
                    ((TakesIndependenceWrapper) algorithmImpl).setIndependenceWrapper(independenceWrapper);
                }

                if (algorithmImpl instanceof UsesScoreWrapper && scoreWrapper != null) {
                    ((UsesScoreWrapper) algorithmImpl).setScoreWrapper(scoreWrapper);
                }

                if (algorithmImpl instanceof TakesIndependenceWrapper && independenceWrapper != null) {
                    ((TakesIndependenceWrapper) algorithmImpl).setIndependenceWrapper(independenceWrapper);
                }

                if (algorithmImpl instanceof UsesScoreWrapper && scoreWrapper != null) {
                    ((UsesScoreWrapper) algorithmImpl).setScoreWrapper(scoreWrapper);
                }

                return algorithmImpl;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            }
        }

        public String toString() {
            return name;
        }
    }

    public static class SimulationSpec implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;
        private final String name;
        private final Class<? extends RandomGraph> graphClass;
        private final Class<? extends Simulation> simulationClass;

        public SimulationSpec(String name, Class<? extends RandomGraph> graph,
                              Class<? extends Simulation> simulation) {
            this.name = name;
            this.graphClass = graph;
            this.simulationClass = simulation;
        }

        public String getName() {
            return name;
        }

        public Simulation getSimulationImpl() {
            try {
                RandomGraph randomGraph = graphClass.getConstructor().newInstance();
                return simulationClass.getConstructor(RandomGraph.class).newInstance(randomGraph);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        public String toString() {
            return name;
        }

    }
}



