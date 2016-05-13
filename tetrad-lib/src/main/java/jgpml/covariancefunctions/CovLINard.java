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

/** Linear covariance function with Automatic Relevance Determination (ARD). The
 * covariance function is parameterized as:
 * <p>
 * k(x^p,x^q) = x^p'*inv(P)*x^q
 * <p>
 * where the P matrix is diagonal with ARD parameters ell_1^2,...,ell_D^2, where
 * D is the dimension of the input space. The hyperparameters are:
 * <p>
 * [ log(ell_1)  <br>
 *              log(ell_2)  <br>
 *               .          <br>
 *              log(ell_D) ] <br>
 * <p>
 * Note that there is no bias term; use covConst to add a bias.
 *
 */

public class CovLINard implements CovarianceFunction{

    private int D;

    /**
     * Creates a new <code>CovSEard CovarianceFunction<code>
     * @param inputDimension muber of dimension of the input
     */
    public CovLINard(int inputDimension){
        this.D = inputDimension;
    }

    /**
     * Returns the number of hyperparameters of this<code>CovarianceFunction</code>
     *
     * @return number of hyperparameters
     */
    public int numParameters() {
        return D;
    }

    /**
     * Compute covariance matrix of a dataset X
     *
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X        input dataset
     * @return K covariance <code>Matrix</code>
     */
    public Matrix compute(Matrix loghyper, Matrix X) {

        if(X.getColumnDimension()!=D)
            throw new IllegalArgumentException("The number of dimensions specified on the covariance function "+D+" must agree with the size of the input vector"+X.getColumnDimension());
        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters());

        final Matrix ell = exp(loghyper.getMatrix(0,D-1,0,0));                         // characteristic length scales
        Matrix diag = new Matrix(D,D);
        for(int i=0; i<D; i++)
            diag.set(i,i,1/ell.get(i,0));

        X = X.times(diag);

        return X.times(X.transpose());
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

        if(X.getColumnDimension()!=D)
            throw new IllegalArgumentException("The number of dimensions specified on the covariance function "+D+" must agree with the size of the input vector"+X.getColumnDimension());
        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters());

        final Matrix ell = exp(loghyper.getMatrix(0,D-1,0,0));                         // characteristic length scales
        Matrix diag = new Matrix(D,D);
        for(int i=0; i<D; i++)
            diag.set(i,i,1/ell.get(i,0));

        X = X.times(diag);

        Xstar = Xstar.times(diag);
        Matrix A = sumRows(Xstar.arrayTimes(Xstar));

        Matrix B = X.times(Xstar.transpose());
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
        if(X.getColumnDimension()!=D)
            throw new IllegalArgumentException("The number of dimensions specified on the covariance function "+D+" must agree with the size of the input vector"+X.getColumnDimension());
        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters());
        if(index>numParameters()-1)
            throw new IllegalArgumentException("Wrong hyperparameters index "+index+" it should be smaller or equal to "+(numParameters()-1));

        final Matrix ell = exp(loghyper.getMatrix(0,D-1,0,0));                         // characteristic length scales
        Matrix diag = new Matrix(D,D);
        for(int i=0; i<D; i++)
            diag.set(i,i,1/ell.get(i,0));

        X = X.times(diag);

        Matrix tmp =  X.getMatrix(0,X.getRowDimension()-1,index,index);
        return tmp.times(tmp.transpose()).times(-2);
    }



    public static void main(String[] args) {

        CovLINard cf = new CovLINard(6);

        Matrix X = Matrix.identity(6,6);
        Matrix logtheta = new Matrix(new double[][]{{0.1},{0.2},{0.3},{0.4},{0.5},{0.6}});

        Matrix z = new Matrix(new double[][]{{1,2,3,4,5,6},{1,2,3,4,5,6}});

        System.out.println("");
        //Matrix K = cf.compute(logtheta,X);
        //K.print(K.getColumnDimension(), 8);

        //Matrix[] res = cf.compute(logtheta,X,z);

        //res[0].print(res[0].getColumnDimension(), 8);
        //res[1].print(res[1].getColumnDimension(), 8);

        Matrix d = cf.computeDerivatives(logtheta,X,5);

        d.print(d.getColumnDimension(), 8);

    }
}
