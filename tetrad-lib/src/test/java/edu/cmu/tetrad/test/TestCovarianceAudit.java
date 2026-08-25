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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.CovarianceAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the covariance matrix audit: each finding code fires on a matrix constructed to exhibit it and does not fire
 * on a clean matrix, nonpositive-variance variables are excluded from the correlation-derived checks, and the
 * accessors report the implied quantities.
 *
 * @author josephramsey
 */
public class TestCovarianceAudit {

    private static ICovarianceMatrix cov(double[][] m, int n, String... names) {
        List<Node> vars = new ArrayList<>();
        for (String name : names) vars.add(new ContinuousVariable(name));
        return new CovarianceMatrix(vars, m, n);
    }

    /**
     * A well-conditioned covariance matrix with a healthy sample size produces no findings, and the accessors report
     * a full-rank, well-conditioned implied correlation matrix.
     */
    @Test
    public void testCleanMatrixNoFindings() {
        double[][] m = {
                {2.0, 0.3, 0.1},
                {0.3, 1.5, 0.2},
                {0.1, 0.2, 1.0}};

        CovarianceAudit audit = new CovarianceAudit(cov(m, 1000, "X1", "X2", "X3"));

        assertTrue("Expected no findings; got " + audit.getFindings(), audit.getFindings().isEmpty());
        assertEquals(3, audit.getCorrelationRank());
        assertTrue(audit.getMinEigenvalue() > 0);
        assertFalse(audit.isCorrelationInput());
        assertEquals(List.of("X1", "X2", "X3"), audit.getVariableNames());
    }

    /**
     * A correlation matrix that violates positive semidefiniteness (r12 = r13 = 0.9, r23 = -0.9 is impossible) is
     * flagged COVARIANCE_NOT_PSD, and its extreme pairwise correlations are flagged HIGH_CORRELATION.
     */
    @Test
    public void testNotPsd() {
        double[][] m = {
                {1.0, 0.9, 0.9},
                {0.9, 1.0, -0.9},
                {0.9, -0.9, 1.0}};

        CovarianceAudit audit = new CovarianceAudit(cov(m, 1000, "X1", "X2", "X3"));

        assertTrue(audit.hasFinding(FindingCode.COVARIANCE_NOT_PSD));
        assertTrue(audit.getMinEigenvalue() < 0);
        assertEquals(3, audit.getFindings(FindingCode.HIGH_CORRELATION).size());
        assertTrue(audit.isCorrelationInput());
    }

    /**
     * A matrix in which one variable is an exact affine copy of another (X2 = 2 X1) is flagged both
     * DUPLICATE_COLUMNS (localizing the pair) and EXACT_LINEAR_DEPENDENCE (rank deficiency), and is not additionally
     * flagged HIGH_CORRELATION for that pair.
     */
    @Test
    public void testDuplicateAndSingular() {
        double[][] m = {
                {1.0, 2.0, 0.1},
                {2.0, 4.0, 0.2},
                {0.1, 0.2, 1.0}};

        CovarianceAudit audit = new CovarianceAudit(cov(m, 1000, "X1", "X2", "X3"));

        assertEquals(1, audit.getFindings(FindingCode.DUPLICATE_COLUMNS).size());
        AuditFinding dup = audit.getFindings(FindingCode.DUPLICATE_COLUMNS).get(0);
        assertEquals(List.of("X1", "X2"), dup.getVariables());

        assertTrue(audit.hasFinding(FindingCode.EXACT_LINEAR_DEPENDENCE));
        assertEquals(2, audit.getCorrelationRank());
        assertFalse(audit.hasFinding(FindingCode.HIGH_CORRELATION));
        assertFalse(audit.hasFinding(FindingCode.COVARIANCE_NOT_PSD));
    }

    /**
     * A pair with implied correlation 0.95 is flagged HIGH_CORRELATION (not DUPLICATE_COLUMNS), with the stated
     * sample size carried as nUsed.
     */
    @Test
    public void testHighCorrelation() {
        double[][] m = {
                {1.0, 0.95, 0.1},
                {0.95, 1.0, 0.1},
                {0.1, 0.1, 1.0}};

        CovarianceAudit audit = new CovarianceAudit(cov(m, 500, "X1", "X2", "X3"));

        assertEquals(1, audit.getFindings(FindingCode.HIGH_CORRELATION).size());
        AuditFinding f = audit.getFindings(FindingCode.HIGH_CORRELATION).get(0);
        assertEquals(List.of("X1", "X2"), f.getVariables());
        assertEquals(500.0, f.getValues().get("nUsed"), 0.0);
        assertFalse(audit.hasFinding(FindingCode.DUPLICATE_COLUMNS));
    }

    /**
     * A variable that is nearly (but not exactly) a linear function of the others is flagged
     * NEAR_DETERMINISM_CONTINUOUS. Here X3 = X1 + X2 + e with var(X1) = var(X2) = 1, cov(X1, X2) = 0, and var(e) =
     * 0.03, so R^2 of X3 on the others is 2/2.03 = 0.9852, above the 0.98 default, while R^2 of X1 (or X2) on the
     * others is 1 - var(e) = 0.97, below it.
     */
    @Test
    public void testNearDeterminism() {
        double[][] m = {
                {1.0, 0.0, 1.0},
                {0.0, 1.0, 1.0},
                {1.0, 1.0, 2.03}};

        CovarianceAudit audit = new CovarianceAudit(cov(m, 1000, "X1", "X2", "X3"));

        assertTrue(audit.hasFinding(FindingCode.NEAR_DETERMINISM_CONTINUOUS));
        AuditFinding f = audit.getFindings(FindingCode.NEAR_DETERMINISM_CONTINUOUS).get(0);
        assertEquals(List.of("X3"), f.getVariables());
        assertEquals(2.0 / 2.03, audit.getR2OnOthers().get("X3"), 1e-6);
        assertFalse(audit.hasFinding(FindingCode.EXACT_LINEAR_DEPENDENCE));

        // With a raised threshold, the same matrix is not flagged.
        CovarianceAudit strict = new CovarianceAudit(cov(m, 1000, "X1", "X2", "X3"),
                new CovarianceAudit.Config().withR2Determinism(0.999));
        assertFalse(strict.hasFinding(FindingCode.NEAR_DETERMINISM_CONTINUOUS));
    }

    /**
     * Zero and negative diagonal entries are each flagged NONPOSITIVE_VARIANCE, and the offending variables are
     * excluded from the correlation-derived checks: the remaining two variables form a clean 2 x 2 problem, so no
     * singularity or PSD finding is emitted despite the degenerate rows and columns in the full matrix.
     */
    @Test
    public void testNonpositiveVarianceExcluded() {
        double[][] m = {
                {1.0, 0.2, 0.0, 0.0},
                {0.2, 1.0, 0.0, 0.0},
                {0.0, 0.0, 0.0, 0.0},
                {0.0, 0.0, 0.0, -0.5}};

        CovarianceAudit audit = new CovarianceAudit(cov(m, 1000, "X1", "X2", "Z", "W"));

        assertEquals(2, audit.getFindings(FindingCode.NONPOSITIVE_VARIANCE).size());
        assertEquals(List.of("X1", "X2"), audit.getVariableNames());
        assertFalse(audit.hasFinding(FindingCode.EXACT_LINEAR_DEPENDENCE));
        assertFalse(audit.hasFinding(FindingCode.COVARIANCE_NOT_PSD));
        assertEquals(2, audit.getCorrelationRank());
    }

    /**
     * An asymmetric matrix is flagged COVARIANCE_NOT_SYMMETRIC, and subsequent checks run on the symmetrized matrix
     * rather than failing.
     */
    @Test
    public void testAsymmetric() {
        double[][] m = {
                {1.0, 0.5, 0.1},
                {0.2, 1.0, 0.1},
                {0.1, 0.1, 1.0}};

        CovarianceAudit audit = new CovarianceAudit(cov(m, 1000, "X1", "X2", "X3"));

        assertTrue(audit.hasFinding(FindingCode.COVARIANCE_NOT_SYMMETRIC));
        AuditFinding f = audit.getFindings(FindingCode.COVARIANCE_NOT_SYMMETRIC).get(0);
        assertEquals(List.of("X1", "X2"), f.getVariables());
        assertEquals(0.3, f.getValues().get("maxAsymmetry"), 1e-12);

        // The symmetrized correlation (0.35) is unremarkable, so no other findings.
        assertEquals(1, audit.getFindings().size());
        assertEquals(0.35, audit.getImpliedCorrelationMatrix().get(0, 1), 1e-12);
    }

    /**
     * A full-rank diagonal matrix with stated sample size smaller than the dimension is flagged
     * SAMPLE_SIZE_FORCES_SINGULARITY with the inconsistency message (rank exceeds n - 1, so the matrix cannot be an
     * ordinary sample covariance at that n), plus LOW_SAMPLE_RATIO; a matrix whose rank is at most n - 1 gets the
     * consistent-with-arithmetic message instead.
     */
    @Test
    public void testSampleSizeForcesSingularity() {
        double[][] fullRank = new double[6][6];
        for (int i = 0; i < 6; i++) fullRank[i][i] = 1.0 + i;

        CovarianceAudit a1 = new CovarianceAudit(cov(fullRank, 4, "A", "B", "C", "D", "E", "F"));

        assertTrue(a1.hasFinding(FindingCode.SAMPLE_SIZE_FORCES_SINGULARITY));
        assertTrue(a1.getFindings(FindingCode.SAMPLE_SIZE_FORCES_SINGULARITY).get(0).getMessage()
                .contains("cannot be an ordinary sample covariance"));
        assertTrue(a1.hasFinding(FindingCode.LOW_SAMPLE_RATIO));
        assertFalse(a1.hasFinding(FindingCode.EXACT_LINEAR_DEPENDENCE));

        double[][] deficient = {
                {1.0, 1.0, 0.0},
                {1.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}};

        CovarianceAudit a2 = new CovarianceAudit(cov(deficient, 3, "A", "B", "C"));

        assertTrue(a2.hasFinding(FindingCode.SAMPLE_SIZE_FORCES_SINGULARITY));
        assertTrue(a2.getFindings(FindingCode.SAMPLE_SIZE_FORCES_SINGULARITY).get(0).getMessage()
                .contains("consistent with that arithmetic"));
    }

    /**
     * An extreme largest-to-smallest variance ratio is flagged EXTREME_VARIANCE_SCALE at severity INFO, naming the
     * largest- and smallest-variance variables.
     */
    @Test
    public void testExtremeVarianceScale() {
        double[][] m = {
                {1e-6, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1e6}};

        CovarianceAudit audit = new CovarianceAudit(cov(m, 1000, "small", "mid", "big"));

        assertEquals(1, audit.getFindings(FindingCode.EXTREME_VARIANCE_SCALE).size());
        AuditFinding f = audit.getFindings(FindingCode.EXTREME_VARIANCE_SCALE).get(0);
        assertEquals(AuditFinding.Severity.INFO, f.getSeverity());
        assertEquals(List.of("big", "small"), f.getVariables());
        assertEquals(1e12, f.getValues().get("ratio"), 1e-2);
    }

    /**
     * The report always renders, mentions the scope limits of covariance-only auditing, and notes when the input is
     * a correlation matrix.
     */
    @Test
    public void testReport() {
        double[][] m = {
                {1.0, 0.3},
                {0.3, 1.0}};

        CovarianceAudit audit = new CovarianceAudit(cov(m, 100, "X1", "X2"));
        String report = audit.report();

        assertTrue(report.contains("no information about missingness"));
        assertTrue(report.contains("correlation matrix"));
        assertTrue(audit.isCorrelationInput());
    }
}
