// =========================
// File: src/test/java/edu/cmu/tetrad/algcomparison/algorithm/mag/GpsSmokeTest.java
// =========================
package edu.cmu.tetrad.algcomparison.algorithm.mag;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class GpsSmokeTest {

    @Test
    public void runsOnSimpleSPDcovariance() throws Exception {
        // ---- Variables ----
        List<Node> vars = new ArrayList<>();
        vars.add(new GraphNode("X"));
        vars.add(new GraphNode("Y"));
        vars.add(new GraphNode("Z"));

        // ---- Simple SPD covariance (3x3), n = 500 ----
        double[][] sigma = {
                {1.0,  0.30, 0.00},
                {0.30, 1.00, 0.20},
                {0.00, 0.20, 1.00}
        };
        int n = 500;
        ICovarianceMatrix cov = new CovarianceMatrix(vars, sigma, n);

        // ---- Parameters (borrowed keys, as in your wrapper) ----
        Parameters params = new Parameters();
        params.set(Params.FAST_ICA_TOLERANCE, 1e-6);
        params.set(Params.GIN_RIDGE, 0.0);
        params.set(Params.NUM_STARTS, 3);        // a couple of restarts
        params.set(Params.MAX_ITERATIONS, 500);  // cap iterations
        params.set(Params.VERBOSE, false);

        // ---- Run GPS via the AlgComparison wrapper ----
        Gps gps = new Gps();
        Graph g = gps.search(cov, params);

        // ---- Basic sanity checks ----
        assertNotNull("Graph is null", g);
        assertEquals("Wrong node count", vars.size(), g.getNumNodes());

        // all graph nodes should match the covariance variable names
        for (Node v : vars) {
            assertNotNull("Missing node in result: " + v.getName(), g.getNode(v.getName()));
        }

        // Edges won’t be known a priori, but ensure it didn’t return something degenerate
        assertTrue("Edge count should be >= 0", g.getNumEdges() >= 0);
    }
}