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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rule BOSS Top-K uses to decide whether an alternative placement of a node is set aside to be run later: it is
 * when it scores below the placement being pursued by less than the split width, and is not tied with it. A tie is
 * anything within the tolerance the moves themselves use. Before, the test was "below by more than zero", and an exact
 * tie that rounding had left a hair below the best was set aside.
 *
 * @author Claude
 */
public class TestBossTopKSplitRule {

    private static final double BEST = -41690.576718711;

    @Test
    public void testATieWithTheBestIsNotSetAside() {
        assertFalse(BossTopK.isNearTie(BEST, BEST, 2.0));
    }

    @Test
    public void testATieLeftAHairBelowByRoundingIsNotSetAside() {
        // The neighbouring doubles just below the best, which is what adding the same terms in another order gives.
        double below = BEST;
        for (int i = 0; i < 4; i++) {
            below = Math.nextDown(below);
            assertTrue(below < BEST);
            assertFalse(BossTopK.isNearTie(BEST, below, 2.0));
        }

        assertFalse(BossTopK.isNearTie(BEST, BEST - 1e-9, 2.0));
        assertFalse(BossTopK.isNearTie(BEST, BEST - 5e-7, 2.0));
    }

    @Test
    public void testAnAlternativeBelowTheBestByLessThanTheSplitWidthIsSetAside() {
        assertTrue(BossTopK.isNearTie(BEST, BEST - 1e-5, 2.0));
        assertTrue(BossTopK.isNearTie(BEST, BEST - 0.5, 2.0));
        assertTrue(BossTopK.isNearTie(BEST, BEST - 1.999, 2.0));
    }

    @Test
    public void testAnAlternativeBelowByTheSplitWidthOrMoreIsNotSetAside() {
        assertFalse(BossTopK.isNearTie(BEST, BEST - 2.0, 2.0));
        assertFalse(BossTopK.isNearTie(BEST, BEST - 50.0, 2.0));
    }

    @Test
    public void testAnAlternativeAboveTheBestIsNotSetAside() {
        assertFalse(BossTopK.isNearTie(BEST, BEST + 1e-9, 2.0));
        assertFalse(BossTopK.isNearTie(BEST, BEST + 0.5, 2.0));
    }

    @Test
    public void testNothingIsSetAsideWhenTheSplitWidthIsNoWiderThanTheTolerance() {
        assertEquals(1e-6, BossTopK.TIE_TOLERANCE, 0.0);
        assertFalse(BossTopK.isNearTie(BEST, BEST - 5e-7, 1e-6));
        assertFalse(BossTopK.isNearTie(BEST, BEST - 1e-3, 0.0));
    }
}
