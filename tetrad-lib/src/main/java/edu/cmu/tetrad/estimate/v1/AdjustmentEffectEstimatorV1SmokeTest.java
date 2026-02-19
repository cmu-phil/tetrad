// v1: JUnit4 smoke test for AdjustmentEffectEstimatorV1 on mixed data (continuous + discrete).
// v1: Tests OR + DR ATE estimates are in the ballpark on a simple backdoor setup with a discrete confounder.
// v1: Stress test: near-positivity violation to ensure DR doesn't explode (propensity clipping).
//
// Assumptions (v1):
// - JUnit4 on classpath (org.junit.Test, org.junit.Assert.*)
// - AdjustmentEffectEstimatorV1 is in package edu.cmu.tetrad.estimate.v1
//
// NOTE (v1): You may need to adapt buildDataSetV1() to your branch’s DataSet/DataBox types.
// If build fails, paste the error and I’ll adjust to your concrete dataset constructors.

package edu.cmu.tetrad.estimate.v1;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class AdjustmentEffectEstimatorV1SmokeTest {

    @Test
    public void testAteOrAndDrAreReasonableV1() {
        // v1: simulate data with a discrete confounder Z (3 categories), binary treatment X, continuous Y.
        int n = 4000;
        long seed = 123456789L;

        SimV1 sim = SimV1.simulateBackdoorMixedV1(n, seed);

        DataSet data = sim.data;
        Node X = sim.x;
        Node Y = sim.y;
        Set<Node> Z = Collections.singleton(sim.z);

        AdjustmentEffectEstimatorV1.ConfigV1 cfg = new AdjustmentEffectEstimatorV1.ConfigV1();
        cfg.basisDegree = 3;                      // v1
        cfg.includeTreatmentInteractions = true;  // v1
        cfg.bootstrapB = 100;                     // v1: keep test fast
        cfg.ciAlpha = 0.05;                       // v1
        cfg.propensityClipEps = 0.01;             // v1
        cfg.maxIrlsIter = 50;                     // v1
        cfg.irlsTol = 1e-8;                       // v1
        cfg.ridge = 1e-8;                         // v1

        AdjustmentEffectEstimatorV1.EffectEstimateResultV1 res =
                AdjustmentEffectEstimatorV1.estimateAteV1(data, X, Y, Z, cfg);

        // v1: true ATE in this simulation is approximately constant = tauTrue
        double tauTrue = sim.tauTrue;

        // v1: sanity checks (non-NaN, not huge)
        assertTrue("v1: OR ATE should be finite", Double.isFinite(res.ateOr));
        assertTrue("v1: DR ATE should be finite", Double.isFinite(res.ateDr));

        assertTrue("v1: OR ATE magnitude unexpectedly huge", Math.abs(res.ateOr) < 50);
        assertTrue("v1: DR ATE magnitude unexpectedly huge", Math.abs(res.ateDr) < 50);

        // v1: in-the-ballpark checks (tolerances are generous; this is a smoke test)
        assertTrue("v1: OR ATE should be reasonably close to truth", Math.abs(res.ateOr - tauTrue) < 0.35);
        assertTrue("v1: DR ATE should be reasonably close to truth", Math.abs(res.ateDr - tauTrue) < 0.25);

        // v1: overlap diagnostics
        assertTrue("v1: propensity range must be within [0,1]", res.minProp >= 0.0 && res.maxProp <= 1.0);
        assertTrue("v1: propensity min must be < max", res.minProp < res.maxProp);
        assertTrue("v1: clipped fraction in [0,1]", res.fracClipped >= 0.0 && res.fracClipped <= 1.0);

        // v1: bootstrap CIs exist
        assertTrue("v1: OR bootstrap SE should be finite", Double.isFinite(res.seOrBoot));
        assertTrue("v1: DR bootstrap SE should be finite", Double.isFinite(res.seDrBoot));
        assertTrue("v1: DR CI order", res.ciLoDr <= res.ciHiDr);
        assertTrue("v1: OR CI order", res.ciLoOr <= res.ciHiOr);

        // v1: CI width non-trivial and not absurd
        assertTrue("v1: DR CI width should be > 0", (res.ciHiDr - res.ciLoDr) > 0.01);
        assertTrue("v1: DR CI width should not be absurd", (res.ciHiDr - res.ciLoDr) < 5.0);
    }

    @Test
    public void testDrDoesNotExplodeUnderNearPositivityViolationV1() {
        // v1: Make treatment almost deterministic given Z to stress propensity clipping.
        int n = 5000;
        long seed = 987654321L;

        SimV1 sim = SimV1.simulateBackdoorMixedNearViolationV1(n, seed);

        DataSet data = sim.data;
        Node X = sim.x;
        Node Y = sim.y;
        Set<Node> Z = Collections.singleton(sim.z);

        AdjustmentEffectEstimatorV1.ConfigV1 cfg = new AdjustmentEffectEstimatorV1.ConfigV1();
        cfg.basisDegree = 2;                      // v1: keep model simple
        cfg.includeTreatmentInteractions = true;  // v1
        cfg.bootstrapB = 0;                       // v1: keep fast; stability only
        cfg.propensityClipEps = 0.02;             // v1: more clipping for stress test
        cfg.ridge = 1e-6;                         // v1: help IRLS in separation-like cases

        AdjustmentEffectEstimatorV1.EffectEstimateResultV1 res =
                AdjustmentEffectEstimatorV1.estimateAteV1(data, X, Y, Z, cfg);

        assertTrue("v1: DR ATE should be finite under clipping", Double.isFinite(res.ateDr));
        assertTrue("v1: DR ATE should not explode under clipping", Math.abs(res.ateDr) < 200);
        assertTrue("v1: should clip some propensities in near-violation scenario", res.fracClipped > 0.0);
    }

    // =========================
    // v1: Simulation helpers
    // =========================

    private static final class SimV1 {
        final DataSet data;
        final Node x;
        final Node y;
        final Node z;
        final double tauTrue;

        private SimV1(DataSet data, Node x, Node y, Node z, double tauTrue) {
            this.data = data;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tauTrue = tauTrue;
        }

        static SimV1 simulateBackdoorMixedV1(int n, long seed) {
            Random rng = new Random(seed);

            // v1: variables
            DiscreteVariable Z = new DiscreteVariable("Z", 3); // v1: categories {0,1,2}
            DiscreteVariable X = new DiscreteVariable("X", 2); // v1: {0,1}
            ContinuousVariable Y = new ContinuousVariable("Y");

            List<Node> vars = Arrays.asList(Z, X, Y);

            // v1: build dataset (mixed)
            DataSet data = buildDataSetV1(vars, n);

            // v1: true ATE (constant in this construction)
            double tauTrue = 1.25;

            // v1: Z -> X (propensity depends on Z); Z -> Y (backdoor); X -> Y (causal effect)
            double[] a = new double[]{-0.5, 0.4, 1.0};

            int zCol = data.getColumn(Z);
            int xCol = data.getColumn(X);
            int yCol = data.getColumn(Y);

            for (int i = 0; i < n; i++) {
                int z = rng.nextInt(3);
                double p = sigmoid(a[z]);
                int x = (rng.nextDouble() < p) ? 1 : 0;

                double fz = (z == 0) ? -0.8 : (z == 1 ? 0.3 : 1.1);
                double eps = rng.nextGaussian();
                double y = tauTrue * x + fz + 0.15 * (eps * eps - 1.0) + 0.5 * eps;

                data.setInt(i, zCol, z);
                data.setInt(i, xCol, x);
                data.setDouble(i, yCol, y);
            }

            return new SimV1(data, X, Y, Z, tauTrue);
        }

        static SimV1 simulateBackdoorMixedNearViolationV1(int n, long seed) {
            Random rng = new Random(seed);

            DiscreteVariable Z = new DiscreteVariable("Z", 3);
            DiscreteVariable X = new DiscreteVariable("X", 2);
            ContinuousVariable Y = new ContinuousVariable("Y");
            List<Node> vars = Arrays.asList(Z, X, Y);
            DataSet data = buildDataSetV1(vars, n);

            double tauTrue = 1.0;

            // v1: near-positivity violation: Z strongly determines X
            double[] a = new double[]{-4.6, 0.0, 4.6};

            int zCol = data.getColumn(Z);
            int xCol = data.getColumn(X);
            int yCol = data.getColumn(Y);

            for (int i = 0; i < n; i++) {
                int z = rng.nextInt(3);
                double p = sigmoid(a[z]);
                int x = (rng.nextDouble() < p) ? 1 : 0;

                double fz = (z == 0) ? -1.0 : (z == 1 ? 0.0 : 1.0);
                double eps = rng.nextGaussian();
                double y = tauTrue * x + fz + eps;

                data.setInt(i, zCol, z);
                data.setInt(i, xCol, x);
                data.setDouble(i, yCol, y);
            }

            return new SimV1(data, X, Y, Z, tauTrue);
        }

        private static double sigmoid(double x) {
            if (x >= 0) {
                double z = Math.exp(-x);
                return 1.0 / (1.0 + z);
            } else {
                double z = Math.exp(x);
                return z / (1.0 + z);
            }
        }
    }

    // =========================
    // v1: DataSet construction adapter
    // =========================

    /**
     * v1: Builds a mixed DataSet with the given variables and row count.
     *
     * v1 NOTE: Adjust to your branch if needed (MixedDataBox availability varies).
     */
    private static DataSet buildDataSetV1(List<Node> vars, int n) {
        try {
            // v1: preferred when present
            DataBox box = new MixedDataBox(vars, n);
            return new BoxDataSet(box, vars);
        } catch (Throwable t) {
            try {
                // v1: fallback
                DataBox box = new VerticalDoubleDataBox(n, vars.size());
                return new BoxDataSet(box, vars);
            } catch (Throwable t2) {
                throw new RuntimeException("v1: Could not construct DataSet; adapt buildDataSetV1() to your branch.", t2);
            }
        }
    }
}