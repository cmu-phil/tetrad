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

/**
 * Independent covariance function, ie "white noise", with specified variance.
 * The covariance function is specified as:
 * <p>
 * k(x^p,x^q) = s2 * \delta(p,q)
 * <p>
 * where s2 is the noise variance and \delta(p,q) is a Kronecker delta function
 * which is 1 iff p=q and zero otherwise. The hyperparameter is
 * <p>
 * [ log(sqrt(s2)) ]
 */
public class CovNoise implements CovarianceFunction {

    /**
     * Creates a new <code>CovNoise CovarianceFunction<code>
     */
    public CovNoise(){
    }

    /**
     * Returns the number of hyperparameters of <code>CovSEard</code>
     * @return number of hyperparameters
     */
    public int numParameters() {
        return 1;
    }

    /**
     * Compute covariance matrix of a dataset X
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X  input dataset
     * @return  K covariance <code>Matrix</code>
     */
    public Matrix compute(Matrix loghyper, Matrix X) {

        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters());

        final double s2 = Math.exp(2*loghyper.get(0,0));                             // noise variance

        Matrix K = Matrix.identity(X.getRowDimension(),X.getRowDimension()).times(s2);

        return K;

    }

    /**
     * Compute compute test set covariances
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X  input dataset
     * @param Xstar  test set
     * @return  [K(Xstar,Xstar) K(X,Xstar)]
     */
    public Matrix[] compute(Matrix loghyper, Matrix X, Matrix Xstar) {

        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters());

        final double s2 = Math.exp(2*loghyper.get(0,0));                             // noise variance

        double[]a = new double[Xstar.getRowDimension()];
        Arrays.fill(a,s2);
        Matrix A =new Matrix(a,Xstar.getRowDimension());   // adding Gaussian

        Matrix B = new Matrix(X.getRowDimension(),Xstar.getRowDimension());

        return new Matrix[]{A,B};
    }

    /**
     * Coompute the derivatives of this <code>CovarianceFunction</code> with respect
     * to the hyperparameter with index <code>idx</code>
     *
     * @param loghyper hyperparameters
     * @param X input dataset
     * @param index hyperparameter index
     * @return  <code>Matrix</code> of derivatives
     */
    public Matrix computeDerivatives(Matrix loghyper, Matrix X, int index){

        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters());
        if(index>numParameters()-1)
            throw new IllegalArgumentException("Wrong hyperparameters index "+index+" it should be smaller or equal to "+(numParameters()-1));

        //noise parameter
        final double s2 = Math.exp(2*loghyper.get(0,0));
        Matrix A = Matrix.identity(X.getRowDimension(),X.getRowDimension()).times(2*s2);

        return A;
    }
}

