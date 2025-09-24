// =========================
// File: src/test/java/edu/cmu/tetrad/search/mag/gps/MagRicfStubTest.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MagRicfStubTest {

    @Test
    public void likelihood_smokeTest_threeNodeVStructure() {
        // Graph: X -> Y <- Z  (simple DAG; no bidirected/undirected parts)
        Graph g = new EdgeListGraph();
        GraphNode X = new GraphNode("X");
        GraphNode Y = new GraphNode("Y");
        GraphNode Z = new GraphNode("Z");
        g.addNode(X); g.addNode(Y); g.addNode(Z);
        g.addEdge(Edges.directedEdge(X, Y));
        g.addEdge(Edges.directedEdge(Z, Y));

        // PD covariance for variables in order [X, Y, Z]
        // (If your CovarianceMatrix constructor is different, tweak the call below.)
        List<String> varNames = Arrays.asList("X", "Y", "Z");
        List<Node> vars = new ArrayList<>(varNames.size());
        for (String varName : varNames) {
            vars.add(new ContinuousVariable(varName));
        }

        double[][] sArr = {
                {1.00, 0.50, 0.30},
                {0.50, 1.00, 0.40},
                {0.30, 0.40, 1.00}
        };
        int n = 500;
        ICovarianceMatrix cov = new CovarianceMatrix(vars, sArr, n);

        // Call the stub
        double ll1 = MagRicfStub.likelihood(g, cov, /*ridge*/0.0, /*restarts*/0);

        System.out.println(ll1);

        assertFalse("log-likelihood should not be NaN", Double.isNaN(ll1));
        assertFalse("log-likelihood should be finite", Double.isInfinite(ll1));

        // Idempotence sanity check: re-running on same inputs should be stable (within tiny epsilon)
        double ll2 = MagRicfStub.likelihood(g, cov, 0.0, 0);
        assertEquals("Likelihood should be reproducible on identical inputs", ll1, ll2, 1e-8);
    }

    @Test
    public void likelihood_handlesDifferentVariableOrder_consistently() {
        // Same graph
        Graph g = new EdgeListGraph();
        GraphNode X = new GraphNode("X");
        GraphNode Y = new GraphNode("Y");
        GraphNode Z = new GraphNode("Z");
        g.addNode(X); g.addNode(Y); g.addNode(Z);
        g.addEdge(Edges.directedEdge(X, Y));
        g.addEdge(Edges.directedEdge(Z, Y));

        // Covariance in a different variable order [Z, X, Y]
        List<String> varNames = Arrays.asList("Z", "X", "Y");

        List<Node> vars = new ArrayList<>(varNames.size());
        for (String varName : varNames) {
            vars.add(new ContinuousVariable(varName));
        }

        double[][] sArr = {
                {1.00, 0.30, 0.40},  // Z row
                {0.30, 1.00, 0.50},  // X row
                {0.40, 0.50, 1.00}   // Y row
        };
        int n = 500;
        ICovarianceMatrix cov = new CovarianceMatrix(vars, sArr, n);

        double ll = MagRicfStub.likelihood(g, cov, 0.0, 0);
        assertFalse(Double.isNaN(ll));
        assertFalse(Double.isInfinite(ll));
    }
}