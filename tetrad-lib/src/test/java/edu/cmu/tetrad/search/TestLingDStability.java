package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the hardened LingD.isStable: it must never throw, must agree with a reference
 * eigendecomposition where one exists, and must classify the sparse unstable B̂ candidates on
 * which EJML's eigenvector extraction fails (the "Eigenvalue Decomposition failed" crash on
 * cyclic non-Gaussian data).
 */
public class TestLingDStability {

    /**
     * A B̂ candidate produced by the LiNG-D pipeline on cyclic Exp(1) data (8 nodes, 10 edges,
     * N = 1000) on which SimpleMatrix.eig() used to throw "Eigenvalue Decomposition failed".
     * Its spectral radius is 8.126 (commons-math and EJML values-only agree), so it is unstable.
     */
    private static final double[][] FAILING_CANDIDATE = {
            {0.0000, 0.0000, -1.3749, -3.4642, 0.0000, 0.0000, 0.0000, -8.6212},
            {0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000},
            {-0.7716, 0.1048, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000},
            {0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000},
            {0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 7.3760, -2.6823, 0.0000},
            {-2.1316, 0.0000, 0.0000, 0.0000, -8.9524, 0.0000, 0.0000, -4.5408},
            {0.0000, 0.5363, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000},
            {-2.4102, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000}};

    @Test
    public void nonFiniteCandidateIsNotStableAndDoesNotThrow() {
        Matrix b = new Matrix(3, 3);
        b.set(0, 1, Double.NaN);
        assertFalse(LingD.isStable(b));

        Matrix c = new Matrix(3, 3);
        c.set(2, 0, Double.POSITIVE_INFINITY);
        assertFalse(LingD.isStable(c));
    }

    @Test
    public void formerlyCrashingCandidateIsClassifiedUnstable() {
        Matrix b = new Matrix(FAILING_CANDIDATE);
        assertFalse(LingD.isStable(b));
    }

    @Test
    public void agreesWithReferenceEigendecompositionAtKnownSpectralRadii() {
        Random rng = new Random(77);
        double[] targets = {0.3, 0.9, 0.999, 1.1, 2.0, 10.0};

        for (int rep = 0; rep < 30; rep++) {
            int n = 5 + rng.nextInt(6);
            double[][] a = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    a[i][j] = rng.nextGaussian();
                }
            }

            double rho = spectralRadiusReference(a);
            if (!(rho > 1e-6)) continue;

            for (double target : targets) {
                double[][] scaled = new double[n][n];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        scaled[i][j] = a[i][j] * (target / rho);
                    }
                }

                assertEquals("target rho = " + target, target < 1.0,
                        LingD.isStable(new Matrix(scaled)));
            }
        }
    }

    @Test
    public void gelfandFallbackCertifiesAndRefusesCorrectly() {
        // Diagonal with known spectral radius 0.5: certified stable.
        SimpleMatrix s = SimpleMatrix.identity(4).scale(0.5);
        assertTrue(LingD.spectralRadiusCertifiedBelow(s, 1.0 - 1e-8));

        // Diagonal with spectral radius 1.2: refused.
        SimpleMatrix u = SimpleMatrix.identity(4).scale(1.2);
        assertFalse(LingD.spectralRadiusCertifiedBelow(u, 1.0 - 1e-8));

        // Strictly lower triangular with huge entries: nilpotent, rho = 0, certified stable
        // despite entries of 1e8 (the scale the epsilon diagonal guard can produce).
        SimpleMatrix nil = new SimpleMatrix(6, 6);
        for (int i = 1; i < 6; i++) {
            for (int j = 0; j < i; j++) {
                nil.set(i, j, 1e8);
            }
        }
        assertTrue(LingD.spectralRadiusCertifiedBelow(nil, 1.0 - 1e-8));

        // The zero matrix: certified stable.
        assertTrue(LingD.spectralRadiusCertifiedBelow(new SimpleMatrix(3, 3), 1.0 - 1e-8));

        // The formerly crashing candidate: refused (rho = 8.126).
        assertFalse(LingD.spectralRadiusCertifiedBelow(
                new Matrix(FAILING_CANDIDATE).getSimpleMatrix(), 1.0 - 1e-8));
    }

    /**
     * End to end on cyclic Exp(1) data for a seed on which the unpatched isStable threw: the
     * full candidate enumeration plus stability filtering must finish without an exception.
     */
    @Test
    public void cyclicNonGaussianPipelineDoesNotThrow() throws Exception {
        RandomUtil.getInstance().setSeed(5);
        Graph g = RandomGraph.randomCyclicGraph2(8, 10, 6);

        Parameters params = new Parameters();
        params.set(Params.CUSTOM_NOISE_OPTION, 2);
        params.set(Params.CUSTOM_NOISE_EXPRESSION, "Exp(1)");

        SemIm im = new SemIm(new SemPm(g), params);
        DataSet data = im.simulateData(1000, false);

        Matrix w = LingD.estimateW(data, 10000, 1e-6, 1.1, false);

        LingD lingD = new LingD();
        lingD.setBThreshold(0.1);
        lingD.setWThreshold(0.1);
        List<Matrix> bHats = lingD.getScaledBHats(w);

        for (Matrix bHat : bHats) {
            LingD.isStable(bHat); // Must not throw; the classification itself may be either way.
        }
    }

    private static double spectralRadiusReference(double[][] a) {
        EigenDecomposition eig = new EigenDecomposition(new Array2DRowRealMatrix(a));
        double rho = 0.0;
        double[] re = eig.getRealEigenvalues();
        double[] im = eig.getImagEigenvalues();
        for (int i = 0; i < re.length; i++) {
            rho = Math.max(rho, Math.hypot(re[i], im[i]));
        }
        return rho;
    }
}
