///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.search.score;

/**
 * Implemented by a {@link Score} whose local score difference can be mapped back to a likelihood-ratio statistic with
 * a known reference distribution, so that the score can report a genuine p-value in addition to its accept/reject
 * verdict.
 * <p>
 * MOTIVATION. A score wrapped as an independence test (see
 * {@link edu.cmu.tetrad.search.test.ScoreIndTest}) decides independence by the sign of the local score difference,
 * which is a perfectly good decision rule but is not a p-value: it is unbounded, and it is not Uniform(0, 1) under
 * the null. Diagnostics that test the distribution of p-values against a uniform null -- the Anderson-Darling, KS,
 * Fisher, and binomial statistics of the Markov check -- are therefore undefined for such a test. A score that can
 * recover the underlying likelihood-ratio statistic can supply a calibrated p-value for reporting, leaving the
 * decision rule untouched.
 * <p>
 * CONTRACT. When {@link #providesCalibratedPValue()} returns true:
 * <ul>
 *     <li>{@link #calibratedPValue(int, int, int[])} returns a value in [0, 1] that is asymptotically Uniform(0, 1)
 *     under the null hypothesis of conditional independence.</li>
 *     <li>{@link #impliedAlpha()} returns the significance level at which the score's own accept/reject rule
 *     operates, so that {@code calibratedPValue(...) > impliedAlpha()} agrees with the score's verdict (a local
 *     score difference of exactly zero is the measure-zero boundary case).</li>
 * </ul>
 * An implementation must return false from {@link #providesCalibratedPValue()} under any configuration for which it
 * cannot honor this contract -- for instance, when a non-zero structure prior makes the effective threshold depend
 * on the size of the conditioning set, so that no single alpha describes the rule.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.test.ScoreIndTest
 */
public interface ProvidesCalibratedPValue {

    /**
     * Returns true just in case this score, as currently configured, can honor the contract of this interface. A
     * caller must consult this before using either of the other two methods; when it is false, their return values
     * are unspecified.
     *
     * @return True if calibrated p-values are available.
     */
    boolean providesCalibratedPValue();

    /**
     * Returns a p-value for the hypothesis that x and y are conditionally independent given z, asymptotically
     * Uniform(0, 1) under that null.
     *
     * @param x The index of the first variable.
     * @param y The index of the second variable.
     * @param z The indices of the conditioning variables.
     * @return The p-value, in [0, 1].
     */
    double calibratedPValue(int x, int y, int[] z);

    /**
     * Returns the significance level at which this score's accept/reject rule operates--that is, the level alpha
     * for which rejecting when {@code calibratedPValue(...) <= alpha} reproduces the sign test on the local score
     * difference.
     *
     * @return The implied alpha, in [0, 1].
     */
    double impliedAlpha();
}
