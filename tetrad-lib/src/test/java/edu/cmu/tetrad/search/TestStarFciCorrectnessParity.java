package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Parity tests for the merge of the {@code BfciBryan} and {@code BfciBryanScore} gates into {@link StarFci}.
 *
 * <p>Each of the three correctness options of {@link Bfci} (through {@link StarFci#setCorrectnessOption}) is run
 * against the fork that originally implemented it, on the same data with the same BOSS settings, and the PAGs must be
 * identical: option 1 against {@code BfciBryan} with the I-map gate on, option 2 against {@code BfciBryanScore}, and
 * option 3 against {@code BfciBryan} with the gate off. The runs are repeated with tier knowledge and a required
 * edge, and the output is checked to honor both.
 *
 * <p>These are regression tests for the merge, not evaluations of the algorithms: identity with the fork is the
 * property being certified.
 *
 * @author josephramsey
 */
public class TestStarFciCorrectnessParity {

    private static final int NUM_MEASURED = 10;
    private static final int NUM_LATENT = 3;
    private static final int NUM_EDGES = 17;
    private static final int SAMPLE_SIZE = 1000;
    private static final double ALPHA = 0.01;
    private static final double PENALTY = 2.0;
    private static final double IMAP_ALPHA = 0.001;
    private static final int REPS = 8;

    /**
     * One simulated problem: the true DAG with latents, the measured data, and the test and score built on it.
     */
    private record Problem(Graph trueDag, DataSet data, IndTestFisherZ test, SemBicScore score) {
    }

    private static Problem problem(long seed) {
        RandomUtil.getInstance().setSeed(seed);
        Graph dag = RandomGraph.randomDag(NUM_MEASURED + NUM_LATENT, NUM_LATENT, NUM_EDGES, 100, 100, 100, false);
        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data;
        try {
            data = im.simulateData(SAMPLE_SIZE, false);
        } catch (java.text.ParseException e) {
            throw new IllegalStateException(e);
        }

        // Drop the latent columns, as a user would never have them.
        List<Node> keep = new ArrayList<>();
        for (Node n : data.getVariables()) {
            Node inDag = dag.getNode(n.getName());
            if (inDag == null || inDag.getNodeType() != NodeType.LATENT) keep.add(n);
        }
        data = data.subsetColumns(keep);

        IndTestFisherZ test = new IndTestFisherZ(data, ALPHA);
        SemBicScore score = new SemBicScore(data, PENALTY, true);
        return new Problem(dag, data, test, score);
    }

    /**
     * Two tiers from the true DAG's causal order over the measured variables, plus one required edge taken from the
     * true DAG whose endpoints are both measured.
     */
    private static Knowledge knowledgeFor(Problem p) {
        Knowledge k = new Knowledge();
        List<Node> order = p.trueDag().paths().getValidOrder(p.trueDag().getNodes(), true);
        List<Node> measured = new ArrayList<>();
        for (Node n : order) if (n.getNodeType() == NodeType.MEASURED) measured.add(n);

        int half = measured.size() / 2;
        for (int i = 0; i < measured.size(); i++) {
            k.addToTier(i < half ? 0 : 1, measured.get(i).getName());
        }

        for (Edge e : p.trueDag().getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            if (a.getNodeType() == NodeType.MEASURED && b.getNodeType() == NodeType.MEASURED) {
                // A DAG edge: the parent is whichever endpoint the edge points away from.
                Node tail = p.trueDag().isParentOf(a, b) ? a : b;
                Node head = tail == a ? b : a;
                if (!k.isForbidden(tail.getName(), head.getName())) {
                    k.setRequired(tail.getName(), head.getName());
                    break;
                }
            }
        }
        return k;
    }

    private static Graph runBfci(Problem p, int option, Knowledge k) throws InterruptedException {
        Bfci s = new Bfci(p.test(), p.score());
        s.setNumStarts(1);
        s.setBossUseBes(true);
        s.setNumThreads(1);
        s.setCorrectnessOption(option, p.score(), IMAP_ALPHA);
        if (k != null) s.setKnowledge(k);
        return s.search();
    }

    private static Graph runBryan(Problem p, boolean imapGate, Knowledge k) throws InterruptedException {
        BfciBryan s = new BfciBryan(p.test(), p.score());
        s.setNumStarts(1);
        s.setBossUseBes(true);
        s.setNumThreads(1);
        s.setRequireImapStates(imapGate);
        s.setUseSaturatingStep(!imapGate);
        s.setImapAlpha(IMAP_ALPHA);
        if (k != null) s.setKnowledge(k);
        return s.search();
    }

    private static Graph runBryanScore(Problem p, Knowledge k) throws InterruptedException {
        BfciBryanScore s = new BfciBryanScore(p.test(), p.score());
        s.setNumStarts(1);
        s.setBossUseBes(true);
        s.setNumThreads(1);
        if (k != null) s.setKnowledge(k);
        return s.search();
    }

    private static void assertSameGraph(String what, Graph expected, Graph actual) {
        assertEquals(what + ": PAGs differ.\nexpected:\n" + expected + "\nactual:\n" + actual, expected, actual);
    }

    private static void assertHonorsKnowledge(String what, Graph g, Knowledge k) {
        for (Edge e : g.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            // No edge may be oriented against a forbidden direction.
            if (g.isParentOf(e.getNode1(), e.getNode2())) {
                assertFalse(what + ": " + a + " -> " + b + " is forbidden", k.isForbidden(a, b));
            }
            if (g.isParentOf(e.getNode2(), e.getNode1())) {
                assertFalse(what + ": " + b + " -> " + a + " is forbidden", k.isForbidden(b, a));
            }
        }
        // Every required edge must be present as a directed edge.
        for (Node x : g.getNodes()) {
            for (Node y : g.getNodes()) {
                if (x == y) continue;
                if (k.isRequired(x.getName(), y.getName())) {
                    assertTrue(what + ": required " + x + " -> " + y + " missing", g.isParentOf(x, y));
                }
            }
        }
    }

    @Test
    public void testOption1MatchesBryanImapGate() throws InterruptedException {
        for (int r = 0; r < REPS; r++) {
            Problem p = problem(1000 + r);
            assertSameGraph("option 1, rep " + r, runBryan(p, true, null), runBfci(p, 1, null));
        }
    }

    @Test
    public void testOption2MatchesBryanScore() throws InterruptedException {
        for (int r = 0; r < REPS; r++) {
            Problem p = problem(2000 + r);
            assertSameGraph("option 2, rep " + r, runBryanScore(p, null), runBfci(p, 2, null));
        }
    }

    @Test
    public void testOption3MatchesBryanNoGate() throws InterruptedException {
        for (int r = 0; r < REPS; r++) {
            Problem p = problem(3000 + r);
            assertSameGraph("option 3, rep " + r, runBryan(p, false, null), runBfci(p, 3, null));
        }
    }

    @Test
    public void testAllOptionsWithKnowledge() throws InterruptedException {
        for (int r = 0; r < REPS; r++) {
            Problem p = problem(4000 + r);
            Knowledge k = knowledgeFor(p);

            Graph g1 = runBfci(p, 1, k);
            assertSameGraph("option 1 with knowledge, rep " + r, runBryan(p, true, k), g1);
            assertHonorsKnowledge("option 1, rep " + r, g1, k);

            Graph g2 = runBfci(p, 2, k);
            assertSameGraph("option 2 with knowledge, rep " + r, runBryanScore(p, k), g2);
            assertHonorsKnowledge("option 2, rep " + r, g2, k);

            Graph g3 = runBfci(p, 3, k);
            assertSameGraph("option 3 with knowledge, rep " + r, runBryan(p, false, k), g3);
            assertHonorsKnowledge("option 3, rep " + r, g3, k);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOption2RefusesNonSemBicScore() {
        Problem p = problem(5000);
        Bfci s = new Bfci(p.test(), p.score());
        // Any Score that is not a SemBicScore must be refused for the score step.
        edu.cmu.tetrad.search.score.Score notSemBic = new edu.cmu.tetrad.search.score.GraphScore(p.trueDag());
        s.setCorrectnessOption(2, notSemBic, IMAP_ALPHA);
    }
}
