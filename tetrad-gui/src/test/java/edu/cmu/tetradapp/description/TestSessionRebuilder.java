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

import edu.cmu.tetrad.algcomparison.algorithm.AlgorithmFactory;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.editor.search.GuidedAlgorithmCard;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.model.NoteModel;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.session.Session;
import edu.cmu.tetradapp.session.SessionNode;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetradapp.description.TestSessionDescription.DATE;
import static org.junit.Assert.*;

/**
 * Tests the building of a skeletal session from a session description, and the return of the described settings
 * when the boxes of that session are given models.
 *
 * @author Claude
 */
public class TestSessionRebuilder {

    private static SessionNode node(SessionWrapper session, String name) {
        return ((SessionNodeWrapper) session.getNode(name)).getSessionNode();
    }

    private static SessionDescription describeAndRead(SessionWrapper session) {
        String text = SessionDescriptionWriter.toText(new SessionDescriber().describe(session, DATE));
        SessionDescriptionParser.Result parsed = SessionDescriptionParser.parse(text);
        assertEquals(List.of(), parsed.warnings());
        return parsed.description();
    }

    /**
     * Gives a box the model its description names, as a double click on the box does, and applies the description.
     */
    private static void makeModel(SessionWrapper session, String name) throws Exception {
        SessionNode node = node(session, name);
        Class<?> modelClass = PendingDescription.modelClass(node);
        assertNotNull(name + " should have a described model.", modelClass);
        node.createModel(modelClass, false);
        assertEquals(List.of(), PendingDescription.apply(node));
    }

    @Test
    public void testBoxesConnectionsAndLocations() throws Exception {
        SessionWrapper original = TestSessionDescription.buildSession();
        SessionDescription description = describeAndRead(original);
        SessionRebuilder.Result result = SessionRebuilder.rebuild(description, "rebuilt.tet");
        SessionWrapper rebuilt = result.session();

        assertEquals(List.of(), result.problems());
        assertEquals("rebuilt.tet", rebuilt.getName());
        assertEquals(original.getNodes().size(), rebuilt.getNodes().size());

        for (Node n : original.getNodes()) {
            SessionNodeWrapper was = (SessionNodeWrapper) n;
            SessionNodeWrapper is = (SessionNodeWrapper) rebuilt.getNode(was.getName());
            assertNotNull(was.getName() + " should be in the rebuilt session.", is);
            assertEquals(was.getSessionNode().getBoxType(), is.getSessionNode().getBoxType());
            assertEquals(was.getButtonType(), is.getButtonType());
            assertEquals(was.getCenterX(), is.getCenterX());
            assertEquals(was.getCenterY(), is.getCenterY());

            List<String> wasParents = new ArrayList<>();
            List<String> isParents = new ArrayList<>();
            was.getSessionNode().getParents().forEach(p -> wasParents.add(p.getDisplayName()));
            is.getSessionNode().getParents().forEach(p -> isParents.add(p.getDisplayName()));
            wasParents.sort(null);
            isParents.sort(null);
            assertEquals(wasParents, isParents);
        }

        assertEquals(original.getEdges().size(), rebuilt.getEdges().size());

        // The note and the hand-drawn graph need no data, so they have their models at once. The rest wait.
        assertTrue(node(rebuilt, "Note1").getModel() instanceof NoteModel);
        assertTrue(node(rebuilt, "Graph1").getModel() instanceof GraphWrapper);
        assertEquals(1, ((GraphWrapper) node(rebuilt, "Graph1").getModel()).getGraph().getNumEdges());
        assertNull(node(rebuilt, "Data1").getModel());
        assertNull(node(rebuilt, "Knowledge1").getModel());
        assertNull(node(rebuilt, "Search1").getModel());
        assertNull(node(rebuilt, "Search2").getModel());

        // The user is told what to load and where.
        assertTrue(result.toDo().get(0).startsWith("Data1: double click it and load study.csv"));
        assertEquals(4, result.toDo().size());
    }

    /**
     * Describing the rebuilt session, before any data are loaded, gives back the description it was built from.
     */
    @Test
    public void testDescriptionOfRebuiltSessionIsTheSame() throws Exception {
        SessionDescription description = describeAndRead(TestSessionDescription.buildSession());
        SessionWrapper rebuilt = SessionRebuilder.rebuild(description, "study.tet").session();
        assertEquals(SessionDescriptionWriter.toText(description),
                SessionDescriptionWriter.toText(new SessionDescriber().describe(rebuilt, DATE)));
    }

    /**
     * The settings of the description come back as the user loads the data and gives the boxes their models.
     */
    @Test
    public void testSettingsReturnWhenBoxesGetModels() throws Exception {
        SessionDescription description = describeAndRead(TestSessionDescription.buildSession());
        SessionWrapper rebuilt = SessionRebuilder.rebuild(description, "study.tet").session();

        // The user makes the data box and loads the data.
        makeModel(rebuilt, "Data1");
        assertNotNull("A data box keeps its description until data are loaded.",
                PendingDescription.get(node(rebuilt, "Data1")));
        assertEquals("study.csv", new SessionDescriber().describe(rebuilt, DATE).getBox("Data1")
                .get(StandardDescribers.DATA_SET));
        ((DataWrapper) node(rebuilt, "Data1").getModel()).setDataModel(TestSessionDescription.simulate());

        // Knowledge.
        makeModel(rebuilt, "Knowledge1");
        Knowledge knowledge = ((KnowledgeBoxModel) node(rebuilt, "Knowledge1").getModel()).getKnowledge();
        assertEquals(List.of("X1"), knowledge.getTier(0));
        assertEquals(List.of("X2", "X3", "X4"), knowledge.getTier(1));
        assertTrue(knowledge.isForbidden("X4", "X3"));
        assertNull("Once applied, the description is dropped.", PendingDescription.get(node(rebuilt, "Knowledge1")));

        // Search: the algorithm, the score and the changed parameters, with nothing opened or chosen by hand.
        makeModel(rebuilt, "Search1");
        GeneralAlgorithmRunner runner = (GeneralAlgorithmRunner) node(rebuilt, "Search1").getModel();
        assertEquals("FGES", runner.getAlgorithm().getClass().getAnnotation(
                edu.cmu.tetrad.annotation.Algorithm.class).name());
        assertEquals(4.0, runner.getParameters().getDouble(Params.PENALTY_DISCOUNT), 0.0);
        assertEquals(5, runner.getParameters().getInt(Params.MAX_DEGREE));

        // The search editor opens on the described algorithm and score.
        GuidedAlgorithmCard card = new GuidedAlgorithmCard(runner, null);
        assertEquals("FGES", card.getSelectedAlgorithm().getName());
        assertEquals("sem-bic-score", card.getSelectedScore().getScore().annotation().command());

        // And the search runs.
        runner.execute();
        assertNotNull(runner.getGraph());

        makeModel(rebuilt, "Result graph");

        // Now the session describes itself as the original did.
        SessionDescription now = new SessionDescriber().describe(rebuilt, DATE);

        for (String name : List.of("Data1", "Knowledge1", "Search1", "Result graph", "Graph1", "Note1", "Search2")) {
            assertEquals(description.getBox(name), now.getBox(name));
        }
    }

    /**
     * A rebuilt data box is told how the description says its file was read, so that the load dialog can open on
     * those settings; and it still describes itself from the description until the file is loaded.
     */
    @Test
    public void testDataBoxIsToldHowItsFileWasRead() throws Exception {
        SessionWrapper original = TestSessionDescription.buildSession();
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("File", "study.csv");
        settings.put("Data file type", "Tabular data");
        settings.put("Value delimiter", "Semicolon");
        settings.put("Missing value marker", "NA");
        ((DataWrapper) node(original, "Data1").getModel()).setLoadSettings("study.csv", settings);

        SessionDescription description = describeAndRead(original);
        assertEquals(Map.of("study.csv", settings),
                PendingDescription.describedLoadSettings(description.getBox("Data1")));

        SessionWrapper rebuilt = SessionRebuilder.rebuild(description, "study.tet").session();
        makeModel(rebuilt, "Data1");
        DataWrapper wrapper = (DataWrapper) node(rebuilt, "Data1").getModel();

        assertEquals(settings, wrapper.getLoadSettings("study.csv"));
        assertEquals(new ArrayList<>(settings.keySet()), new ArrayList<>(wrapper.getLoadSettings("study.csv").keySet()));
        assertNull(wrapper.getLoadSettings("other.csv"));
        assertEquals(description.getBox("Data1"), new SessionDescriber().describe(rebuilt, DATE).getBox("Data1"));

        // A data box whose description records no file has nothing to be told.
        assertTrue(PendingDescription.describedLoadSettings(
                describeAndRead(TestSessionDescription.buildSession()).getBox("Data1")).isEmpty());
    }

    /**
     * The chooser opens on the described algorithm, test and score even where its usual answers would hide them:
     * an algorithm that allows latent variables, and a score of the mixed family on continuous data.
     */
    @Test
    public void testChooserOpensOnWhatWasDescribed() throws Exception {
        for (int which = 0; which < 2; which++) {
            SessionWrapper session = new SessionWrapper(new Session("study.tet"));
            SessionNodeWrapper data = TestSessionDescription.addBox(session, "Data", "Data1", 100, 100);
            SessionNodeWrapper search = TestSessionDescription.addBox(session, "Search", "Search1", 260, 100);
            TestSessionDescription.connect(session, data, search);
            data.getSessionNode().createModel(DataWrapper.class, false);
            ((DataWrapper) data.getSessionNode().getModel()).setDataModel(TestSessionDescription.simulate());
            search.getSessionNode().createModel(GeneralAlgorithmRunner.class, false);
            GeneralAlgorithmRunner original = (GeneralAlgorithmRunner) search.getSessionNode().getModel();

            if (which == 0) {
                original.setAlgorithm(AlgorithmFactory.create(Fci.class, new FisherZ(), null));
                original.getParameters().set(Params.ALPHA, 0.05);
            } else {
                original.setAlgorithm(AlgorithmFactory.create(Boss.class, null, new DegenerateGaussianBicScore()));
            }

            SessionWrapper rebuilt = SessionRebuilder.rebuild(describeAndRead(session), "study.tet").session();
            makeModel(rebuilt, "Data1");
            ((DataWrapper) node(rebuilt, "Data1").getModel()).setDataModel(TestSessionDescription.simulate());
            makeModel(rebuilt, "Search1");
            GeneralAlgorithmRunner runner = (GeneralAlgorithmRunner) node(rebuilt, "Search1").getModel();
            GuidedAlgorithmCard card = new GuidedAlgorithmCard(runner, null);

            if (which == 0) {
                assertEquals("FCI", card.getSelectedAlgorithm().getName());
                assertEquals("fisher-z-test", card.getSelectedTest().getIndependenceTest().annotation().command());
                assertEquals(0.05, runner.getParameters().getDouble(Params.ALPHA), 0.0);
            } else {
                assertEquals("BOSS", card.getSelectedAlgorithm().getName());
                assertEquals("dg-bic-score", card.getSelectedScore().getScore().annotation().command());
            }
        }
    }

    /**
     * A session that is only partly rebuilt can be saved and reopened, and still knows what it was built from.
     */
    @Test
    public void testKeptDescriptionIsSavedWithTheSession() throws Exception {
        SessionDescription description = describeAndRead(TestSessionDescription.buildSession());
        SessionWrapper rebuilt = SessionRebuilder.rebuild(description, "study.tet").session();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(rebuilt);
        }

        SessionWrapper reopened;

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            reopened = (SessionWrapper) in.readObject();
        }

        assertEquals(GeneralAlgorithmRunner.class, PendingDescription.modelClass(node(reopened, "Search1")));
        assertEquals(SessionDescriptionWriter.toText(description),
                SessionDescriptionWriter.toText(new SessionDescriber().describe(reopened, DATE)));
    }

    @Test
    public void testWhatCannotBeRestoredIsReported() {
        String text = """
                Tetrad session description, format 1
                Session: odd.tet

                [Box Data1]
                Type: Data
                Model: Data
                Location: 100, 100

                [Box Data1]
                Type: Data

                [Box Thing1]
                Type: Hologram

                [Box Search1]
                Type: Search
                Parents: Data1, Missing1
                Algorithm: Zebra Search (zebra)
                Score: Sem BIC Score (sem-bic-score)
                Changed: penaltyDiscount = lots (default 2.0)
                Changed: noSuchParameter = 3 (default 1)
                Changed: maxDegree = 7 (default 1000)

                [Box Graph7]
                Type: Graph
                Model: Hypergraph

                End of session description
                """;

        SessionRebuilder.Result result = SessionRebuilder.rebuild(SessionDescriptionParser.parse(text).description(),
                "odd.tet");
        String problems = String.join("\n", result.problems());

        assertEquals(result.problems().toString(), 7, result.problems().size());
        assertTrue(problems.contains("A second box named \"Data1\""));
        assertTrue(problems.contains("\"Hologram\" is not a kind of box"));
        assertTrue(problems.contains("its parent \"Missing1\" is not in the session"));
        assertTrue(problems.contains("Zebra Search (zebra)"));
        assertTrue(problems.contains("the value lots of the parameter \"penaltyDiscount\""));
        assertTrue(problems.contains("\"noSuchParameter\" is not known"));
        assertTrue(problems.contains("\"Hypergraph\" is not a model"));

        // What could be restored was: three boxes, one connection, one parameter. With no locations given, the
        // boxes are laid out on a grid.
        SessionWrapper session = result.session();
        assertEquals(3, session.getNodes().size());
        assertEquals(1, session.getEdges().size());
        assertEquals(7, node(session, "Search1").getParam(GeneralAlgorithmRunner.class).getInt(Params.MAX_DEGREE));
        assertNotEquals(session.getNode("Search1").getCenterX(), session.getNode("Graph7").getCenterX());
    }

    @Test
    public void testModelNamesAreReadBack() {
        assertEquals(KnowledgeBoxModel.class, PendingDescription.modelClass("Knowledge", "Tiers and Edges"));
        assertEquals(GeneralAlgorithmRunner.class, PendingDescription.modelClass("Search", null));
        assertNull(PendingDescription.modelClass("Graph", null));
        assertNull(PendingDescription.modelClass("Graph", "none yet"));
        assertNull(PendingDescription.modelClass("Graph", "No such model"));
        assertNull(PendingDescription.modelClass("No such box", "Graph"));
        assertEquals("boss", PendingDescription.command("BOSS (boss)"));
        assertEquals("sem-bic-score", PendingDescription.command("Sem BIC Score (sem-bic-score)"));
        assertNull(PendingDescription.command("none chosen yet"));
    }
}
