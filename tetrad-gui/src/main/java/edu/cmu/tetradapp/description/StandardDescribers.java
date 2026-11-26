/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.description;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.ContinuousDiscretizationSpec;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.data.DiscreteDiscretizationSpec;
import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.SplitCasesSpec;
import edu.cmu.tetrad.data.UnmixSpec;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GridSearchModel;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.model.NoteModel;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.model.datamanip.DiscretizationWrapper;
import edu.cmu.tetradapp.model.datamanip.SelectionWrapper;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.session.SessionNode;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The model describers that a {@link SessionDescriber} starts with: one each for search, data, knowledge, graph and
 * note boxes, and the general one used for every other model.
 *
 * @author josephramsey
 * @author Claude
 */
public final class StandardDescribers {

    /**
     * The key of the field naming the search algorithm.
     */
    public static final String ALGORITHM = "Algorithm";

    /**
     * The key of the field naming the test of independence.
     */
    public static final String TEST = "Test";

    /**
     * The key of the field naming the score.
     */
    public static final String SCORE = "Score";

    /**
     * The key of the field remarking that the random seed is not fixed.
     */
    public static final String SEED = "Seed";

    /**
     * The key of the field naming a data set.
     */
    public static final String DATA_SET = "Data set";

    /**
     * The key of the field giving the size and kind of a data set.
     */
    public static final String SHAPE = "Shape";

    /**
     * The key of the field saying whether a data set has missing values.
     */
    public static final String MISSING_VALUES = "Missing values";

    /**
     * The key of the field naming the selected data set, where a box holds more than one.
     */
    public static final String SELECTED_DATA_SET = "Selected data set";

    /**
     * The key of the field saying that some data sets of a box are not listed.
     */
    public static final String NOT_LISTED = "Not listed";

    /**
     * The key of the field saying that a box holds no knowledge.
     */
    public static final String KNOWLEDGE = "Knowledge";

    /**
     * The name of the block that holds knowledge.
     */
    public static final String KNOWLEDGE_BLOCK = "knowledge";

    /**
     * The key of the field giving the size of a graph.
     */
    public static final String GRAPH = "Graph";

    /**
     * The name of the block that holds a graph.
     */
    public static final String GRAPH_BLOCK = "graph";

    /**
     * The name of the block that holds the text of a note.
     */
    public static final String NOTE_BLOCK = "note";

    /**
     * The key of a field giving one setting that has no registered default, so that it cannot be said to have
     * changed: its name, an equals sign, and its value written so that its kind can be read back.
     */
    public static final String SETTING = "Setting";

    /**
     * The key of a field naming a setting that is held as an object this description has no way to write.
     */
    public static final String NOT_DESCRIBED = "Not described";

    /**
     * The start of the name of a block holding a setting that is a list of pieces of text, one to a line.
     */
    public static final String LIST_BLOCK = "list ";

    /**
     * The start of the name of a block holding a setting that is one piece of text of several lines.
     */
    public static final String TEXT_BLOCK = "text ";

    /**
     * The start of the name of a block holding a parameter with several values, each a piece of text, one to a
     * line.
     */
    public static final String VALUES_BLOCK = "values ";

    /**
     * The key of the field naming the kind of simulation.
     */
    public static final String SIMULATION = "Simulation";

    /**
     * The key of the field naming the target of a regression.
     */
    public static final String TARGET = "Target";

    /**
     * The key of the field naming the regressors of a regression, separated by commas.
     */
    public static final String REGRESSORS = "Regressors";

    /**
     * The key of a field giving how one variable is discretized.
     */
    public static final String DISCRETIZE = "Discretize";

    /**
     * The label for which rows a selection box keeps, by the values of one variable. One line for each variable.
     */
    public static final String SELECT = "Select";

    /**
     * The label for how a split-cases box divides the rows.
     */
    public static final String SPLIT = "Split";

    /**
     * The field saying how a causal unmix box divides the rows into components: how many components, and each
     * setting of the unmixing that is not at its default.
     */
    public static final String UNMIX = "Unmix";

    /**
     * The label for a parameter that has been given several values, as a grid search box does to compare them.
     */
    public static final String VALUES = "Values";

    /**
     * The label for the simulation a grid search box draws its data from.
     */
    public static final String GRID_SIMULATION = "Grid simulation";

    /**
     * The label for an algorithm a grid search box runs, with its test and score. One line for each.
     */
    public static final String GRID_ALGORITHM = "Grid algorithm";

    /**
     * The label for a column of a grid search box's table of results. One line for each, in order.
     */
    public static final String GRID_COLUMN = "Grid column";

    /**
     * The label for the test a grid search box's Markov checker statistics use, when it is not the usual one.
     */
    public static final String GRID_MARKOV_TEST = "Grid Markov checker test";

    /**
     * The field naming a parameter of a grid search box's Markov checker test whose value differs from its default.
     * The box keeps these apart from its other parameters, so that the checker's test can be given an alpha, say,
     * that is not the alpha of the algorithms being compared.
     */
    public static final String GRID_MARKOV_PARAMETER = "Grid Markov checker parameter";

    /**
     * Settings that record where the interface was left, not a choice about the analysis.
     */
    private static final Set<String> INTERFACE_STATE = Set.of("selectedTabParam", "simulationsDropdownPreference",
            // Written by the grid search editor each time it opens, and read by nothing.
            "markovCheckerFactMode");

    /**
     * Settings held as objects that a describer of their own writes out, so the general describer passes over
     * them.
     */
    private static final Set<String> DESCRIBED_ELSEWHERE = Set.of("discretizationSpecs", "selectionSpecs",
            "splitCasesSpec", "unmixSpec", "algcomparison.selectedSimulation", "algcomparison.selectedSimulations",
            "algcomparison.selectedAlgorithms", "algcomparison.selectedTableColumns");

    /**
     * The largest number of data sets listed for one box.
     */
    private static final int MAX_DATA_SETS_LISTED = 10;

    /**
     * The largest number of edges for which a graph is written out.
     */
    private static final int MAX_EDGES_LISTED = 500;

    private StandardDescribers() {
    }

    //=============================== the describers ===============================//

    /**
     * Describes any model: lists the parameters of the box whose values differ from their defaults.
     *
     * @param model the model.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeGeneral(Object model, SessionNode node, BoxDescription.Builder out) {
        Parameters parameters = node.getParam(model.getClass());

        if (parameters != null) {
            Set<String> names = new TreeSet<>(parameters.getParametersNames());
            changedParameters(out, parameters, names);
            otherSettings(out, parameters, names);
        }
    }

    /**
     * Describes a simulation box: the kind of simulation, and those of its parameters whose values differ from
     * their defaults. The simulated data sets are results and are not listed.
     *
     * @param model the model, a {@link Simulation}.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeSimulation(Object model, SessionNode node, BoxDescription.Builder out) {
        Simulation wrapper = (Simulation) model;
        edu.cmu.tetrad.algcomparison.simulation.Simulation simulation = wrapper.getSimulation();
        Parameters parameters = wrapper.getParams();

        if (simulation == null) {
            out.field(SIMULATION, "none chosen yet");
        } else {
            out.field(SIMULATION, simulation.getDescription());
        }

        if (parameters == null) {
            return;
        }

        if (simulation != null) {
            changedParameters(out, parameters, new TreeSet<>(simulation.getParameters()));
        }

        otherSettings(out, parameters, new TreeSet<>(parameters.getParametersNames()));
    }

    /**
     * Describes a regression box: the target, the regressors, and the parameters of the box whose values differ
     * from their defaults.
     *
     * @param target     the name of the target; may be null.
     * @param regressors the names of the regressors; may be null.
     * @param model      the model.
     * @param node       the session node holding the model.
     * @param out        the description of the box.
     */
    public static void describeRegression(String target, List<String> regressors, Object model, SessionNode node,
                                          BoxDescription.Builder out) {
        if (target != null && !target.isBlank()) {
            out.field(TARGET, target);
        }

        if (regressors != null && !regressors.isEmpty()) {
            out.field(REGRESSORS, String.join(", ", regressors));
        }

        describeGeneral(model, node, out);
    }

    /**
     * Describes a discretization box: how each variable is discretized, and then what a data box says. A
     * continuous variable's line gives its categories, the breakpoints between them and how the breakpoints were
     * chosen; a discrete variable's line gives its new categories and which of them each old category becomes.
     *
     * @param model the model, a {@link DiscretizationWrapper}.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeDiscretization(Object model, SessionNode node, BoxDescription.Builder out) {
        Parameters parameters = node.getParam(model.getClass());
        Object specs = parameters == null || !parameters.getParametersNames().contains("discretizationSpecs")
                ? null : parameters.getValues("discretizationSpecs")[0];

        if (specs instanceof Map<?, ?> map) {
            Map<String, String> lines = new TreeMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof Node variable) {
                    String line = discretizeLine(variable.getName(), entry.getValue());

                    if (line != null) {
                        lines.put(variable.getName(), line);
                    }
                }
            }

            for (String line : lines.values()) {
                out.field(DISCRETIZE, line);
            }
        }

        describeData(model, node, out);
    }

    /**
     * Describes a selection box: which rows it keeps, one line for each variable the rows are selected by, and then
     * what a data box says. A row is kept if it passes every line.
     *
     * @param model the model, a {@link SelectionWrapper}.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeSelection(Object model, SessionNode node, BoxDescription.Builder out) {
        Parameters parameters = node.getParam(model.getClass());
        Object specs = parameters == null || !parameters.getParametersNames().contains("selectionSpecs")
                ? null : parameters.getValues("selectionSpecs")[0];

        if (specs instanceof Map<?, ?> map) {
            Map<String, String> lines = new TreeMap<>();

            // The names of a discrete variable's categories are taken from the box's data where the variable is
            // still there, since the variable a specification is kept under may be a stand-in with a name only.
            DataModel selected = ((DataWrapper) model).getSelectedDataModel();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof Node variable
                    && entry.getValue() instanceof SelectionWrapper.SelectionSpec spec) {
                    Node inData = selected instanceof DataSet dataSet ? dataSet.getVariable(variable.getName()) : null;
                    lines.put(variable.getName(), selectLine(inData != null ? inData : variable, spec));
                }
            }

            for (String line : lines.values()) {
                out.field(SELECT, line);
            }
        }

        describeData(model, node, out);
    }

    /**
     * Describes a split-cases box: how many rows it was set up for, after which rows it breaks them, and what the
     * parts are called; and then what a data box says. Whether the rows are shuffled first is among the settings.
     *
     * @param model the model, a split-cases wrapper.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeSplitCases(Object model, SessionNode node, BoxDescription.Builder out) {
        Parameters parameters = node.getParam(model.getClass());
        Object spec = parameters == null || !parameters.getParametersNames().contains("splitCasesSpec")
                ? null : parameters.getValues("splitCasesSpec")[0];

        if (spec instanceof SplitCasesSpec splitSpec) {
            out.field(SPLIT, splitLine(splitSpec));
        }

        describeData(model, node, out);
    }

    /**
     * Describes a causal unmix box: how many components it divides the rows into, the settings of the unmixing that
     * are not at their defaults, and then what a data box says. The components themselves are results.
     *
     * @param model the model, a causal unmix wrapper.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeUnmix(Object model, SessionNode node, BoxDescription.Builder out) {
        Parameters parameters = node.getParam(model.getClass());
        Object spec = parameters == null || !parameters.getParametersNames().contains("unmixSpec")
                ? null : parameters.getValues("unmixSpec")[0];

        if (spec instanceof UnmixSpec unmixSpec) {
            for (String line : unmixLines(unmixSpec)) {
                out.field(UNMIX, line);
            }
        }

        describeData(model, node, out);
    }

    /**
     * Describes a grid search box: the simulation it draws data from, unless it was given data; the algorithms it
     * runs, each with its test and score; the columns of its table of results, in order; the test of its Markov
     * checker, if it is not the usual one, and that test's parameters that were changed; and then the box's own
     * parameters, of which those given several values to compare are listed with all their values.
     *
     * @param model the model, a {@link GridSearchModel}.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeGridSearch(Object model, SessionNode node, BoxDescription.Builder out) {
        GridSearchModel grid = (GridSearchModel) model;
        Parameters parameters = grid.getParameters();

        if (grid.getSuppliedData() == null && parameters.get("algcomparison.selectedSimulations") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof GridSearchModel.SimulationSpec spec) {
                    out.field(GRID_SIMULATION, gridSimulationLine(spec));
                }
            }
        }

        for (GridSearchModel.AlgorithmSpec spec : grid.getSelectedAlgorithms()) {
            out.field(GRID_ALGORITHM, gridAlgorithmLine(spec));
        }

        for (GridSearchModel.MyTableColumn column : grid.getSelectedTableColumns()) {
            out.field(GRID_COLUMN, gridColumnLine(column));
        }

        IndependenceWrapper markovTest = grid.getMarkovCheckerIndependenceWrapper();

        if (markovTest != null && !(markovTest instanceof edu.cmu.tetrad.algcomparison.independence.DegenerateGaussianLrt)) {
            TestOfIndependence annotation = markovTest.getClass().getAnnotation(TestOfIndependence.class);
            out.field(GRID_MARKOV_TEST, annotation == null ? markovTest.getClass().getSimpleName()
                    : nameAndCommand(annotation.name(), annotation.command()));
        }

        // The Markov checker test's own parameters, whichever test it is.
        if (markovTest != null) {
            Parameters checker = grid.getMarkovCheckerParameters();

            for (String name : new TreeSet<>(markovTest.getParameters())) {
                if (isChanged(checker, name)) {
                    out.field(GRID_MARKOV_PARAMETER, name + " = " + valueText(checker.getValues(name)[0])
                                                     + " (default "
                                                     + valueText(ParamDescriptions.getInstance().get(name).getDefaultValue())
                                                     + ")");
                }
            }
        }

        Set<String> names = new TreeSet<>(parameters.getParametersNames());
        changedParameters(out, parameters, names);
        otherSettings(out, parameters, names);
    }

    /**
     * Describes a search box: the algorithm, its test and score, and those of their parameters whose values differ
     * from their defaults. Parameters left over from other algorithms tried in the same box are not listed.
     * Bootstrapping parameters are listed only if bootstrapping is on. If the algorithm takes a random seed and the
     * seed is left at its default, that is remarked on, since the search may then not be repeatable.
     *
     * @param model the model, a {@link GeneralAlgorithmRunner}.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeSearch(Object model, SessionNode node, BoxDescription.Builder out) {
        GeneralAlgorithmRunner runner = (GeneralAlgorithmRunner) model;
        Algorithm algorithm = runner.getAlgorithm();

        if (algorithm == null) {
            out.field(ALGORITHM, "none chosen yet");
            return;
        }

        edu.cmu.tetrad.annotation.Algorithm algorithmAnnotation
                = algorithm.getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class);
        out.field(ALGORITHM, algorithmAnnotation == null
                ? algorithm.getClass().getSimpleName()
                : nameAndCommand(algorithmAnnotation.name(), algorithmAnnotation.command()));

        if (algorithm instanceof TakesIndependenceWrapper takesTest) {
            IndependenceWrapper test = takesTest.getIndependenceWrapper();

            if (test != null) {
                TestOfIndependence annotation = test.getClass().getAnnotation(TestOfIndependence.class);
                out.field(TEST, annotation == null
                        ? test.getClass().getSimpleName()
                        : nameAndCommand(annotation.name(), annotation.command()));
            }
        }

        if (algorithm instanceof TakesScoreWrapper takesScore) {
            ScoreWrapper score = takesScore.getScoreWrapper();

            if (score != null) {
                Score annotation = score.getClass().getAnnotation(Score.class);
                out.field(SCORE, annotation == null
                        ? score.getClass().getSimpleName()
                        : nameAndCommand(annotation.name(), annotation.command()));
            }
        }

        Parameters parameters = runner.getParameters();

        if (parameters == null) {
            return;
        }

        // The parameters that apply, in the groups the parameter panel shows them in.
        Set<String> names = new LinkedHashSet<>();
        names.addAll(new TreeSet<>(Params.getAlgorithmParameters(algorithm)));
        names.addAll(new TreeSet<>(Params.getScoreParameters(algorithm)));
        names.addAll(new TreeSet<>(Params.getTestParameters(algorithm)));

        Set<String> bootstrapping = Params.getBootstrappingParameters(algorithm);

        if (bootstrapping.contains(Params.NUMBER_RESAMPLING) && isChanged(parameters, Params.NUMBER_RESAMPLING)) {
            names.addAll(new TreeSet<>(bootstrapping));
        }

        changedParameters(out, parameters, names);

        if (names.contains(Params.SEED) && !isChanged(parameters, Params.SEED)) {
            out.field(SEED, "not fixed; results may vary between runs if the search uses random numbers");
        }
    }

    /**
     * Describes a data box: the name, size and kind of each data set, whether it has missing values, any knowledge
     * the selected data set carries, and the parameters of the box whose values differ from their defaults. For a
     * data set loaded from a file in this box, the file's name and the settings it was read with are given too, if
     * they were recorded, under the labels the load dialog uses. The data themselves are not written.
     *
     * @param model the model, a {@link DataWrapper}.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeData(Object model, SessionNode node, BoxDescription.Builder out) {
        DataWrapper wrapper = (DataWrapper) model;
        DataModelList dataModels = wrapper.getDataModelList();
        int count = dataModels == null ? 0 : dataModels.size();

        for (int i = 0; i < Math.min(count, MAX_DATA_SETS_LISTED); i++) {
            DataModel dataModel = dataModels.get(i);
            out.field(DATA_SET, dataModel.getName() == null || dataModel.getName().isBlank()
                    ? "unnamed" : dataModel.getName());

            // How the file was read, if this data set was loaded from a file in this box.
            Map<String, String> loadSettings = wrapper.getLoadSettings(dataModel.getName());

            if (loadSettings != null) {
                loadSettings.forEach(out::field);
            }

            out.field(SHAPE, shape(dataModel));

            if (dataModel instanceof DataSet dataSet) {
                out.field(MISSING_VALUES, dataSet.existsMissingValue() ? "present" : "none");
            }
        }

        if (count > MAX_DATA_SETS_LISTED) {
            out.field(NOT_LISTED, (count - MAX_DATA_SETS_LISTED) + " more data sets");
        }

        DataModel selected = count == 0 ? null : wrapper.getSelectedDataModel();

        if (count > 1 && selected != null && selected.getName() != null) {
            out.field(SELECTED_DATA_SET, selected.getName());
        }

        if (selected != null) {
            knowledge(out, selected.getKnowledge(), false);
        }

        describeGeneral(model, node, out);
    }

    /**
     * Describes a knowledge box: the knowledge, in the text form Tetrad saves and loads knowledge in.
     *
     * @param model the model, a {@link KnowledgeBoxModel}.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeKnowledge(Object model, SessionNode node, BoxDescription.Builder out) {
        knowledge(out, ((KnowledgeBoxModel) model).getKnowledge(), true);
    }

    /**
     * Describes a graph box. A graph box with no parents holds a graph the user drew, loaded or generated, which is
     * a choice, so the graph is written out, in the text form Tetrad saves and loads graphs in, unless it is very
     * large. A graph box with parents holds a graph derived from them, which is a result, so only the parameters of
     * the box whose values differ from their defaults are listed.
     *
     * @param graph the graph the model holds; may be null.
     * @param model the model.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeGraph(Graph graph, Object model, SessionNode node, BoxDescription.Builder out) {
        if (graph == null || !node.getParents().isEmpty()) {
            describeGeneral(model, node, out);
            return;
        }

        int numNodes = graph.getNumNodes();
        int numEdges = graph.getNumEdges();
        out.field(GRAPH, numNodes + (numNodes == 1 ? " node, " : " nodes, ")
                         + numEdges + (numEdges == 1 ? " edge" : " edges"));

        if (numEdges > MAX_EDGES_LISTED) {
            out.field(NOT_LISTED, "the graph, which has more than " + MAX_EDGES_LISTED + " edges");
        } else if (numNodes > 0) {
            out.block(GRAPH_BLOCK, graph.toString());
        }
    }

    private static final String CATEGORIES = "; categories: ";
    private static final String BREAKPOINTS = "; breakpoints: ";
    private static final String CHOSEN_BY = "; chosen by: ";
    private static final String BECOME = "; old categories become: ";
    private static final String BY_VALUES = "Evenly Distributed Values";
    private static final String BY_INTERVALS = "Evenly Distributed Intervals";

    /**
     * Writes how one variable is discretized, as the value of a "Discretize" field. For a continuous variable: its
     * name, its categories, the breakpoints between them, and how the breakpoints were chosen, in the discretization
     * editor's words. For a discrete variable: its name, its new categories, and the index of the new category each
     * old category becomes.
     *
     * @param variableName the name of the variable.
     * @param spec         the variable's discretization specification.
     * @return the text, or null if the variable is not to be discretized or the specification is of no known kind.
     */
    static String discretizeLine(String variableName, Object spec) {
        if (spec instanceof ContinuousDiscretizationSpec continuous) {
            if (continuous.getMethod() == ContinuousDiscretizationSpec.NONE) {
                return null;
            }

            List<String> breakpoints = new ArrayList<>();

            for (double breakpoint : continuous.getBreakpoints()) {
                breakpoints.add(valueText(breakpoint));
            }

            return variableName + CATEGORIES + String.join(", ", continuous.getCategories())
                   + BREAKPOINTS + String.join(", ", breakpoints) + CHOSEN_BY
                   + (continuous.getMethod() == ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES
                    ? BY_VALUES : BY_INTERVALS);
        }

        if (spec instanceof DiscreteDiscretizationSpec discrete) {
            List<String> remap = new ArrayList<>();

            for (int index : discrete.getRemap()) {
                remap.add(String.valueOf(index));
            }

            return variableName + CATEGORIES + String.join(", ", discrete.getCategories())
                   + BECOME + String.join(", ", remap);
        }

        return null;
    }

    /**
     * Reads back what {@link #discretizeLine(String, Object)} wrote, and puts it in the given map as the
     * discretization box keeps it. The box matches specifications to variables by name, so a variable made here
     * from the name alone serves as the key.
     *
     * @param text  the value of a "Discretize" field.
     * @param specs the map to add to.
     * @throws IllegalArgumentException if the text cannot be read, with the reason.
     */
    static void readDiscretizeLine(String text, Map<Node, DiscretizationSpec> specs) {
        int categoriesAt = text.indexOf(CATEGORIES);

        if (categoriesAt <= 0) {
            throw new IllegalArgumentException("it does not name a variable and its categories.");
        }

        String name = text.substring(0, categoriesAt).strip();
        String rest = text.substring(categoriesAt + CATEGORIES.length());
        int breakpointsAt = rest.indexOf(BREAKPOINTS);
        int becomeAt = rest.indexOf(BECOME);

        try {
            if (breakpointsAt >= 0) {
                int chosenAt = rest.indexOf(CHOSEN_BY, breakpointsAt);

                if (chosenAt < 0) {
                    throw new IllegalArgumentException("it does not say how the breakpoints were chosen.");
                }

                List<String> categories = pieces(rest.substring(0, breakpointsAt));
                List<String> breakpointTexts = pieces(rest.substring(breakpointsAt + BREAKPOINTS.length(), chosenAt));
                String chosenBy = rest.substring(chosenAt + CHOSEN_BY.length()).strip();
                double[] breakpoints = new double[breakpointTexts.size()];

                for (int i = 0; i < breakpoints.length; i++) {
                    breakpoints[i] = Double.parseDouble(breakpointTexts.get(i));
                }

                // A category name with a comma in it would be split in two here; the count gives that away.
                if (categories.size() != breakpoints.length + 1) {
                    throw new IllegalArgumentException(categories.size() + " categories do not go with "
                                                       + breakpoints.length + " breakpoints.");
                }

                if (!chosenBy.equals(BY_VALUES) && !chosenBy.equals(BY_INTERVALS)) {
                    throw new IllegalArgumentException("\"" + chosenBy + "\" is not a way of choosing breakpoints.");
                }

                specs.put(new ContinuousVariable(name), new ContinuousDiscretizationSpec(breakpoints, categories,
                        chosenBy.equals(BY_VALUES) ? ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES
                                : ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_INTERVALS));
            } else if (becomeAt >= 0) {
                List<String> categories = pieces(rest.substring(0, becomeAt));
                List<String> remapTexts = pieces(rest.substring(becomeAt + BECOME.length()));
                int[] remap = new int[remapTexts.size()];

                for (int i = 0; i < remap.length; i++) {
                    remap[i] = Integer.parseInt(remapTexts.get(i));

                    if (remap[i] < 0 || remap[i] >= categories.size()) {
                        throw new IllegalArgumentException("there is no category number " + remap[i] + ".");
                    }
                }

                specs.put(new DiscreteVariable(name, remap.length),
                        new DiscreteDiscretizationSpec(remap, categories));
            } else {
                throw new IllegalArgumentException("it gives neither breakpoints nor a mapping of categories.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("a number in it could not be read.");
        }
    }

    private static final String INTERVALS = "; intervals: ";
    private static final String CATEGORY_NUMBERS = "; category numbers: ";
    private static final String CATEGORY_NAMES = "; names: ";
    private static final Pattern INTERVAL = Pattern.compile("\\[\\s*([^,\\]]+),\\s*([^\\]]+)\\]");

    /**
     * Writes which rows a selection box keeps by the values of one variable, as the value of a "Select" field. For
     * a continuous variable: its name and the intervals a value may lie in, ends included. For a discrete variable:
     * its name, the numbers of the categories kept, counting from 0, and their names where they are known.
     *
     * @param variable the variable.
     * @param spec     the variable's selection specification.
     * @return the text.
     */
    static String selectLine(Node variable, SelectionWrapper.SelectionSpec spec) {
        if (spec.isContinuous()) {
            List<String> intervals = new ArrayList<>();

            for (double[] interval : spec.getContinuousIntervals()) {
                intervals.add("[" + valueText(interval[0]) + ", " + valueText(interval[1]) + "]");
            }

            return variable.getName() + INTERVALS + String.join(", ", intervals);
        }

        List<Integer> kept = new ArrayList<>(new TreeSet<>(spec.getAcceptedCategories()));
        List<String> numbers = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (int index : kept) {
            numbers.add(String.valueOf(index));

            if (variable instanceof DiscreteVariable discrete && index >= 0 && index < discrete.getNumCategories()) {
                names.add(discrete.getCategory(index));
            }
        }

        return variable.getName() + CATEGORY_NUMBERS + String.join(", ", numbers)
               + (names.size() == kept.size() ? CATEGORY_NAMES + String.join(", ", names) : "");
    }

    /**
     * Reads back what {@link #selectLine} wrote, and puts it in the given map as the selection box keeps it. The
     * box matches specifications to variables by name, so a variable made here from the name alone serves as the
     * key. The names of categories are for the reader and are not read; the numbers are.
     *
     * @param text  the value of a "Select" field.
     * @param specs the map to add to.
     * @throws IllegalArgumentException if the text cannot be read, with the reason.
     */
    static void readSelectLine(String text, Map<Node, SelectionWrapper.SelectionSpec> specs) {
        int intervalsAt = text.indexOf(INTERVALS);
        int numbersAt = text.indexOf(CATEGORY_NUMBERS);

        try {
            if (intervalsAt > 0) {
                List<double[]> intervals = new ArrayList<>();
                Matcher matcher = INTERVAL.matcher(text.substring(intervalsAt + INTERVALS.length()));

                while (matcher.find()) {
                    double low = Double.parseDouble(matcher.group(1).strip());
                    double high = Double.parseDouble(matcher.group(2).strip());

                    if (low > high) {
                        throw new IllegalArgumentException("an interval in it ends before it starts.");
                    }

                    intervals.add(new double[]{low, high});
                }

                if (intervals.isEmpty()) {
                    throw new IllegalArgumentException("it gives no interval.");
                }

                specs.put(new ContinuousVariable(text.substring(0, intervalsAt).strip()),
                        SelectionWrapper.SelectionSpec.continuous(intervals));
            } else if (numbersAt > 0) {
                String rest = text.substring(numbersAt + CATEGORY_NUMBERS.length());
                int namesAt = rest.indexOf(CATEGORY_NAMES);
                Set<Integer> kept = new LinkedHashSet<>();
                int largest = -1;

                for (String piece : pieces(namesAt < 0 ? rest : rest.substring(0, namesAt))) {
                    int index = Integer.parseInt(piece);

                    if (index < 0) {
                        throw new IllegalArgumentException("there is no category number " + index + ".");
                    }

                    kept.add(index);
                    largest = Math.max(largest, index);
                }

                if (kept.isEmpty()) {
                    throw new IllegalArgumentException("it keeps no category.");
                }

                specs.put(new DiscreteVariable(text.substring(0, numbersAt).strip(), largest + 1),
                        SelectionWrapper.SelectionSpec.discrete(kept));
            } else {
                throw new IllegalArgumentException("it gives neither intervals nor categories.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("a number in it could not be read.");
        }
    }

    private static final String ROWS = " rows; break after rows: ";
    private static final String PARTS = "; parts: ";
    private static final String NO_BREAKS = "none";

    /**
     * Writes how a split-cases box divides the rows, as the value of a "Split" field: the number of rows it was set
     * up for, the rows after which a new part starts, and the names of the parts.
     *
     * @param spec the split.
     * @return the text.
     */
    static String splitLine(SplitCasesSpec spec) {
        List<String> breaks = new ArrayList<>();

        for (int breakpoint : spec.getBreakpoints()) {
            breaks.add(String.valueOf(breakpoint));
        }

        return spec.getSampleSize() + ROWS + (breaks.isEmpty() ? NO_BREAKS : String.join(", ", breaks))
               + PARTS + String.join(", ", spec.getSplitNames());
    }

    /**
     * Reads back what {@link #splitLine} wrote.
     *
     * @param text the value of a "Split" field.
     * @return the split.
     * @throws IllegalArgumentException if the text cannot be read, with the reason.
     */
    static SplitCasesSpec readSplitLine(String text) {
        int rowsAt = text.indexOf(ROWS);
        int partsAt = text.indexOf(PARTS);

        if (rowsAt <= 0 || partsAt < rowsAt) {
            throw new IllegalArgumentException("it does not give the rows, the breaks and the parts.");
        }

        try {
            int rows = Integer.parseInt(text.substring(0, rowsAt).strip());
            String breaksText = text.substring(rowsAt + ROWS.length(), partsAt).strip();
            List<String> breakTexts = breaksText.equals(NO_BREAKS) ? List.of() : pieces(breaksText);
            List<String> names = pieces(text.substring(partsAt + PARTS.length()));
            int[] breaks = new int[breakTexts.size()];
            int last = 0;

            for (int i = 0; i < breaks.length; i++) {
                breaks[i] = Integer.parseInt(breakTexts.get(i));

                if (breaks[i] < last || breaks[i] > rows) {
                    throw new IllegalArgumentException("its breaks are not in order within the " + rows + " rows.");
                }

                last = breaks[i];
            }

            if (names.size() != breaks.length + 1) {
                throw new IllegalArgumentException(names.size() + " parts do not go with " + breaks.length
                                                   + " breaks.");
            }

            return new SplitCasesSpec(rows, breaks, new ArrayList<>(names));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("a number in it could not be read.");
        }
    }

    private static final String COMPONENTS = "components: ";
    private static final String BY_BIC = "chosen by BIC from ";
    private static final String BIC_TO = " to ";

    /**
     * A setting of a causal unmix specification that the box's editor offers and the unmixing uses, other than the
     * number of components: its name, how it is read from a specification, and how a value given as text is set.
     */
    private record UnmixSetting(String name, java.util.function.Function<UnmixSpec, Object> get,
                                java.util.function.BiConsumer<UnmixSpec, String> set) {
    }

    /**
     * The settings, in the order the editor shows them. A specification also holds a graph learner, a PC alpha and
     * collider style, a BOSS penalty discount and a random seed; the editor does not offer those and the box does
     * not use them, so they are not written.
     */
    private static final List<UnmixSetting> UNMIX_SETTINGS = List.of(
            new UnmixSetting("useParentSuperset", UnmixSpec::isUseParentSuperset,
                    (spec, text) -> spec.setUseParentSuperset(trueOrFalse(text))),
            new UnmixSetting("supersetTopM", UnmixSpec::getSupersetTopM,
                    (spec, text) -> spec.setSupersetTopM(Integer.parseInt(text))),
            new UnmixSetting("supersetScore", UnmixSpec::getSupersetScore,
                    (spec, text) -> spec.setSupersetScore(oneOf(UnmixSpec.SupersetScore.class, text))),
            new UnmixSetting("robustScaleResiduals", UnmixSpec::isRobustScaleResiduals,
                    (spec, text) -> spec.setRobustScaleResiduals(trueOrFalse(text))),
            new UnmixSetting("covarianceMode", UnmixSpec::getCovarianceMode,
                    (spec, text) -> spec.setCovarianceMode(oneOf(UnmixSpec.CovarianceMode.class, text))),
            new UnmixSetting("fullSigmaSafetyMargin", UnmixSpec::getFullSigmaSafetyMargin,
                    (spec, text) -> spec.setFullSigmaSafetyMargin(Integer.parseInt(text))),
            new UnmixSetting("kmeansRestarts", UnmixSpec::getKmeansRestarts,
                    (spec, text) -> spec.setKmeansRestarts(Integer.parseInt(text))),
            new UnmixSetting("emMaxIters", UnmixSpec::getEmMaxIters,
                    (spec, text) -> spec.setEmMaxIters(Integer.parseInt(text))),
            new UnmixSetting("ridge", UnmixSpec::getRidge,
                    (spec, text) -> spec.setRidge(Double.parseDouble(text))),
            new UnmixSetting("shrinkage", UnmixSpec::getShrinkage,
                    (spec, text) -> spec.setShrinkage(Double.parseDouble(text))),
            new UnmixSetting("annealSteps", UnmixSpec::getAnnealSteps,
                    (spec, text) -> spec.setAnnealSteps(Integer.parseInt(text))),
            new UnmixSetting("annealStartT", UnmixSpec::getAnnealStartT,
                    (spec, text) -> spec.setAnnealStartT(Double.parseDouble(text))),
            // The editor's one check box sets both of these, and the unmixing logs if either is set.
            new UnmixSetting("saveDiagnostics", spec -> spec.isSaveDiagnostics() || spec.isLogIntermediate(),
                    (spec, text) -> {
                        spec.setSaveDiagnostics(trueOrFalse(text));
                        spec.setLogIntermediate(trueOrFalse(text));
                    }));

    private static <E extends Enum<E>> E oneOf(Class<E> kind, String text) {
        try {
            return Enum.valueOf(kind, text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("\"" + text + "\" is not one of "
                                               + java.util.Arrays.toString(kind.getEnumConstants()) + ".");
        }
    }

    private static boolean trueOrFalse(String text) {
        if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("\"" + text + "\" is neither true nor false.");
        }

        return Boolean.parseBoolean(text);
    }

    /**
     * Writes a causal unmix specification, as the values of "Unmix" fields: first how many components the rows are
     * divided into, a number or a range to choose from by BIC, and then each setting that is not at its default,
     * as a parameter of a box is written.
     *
     * @param spec the specification.
     * @return the lines.
     */
    static List<String> unmixLines(UnmixSpec spec) {
        UnmixSpec defaults = new UnmixSpec();
        List<String> lines = new ArrayList<>();

        lines.add(COMPONENTS + (spec.isAutoSelectK() ? BY_BIC + spec.getKmin() + BIC_TO + spec.getKmax()
                : String.valueOf(spec.getK())));

        for (UnmixSetting setting : UNMIX_SETTINGS) {
            Object value = setting.get().apply(spec);
            Object defaultValue = setting.get().apply(defaults);

            if (!Objects.equals(value, defaultValue)) {
                lines.add(setting.name() + " = " + value + " (default " + defaultValue + ")");
            }
        }

        return lines;
    }

    /**
     * Reads back one line of what {@link #unmixLines} wrote, into the given specification.
     *
     * @param text the value of an "Unmix" field.
     * @param spec the specification to set.
     * @throws IllegalArgumentException if the text cannot be read, with the reason.
     */
    static void readUnmixLine(String text, UnmixSpec spec) {
        String line = text.strip();

        try {
            if (line.startsWith(COMPONENTS)) {
                String components = line.substring(COMPONENTS.length()).strip();

                if (components.startsWith(BY_BIC)) {
                    String[] range = components.substring(BY_BIC.length()).split(BIC_TO);

                    if (range.length != 2) {
                        throw new IllegalArgumentException("it does not give a range of components.");
                    }

                    int low = Integer.parseInt(range[0].strip());
                    int high = Integer.parseInt(range[1].strip());

                    if (low < 1 || high < low) {
                        throw new IllegalArgumentException("its range of components is not one.");
                    }

                    spec.setAutoSelectK(true).setKmin(low).setKmax(high);
                } else {
                    spec.setAutoSelectK(false).setK(Integer.parseInt(components));
                }

                return;
            }

            int equals = line.indexOf(" = ");
            int defaultAt = line.lastIndexOf(" (default ");

            if (equals <= 0) {
                throw new IllegalArgumentException("it gives neither the components nor a setting.");
            }

            String name = line.substring(0, equals).strip();
            String value = line.substring(equals + 3, defaultAt > equals ? defaultAt : line.length()).strip();

            for (UnmixSetting setting : UNMIX_SETTINGS) {
                if (setting.name().equals(name)) {
                    setting.set().accept(spec, value);
                    return;
                }
            }

            throw new IllegalArgumentException("\"" + name + "\" is not a setting of the unmixing.");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("a number in it could not be read.");
        }
    }

    static final String GRID_ON = " on ";
    static final String GRID_TEST = "; test: ";
    static final String GRID_SCORE = "; score: ";
    static final String GRID_STATISTIC = " (statistic)";
    static final String GRID_PARAMETER = " (parameter)";

    /**
     * Writes a grid search box's simulation: what it says of itself, and then, in brackets, the class that
     * simulates and the class that makes the graph, by which it is found again.
     */
    private static String gridSimulationLine(GridSearchModel.SimulationSpec spec) {
        String said;

        try {
            said = oneLine(spec.getSimulationImpl().getDescription());
        } catch (RuntimeException e) {
            said = spec.getSimulationClass().getSimpleName();
        }

        return said + " (" + spec.getSimulationClass().getName() + GRID_ON + spec.getGraphClass().getName() + ")";
    }

    /**
     * Writes one of a grid search box's algorithms as a search box's are written, with its test and score after it
     * on the same line.
     */
    private static String gridAlgorithmLine(GridSearchModel.AlgorithmSpec spec) {
        edu.cmu.tetrad.annotation.Algorithm algorithm = spec.getAlgorithm().getAlgorithm().annotation();
        StringBuilder line = new StringBuilder(nameAndCommand(algorithm.name(), algorithm.command()));

        if (spec.getTest() != null) {
            line.append(GRID_TEST).append(nameAndCommand(spec.getTest().annotation().name(),
                    spec.getTest().annotation().command()));
        }

        if (spec.getScore() != null) {
            line.append(GRID_SCORE).append(nameAndCommand(spec.getScore().annotation().name(),
                    spec.getScore().annotation().command()));
        }

        return line.toString();
    }

    /**
     * Writes one column of a grid search box's table: a statistic by its abbreviation, or a parameter by its name.
     */
    private static String gridColumnLine(GridSearchModel.MyTableColumn column) {
        return column.getType() == GridSearchModel.MyTableColumn.ColumnType.STATISTIC
                ? column.getColumnName() + GRID_STATISTIC : column.getParameter() + GRID_PARAMETER;
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private static List<String> pieces(String text) {
        List<String> pieces = new ArrayList<>();

        if (!text.isBlank()) {
            for (String piece : text.split(",", -1)) {
                pieces.add(piece.strip());
            }
        }

        return pieces;
    }

    /**
     * Describes a note box: the text of the note.
     *
     * @param model the model, a {@link NoteModel}.
     * @param node  the session node holding the model.
     * @param out   the description of the box.
     */
    public static void describeNote(Object model, SessionNode node, BoxDescription.Builder out) {
        StyledDocument document = ((NoteModel) model).getNote();

        if (document == null) {
            return;
        }

        try {
            String text = document.getText(0, document.getLength());

            if (!text.isBlank()) {
                out.block(NOTE_BLOCK, text);
            }
        } catch (BadLocationException e) {
            // Cannot happen for the range of the whole document; a note that cannot be read is left out.
        }
    }

    //=============================== shared helpers ===============================//

    /**
     * Adds a "Changed" field for each of the given parameters that is set in the given parameter object to a value
     * other than its registered default. A parameter is passed over if it has no registered default, since it then
     * cannot be said to have changed, or if its value is not a plain number, truth value or piece of text.
     *
     * @param out        the description of the box.
     * @param parameters the parameter object.
     * @param names      the names of the parameters to consider, in the order to list them.
     */
    public static void changedParameters(BoxDescription.Builder out, Parameters parameters, Collection<String> names) {
        for (String name : names) {
            if (isChanged(parameters, name)) {
                Object value = parameters.getValues(name)[0];
                Object defaultValue = ParamDescriptions.getInstance().get(name).getDefaultValue();
                out.changed(name, valueText(value), valueText(defaultValue));
            } else if (hasSeveralValues(parameters, name)) {
                List<String> values = new ArrayList<>();

                for (Object value : parameters.getValues(name)) {
                    values.add(valueText(value));
                }

                out.field(VALUES, name + " = " + String.join(", ", values) + " (default "
                                  + valueText(ParamDescriptions.getInstance().get(name).getDefaultValue()) + ")");
            }
        }
    }

    /**
     * Says whether a registered parameter has been given more than one value, each a plain number, truth value or
     * piece of text. A grid search box does this to a parameter whose values it is to compare.
     */
    private static boolean hasSeveralValues(Parameters parameters, String name) {
        if (!parameters.getParametersNames().contains(name)
            || !ParamDescriptions.getInstance().getNames().contains(name)) {
            return false;
        }

        Object[] values = parameters.getValues(name);

        if (values == null || values.length < 2) {
            return false;
        }

        for (Object value : values) {
            if (!isPlain(value)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Adds what the given parameter object holds under names that have no registered default. Much of what is
     * chosen outside a search box is kept this way: an imputation method, a number of time lags, the variables to
     * keep. With no default to compare with, these cannot be said to have changed, so all that are present are
     * given.
     * <ul>
     * <li>A number, a truth value, a piece of text on one line, or a named constant: a "Setting" field.</li>
     * <li>A piece of text of several lines, a list of pieces of text, or several text values: a block.</li>
     * <li>Data, a graph, knowledge or another box's model: passed over, being data and not a setting.</li>
     * <li>Any other object: a "Not described" field naming it, so that what the description lacks is on the
     * page.</li>
     * </ul>
     * Names that start with two underscores or with "ui." are the interface's own bookkeeping and are passed over.
     *
     * @param out        the description of the box.
     * @param parameters the parameter object.
     * @param names      the names to consider, in the order to list them.
     */
    public static void otherSettings(BoxDescription.Builder out, Parameters parameters, Collection<String> names) {
        Map<String, String> blocks = new LinkedHashMap<>();
        List<String> notDescribed = new ArrayList<>();

        for (String name : names) {
            if (ParamDescriptions.getInstance().getNames().contains(name)
                || !parameters.getParametersNames().contains(name)
                || name.startsWith("__") || name.startsWith("ui.") || name.contains("\n")
                || INTERFACE_STATE.contains(name) || DESCRIBED_ELSEWHERE.contains(name)) {
                continue;
            }

            Object[] values = parameters.getValues(name);

            if (values == null || values.length == 0 || (values.length == 1 && values[0] == null)) {
                continue;
            }

            if (values.length > 1) {
                if (allText(List.of(values))) {
                    blocks.put(VALUES_BLOCK + name, joinLines(List.of(values)));
                } else {
                    notDescribed.add(name);
                }

                continue;
            }

            Object value = values[0];
            String literal = SettingLiteral.write(value);

            if (literal != null) {
                out.field(SETTING, name + " = " + literal);
            } else if (value instanceof String) {
                blocks.put(TEXT_BLOCK + name, (String) value);
            } else if (value instanceof List<?> list && allText(list)) {
                blocks.put(LIST_BLOCK + name, joinLines(list));
            } else if (!(value instanceof DataModel || value instanceof Graph || value instanceof Knowledge
                         || value instanceof SessionModel)) {
                notDescribed.add(name);
            }
        }

        blocks.forEach(out::block);

        for (String name : notDescribed) {
            out.field(NOT_DESCRIBED, name);
        }
    }

    private static boolean allText(List<?> items) {
        for (Object item : items) {
            if (!(item instanceof String text) || text.contains("\n") || text.contains("\r")) {
                return false;
            }
        }

        return true;
    }

    private static String joinLines(List<?> items) {
        StringBuilder text = new StringBuilder();

        for (Object item : items) {
            text.append(item).append('\n');
        }

        return text.toString();
    }

    /**
     * Says whether the given parameter is set, in the given parameter object, to a single plain value other than
     * its registered default.
     *
     * @param parameters the parameter object.
     * @param name       the name of the parameter.
     * @return true if so.
     */
    public static boolean isChanged(Parameters parameters, String name) {
        if (!parameters.getParametersNames().contains(name)
            || !ParamDescriptions.getInstance().getNames().contains(name)) {
            return false;
        }

        Object[] values = parameters.getValues(name);

        if (values == null || values.length != 1 || !isPlain(values[0])) {
            return false;
        }

        Object defaultValue = ParamDescriptions.getInstance().get(name).getDefaultValue();
        return !sameValue(values[0], defaultValue);
    }

    private static boolean isPlain(Object value) {
        return value instanceof Number || value instanceof Boolean || value instanceof String
               || value instanceof Character || value instanceof Enum<?>;
    }

    private static boolean sameValue(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
        }

        return Objects.equals(a, b) || (a != null && b != null && a.toString().equals(b.toString()));
    }

    /**
     * Returns a value as text. Decimal numbers are written in plain notation, so that 0.0001 reads as 0.0001 and
     * not as 1.0E-4, except where that would run to many digits.
     */
    static String valueText(Object value) {
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            double magnitude = Math.abs(d);

            if (Double.isNaN(d) || Double.isInfinite(d) || magnitude >= 1e15 || (d != 0 && magnitude < 1e-10)) {
                return String.valueOf(d);
            }

            String plain = BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
            return plain.contains(".") ? plain : plain + ".0";
        }

        return String.valueOf(value);
    }

    private static String nameAndCommand(String name, String command) {
        return command == null || command.isBlank() ? name : name + " (" + command + ")";
    }

    private static String shape(DataModel dataModel) {
        if (dataModel instanceof DataSet dataSet) {
            int continuous = 0;
            int discrete = 0;

            for (Node variable : dataSet.getVariables()) {
                if (variable instanceof DiscreteVariable) {
                    discrete++;
                } else {
                    continuous++;
                }
            }

            String kind;

            if (discrete == 0) {
                kind = "continuous";
            } else if (continuous == 0) {
                kind = "discrete";
            } else {
                kind = "mixed (" + continuous + " continuous, " + discrete + " discrete)";
            }

            int rows = dataSet.getNumRows();
            int columns = dataSet.getNumColumns();
            return rows + (rows == 1 ? " row, " : " rows, ") + columns + (columns == 1 ? " column, " : " columns, ")
                   + kind;
        }

        if (dataModel instanceof ICovarianceMatrix matrix) {
            int dimension = matrix.getDimension();
            return (matrix instanceof CorrelationMatrix ? "correlation matrix, " : "covariance matrix, ")
                   + dimension + (dimension == 1 ? " variable, " : " variables, ")
                   + "sample size " + matrix.getSampleSize();
        }

        return dataModel.getClass().getSimpleName();
    }

    /**
     * Adds the given knowledge as a block. If the knowledge is empty, adds a field saying so if asked to, and
     * otherwise nothing.
     */
    private static void knowledge(BoxDescription.Builder out, Knowledge knowledge, boolean sayIfEmpty) {
        if (knowledge == null || knowledge.isEmpty()) {
            if (sayIfEmpty) {
                out.field(KNOWLEDGE, "empty");
            }

            return;
        }

        try {
            StringWriter writer = new StringWriter();
            DataWriter.saveKnowledge(knowledge, writer);
            out.block(KNOWLEDGE_BLOCK, writer.toString());
        } catch (IOException e) {
            // A StringWriter does not throw; knowledge that cannot be written is reported, not dropped silently.
            out.field(KNOWLEDGE, "could not be written: " + e.getMessage());
        }
    }
}
