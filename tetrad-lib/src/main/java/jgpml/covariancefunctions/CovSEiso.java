/* This file is part of the jgpml Project.
 * http://github.com/renzodenardi/jgpml
 *
 * Copyright (c) 2011 Renzo De Nardi and Hugo Gravato-Marques
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package jgpml.covariancefunctions;

import Jama.Matrix;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;

import static jgpml.covariancefunctions.MatrixOperations.exp;

/**
 * Squared Exponential covariance function with isotropic distance measure. The covariance function is parameterized
 * as:
 * <p>
 * k(x^p,x^q) = sf2 * exp(-(x^p - x^q)'*inv(P)*(x^p - x^q)/2)
 * <p>
 * where the P matrix is ell^2 times the unit matrix and sf2 is the signal variance. The hyperparameters are:
 * <p>
 * [ log(ell) log(sqrt(sf2)) ]
 */

public class CovSEiso implements CovarianceFunction {

    /**
     * Creates a new <code>CovSEiso CovarianceFunction</code>
     */
    public CovSEiso() {
    }

    private static Matrix squareDist(Matrix a) {
        return CovSEiso.squareDist(a, a);
    }

    private static Matrix squareDist(Matrix a, Matrix b) {
        Matrix C = new Matrix(a.getColumnDimension(), b.getColumnDimension());
        int m = a.getColumnDimension();
        int n = b.getColumnDimension();
        int d = a.getRowDimension();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double z = 0.0;
                for (int k = 0; k < d; k++) {
                    double t = a.get(k, i) - b.get(k, j);
                    z += t * t;
                }
                C.set(i, j, z);
            }
        }

        return C;
    }

    /**
     * Main method for testing purposes
     * @param args ignored
     */
    public static void main(String[] args) {

        CovSEiso cf = new CovSEiso();

        Matrix X = Matrix.identity(6, 6);
        Matrix logtheta = new Matrix(new double[][]{{0.1}, {0.2}});

        Matrix d = cf.computeDerivatives(logtheta, X, 1);

        d.print(d.getColumnDimension(), 8);
    }

    /**
     * Returns the number of hyperparameters of this<code>CovarianceFunction</code>
     *
     * @return number of hyperparameters
     */
    public int numParameters() {
        return 2;
    }

    /**
     * Compute covariance matrix of a dataset X
     *
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X        input dataset
     * @return K covariance <code>Matrix</code>
     */
    public Matrix compute(Matrix loghyper, Matrix X) {

        if (loghyper.getColumnDimension() != 1 || loghyper.getRowDimension() != numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, " + loghyper.getRowDimension() + " instead of " + numParameters());

        double ell = FastMath.exp(loghyper.get(0, 0));
        double sf2 = FastMath.exp(2 * loghyper.get(1, 0));

        return exp(CovSEiso.squareDist(X.transpose().times(1 / ell)).times(-0.5)).times(sf2);
    }

    /**
     * Compute compute test set covariances
     *
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X        input dataset
     * @param Xstar    test set
     * @return [K(Xstar, Xstar) K(X,Xstar)]
     */
    public Matrix[] compute(Matrix loghyper, Matrix X, Matrix Xstar) {

        if (loghyper.getColumnDimension() != 1 || loghyper.getRowDimension() != numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, " + loghyper.getRowDimension() + " instead of " + numParameters());


        double ell = FastMath.exp(loghyper.get(0, 0));
        double sf2 = FastMath.exp(2 * loghyper.get(1, 0));
        double[] a = new double[Xstar.getRowDimension()];
        Arrays.fill(a, sf2);
        Matrix A = new Matrix(a, a.length);

        Matrix B = exp(CovSEiso.squareDist(X.transpose().times(1 / ell), Xstar.transpose().times(1 / ell)).times(-0.5)).times(sf2);

        return new Matrix[]{A, B};
    }

    /**
     * Coompute the derivatives of this <code>CovarianceFunction</code> with respect to the hyperparameter with index
     * <code>idx</code>
     *
     * @param loghyper hyperparameters
     * @param X        input dataset
     * @param index    hyperparameter index
     * @return <code>Matrix</code> of derivatives
     */
    public Matrix computeDerivatives(Matrix loghyper, Matrix X, int index) {

        if (loghyper.getColumnDimension() != 1 || loghyper.getRowDimension() != numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, " + loghyper.getRowDimension() + " instead of " + numParameters());
        if (index > numParameters() - 1)
            throw new IllegalArgumentException("Wrong hyperparameters index " + index + " it should be smaller or equal to " + (numParameters() - 1));

        double ell = FastMath.exp(loghyper.get(0, 0));
        double sf2 = FastMath.exp(2 * loghyper.get(1, 0));

        Matrix tmp = CovSEiso.squareDist(X.transpose().times(1 / ell));
        Matrix A = null;
        if (index == 0) {
            A = exp(tmp.times(-0.5)).arrayTimes(tmp).times(sf2);
        } else {
            A = exp(tmp.times(-0.5)).times(2 * sf2);
        }

        return A;
    }
}
