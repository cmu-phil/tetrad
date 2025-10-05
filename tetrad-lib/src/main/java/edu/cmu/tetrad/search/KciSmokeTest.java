/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.Kci;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.ejml.UtilEjml.assertTrue;

/**
 * The {@code KciSmokeTest} class contains unit tests to evaluate the performance and correctness of the {@code Kci}
 * class, specifically focusing on conditional independence tests.
 *
 * <p>It provides multiple test methods to verify the behavior of the Kci algorithm
 * under different scenarios, such as independent and dependent data relationships, both with and without approximation
 * techniques.</p>
 *
 * <p>The tests include:</p>
 * <ul>
 *   <li><b>gammaApprox_independent_vs_dependent</b>: Verifies the conditional independence
 *       test using an approximate gamma distribution method for both independent and
 *       dependent cases.</li>
 *   <li><b>nonApprox_independent_vs_dependent</b>: Tests the deterministic behavior and
 *       accuracy of the non-approximate (permutation-based) conditional independence
 *       test with independent and dependent data.</li>
 *   <li><b>permutation_independent_vs_dependent</b>: Validates the conditional independence
 *       test using the permutation-based method under different data relationships.</li>
 * </ul>
 *
 * <p>Utility methods:</p>
 * <ul>
 *   <li><b>makeDataVxN</b>: Generates simulated data for tests, with rows representing
 *       variables (X, Y, Z) and columns representing samples. The method allows
 *       controlling dependency with noise levels.</li>
 *   <li><b>makeKci</b>: Initializes and configures the {@code Kci} instance with given
 *       data and parameters for testing independence.</li>
 * </ul>
 */
public class KciSmokeTest {

    /**
     * Default constructor for the {@code KciSmokeTest} class. This constructor initializes an instance of the
     * {@code KciSmokeTest} class to perform unit tests on the {@code Kci} framework.
     */
    public KciSmokeTest() {

    }

    private static SimpleMatrix makeDataVxN(int n, double depNoise) {
        Random r = new Random(42);
        double[] x = new double[n], z = new double[n], e = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = r.nextGaussian();
            z[i] = r.nextGaussian();
            e[i] = r.nextGaussian();
        }
        // independent case uses depNoise = +Inf sentinel (we'll overwrite y below)
        if (Double.isInfinite(depNoise)) System.arraycopy(e, 0, y, 0, n);
        else for (int i = 0; i < n; i++) y[i] = x[i] + depNoise * e[i];  // strong dependence if depNoise is small

        // rows = variables (X,Y,Z), cols = samples
        SimpleMatrix M = new SimpleMatrix(3, n);
        for (int j = 0; j < n; j++) {
            M.set(0, j, x[j]);
            M.set(1, j, y[j]);
            M.set(2, j, z[j]);
        }
        return M;
    }

    private static Kci makeKci(SimpleMatrix data, boolean approximate) {
        Node X = new GraphNode("X"), Y = new GraphNode("Y"), Z = new GraphNode("Z");
        Map<Node, Integer> map = Map.of(X, 0, Y, 1, Z, 2);
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < data.getNumCols(); i++) rows.add(i);
        Kci k = new Kci(data, map, null, rows);
        k.setApproximate(approximate);
        k.setKernelType(Kci.KernelType.GAUSSIAN);
        k.setEpsilon(1e-3);
        k.setScalingFactor(1.0);
        k.setNumPermutations(500); // lighter permutation for the test
        return k;
    }

    /**
     * Tests the accuracy and behavior of the gamma approximation method for conditional independence testing using the
     * {@code Kci} framework. This method evaluates two scenarios: independent and dependent relationships between
     * variables.
     *
     * <p>The test is conducted with the following configurations:</p>
     * <ul>
     *   <li><b>Independent Test</b>: Variable Y is evaluated as being independent of X given Z.
     *     <ul>
     *       <li>The method ensures that the p-value is within valid bounds and fails
     *           to reject the null hypothesis of independence when p > alpha.</li>
     *     </ul>
     *   </li>
     *   <li><b>Dependent Test</b>: Variable Y is evaluated as linearly dependent on X with
     *       noise (Y = X + 0.05 * e, strong dependence).
     *     <ul>
     *       <li>The method ensures that the p-value indicates strong significance
     *           (p &lt; alpha) for the dependent case.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p><b>Assertions:</b></p>
     * <ul>
     *   <li>The p-value for the independent case lies within the acceptable [0, 1] range.</li>
     *   <li>The p-value for the independent case does not indicate rejection of the null
     *       hypothesis (p &gt; alpha).</li>
     *   <li>The p-value for the dependent case is significant (p &lt; alpha), confirming
     *       the method's ability to detect dependence.</li>
     * </ul>
     */
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

    /**
     * Tests the accuracy and behavior of the non-approximate conditional independence testing method using the
     * {@code Kci} framework. This method evaluates two scenarios: independent and dependent relationships between
     * variables.
     *
     * <p>The test utilizes a fixed random seed for reproducibility and performs the following
     * configurations:</p>
     *
     * <ul>
     *   <li><b>Independent Test</b>: Evaluates whether variable Y is independent of X given Z.
     *     <ul>
     *       <li>Ensures the p-value lies within the valid range [0,1].</li>
     *       <li>Confirms the p-value does not indicate a rejection of independence (p > alpha).</li>
     *     </ul>
     *   </li>
     *   <li><b>Dependent Test</b>: Evaluates a scenario where variable Y is linearly dependent on X
     *       (Y = X + 0.05 * e, with strong dependence).
     *     <ul>
     *       <li>Confirms the p-value indicates strong significance (p &lt; alpha), demonstrating
     *           the method's sensitivity to dependence.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p><b>Assertions:</b></p>
     * <ul>
     *   <li>The p-value for the independent case lies within the range [0,1].</li>
     *   <li>The p-value for the independent case does not reject the null hypothesis (p > alpha).</li>
     *   <li>The p-value for the dependent case is small (e.g., p &lt; alpha), indicating significance.</li>
     * </ul>
     */
    @Test
    public void nonApprox_independent_vs_dependent() {
        double alpha = 0.01;

        // independent: Y = e  (Y â X | Z)
        Kci kInd = makeKci(makeDataVxN(600, Double.POSITIVE_INFINITY), /*approximate=*/false);
        kInd.setNumPermutations(4000);    // min p â 1/4001 â 2.5e-4
        kInd.rng = new Random(123);     // determinism
        double pInd = kInd.isIndependenceConditional(new GraphNode("X"), new GraphNode("Y"),
                List.of(new GraphNode("Z")), alpha);
        assertTrue(0.0 <= pInd && pInd <= 1.0, "p in [0,1]");
        assertTrue(pInd > alpha, "independent should fail to reject (p > alpha)");

        // dependent: Y = X + 0.05*e  (strong dependence)
        Kci kDep = makeKci(makeDataVxN(600, 0.05), /*approximate=*/false);
        kDep.setNumPermutations(4000);
        kDep.rng = new Random(123);
        double pDep = kDep.isIndependenceConditional(new GraphNode("X"), new GraphNode("Y"),
                List.of(new GraphNode("Z")), alpha);
        // Can't be smaller than 1/(B+1); use a realistic bound or just alpha
        assertTrue(pDep < 1e-3, "dependent should yield small p (with 4k perms)");
        // or simply:
        // assertTrue(pDep < alpha, "dependent should be significant");
    }

    /**
     * Tests the behavior and accuracy of the permutation-based conditional independence testing method in the
     * {@code Kci} framework. This method evaluates two scenarios: independent and dependent relationships between
     * variables.
     *
     * <p>The test utilizes the following configuration:</p>
     * <ul>
     *   <li>A fixed random seed for reproducibility.</li>
     *   <li>The permutation testing method to assess variable relationships.</li>
     * </ul>
     *
     * <p>The test performs the following scenarios:</p>
     * <ol>
     *   <li><b>Independent Test</b>:
     *     <ul>
     *       <li>Variable Y is evaluated as being independent of X given Z.</li>
     *       <li>Ensures that the p-value lies within the valid range [0, 1].</li>
     *       <li>Confirms that the p-value fails to reject the null hypothesis of independence
     *           when p > alpha (indicating no significant dependence).</li>
     *     </ul>
     *   </li>
     *   <li><b>Dependent Test</b>:
     *     <ul>
     *       <li>Variable Y is evaluated as linearly dependent on X with noise (Y = X + 0.05 * e, strong dependence).</li>
     *       <li>Ensures that the p-value indicates strong significance (p &lt; alpha), reflecting
     *           the method's ability to detect dependence robustly.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p><b>Assertions:</b></p>
     * <ul>
     *   <li>For the independent case, the p-value lies within [0, 1] and satisfies p &gt; alpha.</li>
     *   <li>For the dependent case, the p-value is highly significant, confirming p &lt; alpha.</li>
     * </ul>
     */
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
