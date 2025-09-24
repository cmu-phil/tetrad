///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.simulation.SingleDatasetSimulation;
import edu.cmu.tetrad.algcomparison.statistic.MarkovCheckerStatistic;
import edu.cmu.tetrad.algcomparison.statistic.ParameterColumn;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.ui.model.*;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * The GridSearchModel class is a session model that allows for running comparisons of algorithms. It provides methods
 * for selecting algorithms, simulations, statistics, and parameters, and then running the comparison.
 * <p>
 * The reference is here:
 * <p>
 * Ramsey, J. D., Malinsky, D., &amp; Bui, K. V. (2020). Algcomparison: Comparing the performance of graphical structure
 * learning algorithms with tetrad. Journal of Machine Learning Research, 21(238), 1-6.
 *
 * @author josephramsey
 */
public class GridSearchModel implements SessionModel, GraphSource {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * A private final variable that holds a Parameters object.
     */
    private final Parameters parameters;
    /**
     * The knowledge to be used for the GridSearchModel.
     */
    private final Knowledge knowledge;
    /**
     * The data to be used for the GridSearchModel.
     */
    private DataSet suppliedData = null;
    /**
     * The graph to be used for the GridSearchModel.
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
     * The variable resultsPath represents the path to the result folder. This is set after a comparison has been run
     * and can be used to add additional files to the comparison results.
     */
    private String resultsPath = null;
    /**
     * This variable represents the currently selected graph.
     */
    private Graph selectedGraph = null;
    /**
     * The selectedSimulation variable represents the index of the currently selected simulation. This variable is used
     * to keep track of the selected simulation in a collection of simulations. The index is zero-based, where 0
     * represents the first simulation.
     * <p>
     * By default, the value of selectedSimulation is 0, indicating that the first simulation is selected.
     * <p>
     * The value of selectedSimulation can be modified externally to change the selected simulation.
     *
     * @see Simulation
     */
    private int selectedSimulation = 0;
    /**
     * The selectedAlgorithm variable holds the index of the currently selected algorithm.
     * <p>
     * The value of selectedAlgorithm represents the index of the algorithm in a collection or an array of algorithms.
     * <p>
     * The default value of selectedAlgorithm is 0, indicating that the first algorithm in the collection or array is
     * selected by default.
     * <p>
     * The value of selectedAlgorithm can be changed to select a different algorithm by assigning a different index to
     * it.
     *
     * @since 1.0
     */
    private int selectedAlgorithm = 0;
    /**
     * The index of the selected graph.
     */
    private int selectedGraphIndex = 0;
    /**
     * Verbose output is sent here.
     */
    private transient PrintStream verboseOut;
    /**
     * A variable that holds an instance of the IndependenceWrapper implementation used for independence testing.
     * <p>
     * In this case, the implementation is `FisherZ`, which is typically employed for assessing statistical independence
     * based on Fisher's Z-transformation. It serves as the primary tool for conditional independence checks in related
     * algorithms or workflows.
     */
    private IndependenceWrapper markovCheckerIndependenceWrapper = new FisherZ();
    /**
     * Represents the type of conditioning set used in the Markov checker. The variable defines how the conditioning set
     * is categorized or scoped, influencing the analysis process in probabilistic or causal models. It is initialized
     * to `ConditioningSetType.LOCAL_MARKOV`, indicating that the default scope pertains to local Markovity.
     */
    private ConditioningSetType markovCheckerConditioningSetType = ConditioningSetType.ORDERED_LOCAL_MARKOV_MAG;
    /**
     * Stores the selected independendence test model for the GridSearchEditor. It needs to be stored here in case the
     * user closes the editor and re-opens it.
     */
    private IndependenceTestModel selectedIndependenceTestModel = null;

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
        this.suppliedData = null;
        this.suppliedGraph = null;
        initializeIfNull();
    }

    /**
     * Initializes a new GridSearchModel with the given KnowledgeBoxModel and Parameters.
     *
     * @param knowledge  The KnowledgeBoxModel containing the knowledge to be used for grid search. Must not be null.
     * @param parameters The Parameters specifying the grid search parameters. Must not be null.
     * @throws IllegalArgumentException If either knowledge or parameters are null.
     */
    public GridSearchModel(KnowledgeBoxModel knowledge, Parameters parameters) {
        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge must not be null.");
        }

        if (parameters == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }

        this.parameters = parameters;
        this.knowledge = knowledge.getKnowledge();
        this.suppliedData = null;
        this.suppliedGraph = null;
        initializeIfNull();
    }

    /**
     * Initializes a new instance of the GridSearchModel class.
     *
     * @param graphSource The graph source to be used for the model.
     * @param parameters  The parameters to be used for the model.
     * @throws IllegalArgumentException if graphSource or parameters is null.
     */
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
        this.suppliedData = null;
        initializeIfNull();
    }

    /**
     * Constructs a grid search model with the given graph source, knowledge box model, and parameters.
     *
     * @param graphSource The source of the graph.
     * @param knowledge   The knowledge box model.
     * @param parameters  The parameters for the grid search model.
     * @throws IllegalArgumentException if graphSource, knowledge, or parameters is null.
     */
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

    /**
     * Constructs a new GridSearchModel instance.
     *
     * @param dataWrapper the data wrapper containing the selected data model
     * @param parameters  the parameters to use for grid search
     * @throws IllegalArgumentException if either dataWrapper or parameters is null
     */
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
        this.suppliedGraph = null;
        initializeIfNull();
    }

    /**
     * Constructs a new instance of the GridSearchModel.
     *
     * @param dataWrapper the data wrapper used for selecting the data model (must not be null)
     * @param knowledge   the knowledge box model (must not be null)
     * @param parameters  the parameters for the model (must not be null)
     * @throws IllegalArgumentException if any of the parameters is null
     */
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

        System.out.println("Variables names = " + this.suppliedData.getVariableNames());

        initializeIfNull();
    }

    /**
     * Finds and returns a list of algorithm classes that implement the Algorithm interface.
     *
     * @return A list of algorithm classes.
     */
    @NotNull
    private static List<Class<? extends Algorithm>> findAlgorithmClasses() {
        Set<Class<? extends Algorithm>> _algorithms = findImplementations("edu.cmu.tetrad.algcomparison.algorithm", Algorithm.class);
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
            } else if (o1.getType() == MyTableColumn.ColumnType.PARAMETER && o2.getType() == MyTableColumn.ColumnType.STATISTIC) {
                return -1;
            } else if (o1.getType() == MyTableColumn.ColumnType.STATISTIC && o2.getType() == MyTableColumn.ColumnType.PARAMETER) {
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

            if (algorithmImpl instanceof TakesScoreWrapper) {
                paramNamesSet.addAll(((TakesScoreWrapper) algorithmImpl).getScoreWrapper().getParameters());
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

    /**
     * Runs the comparison of simulations, algorithms, and statistics.
     *
     * @param ps1 A print stream to write the verbose output.
     * @param ps2 A print stream to write the verbose output.
     */
    public void runComparison(PrintStream ps1, PrintStream ps2) {
        initializeIfNull();

        Simulations simulations = new Simulations();

        if (suppliedData != null) {
            simulations.add(new SingleDatasetSimulation(suppliedData));
        } else {
            for (SimulationSpec simulation : getSelectedSimulationsSpecs())
                simulations.add(simulation.getSimulationImpl());
        }

        Algorithms algorithms = new Algorithms();
        for (AlgorithmSpec algorithm : getSelectedAlgorithmSpecs()) algorithms.add(algorithm.getAlgorithmImpl());

        Comparison comparison = new Comparison();
        comparison.setSaveData(parameters.getBoolean("algcomparisonSaveData"));
        comparison.setSaveGraphs(parameters.getBoolean("algcomparisonSaveGraphs"));
        comparison.setSaveCPDAGs(parameters.getBoolean("algcomparisonSaveCPDAGs"));
        comparison.setSavePags(parameters.getBoolean("algcomparisonSavePAGs"));
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
            String pathname = System.getProperty("user.home") + "/comparison-results/comparison-" + i;
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
        String outputFileName = "Comparison.txt";
        comparison.compareFromSimulations(resultsPath, simulations, outputFileName, ps1, ps2, algorithms, getSelectedStatistics(), new Parameters(parameters));

        this.resultsPath = resultsPath;

    }

    private LinkedList<AlgorithmSpec> getSelectedAlgorithmSpecs() {
        if (!(parameters.get("algcomparison.selectedAlgorithms") instanceof LinkedList<?>)) {
            parameters.set("algcomparison.selectedAlgorithms", new LinkedList<AlgorithmSpec>());
        }

        return (LinkedList<AlgorithmSpec>) parameters.get("algcomparison.selectedAlgorithms");
    }

    private LinkedList<SimulationSpec> getSelectedSimulationsSpecs() {
        if (!(parameters.get("algcomparison.selectedSimulations") instanceof LinkedList<?>)) {
            parameters.set("algcomparison.selectedSimulations", new LinkedList<SimulationSpec>());
        }

        return (LinkedList<SimulationSpec>) parameters.get("algcomparison.selectedSimulations");
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
        getSelectedSimulationsSpecs().add(simulation);
    }

    /**
     * Remove the last simulation from the list of selected simulations.
     */
    public void removeLastSimulation() {
        initializeIfNull();
        if (!getSelectedSimulationsSpecs().isEmpty()) {
            getSelectedSimulationsSpecs().removeLast();
        }
    }

    /**
     * Add an algorithm to the list of selected algorithms.
     *
     * @param algorithm The algorithm to add.
     */
    public void addAlgorithm(AlgorithmSpec algorithm) {
        initializeIfNull();
        getSelectedAlgorithmSpecs().add(algorithm);
    }

    /**
     * Remove the last algorithm from the list of selected algorithms.
     */
    public void removeLastAlgorithm() {
        initializeIfNull();
        LinkedList<AlgorithmSpec> selectedSimulationsSpecs = getSelectedAlgorithmSpecs();
        if (!selectedSimulationsSpecs.isEmpty()) {
            getSelectedAlgorithmSpecs().removeLast();
        }
    }

    /**
     * Add a table column to the list of selected table columns.
     *
     * @param tableColumn The table column to add.
     */
    public void addTableColumn(MyTableColumn tableColumn) {
        if (getSelectedTableColumnsPrivate().contains(tableColumn)) return;
        initializeIfNull();
        getSelectedTableColumnsPrivate().add(tableColumn);
        GridSearchModel.sortTableColumns(getSelectedTableColumnsPrivate());
    }

    /**
     * Remove the last statistic from the list of selected statistics.
     */
    public void removeLastTableColumn() {
        initializeIfNull();
        if (!getSelectedTableColumnsPrivate().isEmpty()) {
            getSelectedTableColumnsPrivate().removeLast();
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
     * Returns the selected graph. This is set by the editor when the user selects a graph.
     *
     * @return The selected graph.
     */
    @Override
    public Graph getGraph() {
        return selectedGraph == null ? new EdgeListGraph() : selectedGraph;
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
        } else {
            for (SimulationSpec simulation : getSelectedSimulationsSpecs())
                simulations.add(simulation.getSimulationImpl());
        }
        return simulations;
    }

    /**
     * A private instance variable that holds a list of selected Algorithm objects.
     */
    public List<AlgorithmSpec> getSelectedAlgorithms() {
        if (!(parameters.get("algcomparison.selectedAlgorithms") instanceof LinkedList<?>)) {
            parameters.set("algcomparison.selectedAlgorithms", new LinkedList<AlgorithmSpec>());
        }

        return (LinkedList<AlgorithmSpec>) parameters.get("algcomparison.selectedAlgorithms");
    }

    public List<MyTableColumn> getSelectedTableColumns() {
        GridSearchModel.sortTableColumns(getSelectedTableColumnsPrivate());
        return new ArrayList<>(getSelectedTableColumnsPrivate());
    }

    private LinkedList<MyTableColumn> getSelectedTableColumnsPrivate() {
        if (!(parameters.get("algcomparison.selectedTableColumns") instanceof LinkedList<?>)) {
            parameters.set("algcomparison.selectedTableColumns", new LinkedList<MyTableColumn>());
        }

        return (LinkedList<MyTableColumn>) parameters.get("algcomparison.selectedTableColumns");
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
        initializeClasses();
        initializeNames();
    }

    private List<String> getSelectedParameters() {
        if (!(parameters.get("algcomparison.selectedParameters") instanceof LinkedList<?>)) {
            parameters.set("algcomparison.selectedParameters", new LinkedList<String>());
        }

        return (LinkedList<String>) parameters.get("algcomparison.selectedParameters");
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
        Set<Class<? extends Simulation>> _simulations = findImplementations("edu.cmu.tetrad.algcomparison.simulation", Simulation.class);
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
        Set<Class<? extends Statistic>> _statistics = findImplementations("edu.cmu.tetrad.algcomparison.statistic", Statistic.class);
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
     * @param algorithmClasses The list of implementation classes for statistics.
     * @return The abbreviations of the statistics.
     */
    private List<String> getStatisticsNamesFromImplementations(List<Class<? extends Statistic>> algorithmClasses) {
        List<String> statisticsNames = new ArrayList<>();

        for (Class<? extends Statistic> statistic : algorithmClasses) {

            try {
                Constructor<?>[] constructors = statistic.getDeclaredConstructors();

                boolean hasNoArgConstructor = false;
                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterCount() == 0) {
                        hasNoArgConstructor = true;
                        break;
                    }
                }

                if (hasNoArgConstructor) {
                    Statistic _statistic = statistic.getConstructor().newInstance();
                    String abbreviation = _statistic.getAbbreviation();
                    statisticsNames.add(abbreviation);
                } else if (MarkovCheckerStatistic.class.isAssignableFrom(statistic)) {
                    Statistic _statistic = statistic.getConstructor(IndependenceWrapper.class, ConditioningSetType.class)
                            .newInstance(getMarkovCheckerIndependenceWrapper(), getMarkovCheckerConditioningSetType());
                    String abbreviation = _statistic.getAbbreviation();
                    statisticsNames.add(abbreviation);
                }
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                TetradLogger.getInstance().log("Error creating statistic: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return statisticsNames;
    }

    /**
     * Retrieves the names of simulations from a list of implementation classes.
     *
     * @param algorithmClasses The list of implementation classes for simulations.
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
        LinkedList<MyTableColumn> selectedTableColumns = getSelectedTableColumnsPrivate();

        Statistics selectedStatistics = new Statistics();
        List<Statistic> lastStatisticsUsed = new ArrayList<>();

        for (MyTableColumn column : selectedTableColumns) {
            if (column.getType() == MyTableColumn.ColumnType.STATISTIC) {

                if (MarkovCheckerStatistic.class.isAssignableFrom(column.getStatistic())) {
                    try {
                        Constructor<? extends Statistic> constructor = column.getStatistic().getConstructor(IndependenceWrapper.class, ConditioningSetType.class);
                        Statistic statistic = constructor.newInstance(getMarkovCheckerIndependenceWrapper(), getMarkovCheckerConditioningSetType());
                        selectedStatistics.add(statistic);
                        lastStatisticsUsed.add(statistic);
                    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                             InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                } else {

                    try {
                        Constructor<?>[] constructors = column.getStatistic().getDeclaredConstructors();

                        boolean hasNoArgConstructor = false;
                        for (Constructor<?> constructor : constructors) {
                            if (constructor.getParameterCount() == 0) {
                                hasNoArgConstructor = true;
                                break;
                            }
                        }

                        if (hasNoArgConstructor) {
                            Statistic statistic = column.getStatistic().getConstructor().newInstance();
                            selectedStatistics.add(statistic);
                            lastStatisticsUsed.add(statistic);
                        }
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                             NoSuchMethodException ex) {
                        System.out.println("Error creating statistic: " + ex.getMessage());
                    }
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
    public List<MyTableColumn> getAllTableColumns() {
        List<MyTableColumn> allTableColumns = new ArrayList<>();

        List<Simulation> simulations = getSelectedSimulations().getSimulations();
        List<AlgorithmSpec> algorithms = getSelectedAlgorithms();

        for (String name : getAllSimulationParameters(simulations)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            MyTableColumn column = new MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        for (String name : getAllAlgorithmParameters(algorithms)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            MyTableColumn column = new MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        for (String name : getAllTestParameters(algorithms)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            MyTableColumn column = new MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        for (String name : getAllScoreParameters(algorithms)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            MyTableColumn column = new MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        for (String name : getAllBootstrapParameters(algorithms)) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            String shortDescriptiom = paramDescription.getShortDescription();
            String description = paramDescription.getLongDescription();
            MyTableColumn column = new MyTableColumn(shortDescriptiom, description, name);
            column.setSetByUser(paramSetByUser(name));
            allTableColumns.add(column);
        }

        List<Class<? extends Statistic>> statisticClasses = getStatisticsClasses();

        for (Class<? extends Statistic> statisticClass : statisticClasses) {
            try {
                Constructor<?>[] constructors = statisticClass.getDeclaredConstructors();

                boolean hasNoArgConstructor = false;
                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterCount() == 0) {
                        hasNoArgConstructor = true;
                        break;
                    }
                }

                if (hasNoArgConstructor) {
                    Statistic statistic = statisticClass.getConstructor().newInstance();
                    MyTableColumn column = new MyTableColumn(statistic.getAbbreviation(), statistic.getDescription(), statisticClass);
                    allTableColumns.add(column);
                } else if (MarkovCheckerStatistic.class.isAssignableFrom(statisticClass)) {
                    Statistic _statistic = statisticClass.getConstructor(IndependenceWrapper.class, ConditioningSetType.class)
                            .newInstance(getMarkovCheckerIndependenceWrapper(), getMarkovCheckerConditioningSetType());
                    MyTableColumn column = new MyTableColumn(_statistic.getAbbreviation(), _statistic.getDescription(), statisticClass);
                    allTableColumns.add(column);
                }
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
     * The suppliedGraph variable represents a graph that can be supplied by the user. This graph will be given as an
     * option in the user interface.
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
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization to restore the
     * state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Represents the variable "knowledge" in the GridSearchModel class. This variable is of type Knowledge and is
     * private and final.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * The suppliedData variable represents a dataset that can be used in place of a simulated dataset for analysis. It
     * can be set to null if no dataset is supplied.
     * <p>
     * Using a supplied dataset restricts the analysis to only those statistics that do not require a true graph.
     * <p>
     * Example usage:
     * <pre>
     * DataSet dataset = new DataSet();
     * suppliedData = dataset;
     * </pre>
     */
    public DataSet getSuppliedData() {
        return suppliedData;
    }

    /**
     * The variable resultsPath represents the path to the result folder. This is set after a comparison has been run
     * and can be used to add additional files to the comparison results.
     */
    public String getResultsPath() {
        return resultsPath;
    }

    public void setResultsPath(String resultsPath) {
        this.resultsPath = resultsPath;
    }

    public void setSelectedGraph(Graph graph) {
        this.selectedGraph = graph;
    }

    public int getSelectedSimulation() {
        return selectedSimulation;
    }

    public void setSelectedSimulation(int selectedSimulation) {
        this.selectedSimulation = selectedSimulation;
    }

    public int getSelectedAlgorithm() {
        return selectedAlgorithm;
    }

    public void setSelectedAlgorithm(int selectedAlgorithm) {
        this.selectedAlgorithm = selectedAlgorithm;
    }

    public int getSelectedGraphIndex() {
        return selectedGraphIndex;
    }

    public void setSelectedGraphIndex(int selectedGraphIndex) {
        this.selectedGraphIndex = selectedGraphIndex;
    }

    /**
     * A wrapper for a statistical independence test used by the Markov checker.
     * <p>
     * This instance by default utilizes the Fisher-Z test for determining statistical independence.
     */
    public IndependenceWrapper getMarkovCheckerIndependenceWrapper() {
        return markovCheckerIndependenceWrapper;
    }

    /**
     * Sets the Markov Checker Independence Wrapper.
     *
     * @param markovCheckerIndependenceWrapper an instance of IndependenceWrapper to be associated with the Markov
     *                                         Checker.
     */
    public void setMarkovCheckerIndependenceWrapper(IndependenceWrapper markovCheckerIndependenceWrapper) {
        if (markovCheckerIndependenceWrapper == null) {
            throw new IllegalArgumentException("markovCheckerIndependenceWrapper cannot be null");
        }
        this.markovCheckerIndependenceWrapper = markovCheckerIndependenceWrapper;

        System.out.println("Setting independence wrapper to " + markovCheckerIndependenceWrapper);
    }

    /**
     * Represents the type of conditioning set used in a Markov property verification process. This variable identifies
     * the specific conditioning set type in the context of the Markov checker.
     * <p>
     * The value is set to {@code ConditioningSetType.LOCAL_MARKOV} by default, indicating that the local Markov
     * property is being utilized.
     */
    public ConditioningSetType getMarkovCheckerConditioningSetType() {
        return markovCheckerConditioningSetType;
    }

    /**
     * Sets the conditioning set type for the Markov checker.
     *
     * @param markovCheckerConditioningSetType the conditioning set type to be set for the Markov checker
     */
    public void setMarkovCheckerConditioningSetType(ConditioningSetType markovCheckerConditioningSetType) {
        if (markovCheckerConditioningSetType == null) {
            throw new IllegalArgumentException("markovCheckerConditioningSetType cannot be null");
        }
        this.markovCheckerConditioningSetType = markovCheckerConditioningSetType;
    }

    public IndependenceTestModel getSelectedIndependenceTestModel() {
        return selectedIndependenceTestModel;
    }

    public void setSelectedIndependenceTestModel(IndependenceTestModel selectedIndependenceTestModel) {
        this.selectedIndependenceTestModel = selectedIndependenceTestModel;
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
        /**
         * The name of the column.
         */
        private final String columnName;
        /**
         * The description of the column.
         */
        private final String description;
        /**
         * The statistic class.
         */
        private final Class<? extends Statistic> statistic;
        /**
         * The parameter name.
         */
        private final String parameter;
        /**
         * The type of the column.
         */
        private final ColumnType type;
        /**
         * A boolean that indicates whether the column was set by the user.
         */
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
            STATISTIC, PARAMETER
        }
    }

    public static class AlgorithmSpec implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;
        /**
         * The name of the algorithm.
         */
        private final String name;
        /**
         * The algorithm model.
         */
        private final AlgorithmModel algorithm;
        /**
         * The test of independence.
         */
        private final AnnotatedClass<TestOfIndependence> test;
        /**
         * The score.
         */
        private final AnnotatedClass<Score> score;

        /**
         * Constructs a new AlgorithmSpec object with the specified name, algorithm model, test of independence, and
         *
         * @param name      The name of the algorithm.
         * @param algorithm The algorithm model.
         * @param test      The test of independence.
         * @param score     The score.
         */
        public AlgorithmSpec(String name, AlgorithmModel algorithm, AnnotatedClass<TestOfIndependence> test, AnnotatedClass<Score> score) {
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

                if (algorithmImpl instanceof TakesScoreWrapper && scoreWrapper != null) {
                    ((TakesScoreWrapper) algorithmImpl).setScoreWrapper(scoreWrapper);
                }

                if (algorithmImpl instanceof TakesIndependenceWrapper && independenceWrapper != null) {
                    ((TakesIndependenceWrapper) algorithmImpl).setIndependenceWrapper(independenceWrapper);
                }

                if (algorithmImpl instanceof TakesScoreWrapper && scoreWrapper != null) {
                    ((TakesScoreWrapper) algorithmImpl).setScoreWrapper(scoreWrapper);
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
        /**
         * The name of the simulation.
         */
        private final String name;
        /**
         * The class of the graph.
         */
        private final Class<? extends RandomGraph> graphClass;
        /**
         * The class of the simulation.
         */
        private final Class<? extends Simulation> simulationClass;

        public SimulationSpec(String name, Class<? extends RandomGraph> graph, Class<? extends Simulation> simulation) {
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




