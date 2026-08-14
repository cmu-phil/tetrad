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
     * A column is constant: it has at most one distinct value among its non-missing entries. This includes the
     * degenerate cases of a column with a single non-missing entry and a column with no non-missing entries at all.
     * Constancy is determined exactly (equality of observed values), not by a variance threshold. A constant column
     * carries no sample information: every independence test and score involving it is degenerate, and including it
     * renders the covariance matrix of the continuous variables singular. A column flagged with this code is not
     * additionally flagged NEAR_CONSTANT.
     */
    CONSTANT_COLUMN,

    /**
     * A variable varies but is nearly constant (a continuous variable with negligible but nonzero variance, or a
     * discrete variable with more than one observed category but almost all mass on one of them). Exactly constant
     * columns are flagged CONSTANT_COLUMN instead.
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
    MISSING_DATA,

    /**
     * Two columns are exact affine functions of one another on the rows where both are observed (identical columns,
     * complementary indicators such as y = 1 - x, or rescaled copies). Unlike EXACT_LINEAR_DEPENDENCE, which reports
     * a rank deficiency it cannot localize, this finding names the specific pair; the values carry the number of
     * jointly observed rows the identity is based on. One of each such pair carries no information the other does
     * not, on the observed overlap.
     */
    DUPLICATE_COLUMNS,

    /**
     * The pairwise-complete correlation matrix of the continuous variables has at least one negative eigenvalue.
     * Because each entry is computed on a different subset of rows, the assembled matrix is not the correlation
     * matrix of any single sample; joint quantities derived from it (rank, variance inflation factors, partial
     * correlations) can therefore be incoherent. Individual pairwise correlations remain interpretable, each on its
     * own row subset.
     */
    PAIRWISE_CORRELATION_NOT_PSD,

    /**
     * The number of complete rows minus one is smaller than the number of continuous variables, so ANY covariance or
     * correlation matrix computed on complete cases is singular by arithmetic, regardless of what the variables
     * measure. When this holds, joint linear-dependence findings cannot be attributed to relationships among
     * specific variables: the singularity is forced by the complete-case count. Localizable exact dependencies, if
     * any, are reported separately as DUPLICATE_COLUMNS.
     */
    COMPLETE_CASES_FORCE_SINGULARITY,

    /**
     * A variable is constant within every level of the configured serial grouping variable (e.g., a subject-level
     * attribute in a repeated-measures file). Its effective sample size for correlational judgments is the number of
     * groups, not the number of rows, and with few groups such variables are frequently exactly collinear with one
     * another by accident.
     */
    GROUP_CONSTANT_VARIABLE,

    /**
     * A continuous variable is serially dependent in file order (autocorrelated across consecutive rows), so rows are
     * not exchangeable as given and independence tests that assume i.i.d. rows may be anticonservative. This check is
     * one-sided with respect to row order: a flag means rows are dependent in the order given, but the absence of a
     * flag does not rule out dependence under some other ordering of the rows (e.g., if a time series was shuffled
     * before saving). If the dataset concatenates blocks (e.g., regions or subjects), autocorrelations should be
     * computed within blocks by naming a grouping variable, since block-level mean shifts and boundary jumps
     * otherwise contaminate the pooled estimate.
     */
    SERIAL_DEPENDENCE
}
