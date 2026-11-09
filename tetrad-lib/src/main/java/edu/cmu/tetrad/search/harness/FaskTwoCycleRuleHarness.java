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

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;

/**
 * Size and power of FASK's two-cycle test, {@link Fask#twoCycleTest}, under both
 * {@link Fask.TwoCycleRule} settings, on two-variable systems with six noise shapes.
 *
 * <p>Three systems are run per cell, all in closed form from the reduced form of the
 * two-variable system, so no search machinery is involved:
 * <ul>
 *   <li><b>acyclic</b> X -&gt; Y with coefficient 0.6. Every acceptance is a false positive, so
 *       this column is the test's size and should sit at alpha.</li>
 *   <li><b>2-cycle (0.6, 0.4)</b>, a same-sign cycle, and <b>2-cycle (0.6, -0.4)</b>, a
 *       mixed-sign cycle. Acceptances here are true positives, so these columns are power.</li>
 * </ul>
 *
 * <p>What the two rules give, measured:
 * {@link Fask.TwoCycleRule#CORRELATION_SHIFT} has no size control - its false-positive rate
 * climbs to 1 with sample size for every noise shape but the strongly right-skewed one - and its
 * apparent power on Gaussian data is spurious, a Gaussian two-cycle being unidentifiable from
 * observational data. {@link Fask.TwoCycleRule#SLOPE_SHIFT} compares regression slopes, which
 * acyclicity leaves invariant under selection on the cause; measured, its size is 0.000 for
 * exponential, Gumbel, Beta and Gaussian noise at every sample size here, and inflated but
 * bounded - roughly 0.07 to 0.23, rather than climbing to 1 - for uniform and Laplace noise,
 * where the normal-theory standard error evidently does not describe the sampling distribution
 * of the slope contrast. It costs power at moderate n: on exponential noise the same-sign cycle
 * is detected at 0.358 at n = 1000 against the correlation rule's 1.000, and needs n = 5000 to
 * reach 0.998. The mixed-sign cycle is detected far earlier, 0.995 at n = 1000.
 *
 * @see Fask.TwoCycleRule
 * @see CpwLingamStudy
 */
public final class FaskTwoCycleRuleHarness {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private static final int REPS = Integer.parseInt(System.getProperty("reps", "400"));
    private static final long BASE_SEED = Long.parseLong(System.getProperty("seed", "9001"));

    private static final double TWO_CYCLE_ALPHA = 0.01;

    private static final int[] SAMPLE_SIZES = {200, 1000, 5000, 20000};

    private static final String[] NOISES = {"Exp", "Gumbel", "Beta2-5", "Uniform", "Laplace", "Normal"};

    /** The systems: an acyclic edge, then a same-sign and a mixed-sign two-cycle. */
    private static final double[][] SYSTEMS = {{0.6, 0.0}, {0.6, 0.4}, {0.6, -0.4}};

    private static final String[] SYSTEM_NAMES = {"acyclic (size)", "2cyc 0.6,0.4", "2cyc 0.6,-0.4"};

    private FaskTwoCycleRuleHarness() {
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    /**
     * Prints the acceptance rate of the two-cycle test for each rule, system, noise shape and
     * sample size.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        double cutoff = StatUtils.getZForAlpha(TWO_CYCLE_ALPHA);

        System.out.printf("FASK two-cycle test: acceptance rate at alpha=%s over %d replicates.%n",
                TWO_CYCLE_ALPHA, REPS);
        System.out.println("The acyclic column is SIZE (want " + TWO_CYCLE_ALPHA
                + "); the two 2-cycle columns are POWER (want high).");
        System.out.println("Gaussian noise makes a two-cycle unidentifiable, so power there SHOULD be low.");

        for (Fask.TwoCycleRule rule : Fask.TwoCycleRule.values()) {
            System.out.println();
            System.out.println("=".repeat(78));
            System.out.println("RULE: " + rule);
            System.out.println("=".repeat(78));

            System.out.printf("%-9s %7s", "noise", "n");
            for (String name : SYSTEM_NAMES) System.out.printf("%17s", name);
            System.out.println();
            System.out.println("-".repeat(68));

            for (String noise : NOISES) {
                for (int n : SAMPLE_SIZES) {
                    System.out.printf("%-9s %7d", noise, n);

                    for (double[] system : SYSTEMS) {
                        int accepted = 0;

                        for (int rep = 0; rep < REPS; rep++) {
                            double[][] d = simulate(noise, system[0], system[1], n,
                                    BASE_SEED + 31L * rep + 7L * n + noise.hashCode());
                            if (Fask.twoCycleTest(d[0], d[1], new double[0][], cutoff, rule)) accepted++;
                        }

                        System.out.printf("%17.3f", accepted / (double) REPS);
                    }

                    System.out.println();
                }
                System.out.println();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Data generation
    // -----------------------------------------------------------------------

    /**
     * The reduced form of x = b*y + e1, y = a*x + e2, which is an acyclic X -&gt; Y when b is 0
     * and a stable two-cycle when |ab| &lt; 1. Noises are i.i.d. draws standardized to mean 0 and
     * variance 1, so only the shape of the noise varies across families. Columns are returned
     * standardized and otherwise uncorrected; the test applies its own skew correction.
     */
    private static double[][] simulate(String noise, double a, double b, int n, long seed) {
        RandomUtil.getInstance().setSeed(seed);

        double[] x = new double[n];
        double[] y = new double[n];
        double det = 1.0 - a * b;

        for (int r = 0; r < n; r++) {
            double e1 = draw(noise);
            double e2 = draw(noise);
            x[r] = (e1 + b * e2) / det;
            y[r] = (a * e1 + e2) / det;
        }

        Fask.standardize(x);
        Fask.standardize(y);
        return new double[][]{x, y};
    }

    /** One draw from the named family, centered and scaled to mean 0 and variance 1. */
    private static double draw(String noise) {
        RandomUtil random = RandomUtil.getInstance();

        return switch (noise) {
            case "Exp" -> random.nextExponential(1) - 1;
            case "Gumbel" -> (random.nextGumbel(0, 1) - 0.5772157) / 1.2825499;
            case "Beta2-5" -> (random.nextBeta(2, 5) - 0.2857143) / 0.1597191;
            case "Uniform" -> random.nextUniform(-1, 1) * 1.7320508;
            case "Laplace" -> random.nextLaplace(0, 1) * 0.7071068;
            case "Normal" -> random.nextGaussian(0, 1);
            default -> throw new IllegalArgumentException("Unknown noise: " + noise);
        };
    }
}
