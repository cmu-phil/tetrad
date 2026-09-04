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
     * A pair of continuous variables is very highly correlated in absolute value. Emitted by {@link DataAudit} from
     * pairwise-complete sample correlations and by {@link CovarianceAudit} from the correlations implied by a
     * supplied covariance matrix (in which case nUsed is the matrix's stated sample size).
     */
    HIGH_CORRELATION,

    /**
     * The correlation matrix of the continuous variables is singular (rank-deficient): some continuous variable is an
     * exact linear function of the others on the analyzed rows. Also emitted by {@link CovarianceAudit} when the
     * correlation matrix implied by a supplied covariance matrix is rank-deficient.
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
     * A continuous variable has a repeated point mass at one extreme of its observed range, separated from the rest
     * of the distribution by a gap far larger than the typical spacing between adjacent observed values elsewhere in
     * the column. This is the signature of a sentinel code - a fixed value such as 0, -1, -99 or 999 written into
     * the file to mark a measurement that was not taken - which arithmetic cannot distinguish from a real
     * measurement, so every downstream computation silently treats the code as data.
     * <p>
     * The consequences are not confined to the flagged variable's marginal distribution. A shared code creates
     * spurious association between any two variables that carry it on the same rows and attenuates the association
     * between a coded variable and an uncoded one, so correlations move in both directions and can change sign. A
     * large point mass also drives the variable toward degeneracy, which costs conditional-independence tests their
     * power: partial correlations shrink toward zero and a graph can pass a Markov check because nothing rejects
     * rather than because the graph is right. Non-Gaussianity findings on such a column typically describe the
     * point mass rather than the measured quantity.
     * <p>
     * The finding is per variable, with the candidate code in the values under "value" and the count of cells
     * holding it under "count"; a variable coded at both ends (for instance -999 and 999) produces two findings.
     * The check is a heuristic on the shape of the observed distribution and cannot read the codebook: a genuine
     * boundary value that repeats and sits far from its neighbors will be flagged, and a sentinel code that falls
     * inside the observed range or coincides with plausible measurements will not be. Whether the flagged value is a
     * code or a measurement is a question about the data's provenance, and the audit takes no position on it. Only
     * continuous variables are checked; a sentinel category of a discrete variable is visible in its level list.
     */
    SENTINEL_VALUE,

    /**
     * Two columns are exact affine functions of one another on the rows where both are observed (identical columns,
     * complementary indicators such as y = 1 - x, or rescaled copies). Unlike EXACT_LINEAR_DEPENDENCE, which reports
     * a rank deficiency it cannot localize, this finding names the specific pair; the values carry the number of
     * jointly observed rows the identity is based on. One of each such pair carries no information the other does
     * not, on the observed overlap. Also emitted by {@link CovarianceAudit} when the correlation implied by a
     * supplied covariance matrix has absolute value 1 within tolerance; the values then carry the implied
     * correlation, and no row counts, since no rows are available.
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
     * A variable is constant, within tolerance, inside every multi-row cell of the joint values of a small set of
     * other few-valued variables: on the analyzed rows, the variable is a function of that set (up to tolerance).
     * Exact or near-exact functional determinism violates faithfulness and destabilizes conditional-independence
     * judgments: conditioning sets containing the determining variables render the determined variable
     * pseudo-independent of everything, sepsets become pathological, and constraint-based orientation can produce
     * arrowheads that no data-generating mechanism supports (a computed or derived column "causing" its inputs).
     * <p>
     * The determined variable is listed FIRST in the finding's variable list; the determining set follows. Only
     * minimal determining sets are reported: once a set is found for a variable, its supersets are not searched. A
     * pair already reported as DUPLICATE_COLUMNS is not re-reported here as a one-element determinism. The linear
     * whole-matrix analog is EXACT_LINEAR_DEPENDENCE; the regression- and eta-squared-based near-determinism
     * findings (NEAR_DETERMINISM_CONTINUOUS, NEAR_DETERMINISM_DISCRETE_CONTINUOUS) cover linear and single-discrete
     * mechanisms, while this finding is nonparametric and joint, so it detects nonlinear functions of variable
     * combinations (e.g., a boundary-layer quantity computed from several experimental settings) that those checks
     * miss.
     * <p>
     * The check is bounded: determining sets up to a configured size, determiner variables up to a configured
     * distinct-value count, and a fixed work budget; single-row cells are vacuous and are excluded, with coverage
     * and multi-row-cell minimums guarding against vacuously "deterministic" fine grids. The absence of this finding
     * therefore does not rule out determinism beyond those bounds.
     */
    DETERMINISTIC_RELATION,

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
    SERIAL_DEPENDENCE,

    /**
     * A supplied covariance matrix is not symmetric within tolerance: some entry (i, j) differs from its transpose
     * entry (j, i) by more than a small fraction of the largest absolute entry. A genuine covariance matrix is
     * symmetric by definition, so asymmetry indicates a transcription or assembly error. Subsequent checks in the
     * same audit are run on the symmetrized matrix (S + S')/2, as stated in the finding's message. Emitted only by
     * {@link CovarianceAudit}.
     */
    COVARIANCE_NOT_SYMMETRIC,

    /**
     * A diagonal entry of a supplied covariance matrix is zero or negative. A zero variance is the covariance-matrix
     * analog of a constant column: the variable carries no sample information, its correlations are undefined, and
     * including it makes the matrix singular. A negative variance is not a variance at all and marks the matrix as
     * invalid input. Variables flagged with this code are excluded from the correlation-derived checks in the same
     * audit. Emitted only by {@link CovarianceAudit}.
     */
    NONPOSITIVE_VARIANCE,

    /**
     * A supplied covariance matrix has at least one negative eigenvalue (judged on the implied correlation matrix,
     * for scale invariance): it is not the covariance matrix of any dataset. This arises when matrices are assembled
     * entrywise from different row subsets (pairwise deletion), transcribed or rounded, or produced by procedures
     * that do not guarantee positive semidefiniteness. Quantities that presuppose a valid covariance matrix
     * (likelihoods, partial correlations, regression coefficients) can fail or behave incoherently. Emitted only by
     * {@link CovarianceAudit}; the analogous finding for a matrix Tetrad itself assembles pairwise from a dataset
     * with missing values is PAIRWISE_CORRELATION_NOT_PSD.
     */
    COVARIANCE_NOT_PSD,

    /**
     * The ratio of the largest to the smallest positive variance on the diagonal of a supplied covariance matrix is
     * extreme. This usually reflects unstandardized variables measured in very different units and degrades the
     * numerical conditioning of decompositions and inversions even when the implied correlations are unremarkable.
     * Emitted only by {@link CovarianceAudit}.
     */
    EXTREME_VARIANCE_SCALE,

    /**
     * The stated sample size n of a supplied covariance matrix satisfies n - 1 &lt; p, where p is the number of
     * (positive-variance) variables, so any ordinary sample covariance matrix computed from n rows is singular by
     * arithmetic. If the supplied matrix has rank at most n - 1, it is consistent with that arithmetic, and joint
     * linear-dependence findings cannot be attributed to relationships among specific variables. If its rank exceeds
     * n - 1, it cannot be an ordinary sample covariance matrix at the stated n: either the sample size is misstated
     * or the matrix was regularized, shrunk, or model-implied. The finding's message states which case holds. Emitted only
     * by {@link CovarianceAudit}; the dataset analog is COMPLETE_CASES_FORCE_SINGULARITY.
     */
    SAMPLE_SIZE_FORCES_SINGULARITY
}
