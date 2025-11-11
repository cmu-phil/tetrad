package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import static org.junit.Assert.*;

public class PagIdaTest {

    @Test
    public void testSimpleChain_X_to_Z_in_PAG() {
        // ---------- 1. True DAG: X -> Y -> Z ----------
        Node X = new ContinuousVariable("X");
        Node Y = new ContinuousVariable("Y");
        Node Z = new ContinuousVariable("Z");

        Graph dag = new EdgeListGraph(Arrays.asList(X, Y, Z));
        dag.addDirectedEdge(X, Y);
        dag.addDirectedEdge(Y, Z);

        // Simulate linear Gaussian data from the DAG.
        // (Use whatever SemIm / simulateData variant matches your version.)
        Graph semGraph = new EdgeListGraph(dag);
        SemPm pm = new SemPm(semGraph);
        SemIm im = new SemIm(pm);

        // Fix edge coefficients so the total effect is known and positive.
        im.setEdgeCoef(X, Y, 1.0);
        im.setEdgeCoef(Y, Z, 0.5);   // true total effect X→Z is 0.5

        DataSet data = im.simulateData(5000, false);

        // ---------- 2. PAG handed to PagIda: X o-o Y o-o Z ----------
        Graph pag = new EdgeListGraph(Arrays.asList(X, Y, Z));
        pag.addEdge(new Edge(X, Y, Endpoint.CIRCLE, Endpoint.CIRCLE));
        pag.addEdge(new Edge(Y, Z, Endpoint.CIRCLE, Endpoint.CIRCLE));

        // ---------- 3. Run PagIda on the PAG ----------
        PagIda ida = new PagIda(data, pag, Collections.singletonList(X));
        ida.setMaxLengthAdjustment(-1);  // no path-length limit

        LinkedList<Double> effects = ida.getTotalEffects(X, Z);

        System.out.println("Effects = " + effects);

        // ---------- 4. Basic sanity checks ----------
        assertFalse("Total-effects list should not be empty.", effects.isEmpty());

        for (Double b : effects) {
            assertNotNull("Effect should not be null", b);
            assertTrue("Effect should be finite", Double.isFinite(b));
        }

//        double min = Collections.min(effects);
//        double max = Collections.max(effects);
//
//        // For this PAG, we expect:
//        //  - at least one refinement where X <- Y or X <-> Y gives a non-amenable path: effect ≈ 0
//        //  - at least one refinement where X -> Y and path X -> Y -> Z is amenable: effect > 0
//        assertEquals("Minimal effect should be (approximately) 0.", 0.0, min, 1e-2);
//        assertTrue("Maximal effect should be positive.", max > 0.0);

        double trueEffect = 0.5;
        double min = Collections.min(effects);
        double max = Collections.max(effects);

        assertEquals("Minimal effect should be approx 0.", 0.0, min, 1e-2);
        assertEquals("Maximal effect should be close to true total effect.",
                trueEffect, max, 0.05);
    }
}