package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.List;

/**
 * Hand-run harness comparing vanilla CCD to BOSS-CCD on data from a linear cyclic SEM, using only a covariance
 * matrix as input (the Drosophila use case). Not a JUnit test; run main() directly.
 *
 * <p>Ground truth: exogenous A -&gt; X, B -&gt; Y; a 2-cycle X &lt;-&gt; Y; Y -&gt; Z; and a separate acyclic chain
 * P -&gt; Q -&gt; R with P also a parent of A (to give the acyclic part some structure). CCD's correct output includes
 * the "virtual edges" A o-&gt; Y and B o-&gt; X (inseparable pairs induced by the cycle), with the dotted-underline
 * marking A, X/Y, B patterns.</p>
 */
public final class BossCcdHarness {

    private BossCcdHarness() {
    }

    public static void main(String[] args) throws Exception {
        RandomUtil.getInstance().setSeed(38482838L);

        // ---- Ground-truth cyclic graph ----
        Node a = new GraphNode("A");
        Node b = new GraphNode("B");
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");
        Node p = new GraphNode("P");
        Node q = new GraphNode("Q");
        Node r = new GraphNode("R");
        Node m = new GraphNode("M");
        Node u = new GraphNode("U");
        Node v = new GraphNode("V");
        Node w = new GraphNode("W");

        Graph g = new EdgeListGraph(List.of(a, b, x, y, z, p, q, r, m, u, v, w));
        g.addDirectedEdge(a, x);
        g.addDirectedEdge(b, y);
        g.addDirectedEdge(x, y);
        g.addDirectedEdge(y, x);   // 2-cycle X <-> Y
        g.addDirectedEdge(y, z);
        g.addDirectedEdge(p, a);
        g.addDirectedEdge(p, q);
        g.addDirectedEdge(q, r);
        g.addDirectedEdge(m, u);
        g.addDirectedEdge(u, v);
        g.addDirectedEdge(v, w);
        g.addDirectedEdge(w, u);   // 3-cycle U -> V -> W -> U

        System.out.println("True cyclic graph:\n" + g + "\n");

        // ---- Parameterize; keep the cycle product well below 1 ----
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
        im.setEdgeCoef(a, x, 0.8);
        im.setEdgeCoef(b, y, 0.8);
        im.setEdgeCoef(x, y, 0.6);
        im.setEdgeCoef(y, x, 0.5);
        im.setEdgeCoef(y, z, 0.8);
        im.setEdgeCoef(p, a, 0.7);
        im.setEdgeCoef(p, q, 0.7);
        im.setEdgeCoef(q, r, 0.7);
        im.setEdgeCoef(m, u, 0.8);
        im.setEdgeCoef(u, v, 0.7);
        im.setEdgeCoef(v, w, 0.7);
        im.setEdgeCoef(w, u, 0.6);

        final int n = 5000;
        DataSet data = im.simulateData(n, false);
        ICovarianceMatrix cov = new CovarianceMatrix(data);  // covariance only, per the use case

        final double alpha = 0.01;
        final double penaltyDiscount = 2.0;

        // ---- Vanilla CCD ----
        {
            IndependenceTest test = new IndTestFisherZ(cov, alpha);
            Ccd ccd = new Ccd(test);
            long t0 = System.currentTimeMillis();
            Graph out = ccd.search();
            long t1 = System.currentTimeMillis();
            System.out.println("==== CCD (" + (t1 - t0) + " ms) ====");
            System.out.println(out);
            System.out.println("Underlines: " + out.getUnderLines());
            System.out.println("Dotted underlines: " + out.getDottedUnderlines());
            System.out.println();
        }

        // ---- BOSS-CCD ----
        {
            IndependenceTest test = new IndTestFisherZ(cov, alpha);
            SemBicScore score = new SemBicScore(cov);
            score.setPenaltyDiscount(penaltyDiscount);
            BossCcd bossCcd = new BossCcd(test, score);
            long t0 = System.currentTimeMillis();
            Graph out = bossCcd.search();
            long t1 = System.currentTimeMillis();
            System.out.println("==== BOSS-CCD (" + (t1 - t0) + " ms) ====");
            System.out.println(out);
            System.out.println("Underlines: " + out.getUnderLines());
            System.out.println("Dotted underlines: " + out.getDottedUnderlines());
        }

        // ---- Oracle CCD: d-separation on the true cyclic graph ----
        {
            IndependenceTest oracle = new edu.cmu.tetrad.search.test.MsepTest(g);
            Ccd ccd = new Ccd(oracle);
            Graph out = ccd.search();
            System.out.println("==== ORACLE CCD (MsepTest on true cyclic graph) ====");
            System.out.println(out);
            System.out.println("Underlines: " + out.getUnderLines());
            System.out.println("Dotted underlines: " + out.getDottedUnderlines());
        }
    }
}
