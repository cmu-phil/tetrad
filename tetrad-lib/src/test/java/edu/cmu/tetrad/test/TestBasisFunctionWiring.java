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
import edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScoreTabular;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * Verifies the 2026-8 wiring fixes to the basis function score wrappers: the BF-BIC wrapper
 * reads the singularity lambda from Params.SINGULARITY_LAMBDA (previously it read
 * REGULARIZATION_LAMBDA, so callers such as py-tetrad that set SINGULARITY_LAMBDA were silently
 * ignored), it forwards DO_ONE_EQUATION_ONLY to the score (previously accepted but unused), and
 * the BasisFunctionBicScoreTabular wrapper exists for the full-sample score (referenced by
 * py-tetrad's use_basis_function_bic_fs).
 * <p>
 * These tests fail against the pre-fix classes: the parameter-list and flow-through tests find
 * the old keys and the dead flag, and the Tabular test fails to load the class at all.
 */
public class TestBasisFunctionWiring {

    /**
     * The BF-BIC wrapper's declared parameters must include SINGULARITY_LAMBDA and
     * DO_ONE_EQUATION_ONLY (and not the stale REGULARIZATION_LAMBDA).
     */
    @Test
    public void testScoreWrapperParameterKeys() {
        List<String> params = new BasisFunctionBicScore().getParameters();
        System.out.printf("BF-BIC wrapper parameters: %s%n", params);
        assertTrue("Wrapper should declare SINGULARITY_LAMBDA",
                params.contains(Params.SINGULARITY_LAMBDA));
        assertTrue("Wrapper should declare DO_ONE_EQUATION_ONLY",
                params.contains(Params.DO_ONE_EQUATION_ONLY));
        assertTrue("Wrapper should no longer declare REGULARIZATION_LAMBDA",
                !params.contains(Params.REGULARIZATION_LAMBDA));
    }

    /**
     * DO_ONE_EQUATION_ONLY must flow through the wrapper: with the flag on, only the first
     * embedded component of each variable is scored, so local scores must differ from the
     * default on nonlinear data. Fails against the pre-fix wrapper, which never forwarded the
     * flag.
     */
    @Test
    public void testDoOneEquationOnlyFlowsThrough() {
        DataSet data = nonlinearData(new Random(72), 800);

        Parameters pAll = baseParams();
        pAll.set(Params.DO_ONE_EQUATION_ONLY, false);
        Score sAll = new BasisFunctionBicScore().getScore(data, pAll);

        Parameters pOne = baseParams();
        pOne.set(Params.DO_ONE_EQUATION_ONLY, true);
        Score sOne = new BasisFunctionBicScore().getScore(data, pOne);

        double a = localScore(sAll, 1, 0);
        double b = localScore(sOne, 1, 0);
        System.out.printf("doOneEquationOnly flow: score(all equations) = %.6f, "
                + "score(one equation) = %.6f%n", a, b);
        assertTrue("Setting DO_ONE_EQUATION_ONLY should change the local score; got " + a
                + " both ways", Math.abs(a - b) > 1e-6);
    }

    /**
     * SINGULARITY_LAMBDA must flow through the wrapper: a large ridge changes the computed
     * likelihoods, so local scores must differ from lambda = 0. Fails against the pre-fix
     * wrapper, which read a different parameter key.
     */
    @Test
    public void testSingularityLambdaFlowsThrough() {
        DataSet data = nonlinearData(new Random(73), 800);

        Parameters p0 = baseParams();
        p0.set(Params.SINGULARITY_LAMBDA, 0.0);
        Score s0 = new BasisFunctionBicScore().getScore(data, p0);

        Parameters p1 = baseParams();
        p1.set(Params.SINGULARITY_LAMBDA, 10.0);
        Score s1 = new BasisFunctionBicScore().getScore(data, p1);

        double a = localScore(s0, 1, 0);
        double b = localScore(s1, 1, 0);
        System.out.printf("singularityLambda flow: score(lambda = 0) = %.6f, "
                + "score(lambda = 10) = %.6f%n", a, b);
        assertTrue("Setting SINGULARITY_LAMBDA should change the local score; got " + a
                + " both ways", Math.abs(a - b) > 1e-6);
    }

    /**
     * The BasisFunctionBicScoreTabular wrapper must exist and produce a working full-sample
     * score. Fails against the pre-fix code base, where the class was absent (py-tetrad's
     * use_basis_function_bic_fs referenced it and broke).
     */
    @Test
    public void testTabularWrapperExistsAndWorks() {
        DataSet data = nonlinearData(new Random(74), 800);
        Score score = new BasisFunctionBicScoreTabular().getScore(data, baseParams());
        double a = localScore(score, 1, 0);
        double b = localScore(score, 1);
        System.out.printf("Tabular wrapper: score(Y | X) = %.6f, score(Y) = %.6f%n", a, b);
        assertTrue("Full-sample score should be finite", Double.isFinite(a) && Double.isFinite(b));
        assertTrue("On dependent data, conditioning on the parent should improve the score",
                a > b);
    }

    // ------------------------------------------------------------------------------------------

    private static Parameters baseParams() {
        Parameters params = new Parameters();
        params.set(Params.TRUNCATION_LIMIT, 3);
        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.SINGULARITY_LAMBDA, 0.0);
        params.set(Params.DO_ONE_EQUATION_ONLY, false);
        return params;
    }

    private static double localScore(Score score, int i, int... parents) {
        try {
            return (double) score.getClass().getMethod("localScore", int.class, int[].class)
                    .invoke(score, i, parents);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static DataSet nonlinearData(Random rng, int n) {
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            d[i][0] = x;
            d[i][1] = x * x + x + 0.7 * rng.nextGaussian();
        }
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    /**
     * Manual runner (the harness is not yet wired into the build).
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        TestBasisFunctionWiring t = new TestBasisFunctionWiring();
        t.testScoreWrapperParameterKeys();
        t.testDoOneEquationOnlyFlowsThrough();
        t.testSingularityLambdaFlowsThrough();
        t.testTabularWrapperExistsAndWorks();
        System.out.println("ALL TESTS PASSED");
    }
}
