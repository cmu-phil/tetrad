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
 * The matrix S - Sigma + Theta need not be positive semidefinite in a sample, so eigenvalues below a small floor
 * are raised to it before the result is returned. The sample size carried is the estimated model's.
 *
 * @author josephramsey
 */
public final class ResidualCovariance {

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

        Matrix floored = floorSpectrum(residual, 1e-8);
        return new CovarianceMatrix(measured, floored, semIm.getSampleSize());
    }

    /**
     * Returns the matrix with every eigenvalue below the floor raised to it (the symmetric part is used), or the
     * matrix itself when none is below.
     *
     * @param m     a symmetric matrix.
     * @param floor the smallest eigenvalue allowed.
     * @return the adjusted matrix.
     */
    static Matrix floorSpectrum(Matrix m, double floor) {
        SimpleMatrix s = m.getSimpleMatrix();
        SimpleEVD<SimpleMatrix> evd = s.eig();
        int p = m.getNumRows();
        boolean adjust = false;

        for (int i = 0; i < p; i++) {
            if (evd.getEigenvalue(i).getReal() < floor) adjust = true;
        }

        if (!adjust) return m;

        SimpleMatrix v = new SimpleMatrix(p, p);
        SimpleMatrix d = new SimpleMatrix(p, p);

        for (int i = 0; i < p; i++) {
            SimpleMatrix vec = evd.getEigenVector(i);
            for (int r = 0; r < p; r++) v.set(r, i, vec.get(r, 0));
            d.set(i, i, Math.max(evd.getEigenvalue(i).getReal(), floor));
        }

        return new Matrix(v.mult(d).mult(v.transpose()));
    }
}
