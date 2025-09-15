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

package edu.cmu.tetrad.search.unmix;

public final class EmUtils {

    private EmUtils() {
    }

    /**
     * Hard labels by MAP over responsibilities (n x K).
     */
    public static int[] mapLabels(double[][] responsibilities) {
        int n = responsibilities.length;
        if (n == 0) return new int[0];
        int K = responsibilities[0].length;
        int[] z = new int[n];
        for (int i = 0; i < n; i++) {
            int arg = 0;
            double best = responsibilities[i][0];
            for (int k = 1; k < K; k++) {
                double v = responsibilities[i][k];
                if (v > best) {
                    best = v;
                    arg = k;
                }
            }
            z[i] = arg;
        }
        return z;
    }

    /**
     * Backward-compatible convenience: delegate to the modelâs own BIC.
     */
    public static double bic(GaussianMixtureEM.Model model, int n) {
        return model.bic(n);
    }

    /**
     * Numerically safe log-sum-exp for a vector.
     */
    public static double logSumExp(double[] a) {
        double m = Double.NEGATIVE_INFINITY;
        for (double v : a) if (v > m) m = v;
        double s = 0.0;
        for (double v : a) s += Math.exp(v - m);
        return m + Math.log(s);
    }

    /**
     * Normalize in-place a length-K vector to sum to 1; returns the sum before normalization.
     */
    public static double normalizeInPlace(double[] p) {
        double s = 0.0;
        for (double v : p) s += v;
        if (s <= 0) {
            double u = 1.0 / p.length;
            for (int i = 0; i < p.length; i++) p[i] = u;
            return 0.0;
        }
        for (int i = 0; i < p.length; i++) p[i] /= s;
        return s;
    }
}
