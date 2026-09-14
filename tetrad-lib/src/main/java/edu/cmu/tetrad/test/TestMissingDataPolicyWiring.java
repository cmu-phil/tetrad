///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScore;
import edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Checks that a score wrapper's missing-data policies agree across the three independent places a policy has to be
 * admitted before a user can reach it:
 *
 * <ol>
 *   <li>the score class's own policy switch, which throws on a policy it does not implement;</li>
 *   <li>the wrapper's {@code getParameters()}, which is what an interface reads to decide whether to render the
 *       control at all;</li>
 *   <li>the native-policy set the wrapper passes to {@link edu.cmu.tetrad.data.missing.MissingDataUtils}{@code
 *       .gate}, which rejects anything outside it before the score is ever constructed.</li>
 * </ol>
 *
 * <p>Nothing else checks that those three agree, and they have drifted: EM covariance support was added to
 * {@code DegenerateGaussianScore} and {@code BasisFunctionBicScore}, and the parameter was declared, while both
 * wrappers still passed {@code Set.of("testwise")} to the gate. The score worked when constructed directly -- as
 * {@code BossMissingDataStudy} does -- and was unreachable from the interface, which is the failure this test
 * exists to catch.</p>
 *
 * <p>The expected policies are written out per wrapper below rather than discovered, so the test fails in both
 * directions: a policy that should work but throws, and a policy that should be refused but silently succeeds.
 * Adding support to a score means updating this table, which is the point.</p>
 *
 * @author josephramsey
 */
public class TestMissingDataPolicyWiring {

    /**
     * Small on purpose. This test is about reachability, not about estimates, and the basis-function score is
     * superlinear in the variable count -- at chem's 35 variables with truncation 3 a single construction runs for
     * minutes, which would make the test useless as a fast check.
     */
    private static final int NUM_VARS = 8;

    /** Rows generated. Enough that no family is empty under test-wise deletion at the rate below. */
    private static final int NUM_ROWS = 300;

    /** Missingness rate per variable, low enough that complete cases survive for the listwise arm. */
    private static final double MISSING_RATE = 0.10;

    /**
     * Default constructor for the TestMissingDataPolicyWiring class.
     */
    public TestMissingDataPolicyWiring() {

    }

    /**
     * Policies each wrapper is expected to accept on mixed data with missing values. Update when a score gains or
     * loses support.
     */
    private static Map<String, Set<String>> expected() {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("DegenerateGaussianBicScore", new LinkedHashSet<>(Arrays.asList("listwise", "testwise", "em")));
        expected.put("BasisFunctionBicScore", new LinkedHashSet<>(Arrays.asList("listwise", "testwise", "em")));
        expected.put("SemBicScore", new LinkedHashSet<>(Arrays.asList("listwise", "testwise", "em")));
        return expected;
    }

    /** Every policy the parameter offers in its dropdown, minus the ones no score implements as a score. */
    private static final List<String> ALL_POLICIES = Arrays.asList("listwise", "testwise", "em");

    /**
     * Each wrapper accepts exactly the policies it is expected to, through the same call path an interface uses.
     */
    @Test
    public void policiesReachableThroughWrapper() {
        Map<String, Set<String>> expected = expected();
        List<String> failures = new ArrayList<>();

        for (ScoreWrapper wrapper : wrappers()) {
            String name = wrapper.getClass().getSimpleName();
            DataSet data = data(!name.equals("SemBicScore"));

            for (String policy : ALL_POLICIES) {
                boolean shouldWork = expected.get(name).contains(policy);
                String outcome = attempt(wrapper, data, policy);

                if (shouldWork && outcome != null) {
                    failures.add(name + " should accept \"" + policy + "\" but threw: " + outcome);
                } else if (!shouldWork && outcome == null) {
                    failures.add(name + " accepted \"" + policy + "\", which it is not expected to support;"
                                 + " either the score gained support and this table is stale, or the gate is"
                                 + " admitting a policy the score does not implement.");
                }
            }
        }

        if (!failures.isEmpty()) fail(String.join("\n", failures));
    }

    /**
     * A wrapper that gates a policy must also declare the parameters that select it, or the control never renders
     * and the policy is unreachable however well the score implements it.
     */
    @Test
    public void policyParametersAreDeclared() {
        for (ScoreWrapper wrapper : wrappers()) {
            List<String> params = wrapper.getParameters();
            String name = wrapper.getClass().getSimpleName();

            assertTrue(name + " does not declare " + Params.MISSING_DATA_POLICY
                       + ", so no interface will render the control.",
                    params.contains(Params.MISSING_DATA_POLICY));

            assertTrue(name + " accepts a policy whose behavior depends on " + Params.MISSING_ESS_MODE
                       + " but does not declare it, so the mode silently stays at its default.",
                    params.contains(Params.MISSING_ESS_MODE));
        }
    }

    /**
     * Attempts to build the score the way an interface would.
     *
     * @return null when it built, or the first sentence of the failure otherwise.
     */
    private static String attempt(ScoreWrapper wrapper, DataSet data, String policy) {
        Parameters params = new Parameters();
        params.set(Params.MISSING_DATA_POLICY, policy);
        params.set(Params.MISSING_ESS_MODE, "meanPairwise");
        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.TRUNCATION_LIMIT, 2);
        params.set(Params.SINGULARITY_LAMBDA, 0.0);
        params.set(Params.PRECOMPUTE_COVARIANCES, true);

        try {
            wrapper.getScore(data, params);
            return null;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            return message.split("\\.")[0];
        }
    }

    private static List<ScoreWrapper> wrappers() {
        return Arrays.asList(new DegenerateGaussianBicScore(), new BasisFunctionBicScore(), new SemBicScore());
    }

    /**
     * A small dataset with missing values, mixed or continuous. Values are independent; this test is about whether
     * a score can be built, not about what it estimates.
     */
    private static DataSet data(boolean mixed) {
        Random random = new Random(492025L);

        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < NUM_VARS; j++) {
            vars.add(mixed && j % 2 == 0
                    ? new DiscreteVariable("D" + j, 3)
                    : new ContinuousVariable("C" + j));
        }

        MixedDataBox box = new MixedDataBox(vars, NUM_ROWS);

        for (int i = 0; i < NUM_ROWS; i++) {
            for (int j = 0; j < NUM_VARS; j++) {
                boolean discrete = vars.get(j) instanceof DiscreteVariable;

                if (random.nextDouble() < MISSING_RATE) {
                    box.set(i, j, discrete ? DiscreteVariable.MISSING_VALUE : Double.NaN);
                } else {
                    box.set(i, j, discrete ? random.nextInt(3) : random.nextGaussian());
                }
            }
        }

        return new BoxDataSet(box, vars);
    }
}
