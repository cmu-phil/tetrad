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

package edu.cmu.tetrad.data.audit;

/**
 * Machine-readable codes for the findings a {@link DataAudit} can emit. Each code identifies a property of the data
 * matrix that bears on the choice or reliability of a causal search procedure. Codes are findings, not
 * recommendations: the audit reports what is true of the data and leaves the response to the user (or to
 * documentation that interprets these codes, such as the analysis guide in the py-tetrad repository).
 * <p>
 * The set of codes is part of the audit's public contract; downstream tools (py-tetrad, reports, AI assistants)
 * dispatch on them. New codes may be added in future releases, so consumers should tolerate unknown codes.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public enum FindingCode {

    /**
     * A variable declared continuous takes only a small number of distinct observed values, suggesting it may be
     * better treated as discrete, or that it was mechanically converted from a discrete source.
     */
    CONTINUOUS_FEW_VALUES,

    /**
     * A discrete variable has many observed levels. Conditioning on such a variable, or crossing it with other
     * discrete variables, produces many small cells; if the levels are ordered, continuous treatment may be an
     * alternative.
     */
    DISCRETE_MANY_LEVELS,

    /**
     * A variable is constant or nearly constant (a continuous variable with negligible variance, or a discrete
     * variable with almost all mass on one category).
     */
    NEAR_CONSTANT,

    /**
     * A discrete variable has one or more observed categories with very small counts.
     */
    SMALL_MARGINAL_CELL,

    /**
     * A pair of discrete variables has small expected cell counts under independence, so conditional independence
     * tests involving this pair (or conditioning sets containing them) will be unreliable.
     */
    SMALL_PAIRWISE_CELLS,

    /**
     * A pair of continuous variables is very highly correlated in absolute value.
     */
    HIGH_CORRELATION,

    /**
     * The correlation matrix of the continuous variables is singular (rank-deficient): some continuous variable is an
     * exact linear function of the others on the analyzed rows.
     */
    EXACT_LINEAR_DEPENDENCE,

    /**
     * A continuous variable is nearly a linear function of the other continuous variables (multiple R-squared above
     * threshold), a near-faithfulness violation that destabilizes conditional independence judgments.
     */
    NEAR_DETERMINISM_CONTINUOUS,

    /**
     * A continuous variable is nearly determined by a discrete variable (eta-squared above threshold): the discrete
     * variable is close to a deterministic coarsening of the continuous one.
     */
    NEAR_DETERMINISM_DISCRETE_CONTINUOUS,

    /**
     * A continuous variable's marginal distribution deviates from Gaussian by the Anderson-Darling test. This is a
     * threat to linear-Gaussian machinery but an asset to methods that exploit non-Gaussianity (LiNGAM family).
     */
    NON_GAUSSIAN,

    /**
     * The ratio of sample size to number of variables is small, limiting the conditioning depth that can be trusted.
     */
    LOW_SAMPLE_RATIO,

    /**
     * The dataset contains missing values. The finding carries summary statistics; detailed pattern information and
     * Little's MCAR test are available from the delegated {@link edu.cmu.tetrad.data.missing.MissingDataAudit}.
     */
    MISSING_DATA
}
