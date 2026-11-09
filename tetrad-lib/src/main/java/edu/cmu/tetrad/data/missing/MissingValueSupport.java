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

package edu.cmu.tetrad.data.missing;

/**
 * A capability declaration for scores and independence tests, stating what the component itself can do with data
 * containing missing values. Callers (search wrappers, the GUI, py-tetrad) can consult this before running, instead
 * of discovering behavior--or silent misbehavior--at runtime.
 * <p>
 * This describes what the component can do natively; it does not preclude handling missingness upstream, e.g., by
 * imputing completed datasets or by supplying an EM-estimated covariance matrix to a covariance-consuming component.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see MissingDataPolicy
 */
public enum MissingValueSupport {

    /**
     * The component has no support for missing values. Passing it raw data containing missing values will produce an
     * exception at best and silently incorrect results (e.g., NaN propagation into likelihoods) at worst. Missingness
     * must be handled upstream.
     */
    NONE,

    /**
     * The component performs test-wise (pairwise/local) deletion: each local calculation uses the rows complete on
     * the variables involved in that calculation. Unbiased only under MCAR.
     */
    TESTWISE,

    /**
     * The component consumes a covariance matrix (or can be constructed from one), so missingness can be handled by
     * supplying a covariance matrix estimated under MAR, e.g., by
     * {@link edu.cmu.tetrad.data.EmCovarianceEstimator}.
     */
    VIA_COVARIANCE,

    /**
     * The component handles missing values natively in a manner valid under MAR, e.g., a full-information maximum
     * likelihood score that computes row-wise likelihood contributions over the observed entries of each row.
     */
    NATIVE_MAR
}
