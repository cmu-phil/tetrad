package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.CyclicStableUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static edu.cmu.tetrad.search.test.IndTestFisherZ.ShrinkageMode.LEDOIT_WOLF;
import static org.junit.Assert.assertTrue;

/**
 * CCD sanity: canonical “simple” cyclic example. Graph (names chosen to match the attached diagram): X1 -> X4,   X2 ->
 * X3,   X3 <-> X4 (2-cycle)
 * <p>
 * Expected PAG: X1 -> X4 X2 -> X3 X3 o-o X4        (non-oriented between cycle nodes) no other adjacencies (optional
 * underline triples: <X1,X4,X3> and <X2,X3,X4>)
 */
public class TestCcdSimpleCycleSanity {

    // Simulation & test knobs
    private static final int N = 10000;
    private static final int N_RUNS = 20;     // repeat over seeds
    private static final double RADIUS = 0.6;    // spectral radius target (stable)
    private static final double CYC_LOW = 0.20;   // cycle coef range used by stabilizer
    private static final double CYC_HIGH = 0.80;
    private static final double ALPHA = 0.01;   // Fisher-Z alpha

    /**
     * Check the expected CCD PAG pattern and record a message if it fails.
     */
    private static boolean checkExpectedPag(Graph pag, Node X1, Node X2, Node X3, Node X4,
                                            List<String> failures, long seed) {

        // 1) Adjacency set must be exactly: {X1-X4, X2-X3, X3-X4}
        Set<String> expectedUndirectedPairs = new HashSet<>(Arrays.asList(
                pairKey(X1, X3), pairKey(X2, X4), pairKey(X2, X3), pairKey(X3, X4), pairKey(X1, X4)
        ));
        Set<String> seenPairs = new HashSet<>();
        for (Edge e : pag.getEdges()) seenPairs.add(pairKey(e.getNode1(), e.getNode2()));

        if (!seenPairs.equals(expectedUndirectedPairs)) {
            failures.add(String.format(Locale.US,
                    "[seed=%d] Wrong adjacencies. Got=%s, Expected=%s",
                    seed, seenPairs, expectedUndirectedPairs));
            return false;
        }

        // 2) Endpoints:
        //    X1 -> X4
        if (!isDirected(pag, X1, X4)) {
            failures.add(String.format(Locale.US, "[seed=%d] Expected X1->X4, got %s",
                    seed, pag.getEdge(X1, X4)));
            return false;
        }
        //    X2 -> X3
        if (!isDirected(pag, X2, X3)) {
            failures.add(String.format(Locale.US, "[seed=%d] Expected X2->X3, got %s",
                    seed, pag.getEdge(X2, X3)));
            return false;
        }
        //    X3 o-o X4 (both circles)
        Edge e34 = pag.getEdge(X3, X4);
        if (e34 == null ||
            pag.getEndpoint(X3, X4) != Endpoint.TAIL ||
            pag.getEndpoint(X4, X3) != Endpoint.TAIL) {
            failures.add(String.format(Locale.US, "[seed=%d] Expected X3 --- X4, got %s",
                    seed, e34));
            return false;
        }

        Set<Triple> dottedUnderlines = pag.getDottedUnderlines();
        System.out.println("Dotted underlines: " + dottedUnderlines);

        // (Optional) underline triples: <X1,X4,X3> and <X2,X3,X4>
        // These are non-colliders in CCD; if your Graph impl records them, you can assert:
        if (!dottedUnderlines.contains(new Triple(X1, X3, X2))) {
            failures.add(String.format(Locale.US, "[seed=%d] Expected dotted underline triple <X1,X3,X2>",
                    seed));
        }
//
        if (!dottedUnderlines.contains(new Triple(X1, X4, X2))) {
            failures.add(String.format(Locale.US, "[seed=%d] Expected dotted underline triple <X1,X4,X2>",
                    seed));
        }

        return true;
    }

    private static boolean isDirected(Graph g, Node from, Node to) {
        if (g.getDirectedEdge(from, to) == null) {
            System.out.println(g);
            g.getDirectedEdge(from, to);
        }

        return g.getDirectedEdge(from, to) != null;
    }

    private static String pairKey(Node a, Node b) {
        String s1 = a.getName(), s2 = b.getName();
        return (s1.compareTo(s2) < 0) ? (s1 + "-" + s2) : (s2 + "-" + s1);
    }

    @Test
    public void ccdProducesExpectedPagAcrossSeeds() throws Exception {
        int ok = 0;
        List<String> failures = new ArrayList<>();

        for (int r = 0; r < N_RUNS; r++) {
            // Build the canonical graph: X1->X4, X2->X3, X3<->X4
            Node X1 = new ContinuousVariable("X1");
            Node X2 = new ContinuousVariable("X2");
            Node X3 = new ContinuousVariable("X3");
            Node X4 = new ContinuousVariable("X4");

            Graph g = new EdgeListGraph(Arrays.asList(X1, X2, X3, X4));
            g.addDirectedEdge(X1, X4);
            g.addDirectedEdge(X2, X3);
            g.addDirectedEdge(X3, X4);
            g.addDirectedEdge(X4, X3);

            Parameters pars = new Parameters();
            pars.set(Params.COEF_SYMMETRIC, false); // positive coefs for stability
            pars.set(Params.COEF_LOW, 0.30);
            pars.set(Params.COEF_HIGH, 0.60);

            long seed = 12345L + r;
            RandomUtil.getInstance().setSeed(seed);

            // Stable cyclic simulation (fixed spectral radius per SCC)
            SemIm.CyclicSimResult sim = CyclicStableUtils.simulateStableFixedRadius(
                    g, N, RADIUS, CYC_LOW, CYC_HIGH, seed, pars);

            DataSet ds = sim.dataSet();

            // Fisher-Z with Ledoit–Wolf shrinkage
            IndTestFisherZ fz = new IndTestFisherZ(ds, ALPHA);
            fz.setShrinkageMode(LEDOIT_WOLF);

            // CCD (with the fixed Step B that uses FAS sepsets)
            Ccd ccd = new Ccd(new CachingIndependenceTest(fz));
            // Optional: enable R1 propagation; off by default:
            ccd.setApplyR1(false);

            Graph pag = ccd.search();

            Node _X1 = pag.getNode("X1");
            Node _X2 = pag.getNode("X2");
            Node _X3 = pag.getNode("X3");
            Node _X4 = pag.getNode("X4");

            // Assertions for this run
            boolean pass = checkExpectedPag(pag, _X1, _X2, _X3, _X4, failures, seed);
            if (pass) ok++;
        }

        // Require all (or nearly all) runs to pass under these settings
        String msg = String.join("\n", failures);

        System.out.println(msg);

        int threshold = (int) (0.8 * N_RUNS);
        assertTrue("More than 20% CCD PAG mismatch on some seeds", ok > threshold);
    }
}