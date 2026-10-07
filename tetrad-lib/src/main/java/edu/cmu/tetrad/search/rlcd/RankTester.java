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

package edu.cmu.tetrad.search.rlcd;

/**
 * A rank test over two (possibly overlapping) column sets of a data matrix, as used by RLCD. The two column sets are
 * allowed to share columns: RLCD tests rank(Σ[A ∪ C, B ∪ C]) ≤ k with C a set of observed non-sink variables, which
 * is how it conditions on observed non-leaf variables.
 *
 * @author josephramsey
 */
public interface RankTester {

    /**
     * Tests the null hypothesis that the cross-covariance of columns <code>pcols</code> and <code>qcols</code> has
     * rank at most <code>r</code>.
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @param r     the hypothesized maximum rank.
     * @param alpha the significance level.
     * @return true if the null is NOT rejected (rank deficiency is consistent with the data), false if rejected.
     */
    boolean failToReject(int[] pcols, int[] qcols, int r, double alpha);
}
