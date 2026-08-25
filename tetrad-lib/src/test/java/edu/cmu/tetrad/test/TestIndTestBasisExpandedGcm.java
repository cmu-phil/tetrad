package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestBasisExpandedGcm;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression pins for the basis-expanded GCM test.
 * <p>
 * The central pin is the sieve-repair property: with X = Z^2 + e and Y = Z^2 + f (X _||_ Y | Z true but the
 * conditional means nonlinear), a Z-side regression basis of truncation 1 reduces the test to OLS residualization,
 * which is known to spuriously reject (the residual products share the unexplained Z^2 component); enlarging only
 * the Z-side basis to cover the quadratic conditional mean repairs the size without changing anything else. This
 * pins both the failure mode and the repair, so any future change that silently re-couples the tested grid and the
 * regression sieve will be caught.
 */
public class TestIndTestBasisExpandedGcm {

    private static DataSet nonlinearNullData(int n, long seed) {
        // Z ~ N(0,1); X = Z^2 + e; Y = Z^2 + f; e, f independent. X _||_ Y | Z is TRUE.
        Random rng = new Random(seed);
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            double z = rng.nextGaussian();
            d[i][0] = z * z + 0.5 * rng.nextGaussian();
            d[i][1] = z * z + 0.5 * rng.nextGaussian();
            d[i][2] = z;
        }
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        vars.add(new ContinuousVariable("Z"));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static double pValue(DataSet ds, int trunc, int zTrunc) {
        IndTestBasisExpandedGcm test = new IndTestBasisExpandedGcm(ds, trunc, zTrunc, 1, 0.0, false);
        test.setNumMultiplierSamples(499);
        Set<Node> z = new HashSet<>();
        z.add(ds.getVariable("Z"));
        return test.checkIndependence(ds.getVariable("X"), ds.getVariable("Y"), z).getPValue();
    }

    /**
     * With zTrunc = 1 the Z-regression is linear (OLS residualization); under the nonlinear conditional mean the
     * product-of-errors bias is O(1) and the test must reject the true conditional independence. This pins the
     * pathology so its absence under the repair below is meaningful.
     */
    @Test
    public void testOlsPathologyIsPresentWithLinearSieve() {
        int rejections = 0;
        for (int rep = 0; rep < 10; rep++) {
            DataSet ds = nonlinearNullData(800, 5000 + rep);
            if (pValue(ds, 1, 1) <= 0.05) rejections++;
        }
        assertTrue("Linear-sieve GCM should spuriously reject under nonlinear conditional means; got "
                + rejections + "/10 rejections", rejections >= 8);
    }

    /**
     * Enlarging only the Z-side regression basis to cover the quadratic conditional mean repairs the size: the same
     * data, same tested grid, must now mostly accept.
     */
    @Test
    public void testRicherSieveRepairsSize() {
        int rejections = 0;
        for (int rep = 0; rep < 10; rep++) {
            DataSet ds = nonlinearNullData(800, 5000 + rep);
            if (pValue(ds, 1, 2) <= 0.05) rejections++;
        }
        assertTrue("Quadratic-sieve GCM should be approximately calibrated; got "
                + rejections + "/10 rejections", rejections <= 2);
    }

    /**
     * The default zTruncationLimit (0 = twice the grid truncation) must also be calibrated on the nonlinear null at
     * a grid truncation of 4 (which requires a Z-basis of degree 8 for the top grid cell).
     */
    @Test
    public void testDefaultZTruncationIsCalibratedOnNonlinearNull() {
        int rejections = 0;
        for (int rep = 0; rep < 10; rep++) {
            DataSet ds = nonlinearNullData(800, 7000 + rep);
            if (pValue(ds, 4, 0) <= 0.05) rejections++;
        }
        assertTrue("Default-sieve BE-GCM should be approximately calibrated on the nonlinear null; got "
                + rejections + "/10 rejections", rejections <= 2);
    }

    /**
     * The test must detect purely nonlinear dependence (Y depends on X only through X^2, invisible to linear
     * residual covariance) once the grid includes quadratic terms.
     */
    @Test
    public void testPowerAgainstQuadraticDependence() {
        Random rng = new Random(99);
        int n = 800;
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            double z = rng.nextGaussian(), x = rng.nextGaussian();
            d[i][0] = x;
            d[i][1] = z * z + 0.5 * x * x + 0.5 * rng.nextGaussian();
            d[i][2] = z;
        }
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        vars.add(new ContinuousVariable("Z"));
        DataSet ds = new BoxDataSet(new DoubleDataBox(d), vars);

        assertTrue("BE-GCM with a quadratic grid should reject quadratic dependence",
                pValue(ds, 4, 0) <= 0.01);
    }

    /**
     * Even under purely additive truth, conditioning on two or more parents makes the higher grid cells'
     * conditional means contain interaction terms the additive sieve cannot represent, and the plain test
     * over-rejects; the control-function augmentation must restore approximate calibration without losing power.
     * The comparison is paired (same datasets) so the pin is stable.
     */
    @Test
    public void testControlFunctionRestoresMultiParentCalibration() {
        int reps = 15, n = 600, k = 4;
        int rejPlain = 0, rejCf = 0;

        for (int rep = 0; rep < reps; rep++) {
            Random rng = new Random(43000 + rep);
            double[][] d = new double[n][k + 2];
            for (int i = 0; i < n; i++) {
                double sx = 0, sy = 0;
                for (int a = 0; a < k; a++) {
                    double z = rng.nextGaussian();
                    d[i][2 + a] = z;
                    sx += switch (a % 3) { case 0 -> z * z; case 1 -> Math.tanh(1.5 * z); default -> Math.abs(z); };
                    sy += switch ((a + 1) % 3) { case 0 -> z * z; case 1 -> Math.tanh(1.5 * z); default -> Math.abs(z); };
                }
                d[i][0] = sx + 0.5 * rng.nextGaussian();
                d[i][1] = sy + 0.5 * rng.nextGaussian();
            }
            List<Node> vars = new ArrayList<>();
            for (int j = 1; j <= k + 2; j++) vars.add(new ContinuousVariable("V" + j));
            DataSet ds = new BoxDataSet(new DoubleDataBox(d), vars);

            Set<Node> z = new HashSet<>();
            for (int a = 0; a < k; a++) z.add(ds.getVariable("V" + (3 + a)));

            for (int c = 0; c < 2; c++) {
                IndTestBasisExpandedGcm test = new IndTestBasisExpandedGcm(ds, 3, 0, 1, 0.0, false);
                test.setNumMultiplierSamples(199);
                test.setControlFunction(c == 1);
                double p = test.checkIndependence(ds.getVariable("V1"), ds.getVariable("V2"), z).getPValue();
                if (p <= 0.05) {
                    if (c == 0) rejPlain++;
                    else rejCf++;
                }
            }
        }

        assertTrue("Control function should not reject more often than the plain test on true CIs; plain = "
                + rejPlain + ", cf = " + rejCf, rejCf <= rejPlain);
        assertTrue("Control function should be approximately calibrated on the additive multi-parent null; got "
                + rejCf + "/" + reps + " rejections", rejCf <= 3);
    }

    /**
     * The control function must not destroy power: dependence carried purely by X^2, conditioning on two nonlinear
     * parents, must still be detected.
     */
    @Test
    public void testControlFunctionRetainsPower() {
        Random rng = new Random(77);
        int n = 600;
        double[][] d = new double[n][4];
        for (int i = 0; i < n; i++) {
            double z1 = rng.nextGaussian(), z2 = rng.nextGaussian(), x = rng.nextGaussian();
            d[i][0] = x;
            d[i][1] = z1 * z1 + Math.tanh(1.5 * z2) + 0.5 * x * x + 0.5 * rng.nextGaussian();
            d[i][2] = z1;
            d[i][3] = z2;
        }
        List<Node> vars = new ArrayList<>();
        for (int j = 1; j <= 4; j++) vars.add(new ContinuousVariable("V" + j));
        DataSet ds = new BoxDataSet(new DoubleDataBox(d), vars);

        IndTestBasisExpandedGcm test = new IndTestBasisExpandedGcm(ds, 3, 0, 1, 0.0, false);
        test.setNumMultiplierSamples(499);
        test.setControlFunction(true);
        Set<Node> z = new HashSet<>(List.of(ds.getVariable("V3"), ds.getVariable("V4")));
        assertTrue(test.checkIndependence(ds.getVariable("V1"), ds.getVariable("V2"), z).getPValue() <= 0.01);
    }

    /**
     * The control function must preserve the sieve-repair property on the single-Z nonlinear null.
     */
    @Test
    public void testControlFunctionKeepsNonlinearNullCalibrated() {
        int rejections = 0;
        for (int rep = 0; rep < 10; rep++) {
            DataSet ds = nonlinearNullData(800, 21000 + rep);
            IndTestBasisExpandedGcm test = new IndTestBasisExpandedGcm(ds, 4, 0, 1, 0.0, false);
            test.setNumMultiplierSamples(499);
            test.setControlFunction(true);
            Set<Node> z = new HashSet<>();
            z.add(ds.getVariable("Z"));
            double p = test.checkIndependence(ds.getVariable("X"), ds.getVariable("Y"), z).getPValue();
            if (p <= 0.05) rejections++;
        }
        assertTrue("Control-function BE-GCM should stay calibrated on the nonlinear null; got "
                + rejections + "/10 rejections", rejections <= 2);
    }

    /**
     * P-values must be deterministic per independence fact regardless of call order (the multiplier bootstrap is
     * seeded from the fact), so that search caching and repeated checks are consistent.
     */
    @Test
    public void testDeterminismAcrossCallOrder() {
        DataSet ds = nonlinearNullData(400, 12345);
        IndTestBasisExpandedGcm test = new IndTestBasisExpandedGcm(ds, 3, 0, 1, 0.0, true);
        test.setNumMultiplierSamples(199);

        Set<Node> z = new HashSet<>();
        z.add(ds.getVariable("Z"));

        double p1 = test.checkIndependence(ds.getVariable("X"), ds.getVariable("Y"), z).getPValue();
        // Interleave a different fact, then repeat the first.
        test.checkIndependence(ds.getVariable("X"), ds.getVariable("Z"), new HashSet<>()).getPValue();
        double p2 = test.checkIndependence(ds.getVariable("X"), ds.getVariable("Y"), z).getPValue();

        assertEquals("Same fact must give the same p-value regardless of call order", p1, p2, 0.0);
    }
}
