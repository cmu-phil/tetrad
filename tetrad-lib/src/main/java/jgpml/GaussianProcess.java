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

package jgpml;

import jgpml.covariancefunctions.CovLINone;
import jgpml.covariancefunctions.CovNoise;
import jgpml.covariancefunctions.CovSum;
import jgpml.covariancefunctions.CovarianceFunction;
import Jama.Matrix;
import Jama.CholeskyDecomposition;

/**
 * Main class of the package, contains the objects that constitutes a Gaussian Process as well
 *  as the algorithm to train the Hyperparameters and to do predictions. 
 */
public class GaussianProcess {

    /**
     * hyperparameters
     */
    public Matrix logtheta;

    /**
     *  input data points
     */
    public Matrix X;

    /**
     * Cholesky decomposition of the input
     */
    public Matrix L;

    /**
     * partial factor
     */
    public Matrix alpha;

    /**
     * covariance function
     */
    CovarianceFunction covFunction;


    /**
     * Creates a new GP object.
     * @param covFunction - the covariance function
     */
    public GaussianProcess(CovarianceFunction covFunction){
        this.covFunction = covFunction;
    }


    /**
     * Trains the GP Hyperparameters maximizing the marginal likelihood. By default the minimisation algorithm performs 100 iterations.
     * @param X - the input data points
     * @param y - the target data points
     * @param logtheta0 - the initial hyperparameters of the covariance function
     */
    public void train(Matrix X, Matrix y, Matrix logtheta0){
        train(X, y, logtheta0, -100);
    }

    /**
     * Trains the GP Hyperparameters maximizing the marginal likelihood. By default the algorithm performs 100 iterations.
     * @param X - the input data points
     * @param y - the target data points
     * @param logtheta0 - the initial hyperparameters of the covariance function
     * @param iterations - number of iterations performed by the minimization algorithm
     */
    public void train(Matrix X, Matrix y, Matrix logtheta0, int iterations){
        System.out.println("training started...");
        this.X = X;
        logtheta = minimize(logtheta0,iterations,X,y);
    }


    /**
     * Computes minus the log likelihood and its partial derivatives with
     * respect to the hyperparameters; this mode is used to fit the hyperparameters.
     * @param logtheta  column <code>Matrix</code> of hyperparameters
     * @param y output dataset
     * @param df0 returned partial derivatives with respect to the hyperparameters
     * @return lml minus log marginal likelihood
     */
    public double negativeLogLikelihood(Matrix logtheta,Matrix x, Matrix y, Matrix df0) {

        int n = x.getRowDimension();

        Matrix K = covFunction.compute(logtheta, x);    // compute training set covariance matrix

        CholeskyDecomposition cd = K.chol();
        if(!cd.isSPD()) {
            throw new RuntimeException("The covariance Matrix is not SDP, check your covariance function (maybe you mess the noise term..)");
        }   else{
            L = cd.getL();                // cholesky factorization of the covariance

            // alpha = L'\(L\y);
            alpha = bSubstitutionWithTranspose(L,fSubstitution(L,y));

//            double[][] yarr = y.getArray();
//            double[][] alphaarr = alpha.getArray();
//            double lml =0;
//            for(int i=0; i<n; i++){
//                lml+= yarr[i][0]*alphaarr[i][0];
//            }
//            lml*=0.5;
//

            // compute the negative log marginal likelihood
            double lml = (y.transpose().times(alpha).times(0.5)).get(0,0);

            for(int i=0; i<L.getRowDimension(); i++) lml+=Math.log(L.get(i,i));
            lml += 0.5*n*Math.log(2*Math.PI);




            Matrix W = bSubstitutionWithTranspose(L,(fSubstitution(L,Matrix.identity(n,n)))).minus(alpha.times(alpha.transpose()));     // precompute for convenience
            for(int i=0; i<df0.getRowDimension(); i++){
                df0.set(i,0,sum(W.arrayTimes(covFunction.computeDerivatives(logtheta, x, i)))/2);
            }

            return lml;
        }
    }



    /**
     * Computes Gaussian predictions, whose mean and variance are returned.
     * Note that in cases where the covariance function has noise contributions,
     * the variance returned in S2 is for noisy test targets;
     * if you want the variance of the noise-free latent function, you must subtract the noise variance.
     * @param xstar test dataset
     * @return [ystar Sstar] predicted mean and covariance
     */

    public Matrix[] predict(Matrix xstar){

        if(alpha == null || L == null) { System.out.println("GP needs to be trained first.."); System.exit(-1);}
        if(xstar.getColumnDimension()!=X.getColumnDimension()) throw new IllegalArgumentException("Wrong size of the input "+xstar.getColumnDimension()+" instead of "+X.getColumnDimension());
        Matrix[] star = covFunction.compute(logtheta, X,xstar);

        Matrix Kstar = star[1];
        Matrix Kss = star[0];

        Matrix ystar = Kstar.transpose().times(alpha);

        Matrix v= fSubstitution(L, Kstar);

        v.arrayTimesEquals(v);

        Matrix Sstar = Kss.minus(sumColumns(v).transpose());

        return new Matrix[]{ystar, Sstar};
    }


        /**
     * Computes Gaussian predictions, whose mean is returned.
     * Note that in cases where the covariance function has noise contributions,
     * the variance returned in S2 is for noisy test targets;
     * if you want the variance of the noise-free latent function, you must substract the noise variance.
     * @param xstar test dataset
     * @return [ystar Sstar] predicted mean and covariance
     */

    public Matrix predictMean(Matrix xstar){

        if(alpha == null || L == null) { System.out.println("GP needs to be trained first.."); System.exit(-1);}
        if(xstar.getColumnDimension()!=X.getColumnDimension()) throw new IllegalArgumentException("Wrong size of the input"+xstar.getColumnDimension()+" instead of "+X.getColumnDimension());

        Matrix[] star = covFunction.compute(logtheta, X,xstar);

        Matrix Kstar = star[1];

        Matrix ystar = Kstar.transpose().times(alpha);

        return ystar;
    }

    private static Matrix sumColumns(Matrix a){
        Matrix sum = new Matrix(1,a.getColumnDimension());
        for(int i=0; i<a.getRowDimension(); i++)
            sum.plusEquals(a.getMatrix(i,i,0,a.getColumnDimension()-1));
        return sum;
    }


    private static double sum(Matrix a){
        double sum =0 ;
        for(int i=0; i<a.getRowDimension(); i++)
            for(int j=0; j<a.getColumnDimension(); j++)
                sum+=a.get(i,j);
        return sum;
    }


    private static Matrix fSubstitution(Matrix L, Matrix B){

        final double[][] l = L.getArray();
        final double[][] b = B.getArray();
        final double[][] x = new double[B.getRowDimension()][B.getColumnDimension()];

        final int n = x.length;

        for(int i=0; i<B.getColumnDimension(); i++){
            for(int k=0; k<n; k++){
                x[k][i] = b[k][i];
                for(int j=0; j<k; j++){
                    x[k][i] -= l[k][j] * x[j][i];
                }
                x[k][i] /= l[k][k];
            }
        }
        return new Matrix(x);
    }



    private static Matrix bSubstitution(Matrix L, Matrix B){

        final double[][] l = L.getArray();
        final double[][] b = B.getArray();
        final double[][] x = new double[B.getRowDimension()][B.getColumnDimension()];

        final int n = x.length-1;

        for(int i=0; i<B.getColumnDimension(); i++){
            for(int k=n; k > -1; k--){
                x[k][i] = b[k][i];
                for(int j=n; j>k; j--){
                    x[k][i] -= l[k][j] * x[j][i];
                }
                x[k][i] /= l[k][k];
            }
        }
        return new Matrix(x);

    }

     private static Matrix bSubstitutionWithTranspose(Matrix L, Matrix B){

        final double[][] l = L.getArray();
        final double[][] b = B.getArray();
        final double[][] x = new double[B.getRowDimension()][B.getColumnDimension()];

        final int n = x.length-1;

        for(int i=0; i<B.getColumnDimension(); i++){
            for(int k=n; k > -1; k--){
                x[k][i] = b[k][i];
                for(int j=n; j>k; j--){
                    x[k][i] -= l[j][k] * x[j][i];
                }
                x[k][i] /= l[k][k];
            }
        }
        return new Matrix(x);

    }

    private final static double INT = 0.1;                // don't reevaluate within 0.1 of the limit of the current bracket

    private final static double EXT = 3.0;                // extrapolate maximum 3 times the current step-size

    private final static int MAX = 20;                    // max 20 function evaluations per line search

    private final static double RATIO = 10;               // maximum allowed slope ratio

    private final static double SIG = 0.1, RHO = SIG/2;   // SIG and RHO are the constants controlling the Wolfe-
    // Powell conditions. SIG is the maximum allowed absolute ratio between
    // previous and new slopes (derivatives in the search direction), thus setting
    // SIG to low (positive) values forces higher precision in the line-searches.
    // RHO is the minimum allowed fraction of the expected (from the slope at the
    // initial point in the linesearch). Constants must satisfy 0 < RHO < SIG < 1.
    // Tuning of SIG (depending on the nature of the function to be optimized) may
    // speed up the minimization; it is probably not worth playing much with RHO.


    private Matrix minimize(Matrix params, int length, Matrix in, Matrix out){

        double A, B;
        double x1, x2, x3, x4;
        double f0, f1, f2, f3, f4;
        double d0, d1, d2, d3, d4;
        Matrix df0, df3;
        Matrix fX;

        double red = 1.0;

        int i = 0;
        int ls_failed = 0;

        int sizeX = params.getRowDimension();

        df0 = new Matrix(sizeX,1);
        f0 = negativeLogLikelihood(params, in, out,df0);
        //f0 = f.evaluate(params,cf, in, out, df0);

        fX = new Matrix(new double[]{f0}, 1);

        i = (length < 0) ? i+1 : i;

        Matrix s = df0.times(-1);

        // initial search direction (steepest) and slope
        d0 = s.times(-1).transpose().times(s).get(0,0);
        x3 = red/(1-d0);                                  // initial step is red/(|s|+1)

        final int nCycles = Math.abs(length);

        int success;

        double M;
        while (i < nCycles){
            //System.out.println("-");
            i = (length > 0) ? i+1 : i;    // count iterations?!

            // make a copy of current values
            double F0 = f0;
            Matrix X0 = params.copy();
            Matrix dF0 = df0.copy();

            M = (length>0) ? MAX : Math.min(MAX, -length-i);

            while (true) {                            // keep extrapolating as long as necessary

                x2 = 0;
                f2 = f0;
                d2 = d0;
                f3 = f0;
                df3 = df0.copy();

                success = 0;

                while (success == 0 && M > 0){
                    //try
                    M = M - 1;   i = (length < 0) ? i+1 : i;    // count iterations?!

                    Matrix m1 = params.plus(s.times(x3));
                    //f3 = f.evaluate(m1,cf, in, out, df3);
                    f3 = negativeLogLikelihood(m1, in, out,df3);

                    if (Double.isNaN(f3) || Double.isInfinite(f3) || hasInvalidNumbers(df3.getRowPackedCopy())){
                        x3 = (x2+x3)/2;     // catch any error which occured in f
                    }else{
                        success = 1;
                    }

                }

                if (f3 < F0){                   // keep best values
                    X0 = s.times(x3).plus(params);
                    F0 = f3;
                    dF0 = df3;
                }

                d3 = df3.transpose().times(s).get(0,0);  // new slope

                if (d3 > SIG*d0 || f3 > f0+x3*RHO*d0 || M == 0){  // are we done extrapolating?
                    break;
                }

                x1 = x2; f1 = f2; d1 = d2;                   // move point 2 to point 1
                x2 = x3; f2 = f3; d2 = d3;                  // move point 3 to point 2

                A = 6*(f1-f2)+3*(d2+d1)*(x2-x1);     // make cubic extrapolation
                B = 3*(f2-f1)-(2*d1+d2)*(x2-x1);

                x3 = x1-d1*(x2-x1)*(x2-x1)/(B + Math.sqrt(B*B-A*d1*(x2-x1)));  // num. error possible, ok!

                if (Double.isNaN(x3) || Double.isInfinite(x3) || x3 < 0)     // num prob | wrong sign?
                    x3 = x2*EXT;                             // extrapolate maximum amount
                else if (x3 > x2*EXT)                        // new point beyond extrapolation limit?
                    x3 = x2*EXT;                            // extrapolate maximum amount
                else if (x3 < x2+INT*(x2-x1))               // new point too close to previous point?
                    x3 = x2+INT*(x2-x1);

            }

            f4 = 0;
            x4 = 0;
            d4 = 0;

            while ((Math.abs(d3) > -SIG*d0 ||
                    f3 > f0+x3*RHO*d0) && M > 0){               // keep interpolating

                if (d3 > 0 || f3 > f0+x3*RHO*d0){                // choose subinterval
                    x4 = x3; f4 = f3; d4 = d3;                  // move point 3 to point 4
                }else{
                    x2 = x3; f2 = f3; d2 = d3;                          // move point 3 to point 2
                }

                if (f4 > f0){
                    x3 = x2-(0.5*d2*(x4-x2)*(x4-x2))/(f4-f2-d2*(x4-x2));    // quadratic interpolation
                }else{
                    A = 6*(f2-f4)/(x4-x2)+3*(d4+d2);                        // cubic interpolation
                    B = 3*(f4-f2)-(2*d2+d4)*(x4-x2);
                    x3 = x2+(Math.sqrt(B*B-A*d2*(x4-x2)*(x4-x2))-B)/A;      // num. error possible, ok!
                }

                if (Double.isNaN(x3) || Double.isInfinite(x3)){
                    x3 = (x2+x4)/2;               // if we had a numerical problem then bisect
                }

                x3 = Math.max(Math.min(x3, x4-INT*(x4-x2)),x2+INT*(x4-x2));  // don't accept too close

                Matrix m1 = s.times(x3).plus(params);
                //f3 = f.evaluate(m1,cf, in, out, df3);
                f3 = negativeLogLikelihood(m1, in, out,df3);

                if (f3 < F0){
                    X0 = m1.copy();
                    F0 = f3;
                    dF0 = df3.copy();                            // keep best values
                }

                M = M - 1;  i = (length < 0) ? i+1 : i;          // count iterations?!

                d3 = df3.transpose().times(s).get(0,0); // new slope

            }                                                    // end interpolation

            if (Math.abs(d3) < -SIG*d0 && f3 < f0+x3*RHO*d0){     // if line search succeeded
                params = s.times(x3).plus(params);
                f0 = f3;

                double[] elem = fX.getColumnPackedCopy();
                double[] newfX = new double[elem.length + 1];

                System.arraycopy(elem, 0, newfX, 0, elem.length);
                newfX[elem.length-1] = f0;
                fX = new Matrix(newfX, newfX.length);                 // update variables


                System.out.println("Function evaluation "+i+" Value "+f0);

                
                double tmp1 = df3.transpose().times(df3).minus(df0.transpose().times(df3)).get(0,0);
                double tmp2 = df0.transpose().times(df0).get(0,0);

                s =  s.times(tmp1/tmp2).minus(df3);

                df0 = df3;                          // swap derivatives
                d3 = d0;
                d0 = df0.transpose().times(s).get(0,0);

                if (d0 > 0){                        // new slope must be negative
                    s = df0.times(-1);              // otherwise use steepest direction
                    d0 = s.times(-1).transpose().times(s).get(0,0);
                }

                x3 = x3 * Math.min(RATIO, d3/(d0-Double.MIN_VALUE));    // slope ratio but max RATIO
                ls_failed = 0;                                          // this line search did not fail

            }else{

                params = X0; f0 = F0; df0 = dF0;                     // restore best point so far

                if (ls_failed == 1 || i > Math.abs(length)){    // line search failed twice in a row
                    break;                                      // or we ran out of time, so we give up
                }

                s = df0.times(-1); d0 = s.times(-1).transpose().times(s).get(0,0);      // try steepest
                x3 = 1/(1-d0);
                ls_failed = 1;                                                     // this line search failed

            }

        }

        return params;
    }


    private static boolean hasInvalidNumbers(double[] array){

        for(double a : array){
            if(Double.isInfinite(a) || Double.isNaN(a)){
                return true;
            }
        }

        return false;
    }


    /**
     * A simple test
     * @param args
     */
    public static void main(String[] args) {


        CovarianceFunction covFunc = new CovSum(6,new CovLINone(),new CovNoise());
       GaussianProcess gp = new GaussianProcess(covFunc);

        double[][] logtheta0 = new double[][]{
                {0.1},
                {Math.log(0.1)}
        };
        /*
       double[][] logtheta0 = new double[][]{
                      {0.1},
                      {0.2},
                      {0.3},
                      {0.4},
                      {0.5},
                      {0.6},
                      {0.7},
                      {Math.log(0.1)}};
        */

        Matrix params0 = new Matrix(logtheta0);

        Matrix[] data = CSVtoMatrix.load("../armdata.csv",6,1);
        Matrix X = data[0];
        Matrix Y = data[1];

        gp.train(X,Y,params0,-20);


//        int size = 100;
//        Matrix Xtrain = new Matrix(size, 1);
//        Matrix Ytrain = new Matrix(size, 1);
//
//        Matrix Xtest = new Matrix(size, 1);
//        Matrix Ytest = new Matrix(size, 1);

        // half of the sinusoid uses points very close to each other and the other half uses
        // more sparse data

//        double inc = 2 * Math.PI / 1000;
//
//        double[][] data = new double[1000][2];
//
//        Random random = new Random();
//
//        for(int i=0; i<1000; i++){
//            data[i][0] = i*inc;
//            data[i][1] = Math.sin(i*+inc);
//        }
//
//
//        // NEED TO FILL Xtrain, Ytrain and Xtest, Ytest
//
//
//        gp.train(Xtrain,Ytest,params0);
//
//  //      SimpleRealTimePlotter plot = new SimpleRealTimePlotter("","","",false,false,true,200);
//
//        final Matrix[] out = gp.predict(Xtest);
//
//        for(int i=0; i<Xtest.getRowDimension(); i++){
//
//            final double mean = out[0].get(i,0);
//            final double var = 3 * Math.sqrt(out[1].get(i,0));
//
//            plot.addPoint(i, mean, 0, true);
//            plot.addPoint(i, mean-var, 1, true);
//            plot.addPoint(i, mean+var, 2, true);
//            plot.addPoint(i, Ytest.get(i,0), 3, true);
//
//            plot.fillPlot();
//        }

        Matrix[] datastar = CSVtoMatrix.load("../armdatastar.csv",6,1);
        Matrix Xstar = datastar[0];
        Matrix Ystar = datastar[1];

        Matrix[] res = gp.predict(Xstar);

        res[0].print(res[0].getColumnDimension(), 16);
        res[1].print(res[1].getColumnDimension(), 16);
    }

}
