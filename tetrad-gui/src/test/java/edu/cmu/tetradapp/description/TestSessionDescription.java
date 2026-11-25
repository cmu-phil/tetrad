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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Pc;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetradapp.app.TetradApplicationConfig;
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

import javax.swing.text.DefaultStyledDocument;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests the legible text description of a session: what is said about each kind of box, that the text reads back
 * to the same description, and that the parser survives what documents and email do to text.
 *
 * @author Claude
 */
public class TestSessionDescription {

    static final LocalDate DATE = LocalDate.of(2026, 11, 25);

    /**
     * Adds a box of the given type to the session, as the session workbench does.
     */
    static SessionNodeWrapper addBox(SessionWrapper session, String boxType, String name, int x, int y) {
        Class<?>[] modelClasses = TetradApplicationConfig.getInstance().getSessionNodeConfig(boxType).getModels();
        SessionNode node = new SessionNode(boxType, name, modelClasses);

        for (Class<?> modelClass : modelClasses) {
            node.putParam(modelClass, new Parameters(node.getParameters()));
        }

        SessionNodeWrapper wrapper = new SessionNodeWrapper(node);
        wrapper.setButtonType(boxType);
        wrapper.setCenter(x, y);
        assertTrue(session.addNode(wrapper));
        return wrapper;
    }

    static void connect(SessionWrapper session, SessionNodeWrapper from, SessionNodeWrapper to) {
        assertTrue(session.addEdge(new Edge(from, to, Endpoint.TAIL, Endpoint.ARROW)));
    }

    static DataSet simulate() throws Exception {
        RandomUtil.getInstance().setSeed(3847L);
        Graph graph = new EdgeListGraph();
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x1, x4);
        DataSet data = new SemIm(new SemPm(graph)).simulateData(200, false);
        data.setName("study.csv");
        return data;
    }

    /**
     * Builds a session of the kind a user builds for real data: data, knowledge, a search with two settings
     * changed, a graph extracted from the search, a graph drawn by hand, a note, and a box left empty.
     */
    static SessionWrapper buildSession() throws Exception {
        SessionWrapper session = new SessionWrapper(new Session("study.tet"));

        SessionNodeWrapper data = addBox(session, "Data", "Data1", 100, 100);
        SessionNodeWrapper knowledge = addBox(session, "Knowledge", "Knowledge1", 100, 240);
        SessionNodeWrapper search = addBox(session, "Search", "Search1", 260, 100);
        SessionNodeWrapper result = addBox(session, "Graph", "Result graph", 420, 100);
        SessionNodeWrapper drawn = addBox(session, "Graph", "Graph1", 420, 240);
        SessionNodeWrapper note = addBox(session, "Note", "Note1", 100, 380);
        addBox(session, "Search", "Search2", 260, 380);

        connect(session, data, knowledge);
        connect(session, data, search);
        connect(session, knowledge, search);
        connect(session, search, result);

        // Data.
        data.getSessionNode().createModel(DataWrapper.class, false);
        ((DataWrapper) data.getSessionNode().getModel()).setDataModel(simulate());

        // Knowledge: two tiers and one forbidden edge.
        knowledge.getSessionNode().createModel(KnowledgeBoxModel.class, false);
        Knowledge k = new Knowledge();
        k.addToTier(0, "X1");
        k.addToTier(1, "X2");
        k.addToTier(1, "X3");
        k.addToTier(1, "X4");
        k.setForbidden("X4", "X3");
        ((KnowledgeBoxModel) knowledge.getSessionNode().getModel()).setKnowledge(k);

        // Search: FGES with the SEM BIC score, penalty discount and maximum degree changed.
        search.getSessionNode().createModel(GeneralAlgorithmRunner.class, false);
        GeneralAlgorithmRunner runner = (GeneralAlgorithmRunner) search.getSessionNode().getModel();
        runner.setAlgorithm(AlgorithmFactory.create(Fges.class, null, new SemBicScore()));
        runner.getParameters().set(Params.PENALTY_DISCOUNT, 4.0);
        runner.getParameters().set(Params.MAX_DEGREE, 5);

        // Parameters of other algorithms, left over in the same box; they must not be listed.
        runner.getParameters().set(Params.ALPHA, 0.2);
        runner.getParameters().set(Params.NUM_STARTS, 3);

        // A parameter set to its default; it must not be listed.
        runner.getParameters().set(Params.FAITHFULNESS_ASSUMED, false);

        // Bootstrapping is off, so a bootstrapping parameter must not be listed even though it is changed.
        runner.getParameters().set(Params.PERCENT_RESAMPLE_SIZE, 50);
        runner.execute();

        // A graph taken from the search.
        result.getSessionNode().createModel(GraphWrapper.class, false);

        // A graph drawn by hand.
        drawn.getSessionNode().createModel(GraphWrapper.class, false);
        Graph g = new EdgeListGraph();
        Node a = new GraphNode("A");
        Node b = new GraphNode("B");
        g.addNode(a);
        g.addNode(b);
        g.addDirectedEdge(a, b);
        ((GraphWrapper) drawn.getSessionNode().getModel()).setGraph(g);

        // A note with lines that test the guarding of block text.
        note.getSessionNode().createModel(NoteModel.class, false);
        DefaultStyledDocument document = new DefaultStyledDocument();
        document.insertString(0, "Second pass, after dropping the pilot rows.\n\n  End note\n\\ a backslash line\nLast line.",
                null);
        ((NoteModel) note.getSessionNode().getModel()).setNote(document);

        return session;
    }

    @Test
    public void testDescribesChoices() throws Exception {
        SessionDescription description = new SessionDescriber().describe(buildSession(), DATE);
        String text = SessionDescriptionWriter.toText(description);
        System.out.println(text);

        assertTrue(text.startsWith("Tetrad session description, format 1\n"));
        assertTrue(text.endsWith("\nEnd of session description\n"));
        assertEquals("study.tet", description.getHeader(SessionDescription.SESSION));
        assertEquals("2026-11-25", description.getHeader(SessionDescription.WRITTEN));
        assertEquals(7, description.getBoxes().size());

        // Every box comes after its parents.
        List<BoxDescription> boxes = description.getBoxes();

        for (int i = 0; i < boxes.size(); i++) {
            for (String parent : description.getParentNames(boxes.get(i))) {
                assertTrue(boxes.indexOf(description.getBox(parent)) < i);
            }
        }

        BoxDescription data = description.getBox("Data1");
        assertEquals("Data", data.get(BoxDescription.TYPE));
        assertEquals("Data", data.get(BoxDescription.MODEL));
        assertArrayEquals(new int[]{100, 100}, data.getLocation());
        assertEquals("study.csv", data.get(StandardDescribers.DATA_SET));
        assertEquals("200 rows, 4 columns, continuous", data.get(StandardDescribers.SHAPE));
        assertEquals("none", data.get(StandardDescribers.MISSING_VALUES));

        BoxDescription knowledge = description.getBox("Knowledge1");
        assertEquals("Tiers and Edges", knowledge.get(BoxDescription.MODEL));
        assertEquals(List.of("Data1"), description.getParentNames(knowledge));
        assertNotNull(knowledge.getBlock(StandardDescribers.KNOWLEDGE_BLOCK));
        assertEquals("/knowledge", knowledge.getBlock(StandardDescribers.KNOWLEDGE_BLOCK).get(0));

        BoxDescription search = description.getBox("Search1");
        assertNull("A box type with one model does not name it.", search.get(BoxDescription.MODEL));
        assertEquals(List.of("Data1", "Knowledge1"), description.getParentNames(search));
        assertEquals("FGES (fges)", search.get(StandardDescribers.ALGORITHM));
        assertNull(search.get(StandardDescribers.TEST));
        assertTrue(search.get(StandardDescribers.SCORE).endsWith("(sem-bic-score)"));
        assertEquals(List.of(
                        new BoxDescription.ChangedParameter("maxDegree", "5", "1000"),
                        new BoxDescription.ChangedParameter("penaltyDiscount", "4.0", "2.0")),
                search.getChangedParameters());
        assertNull("FGES takes no seed, so nothing is said about one.", search.get(StandardDescribers.SEED));

        BoxDescription result = description.getBox("Result graph");
        assertEquals(List.of("Search1"), description.getParentNames(result));
        assertNull("A graph derived from a parent is a result and is not written out.",
                result.getBlock(StandardDescribers.GRAPH_BLOCK));

        BoxDescription drawn = description.getBox("Graph1");
        assertEquals("2 nodes, 1 edge", drawn.get(StandardDescribers.GRAPH));
        assertTrue(String.join("\n", drawn.getBlock(StandardDescribers.GRAPH_BLOCK)).contains("A --> B"));

        BoxDescription note = description.getBox("Note1");
        assertEquals(List.of("Second pass, after dropping the pilot rows.", "", "  End note", "\\ a backslash line",
                "Last line."), note.getBlock(StandardDescribers.NOTE_BLOCK));

        BoxDescription empty = description.getBox("Search2");
        assertEquals("none yet", empty.get(BoxDescription.MODEL));
    }

    /**
     * A search that is set up but not run: a test in place of a score, bootstrapping on, and a seed.
     */
    @Test
    public void testSearchWithTestBootstrappingAndSeed() throws Exception {
        SessionWrapper session = new SessionWrapper(new Session("study.tet"));
        SessionNodeWrapper data = addBox(session, "Data", "Data1", 100, 100);
        SessionNodeWrapper search = addBox(session, "Search", "Search1", 260, 100);
        connect(session, data, search);
        data.getSessionNode().createModel(DataWrapper.class, false);
        ((DataWrapper) data.getSessionNode().getModel()).setDataModel(simulate());
        search.getSessionNode().createModel(GeneralAlgorithmRunner.class, false);
        GeneralAlgorithmRunner runner = (GeneralAlgorithmRunner) search.getSessionNode().getModel();

        // No algorithm chosen yet.
        BoxDescription box = new SessionDescriber().describe(session, DATE).getBox("Search1");
        assertEquals("none chosen yet", box.get(StandardDescribers.ALGORITHM));

        // PC with Fisher Z, alpha changed, bootstrapping on.
        runner.setAlgorithm(AlgorithmFactory.create(Pc.class, new FisherZ(), null));
        runner.getParameters().set(Params.ALPHA, 0.001);
        runner.getParameters().set(Params.NUMBER_RESAMPLING, 100);
        runner.getParameters().set(Params.PERCENT_RESAMPLE_SIZE, 50);

        box = new SessionDescriber().describe(session, DATE).getBox("Search1");
        assertEquals("PC (pc)", box.get(StandardDescribers.ALGORITHM));
        assertTrue(box.get(StandardDescribers.TEST).endsWith("(fisher-z-test)"));
        assertNull(box.get(StandardDescribers.SCORE));
        assertTrue(box.getChangedParameters().contains(new BoxDescription.ChangedParameter("alpha", "0.001", "0.01")));
        assertTrue(box.getChangedParameters()
                .contains(new BoxDescription.ChangedParameter("numberResampling", "100", "0")));
        assertTrue(box.getChangedParameters()
                .contains(new BoxDescription.ChangedParameter("percentResampleSize", "50", "100")));
        assertNotNull("Bootstrapping takes a seed, and it is not set.", box.get(StandardDescribers.SEED));

        // With the seed set, it is listed as a changed parameter and the remark goes away.
        runner.getParameters().set(Params.SEED, 42L);
        box = new SessionDescriber().describe(session, DATE).getBox("Search1");
        assertTrue(box.getChangedParameters().contains(new BoxDescription.ChangedParameter("seed", "42", "-1")));
        assertNull(box.get(StandardDescribers.SEED));
    }

    /**
     * A data set loaded from a file: the file's name and the load settings are given, after the data set's name
     * and before its shape, and they read back.
     */
    @Test
    public void testDataLoadSettings() throws Exception {
        SessionWrapper session = buildSession();
        SessionNode node = ((SessionNodeWrapper) session.getNode("Data1")).getSessionNode();
        DataWrapper wrapper = (DataWrapper) node.getModel();
        assertNull("Nothing is recorded until a file is loaded.", wrapper.getLoadSettings("study.csv"));

        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("File", "study.csv");
        settings.put("First row variable names", "Yes");
        settings.put("Data file type", "Tabular data");
        settings.put("Data type", "Mixed, max discrete categories 3");
        settings.put("Value delimiter", "Comma");
        settings.put("Case column to exclude/ignore", "None");
        settings.put("Comment marker", "//");
        settings.put("Quote character", "\"");
        settings.put("Missing value marker", "*");
        wrapper.setLoadSettings("study.csv", settings);
        wrapper.setLoadSettings("some other file.csv", Map.of("File", "some other file.csv"));

        SessionDescription description = new SessionDescriber().describe(session, DATE);
        BoxDescription box = description.getBox("Data1");
        List<String> keys = new ArrayList<>();

        for (BoxDescription.Item item : box.getItems()) {
            if (item instanceof BoxDescription.Field field) {
                keys.add(field.key());
            }
        }

        assertEquals(List.of("Type", "Model", "Location", "Data set", "File", "First row variable names",
                "Data file type", "Data type", "Value delimiter", "Case column to exclude/ignore", "Comment marker",
                "Quote character", "Missing value marker", "Shape", "Missing values"), keys);
        assertEquals("Comma", box.get("Value delimiter"));
        assertEquals("//", box.get("Comment marker"));
        assertEquals("\"", box.get("Quote character"));
        assertEquals("*", box.get("Missing value marker"));

        String text = SessionDescriptionWriter.toText(description);
        SessionDescriptionParser.Result result = SessionDescriptionParser.parse(text);
        assertEquals(List.of(), result.warnings());
        assertEquals(description, result.description());

        // Replacing the data forgets how the old data were loaded.
        wrapper.clearLoadSettings();
        assertNull(wrapper.getLoadSettings("study.csv"));
        assertNull(new SessionDescriber().describe(session, DATE).getBox("Data1").get("File"));
    }

    /**
     * The load settings are saved with the session and come back with it.
     */
    @Test
    public void testLoadSettingsAreSavedWithTheSession() throws Exception {
        DataWrapper wrapper = new DataWrapper(new Parameters());
        wrapper.setDataModel(simulate());
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("File", "study.csv");
        settings.put("Value delimiter", "Tab");
        settings.put("Missing value marker", "*");
        wrapper.setLoadSettings("study.csv", settings);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(wrapper);
        }

        DataWrapper read;

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            read = (DataWrapper) in.readObject();
        }

        assertEquals(settings, read.getLoadSettings("study.csv"));
        assertEquals(new ArrayList<>(settings.keySet()), new ArrayList<>(read.getLoadSettings("study.csv").keySet()));
        assertNull(read.getLoadSettings("other.csv"));
    }

    @Test
    public void testReadsBackTheSame() throws Exception {
        SessionDescription description = new SessionDescriber().describe(buildSession(), DATE);
        String text = SessionDescriptionWriter.toText(description);

        SessionDescriptionParser.Result result = SessionDescriptionParser.parse(text);
        assertEquals(List.of(), result.warnings());
        assertEquals(description, result.description());
        assertEquals(text, SessionDescriptionWriter.toText(result.description()));
    }

    @Test
    public void testSurvivesDocumentsAndEmail() throws Exception {
        SessionDescription description = new SessionDescriber().describe(buildSession(), DATE);
        String text = SessionDescriptionWriter.toText(description);

        // Windows line endings, with other text before and after.
        String wrapped = "Hi Peter,\r\n\r\nHere is what I ran:\r\n\r\n" + text.replace("\n", "\r\n")
                         + "\r\nBest,\r\nJoe\r\n";
        SessionDescriptionParser.Result result = SessionDescriptionParser.parse(wrapped);
        assertEquals(List.of(), result.warnings());
        assertEquals(description, result.description());

        // Quoted in a reply.
        StringBuilder quoted = new StringBuilder("On Wednesday Joe wrote:\n");

        for (String line : text.split("\n", -1)) {
            quoted.append("> ").append(line).append('\n');
        }

        result = SessionDescriptionParser.parse(quoted.toString());
        assertEquals(List.of(), result.warnings());
        assertEquals(description, result.description());

        // Indented as a whole, as in a document.
        StringBuilder indented = new StringBuilder();

        for (String line : text.split("\n", -1)) {
            indented.append("        ").append(line).append('\n');
        }

        result = SessionDescriptionParser.parse(indented.toString());
        assertEquals(List.of(), result.warnings());
        assertEquals(description, result.description());

        // All indentation stripped. The fields survive; only indentation inside the note is lost.
        StringBuilder stripped = new StringBuilder();

        for (String line : text.split("\n", -1)) {
            stripped.append(line.strip()).append('\n');
        }

        result = SessionDescriptionParser.parse(stripped.toString());
        assertEquals(List.of(), result.warnings());
        SessionDescription read = result.description();
        assertEquals(description.getBox("Search1"), read.getBox("Search1"));
        assertEquals(description.getBox("Knowledge1"), read.getBox("Knowledge1"));
        assertEquals(description.getBox("Graph1"), read.getBox("Graph1"));
        assertEquals(List.of("Second pass, after dropping the pilot rows.", "", "End note", "\\ a backslash line",
                "Last line."), read.getBox("Note1").getBlock(StandardDescribers.NOTE_BLOCK));
    }

    @Test
    public void testReportsWhatItCannotRead() throws Exception {
        SessionDescription description = new SessionDescriber().describe(buildSession(), DATE);
        String text = SessionDescriptionWriter.toText(description);

        // Cut short inside the note.
        String cut = text.substring(0, text.indexOf("Last line."));
        SessionDescriptionParser.Result result = SessionDescriptionParser.parse(cut);
        assertEquals(2, result.warnings().size());
        assertEquals(description.getBox("Search1"), result.description().getBox("Search1"));

        // A stray line.
        result = SessionDescriptionParser.parse(text.replace("[Box Search1]", "what is this\n[Box Search1]"));
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("what is this"));
        assertEquals(description, result.description());

        // Not a description at all.
        try {
            SessionDescriptionParser.parse("Dear Peter,\nnothing to see here.\n");
            fail("Text with no description in it should be refused.");
        } catch (IllegalArgumentException expected) {
            // As it should be.
        }
    }

    @Test
    public void testDescribingChangesNothing() throws Exception {
        SessionWrapper session = buildSession();
        SessionNode search = ((SessionNodeWrapper) session.getNode("Search1")).getSessionNode();
        Parameters parameters = ((GeneralAlgorithmRunner) search.getModel()).getParameters();
        Set<String> before = new HashSet<>(parameters.getParametersNames());
        String usedBefore = parameters.toString();

        new SessionDescriber().describe(session, DATE);

        assertEquals(before, new HashSet<>(parameters.getParametersNames()));
        assertEquals(usedBefore, parameters.toString());
    }

    @Test
    public void testParentNamesWithCommas() {
        BoxDescription a = new BoxDescription.Builder("Data, raw").build();
        BoxDescription b = new BoxDescription.Builder("Data").build();
        BoxDescription c = new BoxDescription.Builder("Search1")
                .field(BoxDescription.PARENTS, "Data, raw, Data").build();
        SessionDescription description = new SessionDescription(List.of(), List.of(a, b, c));
        assertEquals(List.of("Data, raw", "Data"), description.getParentNames(c));
    }

    @Test
    public void testNumbersReadPlainly() {
        assertEquals("0.0001", StandardDescribers.valueText(1.0E-4));
        assertEquals("2.0", StandardDescribers.valueText(2.0));
        assertEquals("0.05", StandardDescribers.valueText(0.05));
        assertEquals("-1", StandardDescribers.valueText(-1L));
        assertEquals("true", StandardDescribers.valueText(true));
    }
}
