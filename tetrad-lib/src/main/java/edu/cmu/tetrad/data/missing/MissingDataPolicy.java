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
 * An explicit statement of how missing values in a dataset are to be handled by a score, independence test, or search
 * wrapper. The intent is to replace the current implicit behavior--in which, e.g., some scores silently switch to
 * test-wise deletion when a dataset contains missing values--with a policy that the user chooses (or is at least
 * warned about), and that every component either honors or rejects loudly.
 * <p>
 * The statistical assumptions differ by policy and matter for real-data analysis. Listwise and test-wise deletion are
 * unbiased in general only when data are missing completely at random (MCAR). EM-estimated covariance and multiple
 * imputation are valid under the weaker missing-at-random (MAR) assumption, for approximately multivariate normal
 * data in the EM case. None of these policies corrects for missing-not-at-random (MNAR) mechanisms.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see MissingDataSpec
 * @see MissingValueSupport
 */
public enum MissingDataPolicy {

    /**
     * Refuse to analyze data containing missing values; an exception should be thrown. This is the safest default for
     * components that have no principled way to handle missingness.
     */
    FAIL,

    /**
     * Delete every row containing at least one missing value, once, up front, and analyze the remaining complete
     * cases. Unbiased only under MCAR; can discard a large fraction of the data when missingness is scattered.
     */
    LISTWISE,

    /**
     * For each local score or independence test, use only the rows that are complete on the variables involved in
     * that particular calculation. This retains more data than listwise deletion but is still unbiased only under
     * MCAR, and different local calculations are performed on different subsamples, which can produce
     * inconsistencies (e.g., non-positive-definite implied covariances). This is the current implicit behavior of,
     * e.g., SemBicScore when missing values are present.
     */
    TESTWISE,

    /**
     * Estimate the mean and covariance matrix once by expectation-maximization under a saturated Gaussian model (see
     * {@link edu.cmu.tetrad.data.EmCovarianceEstimator}) and run the analysis on the estimated covariance matrix as
     * if it were complete data. Valid under MAR for approximately multivariate normal data. Applies to continuous
     * datasets and covariance-consuming scores/tests.
     */
    EM_COVARIANCE,

    /**
     * Impute m completed datasets, run the analysis on each, and pool the results (for graph searches, by edge
     * frequency across imputations). Valid under MAR given a correctly specified imputation model; applicable to
     * continuous, discrete, and mixed data.
     */
    MULTIPLE_IMPUTATION
}
