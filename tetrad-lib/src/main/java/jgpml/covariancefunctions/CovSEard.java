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
import java.util.Arrays;


/** Squared Exponential covariance function with Automatic Relevance Detemination
 * (ARD) distance measure. The covariance function is parameterized as:
 *  <p>
 * k(x^p,x^q) = sf2 * exp(-(x^p - x^q)'*inv(P)*(x^p - x^q)/2)
 * <p>
 * where the P matrix is diagonal with ARD parameters ell_1^2,...,ell_D^2, where
 * D is the dimension of the input space and sf2 is the signal variance. The
 * hyperparameters are:
 * <p>
 * [ log(ell_1)
 * log(ell_2)
 *  .
 * log(ell_D)
 *  log(sqrt(sf2))]
 */
public class CovSEard implements CovarianceFunction {

    private int D;
    private int numParameters;
    private Matrix K=null;

    /**
     * Creates a new <code>CovSEard CovarianceFunction<code>
     * @param inputDimension muber of dimension of the input
     */
    public CovSEard(int inputDimension){
        this.D = inputDimension;
        numParameters = D+1;
    }

    /**
     * Returns the number of hyperparameters of <code>CovSEard</code>
     * @return number of hyperparameters
     */
    public int numParameters() {
        return numParameters;
    }

    /**
     * Compute covariance matrix of a dataset X
     * @param loghyper column <code>Matrix</code> of hyperparameters
     * @param X  input dataset
     * @return  K covariance <code>Matrix</code>
     */
    public Matrix compute(Matrix loghyper, Matrix X) {

        if(X.getColumnDimension()!=D)
            throw new IllegalArgumentException("The number of dimensions specified on the covariance function "+D+" must agree with the size of the input vector"+X.getColumnDimension());
        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters)
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters);

        final Matrix ell = exp(loghyper.getMatrix(0,D-1,0,0));                         // characteristic length scales
        final double sf2 = Math.exp(2*loghyper.get(D,0));                              // signal variance

        Matrix diag = new Matrix(D,D);
        for(int i=0; i<D; i++)
            diag.set(i,i,1/ell.get(i,0));

        K = exp(squareDist(diag.times(X.transpose())).times(-0.5)).times(sf2);   // SE covariance

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

        if(X.getColumnDimension()!=D)
            throw new IllegalArgumentException("The number of dimensions specified on the covariance function "+D+" must agree with the size of the input vector"+X.getColumnDimension());
        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters)
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters);

        final Matrix ell = exp(loghyper.getMatrix(0,D-1,0,0));                         // characteristic length scales
        final double sf2 = Math.exp(2*loghyper.get(D,0));                              // signal variance

        double[] a = new double[Xstar.getRowDimension()];
        Arrays.fill(a,sf2);
        Matrix A = new Matrix(a,Xstar.getRowDimension());

        Matrix diag = new Matrix(D,D);
        for(int i=0; i<D; i++)
            diag.set(i,i,1/ell.get(i,0));

        Matrix B = exp(squareDist(diag.times(X.transpose()),diag.times(Xstar.transpose())).times(-0.5)).times(sf2);

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

        if(X.getColumnDimension()!=D)
            throw new IllegalArgumentException("The number of dimensions specified on the covariance function "+D+" must agree with the size of the input vector"+X.getColumnDimension());
        if(loghyper.getColumnDimension()!=1 || loghyper.getRowDimension()!=numParameters)
            throw new IllegalArgumentException("Wrong number of hyperparameters, "+loghyper.getRowDimension()+" instead of "+numParameters);
        if(index>numParameters()-1)
            throw new IllegalArgumentException("Wrong hyperparameters index "+index+" it should be smaller or equal to "+(numParameters()-1));

        Matrix A=null;

        final Matrix ell = exp(loghyper.getMatrix(0,D-1,0,0));                         // characteristic length scales
        final double sf2 = Math.exp(2*loghyper.get(D,0));                              // signal variance
        // noise variance

        if(K.getRowDimension()!=X.getRowDimension() || K.getColumnDimension()!=X.getRowDimension()){
            Matrix diag = new Matrix(D,D);
            for(int i=0; i<D; i++)
                diag.set(i,i,1/ell.get(i,0));

            K = exp(squareDist(diag.times(X.transpose())).times(-0.5)).times(sf2);   // SE covariance
        }

        if(index<D){   //length scale parameters
            Matrix col = squareDist(X.getMatrix(0,X.getRowDimension()-1,index,index).transpose().times(1/ell.get(index,0)));

            A = K.arrayTimes(col);
        } else {    // magnitude parameter
            A=K.times(2);
            K = null;
        }

        return A;
    }

    private static Matrix squareDist(Matrix a){
        return squareDist(a,a);
    }

    private static Matrix squareDist(Matrix a, Matrix b){
        Matrix C = new Matrix(a.getColumnDimension(),b.getColumnDimension());
        final int m = a.getColumnDimension();
        final int n = b.getColumnDimension();
        final int d = a.getRowDimension();

        for (int i=0; i<m; i++){
            for (int j=0; j<n; j++) {
                double z = 0.0;
                for (int k=0; k<d; k++) { double t = a.get(k,i) - b.get(k,j); z += t*t; }
                C.set(i,j,z);
            }
        }

        return C;
    }

}
