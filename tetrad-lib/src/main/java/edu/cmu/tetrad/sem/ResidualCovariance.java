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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * The covariance among the measured variables that an estimated SEM leaves unexplained: the sample covariance
 * minus the covariance the model implies, with the model's error variances put back on the diagonal.
 * <p>
 * For a pure measurement model, where every measured variable has one latent parent and no measured parent, the
 * implied covariance is Lambda Phi Lambda' + Theta with Theta diagonal, so the residual is S - Lambda Phi Lambda':
 * the data "with the latents factored out." A search run on it finds structure among the measured variables that
 * is not carried by the latents -- module-to-module and module-to-unit links under a single completion factor,
 * say -- which the same search on S cannot see, because there the common factor makes every pair adjacent.
 * <p>
 * For a model that also has measured-to-measured edges, the implied covariance includes their effects, and the
 * residual removes those too: it is what the model as a whole leaves unexplained, so a search on it reports edges
 * the model lacks, not the ones it already has.
 * <p>
 * No factor scores are involved. Residualizing each indicator on a score computed from the indicators would make
 * the residuals of one cluster negatively correlated by construction; subtracting the implied covariance does
 * not. The diagonal is S_ii - Sigma_ii + Theta_ii, which equals Theta_ii at a fit that reproduces the variances
 * and otherwise keeps the misfit of the variances in the residual where a search can see it.
 * <p>
 * <b>When the residual localizes an edge, and when it does not.</b> The residual can only show what the fit did
 * not absorb. A maximum-likelihood fit of a pure model to data with a measured-to-measured edge inside a cluster
 * absorbs much of that edge into the two loadings and error variances when the cluster is small: with five
 * indicators per latent and an edge of 0.6 between two of them, the pair's residual correlation is below the
 * sampling noise of the other pairs (0.08 against 0.14 at n = 5000), while with ten indicators it stands out
 * clearly (0.40 against 0.09, in ten seeds of ten). The other indicators are what pin the loadings down. An edge
 * between clusters is larger in the residual (0.6 or so) but also spills into the other cross-cluster pairs
 * (0.4 to 0.5), because it distorts the fitted latent covariance; with five indicators per cluster it is the
 * largest residual in seven seeds of ten, with ten in all ten. So: read the residual for latents with many
 * indicators -- a
 * block of twenty-eight checkpoints under one completion factor is the ideal case -- and do not read it at all
 * for latents with one or two, where it is degenerate (one indicator: residual variance zero; two: the loadings
 * are not separately identified). A chain that a latent search rendered as a string of two-indicator latents
 * should be put into the model as the observed chain, or left out, before the residual is taken.
 * <p>
 * The residual correlations are correlations among error terms, so their sampling noise is the noise of the
 * sample covariances divided by the error standard deviations: with strong loadings and small error variances,
 * noise of 0.1 to 0.15 at n = 5000 is normal, and a search on the residual needs a penalty that respects it.
 * <p>
 * <b>Indefiniteness, and what it means.</b> The matrix S - Sigma + Theta need not be positive definite in a
 * sample, and when it is not, that is information: some combination of the measured variables has less sample
 * covariance than the model implies for it, which is misfit, typically between clusters (one latent-to-latent
 * correlation cannot fit a cross-block covariance that is not rank one). Raising the offending eigenvalues to a
 * tiny floor would be the wrong repair: a direction with next to no variance is a deterministic relation, and a
 * search on the result draws every variable into whichever one that direction loads on. (On the chemistry data,
 * a residual over exams plus checkpoints had a smallest eigenvalue of -51 against a next of +56, and BOSS drew
 * thirty edges into Exam1.) Instead, when the smallest eigenvalue is below {@link #MIN_EIGENVALUE_FRACTION} of
 * the mean residual variance, a ridge is added to the diagonal to bring it up to that, and the smallest
 * eigenvalue, the ridge, and the variables the deficient direction loads on are logged. A large ridge is a sign
 * to residualize a block at a time -- the checkpoint block under its one factor is positive definite on its own
 * -- rather than the whole model. The sample size carried is the estimated model's.
 *
 * @author josephramsey
 */
public final class ResidualCovariance {

    /**
     * The smallest eigenvalue allowed, as a fraction of the mean residual variance; a ridge brings a smaller one
     * up to this.
     */
    public static final double MIN_EIGENVALUE_FRACTION = 0.05;

    private ResidualCovariance() {
    }

    /**
     * The residual covariance of the measured variables under the given estimated model.
     *
     * @param semIm an estimated SEM (one that carries a sample covariance, as the SEM estimator's result does).
     * @return the residual covariance, over the model's measured variables in the model's order.
     * @throws IllegalArgumentException if the model carries no sample covariance.
     */
    public static CovarianceMatrix residual(SemIm semIm) {
        Matrix sample = semIm.getSampleCovar();

        if (sample == null) {
            throw new IllegalArgumentException("The SEM carries no sample covariance; it was not estimated from "
                                               + "data. Estimate it first (an Estimator box), then take the "
                                               + "residual of the estimate.");
        }

        List<Node> measured = semIm.getMeasuredNodes();
        Matrix implied = semIm.getImplCovarMeas();
        int p = measured.size();

        if (sample.getNumRows() != p || implied.getNumRows() != p) {
            throw new IllegalStateException("Sample and implied covariance matrices do not match the measured "
                                            + "variables: " + sample.getNumRows() + ", " + implied.getNumRows()
                                            + ", " + p + ".");
        }

        Matrix residual = sample.minus(implied);

        for (int i = 0; i < p; i++) {
            residual.set(i, i, residual.get(i, i) + semIm.getErrVar(measured.get(i)));
        }

        // Symmetrize against floating-point drift, then floor the spectrum.
        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double v = 0.5 * (residual.get(i, j) + residual.get(j, i));
                residual.set(i, j, v);
                residual.set(j, i, v);
            }
        }

        Matrix repaired = ridgeIfIndefinite(residual, measured);
        return new CovarianceMatrix(measured, repaired, semIm.getSampleSize());
    }

    /**
     * Adds a ridge to the diagonal if the smallest eigenvalue is below MIN_EIGENVALUE_FRACTION of the mean
     * diagonal, logging what was done and which variables the deficient direction loads on; otherwise returns
     * the matrix itself.
     *
     * @param m        a symmetric matrix.
     * @param measured the variables, for the log.
     * @return the matrix, ridged if it had to be.
     */
    static Matrix ridgeIfIndefinite(Matrix m, List<Node> measured) {
        int p = m.getNumRows();
        double meanDiag = 0;
        for (int i = 0; i < p; i++) meanDiag += m.get(i, i);
        meanDiag /= p;

        SimpleEVD<SimpleMatrix> evd = m.getSimpleMatrix().eig();
        int argmin = 0;
        double min = Double.POSITIVE_INFINITY;

        for (int i = 0; i < p; i++) {
            double e = evd.getEigenvalue(i).getReal();
            if (e < min) {
                min = e;
                argmin = i;
            }
        }

        double target = MIN_EIGENVALUE_FRACTION * meanDiag;
        if (min >= target) return m;

        double ridge = target - min;
        Matrix out = m.copy();
        for (int i = 0; i < p; i++) out.set(i, i, out.get(i, i) + ridge);

        SimpleMatrix vec = evd.getEigenVector(argmin);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < p; i++) order.add(i);
        order.sort((a, b) -> Double.compare(Math.abs(vec.get(b, 0)), Math.abs(vec.get(a, 0))));
        StringBuilder loads = new StringBuilder();
        for (int k = 0; k < Math.min(6, p); k++) {
            int i = order.get(k);
            loads.append(k == 0 ? "" : ", ").append(measured.get(i).getName())
                    .append(String.format(" %+.2f", vec.get(i, 0)));
        }

        TetradLogger.getInstance().log(String.format(
                "Residual covariance is not positive definite: smallest eigenvalue %.2f against a mean residual "
                + "variance of %.2f. A ridge of %.2f was added to every variance to bring it to %.2f. The deficient "
                + "direction loads mainly on: %s. A ridge this size means the measurement model misfits in that "
                + "direction, usually between clusters; consider residualizing one block at a time.",
                min, meanDiag, ridge, target, loads));
        return out;
    }

}
