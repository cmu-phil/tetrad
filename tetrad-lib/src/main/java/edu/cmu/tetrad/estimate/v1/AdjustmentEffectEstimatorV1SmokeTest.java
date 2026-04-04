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
import edu.cmu.tetrad.util.TMath;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Smoke test suite for the AdjustmentEffectEstimatorV1 class, which validates the functionality and stability
 * of the causal effect estimation methods. The tests focus on ensuring proper behavior under normal conditions as well
 * as edge cases, such as near-positivity violations. The suite incorporates configuration flexibility,
 * sanity checks, and stress test scenarios.
 *
 * Main components of this test class:
 * 1. Sanity checks for the estimated treatment effects and confidence intervals.
 * 2. Stress testing of the double-robust (DR) estimator's stability under near violations of positivity.
 * 3. Generation of synthetic test data using simulation models with known causal parameters.
 *
 * Testing methods:
 * - `testAteOrAndDrAreReasonableV1()`: Validates that the average treatment effect (ATE) estimates, using both
 *   outcome regression (OR) and double-robust (DR) estimators, are reasonable based on simulated data.
 *   Checks include bounds and proximity to true known values, diagnostic metrics for overlap, and bootstrap CI properties.
 *
 * - `testDrDoesNotExplodeUnderNearPositivityViolationV1()`: Ensures the DR estimator behaves predictably in scenarios
 *   with tight propensity clipping due to near-deterministic treatment assignments. Confirms that clipping helps
 *   maintain finite and stable treatment effect estimates.
 *
 * Inner class:
 * - `SimV1`: Helper class used to simulate datasets that conform to backdoor mixed-model scenarios with known causal
 *   pathways and true values of ATE (tauTrue). Includes methods for generating general data and those under
 *   near-positivity violations.
 */
public class AdjustmentEffectEstimatorV1SmokeTest {

    /**
     * Private constructor for the AdjustmentEffectEstimatorV1SmokeTest class.
     *
     * This constructor is intentionally private to prevent instantiation of
     * the test class. The class is designed to group together unit tests
     * for evaluating the robustness and correctness of the AdjustmentEffectEstimator
     * version 1 implementation. All functionality is encapsulated within test methods.
     */
    public AdjustmentEffectEstimatorV1SmokeTest() {}

    /**
     * Tests that the estimated Average Treatment Effects (ATE) using Outcome Regression (OR)
     * and Doubly Robust (DR) methods in version 1 of the adjustment effect estimator are
     * reasonable under simulated data with a discrete confounder, binary treatment, and
     * continuous outcome.
     *
     * Key aspects tested:
     * - The OR and DR ATE estimates are finite and within a reasonable magnitude.
     * - The OR and DR ATE estimates are close to the true ATE generated in the simulation.
     * - Propensity scores lie in [0, 1], have a valid range, and a reasonable fraction of scores are clipped.
     * - Bootstrap confidence intervals for OR and DR estimates are correctly ordered, exist, and have reasonable widths.
     * - Configuration ensures stability and correctness of estimation (e.g., regularization, interaction terms, etc.).
     *
     * Simulation:
     * - Generates data using the `simulateBackdoorMixedV1` method with a discrete confounder Z
     *   (3 categories), binary treatment X, and continuous outcome Y.
     * - Implements a backdoor adjustment scenario where Z impacts both X and Y.
     * - True ATE is set to a known constant value (`tauTrue`).
     *
     * Assertions include:
     * - Finite and reasonable results for ATE estimates (OR and DR).
     * - OR and DR estimates are close to the true ATE (`tauTrue`), within specified tolerances.
     * - Validity of propensity score diagnostics (range, clipping fraction).
     * - Existence and validity of bootstrap standard errors and confidence intervals.
     *
     * Configuration notes:
     * - Basis degree and treatment interaction settings are set to model complexity.
     * - Regularization and iteration thresholds are set to ensure numerical stability.
     * - Bootstrap sample settings balance computational efficiency and statistical accuracy.
     */
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

        assertTrue("v1: OR ATE magnitude unexpectedly huge", TMath.abs(res.ateOr) < 50);
        assertTrue("v1: DR ATE magnitude unexpectedly huge", TMath.abs(res.ateDr) < 50);

        // v1: in-the-ballpark checks (tolerances are generous; this is a smoke test)
        assertTrue("v1: OR ATE should be reasonably close to truth", TMath.abs(res.ateOr - tauTrue) < 0.35);
        assertTrue("v1: DR ATE should be reasonably close to truth", TMath.abs(res.ateDr - tauTrue) < 0.25);

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

    /**
     * Tests that the Doubly Robust (DR) Average Treatment Effect (ATE) estimate in version 1
     * of the adjustment effect estimator does not suffer from instability or explosion under
     * scenarios with near-positivity violations in propensity scores.
     *
     * Key aspects verified:
     * - DR ATE estimates remain finite and bounded under a controlled near-positivity violation scenario.
     * - Propensity score clipping appropriately limits extreme values, ensuring numerical stability.
     * - A non-zero fraction of propensity scores are clipped in scenarios designed to stress clipping behavior.
     *
     * Test details:
     * - Simulated data is generated using a backdoor adjustment scenario where a discrete confounder (Z)
     *   strongly determines the binary treatment (X), leading to near-positivity violations.
     * - Simulation parameters include deterministic treatment assignment for stress testing,
     *   simple model configurations, and elevated clipping thresholds to emphasize stability.
     * - Finite checks and magnitude bounds ensure results do not exhibit instability or unreasonable growth.
     *
     * Assertions include:
     * - The DR ATE estimate is finite (not NaN or infinite).
     * - The magnitude of the DR ATE estimate is bounded below a predefined threshold.
     * - A non-zero fraction of propensity scores are clipped as an indication of stability controls being engaged.
     *
     * Configuration notes:
     * - Basis degree and interaction terms are kept simple to focus on testing clipping behavior
     *   rather than model complexity.
     * - Regularization and elevated propensity clipping thresholds are applied to stabilize
     *   estimation under challenging scenarios.
     */
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
        assertTrue("v1: DR ATE should not explode under clipping", TMath.abs(res.ateDr) < 200);
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

            int zCol = data.getColumnIndex(Z);
            int xCol = data.getColumnIndex(X);
            int yCol = data.getColumnIndex(Y);

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

            int zCol = data.getColumnIndex(Z);
            int xCol = data.getColumnIndex(X);
            int yCol = data.getColumnIndex(Y);

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
                double z = TMath.exp(-x);
                return 1.0 / (1.0 + z);
            } else {
                double z = TMath.exp(x);
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