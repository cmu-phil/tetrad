///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.BossCcd;
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Regression tests for BOSS-CCD and for CCD's Step-B sepset semantics on cyclic models.
 *
 * <p>Ground-truth model: exogenous A -&gt; X, B -&gt; Y; a 2-cycle X &lt;-&gt; Y; Y -&gt; Z; and an acyclic chain
 * P -&gt; A, P -&gt; Q -&gt; R. Richardson's correct output for this class includes the colliders A -&gt; X &lt;- B and
 * A -&gt; Y &lt;- B with dotted underlines (the cycle marks), the virtual edges A*-*Y and B*-*X, tails on X-Y, and an
 * unoriented chain.</p>
 *
 * <p>These tests pin two behaviors: (1) CCD's Step B must use minimal-cardinality-first sepsets. For cyclic models a
 * node can be in some valid sepsets of a pair but not others (here both {} and {X, Y} separate A from B); max-p sepset
 * selection tends to choose the larger sets, converting the crucial colliders into underlines so that Step D never
 * produces a dotted underline and no cycle is ever detected. (2) The BOSS superstructure path through
 * {@link BossCcd} recovers the same cyclic PAG from a covariance matrix alone.</p>
 */
public class TestBossCcd {

    /**
     * Builds the ground-truth cyclic graph over A, B, X, Y, Z, P, Q, R.
     */
    private static Graph trueGraph() {
        Node a = new GraphNode("A");
        Node b = new GraphNode("B");
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");
        Node p = new GraphNode("P");
        Node q = new GraphNode("Q");
        Node r = new GraphNode("R");

        Graph g = new EdgeListGraph(List.of(a, b, x, y, z, p, q, r));
        g.addDirectedEdge(a, x);
        g.addDirectedEdge(b, y);
        g.addDirectedEdge(x, y);
        g.addDirectedEdge(y, x);   // 2-cycle
        g.addDirectedEdge(y, z);
        g.addDirectedEdge(p, a);
        g.addDirectedEdge(p, q);
        g.addDirectedEdge(q, r);
        return g;
    }

    private static void assertCyclicPagShape(Graph pag) {
        Node a = pag.getNode("A");
        Node b = pag.getNode("B");
        Node x = pag.getNode("X");
        Node y = pag.getNode("Y");
        Node z = pag.getNode("Z");

        // Colliders at X and Y from the two nonadjacent parents, including the virtual edges A-Y and B-X.
        assertTrue(pag.isAdjacentTo(a, x));
        assertTrue(pag.isAdjacentTo(a, y));
        assertTrue(pag.isAdjacentTo(b, x));
        assertTrue(pag.isAdjacentTo(b, y));
        assertFalse(pag.isAdjacentTo(a, b));

        assertEquals(Endpoint.ARROW, pag.getEndpoint(a, x));
        assertEquals(Endpoint.ARROW, pag.getEndpoint(b, x));
        assertEquals(Endpoint.ARROW, pag.getEndpoint(a, y));
        assertEquals(Endpoint.ARROW, pag.getEndpoint(b, y));

        // The cycle marks: dotted underlines at both X and Y for the (A, ., B) triples.
        Set<Triple> dotted = pag.getDottedUnderlines();
        assertTrue("Expected dotted underline <A, X, B>",
                dotted.contains(new Triple(a, x, b)) || dotted.contains(new Triple(b, x, a)));
        assertTrue("Expected dotted underline <A, Y, B>",
                dotted.contains(new Triple(a, y, b)) || dotted.contains(new Triple(b, y, a)));

        // Within-cycle adjacency X - Y, and Y -> Z oriented out of the cycle.
        assertTrue(pag.isAdjacentTo(x, y));
        assertEquals(Endpoint.ARROW, pag.getEndpoint(y, z));
    }

    /**
     * Oracle CCD (d-separation on the true cyclic graph) must detect the 2-cycle and leave the acyclic chain
     * unoriented. Under max-p sepsets this failed: no colliders and no dotted underlines were produced.
     */
    @Test
    public void testOracleCcdDetectsTwoCycle() throws Exception {
        Graph g = trueGraph();
        Ccd ccd = new Ccd(new MsepTest(g));
        Graph pag = ccd.search();

        assertCyclicPagShape(pag);

        // The acyclic chain P - Q - R must remain unoriented (circles) in this equivalence class.
        Node p = pag.getNode("P");
        Node q = pag.getNode("Q");
        Node r = pag.getNode("R");
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(p, q));
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(q, p));
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(q, r));
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(r, q));
    }

    /**
     * BOSS-CCD from a covariance matrix alone (fixed seed, n = 5000) recovers the oracle skeleton, the colliders, and
     * both dotted underlines.
     */
    @Test
    public void testBossCcdFromCovariance() throws Exception {
        RandomUtil.getInstance().setSeed(38482838L);

        Graph g = trueGraph();
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
        im.setEdgeCoef(g.getNode("A"), g.getNode("X"), 0.8);
        im.setEdgeCoef(g.getNode("B"), g.getNode("Y"), 0.8);
        im.setEdgeCoef(g.getNode("X"), g.getNode("Y"), 0.6);
        im.setEdgeCoef(g.getNode("Y"), g.getNode("X"), 0.5);
        im.setEdgeCoef(g.getNode("Y"), g.getNode("Z"), 0.8);
        im.setEdgeCoef(g.getNode("P"), g.getNode("A"), 0.7);
        im.setEdgeCoef(g.getNode("P"), g.getNode("Q"), 0.7);
        im.setEdgeCoef(g.getNode("Q"), g.getNode("R"), 0.7);

        DataSet data = im.simulateData(5000, false);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(2.0);
        BossCcd bossCcd = new BossCcd(new IndTestFisherZ(cov, 0.01), score);
        Graph pag = bossCcd.search();

        assertCyclicPagShape(pag);
    }

    /**
     * The superstructure restricts adjacencies: CCD with a superstructure lacking an edge can never output that edge.
     */
    @Test
    public void testSuperstructureIsRespected() throws Exception {
        Graph g = trueGraph();

        // Superstructure: the oracle skeleton minus the Y - Z edge.
        Ccd oracle = new Ccd(new MsepTest(g));
        Graph ref = oracle.search();
        Graph superstructure = GraphUtils.undirectedGraph(ref);
        superstructure.removeEdge(superstructure.getNode("Y"), superstructure.getNode("Z"));

        Ccd ccd = new Ccd(new MsepTest(g));
        ccd.setSuperstructure(superstructure);
        Graph pag = ccd.search();

        assertFalse(pag.isAdjacentTo(pag.getNode("Y"), pag.getNode("Z")));

        // And every output adjacency lies within the superstructure.
        for (Edge e : pag.getEdges()) {
            assertTrue(superstructure.isAdjacentTo(
                    superstructure.getNode(e.getNode1().getName()),
                    superstructure.getNode(e.getNode2().getName())));
        }
    }
}
