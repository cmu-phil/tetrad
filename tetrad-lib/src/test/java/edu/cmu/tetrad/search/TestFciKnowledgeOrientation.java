package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.util.Matrix;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the semantics of background knowledge in FCI-style orientation.
 * <p>
 * Pins three behaviors:
 * <ol>
 * <li>Tier (forbidden-edge) knowledge is enforced by FCI and BFCI regardless of the excludeSelectionBias setting.
 * Previously, fciOrientbk skipped all forbidden-edge enforcement whenever selection bias was allowed, silently
 * discarding tier knowledge (tiers compile to forbidden edges only).</li>
 * <li>A forbidden edge x --&gt; y does not veto a collider arrowhead at y. Previously, isArrowheadAllowed vetoed the
 * arrowhead at y whenever x --&gt; y was forbidden; under tiers this deleted correct collider arrowheads at
 * earlier-tier variables and erased the record of possible latent confounding, yielding graphs whose entailed
 * independencies the data reject.</li>
 * <li>The possible-dsep edge-removal step in FCI does not condition on variables in a strictly later tier than both
 * endpoints, since no admissible separating set contains such a variable.</li>
 * </ol>
 * All tests use exact analytic covariance matrices, so they are deterministic.
 */
public class TestFciKnowledgeOrientation {

    private static CovarianceMatrix cov(String[] names, double[][] m, int n) {
        List<Node> nodes = new ArrayList<>();
        for (String name : names) nodes.add(new ContinuousVariable(name));
        return new CovarianceMatrix(nodes, new Matrix(m), n);
    }

    private static Knowledge tiers(String[][] tiers) {
        Knowledge k = new Knowledge();
        for (int i = 0; i < tiers.length; i++) {
            for (String v : tiers[i]) k.addToTier(i, v);
        }
        return k;
    }

    private static Edge edge(Graph g, String a, String b) {
        return g.getEdge(g.getNode(a), g.getNode(b));
    }

    private static void assertMarks(Graph g, String a, String b, Endpoint atA, Endpoint atB) {
        Edge e = edge(g, a, b);
        assertTrue("Expected an edge between " + a + " and " + b + " in " + g, e != null);
        assertEquals("Wrong mark at " + a + " on " + a + "-" + b + " in " + g, atA,
                e.getProximalEndpoint(g.getNode(a)));
        assertEquals("Wrong mark at " + b + " on " + a + "-" + b + " in " + g, atB,
                e.getProximalEndpoint(g.getNode(b)));
    }

    /**
     * Exact covariance for the chain a -&gt; b -&gt; c with unit coefficients and unit noise variances.
     */
    private static CovarianceMatrix chainCov() {
        return cov(new String[]{"a", "b", "c"}, new double[][]{
                {1, 1, 1},
                {1, 2, 2},
                {1, 2, 3}}, 2000);
    }

    /**
     * Tier knowledge must be enforced by FCI whether or not selection bias is excluded. Chain a -&gt; b -&gt; c with
     * (true) tiers a &lt; b &lt; c should give a o-&gt; b and b --&gt; c in both settings.
     */
    @Test
    public void testFciHonorsTiersRegardlessOfSelectionBiasFlag() throws InterruptedException {
        for (boolean flag : new boolean[]{false, true}) {
            Fci fci = new Fci(new IndTestFisherZ(chainCov(), 0.01));
            fci.setStable(true);
            fci.setKnowledge(tiers(new String[][]{{"a"}, {"b"}, {"c"}}));
            fci.setExcludeSelectionBias(flag);
            Graph g = fci.search();

            assertMarks(g, "a", "b", Endpoint.CIRCLE, Endpoint.ARROW);
            assertMarks(g, "b", "c", Endpoint.TAIL, Endpoint.ARROW);
        }
    }

    /**
     * Same as above for BFCI, which routes knowledge orientation through the same FciOrient machinery via StarFci.
     */
    @Test
    public void testBfciHonorsTiersRegardlessOfSelectionBiasFlag() throws InterruptedException {
        for (boolean flag : new boolean[]{false, true}) {
            SemBicScore score = new SemBicScore(chainCov());
            score.setPenaltyDiscount(1.0);
            Bfci bfci = new Bfci(new IndTestFisherZ(chainCov(), 0.01), score);
            bfci.setBossUseBes(true);
            bfci.setKnowledge(tiers(new String[][]{{"a"}, {"b"}, {"c"}}));
            bfci.setExcludeSelectionBias(flag);
            Graph g = bfci.search();

            assertMarks(g, "a", "b", Endpoint.CIRCLE, Endpoint.ARROW);
            assertMarks(g, "b", "c", Endpoint.TAIL, Endpoint.ARROW);
        }
    }

    /**
     * Generating model a -&gt; b &lt;- L -&gt; c, b -&gt; d with L unmeasured; true MAG over the measured variables is
     * a -&gt; b, b &lt;-&gt; c, b -&gt; d. The (true) tiers a &lt; b &lt; c &lt; d license an arrowhead at the c end of
     * the b-c edge and say nothing about the b end; the collider a *-&gt; b &lt;-* c supplies the arrowhead at the b
     * end, giving b &lt;-&gt; c. Previously the forbidden edge c --&gt; b vetoed the collider arrowhead at b, the
     * confounding record was erased, and the output entailed a _||_ c | b, which the data reject. Exact covariance for
     * b = a + L + 0.5 e, c = L + 0.5 e, d = b + 0.5 e with a, L standard normal.
     */
    @Test
    public void testForbiddenEdgeDoesNotDeleteColliderArrowhead() throws InterruptedException {
        CovarianceMatrix cov = cov(new String[]{"a", "b", "c", "d"}, new double[][]{
                {1, 1, 0, 1},
                {1, 2.25, 1, 2.25},
                {0, 1, 1.25, 1},
                {1, 2.25, 1, 2.5}}, 3000);

        Fci fci = new Fci(new IndTestFisherZ(cov, 0.01));
        fci.setStable(true);
        fci.setKnowledge(tiers(new String[][]{{"a"}, {"b"}, {"c"}, {"d"}}));
        fci.setExcludeSelectionBias(true);
        Graph g = fci.search();

        assertMarks(g, "a", "b", Endpoint.CIRCLE, Endpoint.ARROW);
        assertMarks(g, "b", "c", Endpoint.ARROW, Endpoint.ARROW); // the confounding record: b <-> c
        assertMarks(g, "b", "d", Endpoint.TAIL, Endpoint.ARROW);
    }

    /**
     * Unit semantics of isArrowheadAllowed: a forbidden x --&gt; y does not contradict an arrowhead at y (the
     * arrowhead asserts y is not an ancestor of x and says nothing about whether x causes y), whereas a required
     * y --&gt; x does.
     */
    @Test
    public void testIsArrowheadAllowedKnowledgeSemantics() {
        Node b = new GraphNode("b");
        Node c = new GraphNode("c");
        Graph g = new EdgeListGraph(List.of(b, c));
        g.addEdge(new Edge(b, c, Endpoint.CIRCLE, Endpoint.CIRCLE));

        Knowledge forbid = new Knowledge();
        forbid.setForbidden("c", "b");
        assertTrue("Forbidding c->b must not veto an arrowhead at b",
                FciOrient.isArrowheadAllowed(c, b, g, forbid));

        Knowledge require = new Knowledge();
        require.setRequired("b", "c");
        assertFalse("Requiring b->c must veto an arrowhead at b",
                FciOrient.isArrowheadAllowed(c, b, g, require));
    }

    /**
     * Timberlake and Williams (1984) correlations, N = 72, tiers fi &lt; en &lt; {po, cv}. Under these tiers, cv lies
     * in a strictly later tier than both fi and en, so no admissible separating set for the fi-en edge contains cv;
     * the possible-dsep step must not delete fi-en by conditioning on cv. Previously (once tier arrowheads were placed
     * correctly) possible-dsep tested fi _||_ en given a set containing cv and removed the edge, yielding a model the
     * data reject while the model retaining the edge fits.
     */
    @Test
    public void testPossibleDsepDoesNotConditionOnLaterTierVariables() throws InterruptedException {
        CovarianceMatrix cov = cov(new String[]{"po", "fi", "en", "cv"}, new double[][]{
                {1.000, -0.175, -0.480, 0.868},
                {-0.175, 1.000, 0.330, -0.391},
                {-0.480, 0.330, 1.000, -0.430},
                {0.868, -0.391, -0.430, 1.000}}, 72);

        Fci fci = new Fci(new IndTestFisherZ(cov, 0.05));
        fci.setStable(true);
        fci.setKnowledge(tiers(new String[][]{{"fi"}, {"en"}, {"po", "cv"}}));
        fci.setExcludeSelectionBias(true);
        Graph g = fci.search();

        assertTrue("The fi-en edge must not be deleted on a later-tier conditioning set",
                g.isAdjacentTo(g.getNode("fi"), g.getNode("en")));
    }
}
