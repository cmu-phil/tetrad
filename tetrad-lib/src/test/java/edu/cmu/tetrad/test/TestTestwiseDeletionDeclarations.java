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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.data.missing.MissingValueSupport;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.score.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Verifies the test-wise deletion behavior of the scores that computed each family on its own complete rows before
 * declaring so (HT SEM BIC, RFF BIC, TRFF BIC, KCV BIC, FFML, FFML-Continuous, Legendre BIC), of ZS Bound (routed
 * through SemBicScore's row-subset path like EBIC), and of the Poisson Prior Test and Blocks-Test wrappers. The
 * identity checked is the one that must hold for any per-family computation: with missing values confined to one
 * column, a local score whose family does not include that column equals the complete-data local score, and every
 * local score is finite. The wrapper checks fail on a jar that rejects the 'testwise' policy for these components.
 *
 * @author josephramsey
 */
public class TestTestwiseDeletionDeclarations {

    private static final int N = 300;

    /**
     * Constructs a new test.
     */
    public TestTestwiseDeletionDeclarations() {
    }

    private static DataSet[] continuousWithMissingLast(long seed, double missingRate) {
        Random rand = new Random(seed);
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 4; j++) vars.add(new ContinuousVariable("X" + j));

        DataSet full = new BoxDataSet(new DoubleDataBox(N, 4), vars);
        for (int i = 0; i < N; i++) {
            double x0 = rand.nextGaussian();
            double x1 = 0.7 * x0 + rand.nextGaussian();
            double x2 = 0.5 * x1 * x1 + rand.nextGaussian();
            double x3 = 0.6 * x2 - 0.3 * x0 + rand.nextGaussian();
            full.setDouble(i, 0, x0);
            full.setDouble(i, 1, x1);
            full.setDouble(i, 2, x2);
            full.setDouble(i, 3, x3);
        }

        DataSet missing = full.copy();
        for (int i = 0; i < N; i++) {
            if (rand.nextDouble() < missingRate) missing.setDouble(i, 3, Double.NaN);
        }

        return new DataSet[]{full, missing};
    }

    private static DataSet[] mixedWithMissingLast(long seed, double missingRate) {
        Random rand = new Random(seed);
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("C1"));
        vars.add(new ContinuousVariable("C2"));
        vars.add(new DiscreteVariable("D1", 3));
        vars.add(new ContinuousVariable("C3"));

        DataSet full = new BoxDataSet(new MixedDataBox(vars, N), vars);
        for (int i = 0; i < N; i++) {
            double c1 = rand.nextGaussian();
            double c2 = 0.6 * c1 + rand.nextGaussian();
            int d1 = c2 > 0.5 ? 2 : (c2 > -0.5 ? 1 : 0);
            if (rand.nextDouble() < 0.2) d1 = rand.nextInt(3);
            double c3 = 0.5 * c2 + 0.4 * d1 + rand.nextGaussian();
            full.setDouble(i, 0, c1);
            full.setDouble(i, 1, c2);
            full.setInt(i, 2, d1);
            full.setDouble(i, 3, c3);
        }

        DataSet missing = full.copy();
        for (int i = 0; i < N; i++) {
            if (rand.nextDouble() < missingRate) missing.setDouble(i, 3, Double.NaN);
        }

        return new DataSet[]{full, missing};
    }

    private static void checkPairs(String name, Score full, Score missing, double tol) {
        assertEquals(name, MissingValueSupport.TESTWISE, missing.getMissingValueSupport());

        // Families without the last column (index 3): identical to complete data (tol is absolute, or, when
        // negative, relative to the complete-data score).
        for (int[] fam : new int[][]{{1, 0}, {2, 0, 1}, {0}}) {
            int child = fam[0];
            int[] pa = java.util.Arrays.copyOfRange(fam, 1, fam.length);
            double a = full.localScore(child, pa), b = missing.localScore(child, pa);
            assertEquals(name + " " + java.util.Arrays.toString(fam), a, b, tol < 0 ? -tol * Math.abs(a) : tol);
        }

        // Families with it: finite.
        assertFalse(name + " 3|2,0", Double.isNaN(missing.localScore(3, 2, 0)));
        assertFalse(name + " 2|3", Double.isNaN(missing.localScore(2, 3)));
        assertFalse(name + " 3", Double.isNaN(missing.localScore(3)));
    }

    /**
     * The continuous scores that already did per-family row selection, plus ZS Bound.
     */
    @Test
    public void testContinuousScores() {
        DataSet[] pair = continuousWithMissingLast(1, 0.12);
        DataSet full = pair[0];
        DataSet missing = pair[1];

        checkPairs("HeavyTailSemBicScore", new HeavyTailSemBicScore(full), new HeavyTailSemBicScore(missing), 1e-8);
        checkPairs("RffBicScore", new RffBicScore(full), new RffBicScore(missing), 1e-8);
        checkPairs("KcvBicScore", new KcvBicScore(full), new KcvBicScore(missing), 1e-8);
        // FFML-Continuous estimates one RBF bandwidth per child from all other variables (for nesting stability),
        // and with missing values that estimate uses the rows complete on the design, so families not touching
        // the missing column agree only up to that hyperparameter. Every other score here is exact.
        checkPairs("FfMlContinuous", new FfMlContinuous(full), new FfMlContinuous(missing), -1e-3);
        checkPairs("LegendreBicScore", new LegendreBicScore(full), new LegendreBicScore(missing), 1e-8);
        checkPairs("ZsbScore", new ZsbScore(full, true), new ZsbScore(missing, true), 1e-8);
        checkPairs("EbicScore", new EbicScore(full, true), new EbicScore(missing, true), 1e-8);
        checkPairs("PoissonPriorScore", new PoissonPriorScore(full, true), new PoissonPriorScore(missing, true), 1e-8);
    }

    /**
     * The RFF-based scores are deterministic across instances (their feature draws are seeded per cache key rather
     * than taken from the global RandomUtil, as of 2026-9).
     */
    @Test
    public void testRffScoresDeterministic() {
        DataSet full = continuousWithMissingLast(5, 0.0)[0];
        DataSet mixed = mixedWithMissingLast(6, 0.0)[0];
        assertEquals(new TRffBicScore(mixed).localScore(3, 1, 2), new TRffBicScore(mixed).localScore(3, 1, 2), 0.0);
        assertEquals(new FfMlContinuous(full).localScore(3, 2, 0), new FfMlContinuous(full).localScore(3, 2, 0), 0.0);
        assertEquals(new FfMl(mixed).localScore(3, 1, 2), new FfMl(mixed).localScore(3, 1, 2), 0.0);
        assertEquals(new RffBicScore(full).localScore(3, 2, 0), new RffBicScore(full).localScore(3, 2, 0), 0.0);
    }

    /**
     * The mixed scores that already did per-family row selection.
     */
    @Test
    public void testMixedScores() {
        DataSet[] pair = mixedWithMissingLast(2, 0.12);
        DataSet full = pair[0];
        DataSet missing = pair[1];

        checkPairs("TRffBicScore", new TRffBicScore(full), new TRffBicScore(missing), 1e-8);
        checkPairs("FfMl", new FfMl(full), new FfMl(missing), 1e-8);
    }

    /**
     * The wrappers accept 'testwise' where they previously threw.
     */
    @Test
    public void testWrappersAcceptTestwise() throws InterruptedException {
        DataSet cont = continuousWithMissingLast(3, 0.1)[1];
        DataSet mixed = mixedWithMissingLast(4, 0.1)[1];

        Parameters testwise = new Parameters();
        testwise.set(Params.MISSING_DATA_POLICY, "testwise");

        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.HeavyTailSemBicScore()
                .getScore(cont, testwise).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.RffBicScore()
                .getScore(cont, testwise).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.KcvBicScore()
                .getScore(cont, testwise).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.FfMlContinuous()
                .getScore(cont, testwise).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.ZhangShenBoundScore()
                .getScore(cont, testwise).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.TRffBicScore()
                .getScore(mixed, testwise).localScore(3, 1, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.FfMl()
                .getScore(mixed, testwise).localScore(3, 1, 2)));

        List<Node> cv = cont.getVariables();
        IndependenceTest poisson = new edu.cmu.tetrad.algcomparison.independence.PoissonBicTest()
                .getTest(cont, testwise);
        assertEquals(MissingValueSupport.TESTWISE, poisson.getMissingValueSupport());
        assertNotNull(poisson.checkIndependence(cv.get(3), cv.get(0), Set.of(cv.get(2))));

        // Blocks-Test over singleton blocks.
        List<List<Integer>> blocks = new ArrayList<>();
        for (int j = 0; j < cont.getNumColumns(); j++) blocks.add(List.of(j));
        BlockSpec spec = new BlockSpec(cont, blocks, cont.getVariables());
        edu.cmu.tetrad.algcomparison.independence.BlocksIndTest blocksWrapper =
                new edu.cmu.tetrad.algcomparison.independence.BlocksIndTest();
        blocksWrapper.setBlockSpec(spec);
        IndependenceTest blocks1 = blocksWrapper.getTest(cont, testwise);
        assertEquals(MissingValueSupport.TESTWISE, blocks1.getMissingValueSupport());
        double p = blocks1.checkIndependence(cv.get(3), cv.get(0), Set.of(cv.get(2))).getPValue();
        assertFalse(Double.isNaN(p));

        // And the same test on the complete rows agrees with the block test built on the listwise subset.
        DataSet listwise = edu.cmu.tetrad.data.missing.MissingDataUtils.listwiseDelete(cont);
        BlockSpec lwSpec = new BlockSpec(listwise, blocks, listwise.getVariables());
        IndependenceTest blocksLw = new edu.cmu.tetrad.search.test.IndTestBlocksWilkes(lwSpec);
        List<Node> lv = listwise.getVariables();
        assertEquals(blocksLw.checkIndependence(lv.get(3), lv.get(0), Set.of(lv.get(2))).getPValue(), p, 1e-9);
    }
}
