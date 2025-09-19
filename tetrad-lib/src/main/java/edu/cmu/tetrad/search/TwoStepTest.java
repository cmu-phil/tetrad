package edu.cmu.tetrad.search;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import org.ejml.simple.SimpleMatrix;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Deprecated
public class TwoStepTest {

    /**
     * Make a p×p random B with given density, then scale so spectral radius < 0.8 for stability.
     */
    private static SimpleMatrix makeRandomStableB(int p, double density, Random rnd) {
        SimpleMatrix B = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                if (i == j) continue;
                if (rnd.nextDouble() < density) {
                    // random sign and magnitude away from 0
                    double sign = rnd.nextBoolean() ? 1.0 : -1.0;
                    double mag = 0.2 + 0.8 * rnd.nextDouble(); // (0.2, 1.0)
                    B.set(i, j, sign * mag);
                }
            }
        }
        // scale to ensure I - B is invertible and well-conditioned:
        // use row-sum (∞-norm) scaling so ||B||∞ < 1, then pad margin
        double maxRowSum = 0.0;
        for (int i = 0; i < p; i++) {
            double s = 0.0;
            for (int j = 0; j < p; j++) s += Math.abs(B.get(i, j));
            maxRowSum = Math.max(maxRowSum, s);
        }
        double scale = (maxRowSum > 0.0) ? (0.8 / maxRowSum) : 1.0;
        return B.scale(scale);
    }

    // -------------------------- Helpers --------------------------

    /**
     * Simulate X = (I - B)^{-1} E, where E has i.i.d. Laplace(0,1) entries.
     */
    private static SimpleMatrix simulateLinearSEM(SimpleMatrix B, int n, Random rnd) {
        int p = B.numRows();
        SimpleMatrix I = SimpleMatrix.identity(p);
        SimpleMatrix A = I.minus(B);      // A = I - B
        SimpleMatrix Ainv = A.invert();

        // E: n × p, Laplace(0,1) entries
        SimpleMatrix E = new SimpleMatrix(n, p);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < p; j++) {
                E.set(i, j, laplace01(rnd));
            }
        // X = E * A^{-T}? Careful with orientation:
        // Our convention in TwoStep is: rows=samples, cols=variables, and X = (I - B)^{-1} E (column view).
        // Derivation with our shapes gives: X = E * Ainv.transpose()
        return E.mult(Ainv.transpose());
    }

    private static double laplace01(Random rnd) {
        // Inverse CDF: Laplace(0,1)
        double u = rnd.nextDouble() - 0.5; // (-0.5, 0.5)
        return (u >= 0.0) ? -Math.log(1.0 - 2.0 * u) : Math.log(1.0 + 2.0 * u);
    }

    private static List<String> defaultVarNames(int p) {
        List<String> names = new ArrayList<>(p);
        for (int j = 0; j < p; j++) names.add("X" + (j + 1));
        return names;
    }

    /**
     * Build a Tetrad DataSet from an n×p matrix with given variable names.
     */
    private static DataSet toDataSet(SimpleMatrix X, List<String> varNames) {
        int n = X.numRows(), p = X.numCols();
        if (varNames.size() != p) throw new IllegalArgumentException("varNames size != p");

        List<Node> vars = new ArrayList<>(p);
        for (String nm : varNames) vars.add(new ContinuousVariable(nm));

        DoubleDataBox box = new DoubleDataBox(n, p);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < p; j++) {
                box.set(i, j, X.get(i, j));
            }
        return new BoxDataSet(box, vars);
    }

    /**
     * Structural Hamming Distance between adjacency of Btrue and Bhat.
     */
    private static int shdAdjacency(SimpleMatrix Btrue, SimpleMatrix Bhat, double trueZeroTol, double estZeroTol) {
        int p = Btrue.numRows();
        int shd = 0;
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                if (i == j) continue;
                boolean t = Math.abs(Btrue.get(i, j)) > trueZeroTol;
                boolean e = Math.abs(Bhat.get(i, j)) > estZeroTol;
                if (t != e) shd++;
            }
        }
        return shd;
    }

    /**
     * RMSE of coefficients on the union of supports of true and estimated B (i.e., we only compare where at least one
     * is nonzero).
     */
    private static double rmseOnSupport(SimpleMatrix Btrue, SimpleMatrix Bhat, double trueZeroTol, double estZeroTol) {
        int p = Btrue.numRows();
        double se = 0.0;
        int cnt = 0;
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                if (i == j) continue;
                boolean in = Math.abs(Btrue.get(i, j)) > trueZeroTol || Math.abs(Bhat.get(i, j)) > estZeroTol;
                if (in) {
                    double d = Btrue.get(i, j) - Bhat.get(i, j);
                    se += d * d;
                    cnt++;
                }
            }
        }
        return (cnt == 0) ? 0.0 : Math.sqrt(se / cnt);
    }

    @Test
    public void recoversCyclicLinearNonGaussian() {
        final long seed = 4299L;
        final Random rnd = new Random(seed);

        final int p = 6;           // number of variables
        final int n = 5000;        // samples (keep modest for CI speed)

        // --- 1) Simulate a stable cyclic B and Laplace errors ---
        SimpleMatrix Btrue = makeRandomStableB(p, 0.25, rnd); // ~25% density, cycles allowed
        SimpleMatrix X = simulateLinearSEM(Btrue, n, rnd);    // X = (I - B)^(-1) E, E ~ Laplace(0,1)

        // --- 2) Wrap as a Tetrad DataSet ---
        DataSet data = toDataSet(X, defaultVarNames(p));

        // --- 3) Run TwoStep ---
        TwoStep algo = new TwoStep();
        algo.setRandomSeed(123L);
        algo.setLambda(0.05);         // tune as desired
        algo.setMaskThreshold(1e-3);  // generous support
        algo.setCoefThreshold(1e-3);  // slightly stricter prune
        TwoStep.Result out = algo.search(data);

        SimpleMatrix Bhat = out.B;

        // --- 4) Metrics: SHD on adjacency and RMSE on coefficients (allowed positions) ---
        int shd = shdAdjacency(Btrue, Bhat, 1e-8, 1e-1); // (trueZeroTol, estZeroTol)
        double rmse = rmseOnSupport(Btrue, Bhat, 1e-8, 1e-1);

        // Print for debugging when running locally
        System.out.println("True B:\n" + Btrue);
        System.out.println("Estimated B:\n" + Bhat);
        System.out.println("SHD (adjacency): " + shd);
        System.out.println("RMSE (coefficients on union support): " + rmse);

        // --- 5) Loose sanity assertions (non-trivial recovery) ---
        // We don't expect perfect recovery for small n and generic lambda,
        // but SHD should be well below a dense random guess, and RMSE should be finite and modest.
        Assert.assertTrue("SHD too large", shd <= p * (p - 1) / 2); // must be much less than dense upper bound
        Assert.assertTrue("RMSE is NaN/Inf", !Double.isNaN(rmse) && !Double.isInfinite(rmse));
        Assert.assertTrue("RMSE too high", rmse < 0.5); // adjust as needed for your data sizes
    }
}