package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.Kci;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.util.*;

import static org.ejml.UtilEjml.assertTrue;

public class KciSmokeTest {

    private static SimpleMatrix makeDataVxN(int n, double depNoise) {
        Random r = new Random(42);
        double[] x = new double[n], z = new double[n], e = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) { x[i] = r.nextGaussian(); z[i] = r.nextGaussian(); e[i] = r.nextGaussian(); }
        // independent case uses depNoise = +Inf sentinel (we'll overwrite y below)
        if (Double.isInfinite(depNoise)) System.arraycopy(e, 0, y, 0, n);
        else for (int i = 0; i < n; i++) y[i] = x[i] + depNoise * e[i];  // strong dependence if depNoise is small

        // rows = variables (X,Y,Z), cols = samples
        SimpleMatrix M = new SimpleMatrix(3, n);
        for (int j = 0; j < n; j++) { M.set(0, j, x[j]); M.set(1, j, y[j]); M.set(2, j, z[j]); }
        return M;
    }

    private static Kci makeKci(SimpleMatrix data, boolean approximate) {
        Node X = new GraphNode("X"), Y = new GraphNode("Y"), Z = new GraphNode("Z");
        Map<Node,Integer> map = Map.of(X,0, Y,1, Z,2);
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < data.numCols(); i++) rows.add(i);
        Kci k = new Kci(data, map, null, rows);
        k.approximate = approximate;
        k.kernelType = Kci.KernelType.GAUSSIAN;
        k.epsilon = 1e-3;
        k.scalingFactor = 1.0;
        k.numPermutations = 500; // lighter permutation for the test
        return k;
    }

    @Test
    public void gammaApprox_independent_vs_dependent() {
        double alpha = 0.01;
        // independent: Y = e (independent of X given Z)
        Kci kInd = makeKci(makeDataVxN(600, Double.POSITIVE_INFINITY), true);
        double pInd = kInd.isIndependenceConditional(new GraphNode("X"), new GraphNode("Y"),
                List.of(new GraphNode("Z")), alpha);
        assertTrue(0.0 <= pInd && pInd <= 1.0, "p in [0,1]");
        assertTrue(pInd > alpha, "independent should fail to reject (p > alpha)");



        // dependent: Y = X + 0.05*e (strong)
        Kci kDep = makeKci(makeDataVxN(600, 0.05), true);
        double pDep = kDep.isIndependenceConditional(new GraphNode("X"), new GraphNode("Y"),
                List.of(new GraphNode("Z")), alpha);
//        assertTrue(pDep < .01, "dependent should produce tiny p");

        assertTrue(pDep < alpha, "dependent should be significant (gamma approx).");
//        assertTrue(pDep < 1e-3, "dependent should be very small (gamma approx).");
    }

    @Test
    public void nonApprox_independent_vs_dependent() {
        double alpha = 0.01;

        // independent: Y = e  (Y ⟂ X | Z)
        Kci kInd = makeKci(makeDataVxN(600, Double.POSITIVE_INFINITY), /*approximate=*/false);
        kInd.numPermutations = 4000;    // min p ≈ 1/4001 ≈ 2.5e-4
        kInd.rng = new Random(123);     // determinism
        double pInd = kInd.isIndependenceConditional(new GraphNode("X"), new GraphNode("Y"),
                List.of(new GraphNode("Z")), alpha);
        assertTrue(0.0 <= pInd && pInd <= 1.0, "p in [0,1]");
        assertTrue(pInd > alpha, "independent should fail to reject (p > alpha)");

        // dependent: Y = X + 0.05*e  (strong dependence)
        Kci kDep = makeKci(makeDataVxN(600, 0.05), /*approximate=*/false);
        kDep.numPermutations = 4000;
        kDep.rng = new Random(123);
        double pDep = kDep.isIndependenceConditional(new GraphNode("X"), new GraphNode("Y"),
                List.of(new GraphNode("Z")), alpha);
        // Can't be smaller than 1/(B+1); use a realistic bound or just alpha
        assertTrue(pDep < 1e-3, "dependent should yield small p (with 4k perms)");
        // or simply:
        // assertTrue(pDep < alpha, "dependent should be significant");
    }

    @Test
    public void permutation_independent_vs_dependent() {
        double alpha = 0.01;
        Kci kInd = makeKci(makeDataVxN(400, Double.POSITIVE_INFINITY), false);
        kInd.rng = new Random(123); // determinism
        double pInd = kInd.isIndependenceConditional(new GraphNode("X"), new GraphNode("Y"),
                List.of(new GraphNode("Z")), alpha);
        assertTrue(pInd > alpha, "perm: independent should fail to reject");

        Kci kDep = makeKci(makeDataVxN(400, 0.05), false);
        kDep.rng = new Random(123);
        double pDep = kDep.isIndependenceConditional(new GraphNode("X"), new GraphNode("Y"),
                List.of(new GraphNode("Z")), alpha);
        assertTrue(pDep < 1e-3, "perm: dependent should be significant");
    }
}