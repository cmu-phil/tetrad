package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test class for verifying the functionality of operations related to junction tree updates in Bayesian networks.
 * This class contains methods for evaluating propagation of evidence, handling interventions, and other utility
 * functions related to Bayesian inference and updates.
 * <p>
 * Fields:
 * - EPS: Permissible error tolerance for comparing probability distributions.
 * <p>
 * Methods:
 * - idx(BayesIm im, String name): Retrieves the index of a node in the Bayesian network by its name.
 * - assertDistClose(double[] a, double[] b, double eps): Verifies that two probability distributions are within a
 * specified tolerance.
 * - distDiff(double[] a, double[] b): Computes the maximum absolute difference between two distributions.
 * - evidenceXEquals(BayesIm im, String varName, int value): Creates evidence to constrain a variable to a specific
 * single value (hard evidence).
 * - manipulateXToValue(BayesIm im, Evidence e, String varName, int value): Marks a variable as manipulated and
 * sets its do() distribution to a point mass at a specified value.
 * - assertCptEqualByName(BayesIm src, BayesIm dst, String nodeName, double eps): Compares two conditional probability
 * tables (CPTs) for a node by its name, avoiding dependencies on row order.
 * - buildToyBayesIm(): Constructs a small binary Bayesian network with non-symmetric conditional probability
 * tables (CPTs) for testing purposes.
 * - setBinaryCptOneParent(BayesIm im, int child, int parent, int parentVal, double p0): Configures the conditional
 * probability table (CPT) for a binary child node with one binary parent, based on a specified probability.
 * - testEvidencePropagatesUpAndDown_andUpdatersAgree(): Verifies that evidence propagation occurs correctly both
 * upward and downward in the Bayesian network structure, with consistency across updaters.
 * - testDoInterventionRemovesParents_preservesOtherCpts_andUpdatersAgree(): Checks the behavior of a do() intervention
 * to ensure that incoming edges are removed, non-intervention CPTs are preserved, and agreement exists across
 * inference methods.
 */
public final class JunctionTreeUpdaterTest {

    private static final double EPS = 1e-10;

    /**
     * Test class for verifying the functionality of the JunctionTreeUpdater component,
     * utilized for probabilistic reasoning and evidence propagation in Bayesian networks.
     * This class contains unit tests and utility methods designed to test the correct
     * behavior of evidence propagation, intervention operations, and agreement between
     * different methods of inference.
     */
    public JunctionTreeUpdaterTest() {
    }

    private static int idx(BayesIm im, String name) {
        Node n = im.getNode(name);
        assertNotNull("No node named " + name, n);
        return im.getNodeIndex(n);
    }

    private static void assertDistClose(double[] a, double[] b, double eps) {
        assertEquals("Different lengths", a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals("Mismatch at " + i, a[i], b[i], eps);
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private static double distDiff(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += Math.abs(a[i] - b[i]);
        return s;
    }

    /**
     * Evidence that forces X=name to a single category (hard evidence).
     * Adjust this if your Evidence API differs.
     */
    private static Evidence evidenceXEquals(BayesIm im, String varName, int value) {
        Evidence e = Evidence.tautology(im);

        int v = idx(im, varName);

        // Common Tetrad pattern: evidence stores allowed categories in a Proposition
        Proposition p = e.getProposition();
        int k = e.getNumCategories(v);

        for (int c = 0; c < k; c++) {
            if (c != value) p.removeCategory(v, c);
        }

        // If your Evidence clones proposition internally, you may need:
        // e.setProposition(p);  // only if such setter exists.

        return e;
    }

    /**
     * Mark variable as manipulated and set its do() distribution to a point mass at value.
     * Adjust to match your Evidence API (some versions have setManipulated / setManipulated(i,true)).
     */
    private static Evidence manipulateXToValue(BayesIm im, Evidence e, String varName, int value) {
        int v = idx(im, varName);

        // 1) mark manipulated
        e.setManipulated(v, true); // <-- if your API differs, adapt here

        // 2) restrict allowed categories to the do-value
        Proposition p = e.getProposition();
        int k = e.getNumCategories(v);
        for (int c = 0; c < k; c++) {
            if (c != value) p.removeCategory(v, c);
        }

        return e;
    }

    /**
     * Compare two CPTs for the node by name, matching parent-value rows by parent names.
     * This avoids row-order dependence.
     */
    private static void assertCptEqualByName(BayesIm src, BayesIm dst, String nodeName, double eps) {
        int s = idx(src, nodeName);
        int d = idx(dst, nodeName);

        assertEquals("Category mismatch for " + nodeName,
                src.getNumColumns(s), dst.getNumColumns(d));

        // parents must match by name (for non-manipulated nodes)
        int[] sp = src.getParents(s);
        int[] dp = dst.getParents(d);
        assertEquals("Parent count mismatch for " + nodeName, sp.length, dp.length);

        // map dst parent order -> src parent order
        int[] dstPosToSrcPos = new int[dp.length];
        for (int i = 0; i < dp.length; i++) {
            String pName = dst.getNode(dp[i]).getName();
            int found = -1;
            for (int j = 0; j < sp.length; j++) {
                if (src.getNode(sp[j]).getName().equals(pName)) {
                    found = j;
                    break;
                }
            }
            assertTrue("Missing parent " + pName + " for " + nodeName, found >= 0);
            dstPosToSrcPos[i] = found;
        }

        // For each dst row, build src parent-values and compare row
        for (int dr = 0; dr < dst.getNumRows(d); dr++) {
            int[] dpv = dst.getParentValues(d, dr);

            int[] spv = new int[sp.length];
            for (int i = 0; i < dp.length; i++) {
                spv[dstPosToSrcPos[i]] = dpv[i];
            }

            int sr = src.getRowIndex(s, spv);

            for (int c = 0; c < dst.getNumColumns(d); c++) {
                assertEquals("CPT mismatch for " + nodeName + " row " + dr + " col " + c,
                        src.getProbability(s, sr, c),
                        dst.getProbability(d, dr, c),
                        eps);
            }
        }
    }

    /**
     * Build a small binary Bayes net with non-symmetric CPTs.
     * Adjust the CPT filling to match your MlBayesIm constructors if needed.
     */
    private static BayesIm buildToyBayesIm() {
        // Variables
        DiscreteVariable X1 = new DiscreteVariable("X1", 2);
        DiscreteVariable X2 = new DiscreteVariable("X2", 2);
        DiscreteVariable X3 = new DiscreteVariable("X3", 2);
        DiscreteVariable X4 = new DiscreteVariable("X4", 2);
        DiscreteVariable X5 = new DiscreteVariable("X5", 2);

        // Graph
        Graph g = new EdgeListGraph();
        g.addNode(X1);
        g.addNode(X2);
        g.addNode(X3);
        g.addNode(X4);
        g.addNode(X5);

        g.addDirectedEdge(X1, X2);
        g.addDirectedEdge(X2, X3);
        g.addDirectedEdge(X2, X4);
        g.addDirectedEdge(X3, X5);

        Dag dag = new Dag(g);
        BayesPm pm = new BayesPm(dag);

        // Create IM with manual init (so we can set CPTs)
        BayesIm im = new MlBayesIm(pm, MlBayesIm.InitializationMethod.MANUAL);

        int iX1 = idx(im, "X1");
        int iX2 = idx(im, "X2");
        int iX3 = idx(im, "X3");
        int iX4 = idx(im, "X4");
        int iX5 = idx(im, "X5");

        // X1 prior: P(X1=1)=0.3
        im.setProbability(iX1, 0, 0, 0.7);
        im.setProbability(iX1, 0, 1, 0.3);

        // X2 | X1
        // if X1=0: P(X2=1)=0.2
        // if X1=1: P(X2=1)=0.8
        setBinaryCptOneParent(im, iX2, iX1, 0, 0.8); // row for X1=0 => p1=0.2
        setBinaryCptOneParent(im, iX2, iX1, 1, 0.2); // row for X1=1 => p1=0.8

        // X3 | X2
        // if X2=0: P(X3=1)=0.1
        // if X2=1: P(X3=1)=0.9
        setBinaryCptOneParent(im, iX3, iX2, 0, 0.9); // row X2=0 => p1=0.1
        setBinaryCptOneParent(im, iX3, iX2, 1, 0.1); // row X2=1 => p1=0.9

        // X4 | X2
        // if X2=0: P(X4=1)=0.3
        // if X2=1: P(X4=1)=0.6
        setBinaryCptOneParent(im, iX4, iX2, 0, 0.7); // row X2=0 => p1=0.3
        setBinaryCptOneParent(im, iX4, iX2, 1, 0.4); // row X2=1 => p1=0.6

        // X5 | X3
        // if X3=0: P(X5=1)=0.2
        // if X3=1: P(X5=1)=0.7
        setBinaryCptOneParent(im, iX5, iX3, 0, 0.8); // row X3=0 => p1=0.2
        setBinaryCptOneParent(im, iX5, iX3, 1, 0.3); // row X3=1 => p1=0.7

        im.normalizeAll();
        return im;
    }

    /**
     * Convenience: set CPT for a binary child with ONE binary parent.
     * <p>
     * We set: P(child=0|parent=pVal)=p0, P(child=1|parent=pVal)=1-p0.
     * <p>
     * The mapping from parent value -> row index is via getRowIndex(child, parentValues).
     */
    private static void setBinaryCptOneParent(BayesIm im, int child, int parent, int parentVal, double p0) {
        int[] parents = im.getParents(child);
        assertEquals("Expected exactly 1 parent", 1, parents.length);
        assertEquals("Unexpected parent", parent, parents[0]);

        int row = im.getRowIndex(child, new int[]{parentVal});
        im.setProbability(child, row, 0, p0);
        im.setProbability(child, row, 1, 1.0 - p0);
    }

    /**
     * Structure:
     * X1 -> X2 -> X3 -> X5
     * \-> X4
     * <p>
     * Evidence: X3 = 1 should propagate:
     * - Up: X2 and X1 change
     * - Down: X5 changes
     * - Side child: X4 changes because X2 changes
     */
    @Test
    public void testEvidencePropagatesUpAndDown_andUpdatersAgree() {
        BayesIm im = buildToyBayesIm();

        int X1 = idx(im, "X1");
        int X2 = idx(im, "X2");
        int X3 = idx(im, "X3");
        int X4 = idx(im, "X4");
        int X5 = idx(im, "X5");

        // --- PRIOR (tautology evidence): updaters must agree ---
        Evidence taut = Evidence.tautology(im);

        RowSummingExactUpdater rsPrior = new RowSummingExactUpdater(im, taut);
        JunctionTreeUpdater jtPrior = new JunctionTreeUpdater(im, taut);

        assertDistClose(rsPrior.calculateUpdatedMarginals(X1), jtPrior.calculateUpdatedMarginals(X1), EPS);
        assertDistClose(rsPrior.calculateUpdatedMarginals(X2), jtPrior.calculateUpdatedMarginals(X2), EPS);
        assertDistClose(rsPrior.calculateUpdatedMarginals(X3), jtPrior.calculateUpdatedMarginals(X3), EPS);
        assertDistClose(rsPrior.calculateUpdatedMarginals(X4), jtPrior.calculateUpdatedMarginals(X4), EPS);
        assertDistClose(rsPrior.calculateUpdatedMarginals(X5), jtPrior.calculateUpdatedMarginals(X5), EPS);

        double[] priorX1 = jtPrior.calculateUpdatedMarginals(X1);
        double[] priorX2 = jtPrior.calculateUpdatedMarginals(X2);
        double[] priorX4 = jtPrior.calculateUpdatedMarginals(X4);
        double[] priorX5 = jtPrior.calculateUpdatedMarginals(X5);

        // --- POSTERIOR: evidence X3=1 ---
        Evidence e = evidenceXEquals(im, "X3", 1);

        RowSummingExactUpdater rsPost = new RowSummingExactUpdater(im, e);
        JunctionTreeUpdater jtPost = new JunctionTreeUpdater(im, e);

        // (4) Agreement between methods (spot + whole-vector)
        assertDistClose(rsPost.calculateUpdatedMarginals(X1), jtPost.calculateUpdatedMarginals(X1), EPS);
        assertDistClose(rsPost.calculateUpdatedMarginals(X2), jtPost.calculateUpdatedMarginals(X2), EPS);
        assertDistClose(rsPost.calculateUpdatedMarginals(X3), jtPost.calculateUpdatedMarginals(X3), EPS);
        assertDistClose(rsPost.calculateUpdatedMarginals(X4), jtPost.calculateUpdatedMarginals(X4), EPS);
        assertDistClose(rsPost.calculateUpdatedMarginals(X5), jtPost.calculateUpdatedMarginals(X5), EPS);

        double[] postX1 = jtPost.calculateUpdatedMarginals(X1);
        double[] postX2 = jtPost.calculateUpdatedMarginals(X2);
        double[] postX4 = jtPost.calculateUpdatedMarginals(X4);
        double[] postX5 = jtPost.calculateUpdatedMarginals(X5);

        // (1) Ancestors should change: X1, X2
        assertTrue("Ancestor X2 marginal should change under evidence X3=1",
                distDiff(priorX2, postX2) > 1e-6);
        assertTrue("Ancestor X1 marginal should change under evidence X3=1",
                distDiff(priorX1, postX1) > 1e-6);

        // (2) Descendants/children should change: X5, and X4 via X2
        assertTrue("Descendant X5 marginal should change under evidence X3=1",
                distDiff(priorX5, postX5) > 1e-6);
        assertTrue("Child of ancestor X4 marginal should change under evidence X3=1",
                distDiff(priorX4, postX4) > 1e-6);

        // Also sanity: evidence variable should be (nearly) degenerate at 1.
        double[] postX3 = jtPost.calculateUpdatedMarginals(X3);
        assertEquals(0.0, postX3[0], 1e-9);
        assertEquals(1.0, postX3[1], 1e-9);
    }

    /**
     * (3) do(X): incoming edges removed, other CPTs preserved, manipulated CPT set to intervention distribution.
     * (4) Agreement between methods under manipulation too.
     */
    @Test
    public void testDoInterventionRemovesParents_preservesOtherCpts_andUpdatersAgree() {
        BayesIm im = buildToyBayesIm();

        int X2 = idx(im, "X2");
        int X3 = idx(im, "X3");
        int X4 = idx(im, "X4");

        // do(X2 = 1)
        Evidence e = Evidence.tautology(im);
        e = manipulateXToValue(im, e, "X2", 1);

        RowSummingExactUpdater rs = new RowSummingExactUpdater(im, e);
        JunctionTreeUpdater jt = new JunctionTreeUpdater(im, e);

        // (3a) incoming edges removed: manipulated X2 must have no parents in manipulated graph
        BayesIm rsManip = rs.getManipulatedBayesIm();
        BayesIm jtManip = jt.getManipulatedBayesIm();

        int rsX2 = idx(rsManip, "X2");
        int jtX2 = idx(jtManip, "X2");

        assertEquals("Manipulated X2 should have 0 parents (row count 1) in RowSumming manipulated IM",
                1, rsManip.getNumRows(rsX2));
        assertEquals("Manipulated X2 should have 0 parents (row count 1) in JT manipulated IM",
                1, jtManip.getNumRows(jtX2));

        // (3b) manipulated distribution is point-mass at 1
        assertEquals(0.0, rsManip.getProbability(rsX2, 0, 0), 0.0);
        assertEquals(1.0, rsManip.getProbability(rsX2, 0, 1), 0.0);

        assertEquals(0.0, jtManip.getProbability(jtX2, 0, 0), 0.0);
        assertEquals(1.0, jtManip.getProbability(jtX2, 0, 1), 0.0);

        // (3c) CPTs of non-manipulated nodes are preserved (in manipulated IM) up to identity-by-name mapping.
        // We'll check X3 and X4 tables exactly (these have parents; good stress).
        assertCptEqualByName(im, rsManip, "X3", EPS);
        assertCptEqualByName(im, rsManip, "X4", EPS);

        assertCptEqualByName(im, jtManip, "X3", EPS);
        assertCptEqualByName(im, jtManip, "X4", EPS);

        // (4) Updaters agree on post-do marginals too.
        assertDistClose(rs.calculateUpdatedMarginals(X2), jt.calculateUpdatedMarginals(X2), EPS);
        assertDistClose(rs.calculateUpdatedMarginals(X3), jt.calculateUpdatedMarginals(X3), EPS);
        assertDistClose(rs.calculateUpdatedMarginals(X4), jt.calculateUpdatedMarginals(X4), EPS);

        // And sanity: X2 marginal after do should be degenerate at 1.
        double[] m2 = jt.calculateUpdatedMarginals(X2);
        assertEquals(0.0, m2[0], 1e-12);
        assertEquals(1.0, m2[1], 1e-12);
    }
}