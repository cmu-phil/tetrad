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
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
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
import edu.cmu.tetradapp.session.SessionNode;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
            changedParameters(out, parameters, new TreeSet<>(parameters.getParametersNames()));
        }
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
            out.field(DATA_SET, dataModel.getName() == null ? "unnamed" : dataModel.getName());

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
