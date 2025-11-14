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
import java.util.List;

import static org.junit.Assert.*;

/**
 * Provides a suite of unit tests for evaluating the functionality of the PagIda algorithm
 * when applied to partially directed acyclic graphs (PAGs). The primary focus is to verify
 * the correctness of total causal effect calculations between variables in a PAG, using simulated data
 * and a predefined structural causal model.
 */
public class PdagPagIdaTest {

    /**
     * Default constructor for the PdagPagIdaTest class.
     * This class is designed to test the functionality and correctness of the
     * PagIda algorithm for calculating total causal effects in a partially directed
     * acyclic graph (PAG).
     *
     * The constructor initializes an instance of PdagPagIdaTest but does not perform
     * any additional setup or processing.
     */
    public PdagPagIdaTest() {

    }

    /**
     * Tests the calculation of total causal effects between variables in a partially directed acyclic graph (PAG)
     * using the PagIda algorithm. Specifically, this test examines a simple chain structure from variable X to Y to Z.
     *
     * The test involves the following steps:
     * 1. Constructs a directed acyclic graph (DAG) with nodes X, Y, and Z, with directed edges X -> Y and Y -> Z.
     * 2. Simulates data from the DAG using a structural equation model with known causal coefficients.
     * 3. Constructs a PAG corresponding to an undirected version of the DAG with circle endpoints (X o-o Y o-o Z).
     * 4. Runs the PagIda algorithm on the PAG to compute total effects from X to Z.
     * 5. Performs assertions to verify the correctness and sanity of calculated total effects:
     *    - Ensures the list of effects is not empty.
     *    - Ensures all calculated effects are finite and non-null.
     *    - Checks that the minimal effect is approximately zero.
     *    - Confirms that the maximum effect closely matches the expected true total effect, based on the known coefficients.
     */
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
        PdagPagIda ida = new PdagPagIda(data, pag, List.of());
        ida.setMaxLengthAdjustment(-1);  // no path-length limit

        LinkedList<Double> effects = ida.getTotalEffects(X, Z);

        System.out.println("Effects = " + effects);

        // ---------- 4. Basic sanity checks ----------
        assertFalse("Total-effects list should not be empty.", effects.isEmpty());

        for (Double b : effects) {
            assertNotNull("Effect should not be null", b);
            assertTrue("Effect should be finite", Double.isFinite(b));
        }

        double trueEffect = 0.5;
        double min = Collections.min(effects);
        double max = Collections.max(effects);

        assertEquals("Minimal effect should be approx 0.", 0.0, min, 1e-2);
        assertEquals("Maximal effect should be close to true total effect.",
                trueEffect, max, 0.05);
    }
}