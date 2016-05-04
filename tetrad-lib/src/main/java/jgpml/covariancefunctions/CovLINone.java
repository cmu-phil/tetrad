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
import static jgpml.covariancefunctions.MatrixOperations.*;

/**  Linear covariance function with a single hyperparameter. The covariance
 * function is parameterized as:
 * <p>
 * k(x^p,x^q) = x^p'*inv(P)*x^q + 1./t2;
 *  <p>
 * where the P matrix is t2 times the unit matrix. The second term plays the
 * role of the bias. The hyperparameter is:
 *  <p>
 * [ log(sqrt(t2)) ]
 */

public class CovLINone implements CovarianceFunction{

    public CovLINone(){}

    /**
     * Returns the number of hyperparameters of this<code>CovarianceFunction</code>
     *
     * @return number of hyperparameters
     */
    public int numParameters() {
        return 1;
    }

    /**
     * Compute covariance matrix of a dataset X
     *
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X        input dataset
     * @return K covariance <code>Matrix</code>
     */
    public Matrix compute(Matrix loghyper, Matrix X) {
        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters());

        final double it2 = Math.exp(-2*loghyper.get(0,0));

        Matrix A = X.times(X.transpose());
        return addValue(A,1).times(it2);
    }

    /**
     * Compute compute test set covariances
     *
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X        input dataset
     * @param Xstar    test set
     * @return [K(Xstar,Xstar) K(X,Xstar)]
     */
    public Matrix[] compute(Matrix loghyper, Matrix X, Matrix Xstar) {
        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters());

        final double it2 = Math.exp(-2*loghyper.get(0,0));

        Matrix A = sumRows(Xstar.arrayTimes(Xstar));

        A= addValue(A,1).times(it2);

        Matrix B = X.times(Xstar.transpose());
        B = addValue(B,1).times(it2);

        return new Matrix[]{A,B};
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

        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters());
        if(index>numParameters()-1)
            throw new IllegalArgumentException("Wrong hyperparameters index "+index+" it should be smaller or equal to "+(numParameters()-1));

        final double it2 = Math.exp(-2*loghyper.get(0,0));
        Matrix A = X.times(X.transpose());
        return addValue(A,1).times(-2*it2);
    }

    public static void main(String[] args) {
        CovLINone cf = new CovLINone();

            Matrix X = Matrix.identity(6,6);
            Matrix logtheta = new Matrix(new double[][]{{0.1}});

            Matrix z = new Matrix(new double[][]{{1,2,3,4,5,6},{1,2,3,4,5,6}});

            System.out.println("");
            Matrix K = cf.compute(logtheta,X);
            K.print(K.getColumnDimension(), 8);

            Matrix[] res = cf.compute(logtheta,X,z);

            res[0].print(res[0].getColumnDimension(), 8);
            res[1].print(res[1].getColumnDimension(), 8);

            Matrix d = cf.computeDerivatives(logtheta,X,0);

            d.print(d.getColumnDimension(), 8);

    }
}
