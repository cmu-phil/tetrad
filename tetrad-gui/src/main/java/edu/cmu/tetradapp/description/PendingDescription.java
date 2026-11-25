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
import edu.cmu.tetrad.algcomparison.algorithm.AlgorithmFactory;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.AlgorithmAnnotations;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.ScoreAnnotations;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.annotation.TestOfIndependenceAnnotations;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.app.SessionNodeConfig;
import edu.cmu.tetradapp.app.SessionNodeModelConfig;
import edu.cmu.tetradapp.app.TetradApplicationConfig;
import edu.cmu.tetradapp.editor.search.GuidedAlgorithmCard;
import edu.cmu.tetradapp.model.DagWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.model.NoteModel;
import edu.cmu.tetradapp.model.SemGraphWrapper;
import edu.cmu.tetradapp.session.SessionNode;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The description a box was built from, kept on its session node until the box has a model to take it.
 * <p>
 * A session built from a session description has no data, so most of its boxes cannot have models yet: a search needs
 * data, and so does knowledge. What the description said about such a box (which model, which algorithm, test and
 * score, what knowledge) is kept here, as text, in the node's attributes, which are saved with the session. When the
 * user later gives the box a model, the description is applied to the model and then dropped. Until then, describing
 * the session writes the kept description back out, so nothing is lost by describing a session that is only partly
 * rebuilt.
 * <p>
 * Parameters are not handled here. They are put in the box's parameter object when the session is built, since a
 * model reads them as it is constructed.
 *
 * @author josephramsey
 * @author Claude
 * @see SessionRebuilder
 */
public final class PendingDescription {

    /**
     * The key, in a session node's attributes, of the description the box was built from.
     */
    static final String ATTRIBUTE = "sessionDescription.pending";

    /**
     * What a description writes for the model of a box that has none.
     */
    static final String NO_MODEL = "none yet";

    private PendingDescription() {
    }

    //=============================== keeping ===============================//

    /**
     * Keeps the given description on the given node.
     *
     * @param node the session node.
     * @param box  the description the box was built from.
     */
    public static void set(SessionNode node, BoxDescription box) {
        node.addAttribute(ATTRIBUTE, SessionDescriptionWriter.toText(new SessionDescription(List.of(), List.of(box))));
    }

    /**
     * Returns the description kept on the given node.
     *
     * @param node the session node.
     * @return the description the box was built from, or null if none is kept.
     */
    public static BoxDescription get(SessionNode node) {
        // A session saved before session nodes had attributes reads them as null.
        Map<String, Object> attributes = node.getAllAttributes();

        if (attributes == null || !(attributes.get(ATTRIBUTE) instanceof String text)) {
            return null;
        }

        try {
            List<BoxDescription> boxes = SessionDescriptionParser.parse(text).description().getBoxes();
            return boxes.isEmpty() ? null : boxes.get(0);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Drops the description kept on the given node, if any.
     *
     * @param node the session node.
     */
    public static void clear(SessionNode node) {
        Map<String, Object> attributes = node.getAllAttributes();

        if (attributes != null) {
            attributes.remove(ATTRIBUTE);
        }
    }

    /**
     * Says whether describing the given box should write out the description kept on its node instead of describing
     * its model: when the box has no model, or is a data box into which no data have been loaded yet.
     *
     * @param node the session node.
     * @return true if so.
     */
    public static boolean standsInForModel(SessionNode node) {
        return get(node) != null && (node.getModel() == null || awaitsData(node));
    }

    /**
     * Says whether the given box is a data box of the kind files are loaded into, holding no data yet.
     */
    private static boolean awaitsData(SessionNode node) {
        if (node.getModel() == null || node.getModel().getClass() != DataWrapper.class
            || !node.getParents().isEmpty()) {
            return false;
        }

        for (DataModel dataModel : ((DataWrapper) node.getModel()).getDataModelList()) {
            if (!(dataModel instanceof DataSet dataSet) || dataSet.getNumColumns() > 0) {
                return false;
            }
        }

        return true;
    }

    //=============================== model classes ===============================//

    /**
     * Returns the model class that the description kept on the given node names, so that the box can be given that
     * model without asking the user to choose.
     *
     * @param node the session node.
     * @return the model class, or null if no description is kept or it names no model this box type offers.
     */
    public static Class<?> modelClass(SessionNode node) {
        BoxDescription box = get(node);
        return box == null ? null : modelClass(node.getBoxType(), box.get(BoxDescription.MODEL));
    }

    /**
     * Finds the model class a description names for a box of the given type. This reverses what
     * {@link SessionDescriber} writes: nothing for a box type that offers only one model, the model's name as the
     * model chooser shows it, and the class name added in parentheses where two models share a name.
     *
     * @param boxType   the box type.
     * @param modelText the value of the box's "Model" field, or null if it has none.
     * @return the model class, or null if the box has no model or the model is not one this box type offers.
     */
    static Class<?> modelClass(String boxType, String modelText) {
        SessionNodeConfig nodeConfig = boxType == null ? null
                : TetradApplicationConfig.getInstance().getSessionNodeConfig(boxType);

        if (nodeConfig == null || NO_MODEL.equals(modelText)) {
            return null;
        }

        Class<?>[] models = nodeConfig.getModels();

        if (modelText == null) {
            return models.length == 1 ? models[0] : null;
        }

        Class<?> byName = null;

        for (Class<?> model : models) {
            SessionNodeModelConfig modelConfig = nodeConfig.getModelConfig(model);

            if (modelConfig == null || modelConfig.name() == null) {
                continue;
            }

            if (modelText.equals(modelConfig.name() + " (" + model.getSimpleName() + ")")) {
                return model;
            }

            if (byName == null && modelText.equals(modelConfig.name())) {
                byName = model;
            }
        }

        return byName;
    }

    //=============================== applying ===============================//

    /**
     * Applies the description kept on the given node to the node's model, and drops it. Does nothing if the node
     * has no model or no description is kept. To be called when a box is given a model.
     *
     * @param node the session node.
     * @return one message for each thing in the description that could not be applied; empty if all was applied.
     */
    public static List<String> apply(SessionNode node) {
        List<String> notes = new ArrayList<>();
        Object model = node.getModel();
        BoxDescription box = get(node);

        if (model == null || box == null) {
            return notes;
        }

        // A data box keeps its description until data are loaded into it; see standsInForModel.
        if (awaitsData(node)) {
            return notes;
        }

        // If the user gave the box some other model than the one described, the description does not apply.
        if (model.getClass() == modelClass(node.getBoxType(), box.get(BoxDescription.MODEL))) {
            String name = node.getDisplayName();

            try {
                if (model instanceof GeneralAlgorithmRunner runner) {
                    applySearch(name, box, runner, notes);
                } else if (model instanceof KnowledgeBoxModel knowledgeModel) {
                    applyKnowledge(box, knowledgeModel);
                } else if (model instanceof NoteModel noteModel) {
                    applyNote(box, noteModel);
                } else if (model instanceof GraphWrapper || model instanceof DagWrapper
                           || model instanceof SemGraphWrapper) {
                    applyGraph(name, box, model, notes);
                }
            } catch (Exception e) {
                notes.add(name + ": the description could not be applied (" + e + ").");
            }
        }

        clear(node);

        for (String note : notes) {
            TetradLogger.getInstance().log(note);
        }

        return notes;
    }

    /**
     * Says what in the description of a search box this version of Tetrad does not know, without applying anything;
     * for reporting when a session is built, long before the search box has a model.
     *
     * @param name the name of the box.
     * @param box  the description of the box.
     * @return one message for each algorithm, test or score named that is not known.
     */
    static List<String> checkSearch(String name, BoxDescription box) {
        List<String> notes = new ArrayList<>();
        String algorithm = command(box.get(StandardDescribers.ALGORITHM));
        String test = command(box.get(StandardDescribers.TEST));
        String score = command(box.get(StandardDescribers.SCORE));

        if (algorithm != null && findAlgorithm(algorithm) == null) {
            notes.add(name + ": the algorithm \"" + box.get(StandardDescribers.ALGORITHM)
                      + "\" is not known to this version of Tetrad.");
        }

        if (test != null && findTest(test) == null) {
            notes.add(name + ": the test \"" + box.get(StandardDescribers.TEST)
                      + "\" is not known to this version of Tetrad.");
        }

        if (score != null && findScore(score) == null) {
            notes.add(name + ": the score \"" + box.get(StandardDescribers.SCORE)
                      + "\" is not known to this version of Tetrad.");
        }

        return notes;
    }

    /**
     * Gives a search box the algorithm, test and score described: sets the algorithm on the runner, so that the
     * search can be run and described at once, and sets the algorithm chooser's remembered selections, so that the
     * search editor opens on them.
     */
    @SuppressWarnings("unchecked")
    private static void applySearch(String name, BoxDescription box, GeneralAlgorithmRunner runner,
                                    List<String> notes) throws Exception {
        notes.addAll(checkSearch(name, box));

        String algorithmCommand = command(box.get(StandardDescribers.ALGORITHM));
        AnnotatedClass<edu.cmu.tetrad.annotation.Algorithm> algorithm
                = algorithmCommand == null ? null : findAlgorithm(algorithmCommand);

        if (algorithm == null) {
            return;
        }

        String testCommand = command(box.get(StandardDescribers.TEST));
        String scoreCommand = command(box.get(StandardDescribers.SCORE));
        AnnotatedClass<TestOfIndependence> test = testCommand == null ? null : findTest(testCommand);
        AnnotatedClass<Score> score = scoreCommand == null ? null : findScore(scoreCommand);

        GuidedAlgorithmCard.seedSelection(runner, algorithm, test, score);

        Algorithm made = AlgorithmFactory.create(
                (Class<? extends Algorithm>) algorithm.clazz(),
                test == null ? null : (Class<? extends IndependenceWrapper>) test.clazz(),
                score == null ? null : (Class<? extends ScoreWrapper>) score.clazz());

        // As the algorithm chooser does for algorithms that start from a graph.
        if (made instanceof TakesExternalGraph takesGraph && runner.getSourceGraph() != null) {
            takesGraph.setExternalGraph(new SingleGraphAlg(runner.getSourceGraph()));
        }

        runner.setAlgorithm(made);
    }

    /**
     * Gives a knowledge box the knowledge described. The variables of the box's inputs are added to it, since the
     * text form of knowledge names only the variables the knowledge mentions.
     */
    private static void applyKnowledge(BoxDescription box, KnowledgeBoxModel model) throws Exception {
        List<String> lines = box.getBlock(StandardDescribers.KNOWLEDGE_BLOCK);

        if (lines == null) {
            return;
        }

        Knowledge knowledge = SimpleDataLoader.loadKnowledge(new StringReader(String.join("\n", lines) + "\n"),
                DelimiterType.WHITESPACE, "//");

        for (String variable : model.getVariableNames()) {
            knowledge.addVariable(variable);
        }

        model.setKnowledge(knowledge);
    }

    /**
     * Gives a note box the text described.
     */
    private static void applyNote(BoxDescription box, NoteModel model) throws BadLocationException {
        List<String> lines = box.getBlock(StandardDescribers.NOTE_BLOCK);

        if (lines == null) {
            return;
        }

        DefaultStyledDocument document = new DefaultStyledDocument();
        document.insertString(0, String.join("\n", lines), null);
        model.setNote(document);
    }

    /**
     * Gives a graph box the graph described. The text form of a graph does not say where its nodes sit, so the
     * graph is laid out afresh.
     */
    private static void applyGraph(String name, BoxDescription box, Object model, List<String> notes)
            throws Exception {
        List<String> lines = box.getBlock(StandardDescribers.GRAPH_BLOCK);

        if (lines == null) {
            if (box.get(StandardDescribers.NOT_LISTED) != null) {
                notes.add(name + ": the graph was too large to be written into the description, so the box starts "
                          + "with a new graph.");
            }

            return;
        }

        Graph graph = GraphSaveLoadUtils.readerToGraphTxt(String.join("\n", lines) + "\n");
        LayoutUtil.defaultLayout(graph);

        if (model instanceof GraphWrapper wrapper) {
            wrapper.setGraph(graph);
        } else if (model instanceof DagWrapper wrapper) {
            wrapper.setGraph(graph);
        } else if (model instanceof SemGraphWrapper wrapper) {
            wrapper.setGraph(graph);
        }
    }

    //=============================== looking things up ===============================//

    /**
     * Returns the command in a value such as "BOSS (boss)": the text in the last pair of parentheses.
     *
     * @param value the value of an "Algorithm", "Test" or "Score" field, or null.
     * @return the command, or null if the value is null or has no parentheses at its end.
     */
    static String command(String value) {
        if (value == null || !value.endsWith(")")) {
            return null;
        }

        int open = value.lastIndexOf('(');
        return open < 0 ? null : value.substring(open + 1, value.length() - 1).strip();
    }

    private static AnnotatedClass<edu.cmu.tetrad.annotation.Algorithm> findAlgorithm(String command) {
        for (AnnotatedClass<edu.cmu.tetrad.annotation.Algorithm> candidate
                : AlgorithmAnnotations.getInstance().getAnnotatedClasses()) {
            if (command.equals(candidate.annotation().command())) {
                return candidate;
            }
        }

        return null;
    }

    private static AnnotatedClass<TestOfIndependence> findTest(String command) {
        for (AnnotatedClass<TestOfIndependence> candidate
                : TestOfIndependenceAnnotations.getInstance().getAnnotatedClasses()) {
            if (command.equals(candidate.annotation().command())) {
                return candidate;
            }
        }

        return null;
    }

    private static AnnotatedClass<Score> findScore(String command) {
        for (AnnotatedClass<Score> candidate : ScoreAnnotations.getInstance().getAnnotatedClasses()) {
            if (command.equals(candidate.annotation().command())) {
                return candidate;
            }
        }

        return null;
    }
}
