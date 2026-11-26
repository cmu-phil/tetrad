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
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rule BOSS Top-K uses to decide whether an alternative placement of a node is set aside to be run later: it is
 * when it scores below the placement being pursued by less than the split width. A placement that ties with the one
 * being pursued, to within the tolerance the moves themselves use, is set aside or not as the defer-ties option says.
 * Before, the test was "below by more than zero", and rounding decided which exact ties were set aside.
 *
 * @author Claude
 */
public class TestBossTopKSplitRule {

    private static final double BEST = -41690.576718711;

    /**
     * The neighbouring doubles on either side of the best, which is what adding the same terms in another order
     * gives, and two wider gaps still inside the tolerance.
     */
    private static double[] ties() {
        return new double[]{BEST, Math.nextDown(BEST), Math.nextDown(Math.nextDown(BEST)), Math.nextUp(BEST),
                BEST - 1e-9, BEST - 5e-7, BEST + 1e-9, BEST + 5e-7};
    }

    @Test
    public void testATieIsSetAsideWhenTiesAreDeferred() {
        for (double tie : ties()) {
            assertTrue(Double.toString(tie), BossTopK.isNearTie(BEST, tie, 2.0, true));
        }
    }

    @Test
    public void testATieIsNotSetAsideWhenTiesAreLeftOut() {
        for (double tie : ties()) {
            assertFalse(Double.toString(tie), BossTopK.isNearTie(BEST, tie, 2.0, false));
        }
    }

    @Test
    public void testAnAlternativeBelowTheBestByLessThanTheSplitWidthIsSetAsideEitherWay() {
        for (boolean deferTies : new boolean[]{true, false}) {
            assertTrue(BossTopK.isNearTie(BEST, BEST - 1e-5, 2.0, deferTies));
            assertTrue(BossTopK.isNearTie(BEST, BEST - 0.5, 2.0, deferTies));
            assertTrue(BossTopK.isNearTie(BEST, BEST - 1.999, 2.0, deferTies));
        }
    }

    @Test
    public void testAnAlternativeBelowByTheSplitWidthOrMoreIsNotSetAsideEitherWay() {
        for (boolean deferTies : new boolean[]{true, false}) {
            assertFalse(BossTopK.isNearTie(BEST, BEST - 2.0, 2.0, deferTies));
            assertFalse(BossTopK.isNearTie(BEST, BEST - 50.0, 2.0, deferTies));
        }
    }

    @Test
    public void testAnAlternativeClearlyAboveTheBestIsNotSetAsideEitherWay() {
        for (boolean deferTies : new boolean[]{true, false}) {
            assertFalse(BossTopK.isNearTie(BEST, BEST + 1e-5, 2.0, deferTies));
            assertFalse(BossTopK.isNearTie(BEST, BEST + 0.5, 2.0, deferTies));
        }
    }

    @Test
    public void testTheToleranceIsTheOneTheMovesUse() {
        assertEquals(1e-6, BossTopK.TIE_TOLERANCE, 0.0);
    }

    @Test
    public void testDeferringTiesIsTheRegisteredDefaultAndIsOfferedByTheAlgorithm() {
        assertTrue(new Parameters().getBoolean(Params.SPLIT_DEFER_TIES));
        assertTrue(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BossTopK().getParameters()
                .contains(Params.SPLIT_DEFER_TIES));
    }
}
