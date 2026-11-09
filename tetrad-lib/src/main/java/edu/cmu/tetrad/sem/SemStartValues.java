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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.List;

/**
 * Random starting values for a SEM's free parameters that respect the scale of the sample covariance. The
 * optimizers used to draw every start from a fixed box -- Powell variances on (0, 1) and coefficients on (-1, 1),
 * EM variances on (0, 3) and coefficients on (-2, 2) -- which is fine for simulated data with unit variances and
 * hopeless for a grade file whose variances run from 370 to 1700: the discrepancy at such a start was 10,000 on
 * the chemistry data, the EM optimizer returned after one step, and the SEM estimator handed back what were
 * essentially its starting values, with implied variances of 2 to 5 against sample variances in the hundreds, and
 * nothing said so. Powell from the same kind of start found a poor local optimum.
 * <p>
 * Here a measured variable's error variance starts at a random fraction, between 0.3 and 0.8, of its sample
 * variance; a loading from a latent to a measured variable starts at the positive square root of a random fraction
 * of the sample variance, so that loadings of one factor agree in sign; a latent's variance starts near 1; a
 * coefficient between two measured variables, or between two latents, starts small and scale-free, a random value
 * on (-0.5, 0.5) times the ratio of the standard deviations; and an error covariance starts at zero. With this
 * start, Powell on the chemistry model reaches a fit that reproduces every variance in two seconds.
 *
 * @author josephramsey
 */
public final class SemStartValues {

    private SemStartValues() {
    }

    /**
     * Draws scale-aware random starting values for the given free parameters.
     *
     * @param semIm          the model, carrying the sample covariance (if it carries none, the fixed-box draws of
     *                       the past are used, since there is no scale to respect).
     * @param freeParameters the free parameters, in the order the values are to be returned.
     * @return one value per parameter.
     */
    public static double[] draw(SemIm semIm, List<Parameter> freeParameters) {
        Matrix cov = semIm.getSampleCovar();
        List<Node> measured = semIm.getMeasuredNodes();
        RandomUtil random = RandomUtil.getInstance();
        double[] p = new double[freeParameters.size()];

        for (int i = 0; i < freeParameters.size(); i++) {
            Parameter param = freeParameters.get(i);
            Node a = param.getNodeA(), b = param.getNodeB();
            double varA = sampleVariance(cov, measured, a), varB = sampleVariance(cov, measured, b);

            switch (param.getType()) {
                case VAR -> p[i] = Double.isNaN(varA)
                        ? random.nextUniform(0.5, 2.0)                        // a latent's variance
                        : random.nextUniform(0.3, 0.8) * varA;                // a measured error variance
                case COEF -> {
                    boolean latentToMeasured = a.getNodeType() == NodeType.LATENT && !Double.isNaN(varB);
                    if (latentToMeasured) {
                        p[i] = Math.sqrt(random.nextUniform(0.2, 0.7) * varB); // a loading, positive
                    } else {
                        double scale = (Double.isNaN(varA) || Double.isNaN(varB)) ? 1.0 : Math.sqrt(varB / varA);
                        p[i] = random.nextUniform(-0.5, 0.5) * scale;
                    }
                }
                case COVAR -> p[i] = 0.0;
                default -> p[i] = random.nextUniform(-1, 1);
            }
        }

        return p;
    }

    /**
     * The sample variance of a measured node, or NaN for a latent (or when there is no sample covariance).
     */
    private static double sampleVariance(Matrix cov, List<Node> measured, Node node) {
        if (cov == null || node == null) return Double.NaN;
        int j = measured.indexOf(node);
        if (j < 0) {
            for (int k = 0; k < measured.size(); k++) {
                if (measured.get(k).getName().equals(node.getName())) {
                    j = k;
                    break;
                }
            }
        }
        return j < 0 ? Double.NaN : cov.get(j, j);
    }
}
