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

/**
 * Composes a covariance function as the sum of other covariance
 * functions. This function doesn't actually compute very much on its own, it
 * merely calls other covariance functions with the right parameters.
 */

public class CovSum implements CovarianceFunction {

    CovarianceFunction[] f;
    int[] idx;

    /**
     * Create a new <code>CovarianceFunction</code> as sum of the
     * <code>CovarianceFunction</code>s passed as input.
     *
     * @param inputDimensions input dimension of the dataset
     * @param f               array of <code>CovarianceFunction</code>
     * @see CovarianceFunction
     */
    public CovSum(int inputDimensions, CovarianceFunction... f) {
        this.f = f;
        this.idx = new int[f.length + 1];
        for (int i = 0; i < f.length; i++) {
            this.idx[i + 1] = this.idx[i] + f[i].numParameters();
        }
    }

    /**
     * Returns the number of hyperparameters of this<code>CovarianceFunction</code>
     *
     * @return number of hyperparameters
     */
    public int numParameters() {
        return this.idx[this.f.length];
    }

    /**
     * Compute covariance matrix of a dataset X
     *
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X        input dataset
     * @return K covariance <code>Matrix</code>
     */
    public Matrix compute(Matrix loghyper, Matrix X) {

        Matrix K = new Matrix(X.getRowDimension(), X.getRowDimension());

        for (int i = 0; i < this.f.length; i++) {
            Matrix loghyperi = loghyper.getMatrix(this.idx[i], this.idx[i + 1] - 1, 0, 0);
            K.plusEquals(this.f[i].compute(loghyperi, X));
        }
        return K;
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

        Matrix A = new Matrix(Xstar.getRowDimension(), 1);
        Matrix B = new Matrix(X.getRowDimension(), Xstar.getRowDimension());

        for (int i = 0; i < this.f.length; i++) {
            Matrix loghyperi = loghyper.getMatrix(this.idx[i], this.idx[i + 1] - 1, 0, 0);
            Matrix[] K = this.f[i].compute(loghyperi, X, Xstar);
            A.plusEquals(K[0]);
            B.plusEquals(K[1]);
        }
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
    public Matrix computeDerivatives(Matrix loghyper, Matrix X, int index) {


        if (index > numParameters() - 1)
            throw new IllegalArgumentException("Wrong hyperparameters index " + index + " it should be smaller or equal to " + (numParameters() - 1));

        int whichf = 0;
        while (index > (this.idx[whichf + 1] - 1)) whichf++;  // find in which of the covariance this parameter is

        Matrix loghyperi = loghyper.getMatrix(this.idx[whichf], this.idx[whichf + 1] - 1, 0, 0);
        index -= this.idx[whichf];
        return this.f[whichf].computeDerivatives(loghyperi, X, index);
    }


}
