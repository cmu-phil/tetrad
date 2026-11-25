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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.model.NoteModel;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.model.datamanip.DiscretizationWrapper;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.session.SessionNode;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
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
     * Settings that record where the interface was left, not a choice about the analysis.
     */
    private static final Set<String> INTERFACE_STATE = Set.of("selectedTabParam", "simulationsDropdownPreference");

    /**
     * Settings held as objects that a describer of their own writes out, so the general describer passes over
     * them.
     */
    private static final Set<String> DESCRIBED_ELSEWHERE = Set.of("discretizationSpecs");

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
            }
        }
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
