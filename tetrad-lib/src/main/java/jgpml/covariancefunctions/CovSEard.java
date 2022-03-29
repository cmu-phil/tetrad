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

import java.util.Arrays;

import static jgpml.covariancefunctions.MatrixOperations.exp;


/**
 * Squared Exponential covariance function with Automatic Relevance Detemination
 * (ARD) distance measure. The covariance function is parameterized as:
 * <p>
 * k(x^p,x^q) = sf2 * exp(-(x^p - x^q)'*inv(P)*(x^p - x^q)/2)
 * <p>
 * where the P matrix is diagonal with ARD parameters ell_1^2,...,ell_D^2, where
 * D is the dimension of the input space and sf2 is the signal variance. The
 * hyperparameters are:
 * <p>
 * [ log(ell_1)
 * log(ell_2)
 * .
 * log(ell_D)
 * log(sqrt(sf2))]
 */
public class CovSEard implements CovarianceFunction {

    private final int D;
    private final int numParameters;
    private Matrix K;

    /**
     * Creates a new <code>CovSEard CovarianceFunction<code>
     *
     * @param inputDimension muber of dimension of the input
     */
    public CovSEard(final int inputDimension) {
        this.D = inputDimension;
        this.numParameters = this.D + 1;
    }

    /**
     * Returns the number of hyperparameters of <code>CovSEard</code>
     *
     * @return number of hyperparameters
     */
    public int numParameters() {
        return this.numParameters;
    }

    /**
     * Compute covariance matrix of a dataset X
     *
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X        input dataset
     * @return K covariance <code>Matrix</code>
     */
    public Matrix compute(final Matrix loghyper, final Matrix X) {

        if (X.getColumnDimension() != this.D)
            throw new IllegalArgumentException("The number of dimensions specified on the covariance function " + this.D + " must agree with the size of the input vector" + X.getColumnDimension());
        if (loghyper.getColumnDimension() != 1 || loghyper.getRowDimension() != this.numParameters)
            throw new IllegalArgumentException("Wrong number of hyperparameters, " + loghyper.getRowDimension() + " instead of " + this.numParameters);

        final Matrix ell = exp(loghyper.getMatrix(0, this.D - 1, 0, 0));                         // characteristic length scales
        final double sf2 = Math.exp(2 * loghyper.get(this.D, 0));                              // signal variance

        final Matrix diag = new Matrix(this.D, this.D);
        for (int i = 0; i < this.D; i++)
            diag.set(i, i, 1 / ell.get(i, 0));

        this.K = exp(CovSEard.squareDist(diag.times(X.transpose())).times(-0.5)).times(sf2);   // SE covariance

        return this.K;
    }

    /**
     * Compute compute test set covariances
     *
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X        input dataset
     * @param Xstar    test set
     * @return [K(Xstar, Xstar) K(X,Xstar)]
     */
    public Matrix[] compute(final Matrix loghyper, final Matrix X, final Matrix Xstar) {

        if (X.getColumnDimension() != this.D)
            throw new IllegalArgumentException("The number of dimensions specified on the covariance function " + this.D + " must agree with the size of the input vector" + X.getColumnDimension());
        if (loghyper.getColumnDimension() != 1 || loghyper.getRowDimension() != this.numParameters)
            throw new IllegalArgumentException("Wrong number of hyperparameters, " + loghyper.getRowDimension() + " instead of " + this.numParameters);

        final Matrix ell = exp(loghyper.getMatrix(0, this.D - 1, 0, 0));                         // characteristic length scales
        final double sf2 = Math.exp(2 * loghyper.get(this.D, 0));                              // signal variance

        final double[] a = new double[Xstar.getRowDimension()];
        Arrays.fill(a, sf2);
        final Matrix A = new Matrix(a, Xstar.getRowDimension());

        final Matrix diag = new Matrix(this.D, this.D);
        for (int i = 0; i < this.D; i++)
            diag.set(i, i, 1 / ell.get(i, 0));

        final Matrix B = exp(CovSEard.squareDist(diag.times(X.transpose()), diag.times(Xstar.transpose())).times(-0.5)).times(sf2);

        return new Matrix[]{A, B};
    }


    /**
     * Coompute the derivatives of this <code>CovarianceFunction</code> with respect
     * to the hyperparameter with index <code>idx</code>
     *
     * @param loghyper hyperparameters
     * @param X        input dataset
     * @param index    hyperparameter index
     * @return <code>Matrix</code> of derivatives
     */
    public Matrix computeDerivatives(final Matrix loghyper, final Matrix X, final int index) {

        if (X.getColumnDimension() != this.D)
            throw new IllegalArgumentException("The number of dimensions specified on the covariance function " + this.D + " must agree with the size of the input vector" + X.getColumnDimension());
        if (loghyper.getColumnDimension() != 1 || loghyper.getRowDimension() != this.numParameters)
            throw new IllegalArgumentException("Wrong number of hyperparameters, " + loghyper.getRowDimension() + " instead of " + this.numParameters);
        if (index > numParameters() - 1)
            throw new IllegalArgumentException("Wrong hyperparameters index " + index + " it should be smaller or equal to " + (numParameters() - 1));

        Matrix A = null;

        final Matrix ell = exp(loghyper.getMatrix(0, this.D - 1, 0, 0));                         // characteristic length scales
        final double sf2 = Math.exp(2 * loghyper.get(this.D, 0));                              // signal variance
        // noise variance

        if (this.K.getRowDimension() != X.getRowDimension() || this.K.getColumnDimension() != X.getRowDimension()) {
            final Matrix diag = new Matrix(this.D, this.D);
            for (int i = 0; i < this.D; i++)
                diag.set(i, i, 1 / ell.get(i, 0));

            this.K = exp(CovSEard.squareDist(diag.times(X.transpose())).times(-0.5)).times(sf2);   // SE covariance
        }

        if (index < this.D) {   //length scale parameters
            final Matrix col = CovSEard.squareDist(X.getMatrix(0, X.getRowDimension() - 1, index, index).transpose().times(1 / ell.get(index, 0)));

            A = this.K.arrayTimes(col);
        } else {    // magnitude parameter
            A = this.K.times(2);
            this.K = null;
        }

        return A;
    }

    private static Matrix squareDist(final Matrix a) {
        return CovSEard.squareDist(a, a);
    }

    private static Matrix squareDist(final Matrix a, final Matrix b) {
        final Matrix C = new Matrix(a.getColumnDimension(), b.getColumnDimension());
        final int m = a.getColumnDimension();
        final int n = b.getColumnDimension();
        final int d = a.getRowDimension();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double z = 0.0;
                for (int k = 0; k < d; k++) {
                    final double t = a.get(k, i) - b.get(k, j);
                    z += t * t;
                }
                C.set(i, j, z);
            }
        }

        return C;
    }

}
