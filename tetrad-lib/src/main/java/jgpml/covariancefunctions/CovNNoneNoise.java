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
 * Neural network covariance function with a single parameter for the distance
 * measure and white noise. The covariance function is parameterized as:
 * <P>
 * k(x^p,x^q) = sf2 * asin(x^p'*P*x^q / sqrt[(1+x^p'*P*x^p)*(1+x^q'*P*x^q)]) + s2 * \delta(p,q)
 * <P>
 * where the x^p and x^q vectors on the right hand side have an added extra bias
 * entry with unit value. P is ell^-2 times the unit matrix and sf2 controls the
 * signal variance. The hyperparameters are:
 * <P>
 * [ log(ell)
 * log(sqrt(sf2)
 * log(s2)]
 *
 * <P>
 * For reson of speed consider to use this covariance function instead of <code>CovSum(CovNNone,CovNoise)</code>
 *
 */

public class CovNNoneNoise implements CovarianceFunction{

    double[][] k;
    double[][] q;

    public CovNNoneNoise(){}


    /**
     * Returns the number of hyperparameters of this<code>CovarianceFunction</code>
     *
     * @return number of hyperparameters
     */
    public int numParameters() {
        return 3;
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

        final double ell = Math.exp(loghyper.get(0,0));
        final double em2 = 1/(ell*ell);
        final double oneplusem2 = 1+em2;
        final double sf2 = Math.exp(2*loghyper.get(1,0));
        final double s2 = Math.exp(2*loghyper.get(2,0));

        final int m = X.getRowDimension();
        final int n = X.getColumnDimension();
        double[][] x= X.getArray();

//        Matrix Xc= X.times(1/ell);
//
//        Q = Xc.times(Xc.transpose());
//        System.out.print("Q=");Q.print(Q.getColumnDimension(), 8);

//        Q = new Matrix(m,m);
//        double[][] q = Q.getArray();
//        double[][] q;
        q = new double[m][m];

        for(int i=0;i<m;i++){
            for(int j=0;j<m;j++){
                double t = 0;
                for(int k=0;k<n;k++){
                    t+=x[i][k]*x[j][k]*em2;
                }
                q[i][j]=t;
            }
        }
//        System.out.print("q=");Q.print(Q.getColumnDimension(), 8);

//        Matrix dQ = diag(Q);
//        Matrix dQT = dQ.transpose();
//        Matrix Qc = Q.copy();
//        K = addValue(Qc,em2).arrayRightDivide(sqrt(addValue(dQ,1+em2)).times(sqrt(addValue(dQT,1+em2))));
//        System.out.print("K=");K.print(K.getColumnDimension(), 8);

        double[] dq = new double[m];
        for(int i=0;i<m;i++){
            dq[i]=Math.sqrt(oneplusem2+q[i][i]);
        }

        //K = new Matrix(m,m);
        Matrix A = new Matrix(m,m);
        //double[][] k;
        k = new double[m][m];//K.getArray();
        double[][] a =A.getArray();
        for(int i=0;i<m;i++){
            final double dqi = dq[i];
            for(int j=0;j<m;j++){
                final double t  = (em2+q[i][j])/(dqi*dq[j]);
                k[i][j]=t;
                a[i][j]=sf2*Math.asin(t);
            }
            a[i][i]+=s2;
        }
//        System.out.print("k=");K.print(K.getColumnDimension(), 8);
//        System.out.println("");

//        Matrix A = asin(K).times(sf2);
        return A;
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

        final double ell = Math.exp(loghyper.get(0,0));
        final double em2 = 1/(ell*ell);
        final double oneplusem2 = 1+em2;
        final double sf2 = Math.exp(2*loghyper.get(1,0));
        final double s2 = Math.exp(2*loghyper.get(2,0));


        final int m = X.getRowDimension();
        final int n = X.getColumnDimension();
        double[][] x= X.getArray();
        final int mstar = Xstar.getRowDimension();
        final int nstar = Xstar.getColumnDimension();
        double[][] xstar= Xstar.getArray();


        double[] sumxstardotTimesxstar = new double[mstar];
        for(int i=0; i<mstar; i++){
            double t =0;
            for(int j=0; j<nstar; j++){
                final double tt = xstar[i][j];
                t+=tt*tt*em2;
            }
            sumxstardotTimesxstar[i]=t;
        }

        Matrix A = new Matrix(mstar,1);
        double[][] a = A.getArray();
        for(int i=0; i<mstar; i++){
            a[i][0]=sf2*Math.asin((em2+sumxstardotTimesxstar[i])/(oneplusem2+sumxstardotTimesxstar[i]))+s2;
        }



//        X = X.times(1/ell);
//        Xstar = Xstar.times(1/ell);
//        Matrix tmp = sumRows(Xstar.arrayTimes(Xstar));
//
//        Matrix tmp2 = tmp.copy();
//        addValue(tmp,em2);
//        addValue(tmp2,oneplusem2);
//        Matrix A = asin(tmp.arrayRightDivide(tmp2)).times(sf2);


        double[] sumxdotTimesx = new double[m];
        for(int i=0; i<m; i++){
            double t =0;
            for(int j=0; j<n; j++){
                final double tt = x[i][j];
                t+=tt*tt*em2;
            }
            sumxdotTimesx[i]=t+oneplusem2;
        }

        Matrix B = new Matrix(m,mstar);
        double[][] b = B.getArray();
        for(int i=0; i<m; i++){
            final double[] xi = x[i];
            for(int j=0; j<mstar; j++){
                double t=0;
                final double[] xstarj = xstar[j];
                for(int k=0; k<n; k++){
                    t+=xi[k]*xstarj[k]*em2;
                }
                b[i][j]=t+em2;
            }
        }

        for(int i=0; i<m; i++){
            for(int j=0; j<mstar; j++){
                b[i][j] = sf2*Math.asin(b[i][j]/Math.sqrt((sumxstardotTimesxstar[j]+oneplusem2)*sumxdotTimesx[i]));
            }
        }



//        tmp = sumRows(X.arrayTimes(X));
//        addValue(tmp,oneplusem2);
//
//        tmp2=tmp2.transpose();
//
//        tmp = addValue(X.times(Xstar.transpose()),em2).arrayRightDivide(sqrt(tmp.times(tmp2)));
//        Matrix B = asin(tmp).times(sf2);

        //System.out.println("");
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

        final double ell = Math.exp(loghyper.get(0,0));
        final double em2 = 1/(ell*ell);
        final double oneplusem2 = 1+em2;
        final double twosf2 = 2*Math.exp(2*loghyper.get(1,0));
        final double twos2 = 2*Math.exp(2*loghyper.get(2,0));

        final int m = X.getRowDimension();
        final int n = X.getColumnDimension();
        double[][] x= X.getArray();

//        Matrix X  = XX.times(1/ell);
//        double[][] q=null;
        if(q==null || q.length!=m || q[0].length!=m) {
            q = new double[m][m];

            for(int i=0;i<m;i++){
                for(int j=0;j<m;j++){
                    double t = 0;
                    for(int k=0;k<n;k++){
                        t+=x[i][k]*x[j][k]*em2;
                    }
                    q[i][j]=t;
                }
            }
        }

        double[] dq = new double[m];
        for(int i=0;i<m;i++){
            dq[i]=Math.sqrt(oneplusem2+q[i][i]);
        }
//        double[][] k=null;
        if(k==null || k.length!=m || k[0].length!=m) {
            k = new double[m][m];
            for(int i=0;i<m;i++){
                final double dqi = dq[i];
                for(int j=0;j<m;j++){
                    final double t  = (em2+q[i][j])/(dqi*dq[j]);
                    k[i][j]=t;
                }
            }
        }

//        Matrix Xc= XX.times(1/ell);
//        Matrix Q = Xc.times(Xc.transpose());
//
//        Matrix dQ = diag(Q);
//        Matrix dQT = dQ.transpose();
//        Matrix K = addValue(Q.copy(),em2).arrayRightDivide(sqrt(addValue(dQ.copy(),1+em2)).times(sqrt(addValue(dQT,1+em2))));
//        Matrix dQc = dQ.copy();

        Matrix A = null;
        switch(index){
            case 0:
                for(int i=0;i<m;i++){
                    dq[i]=oneplusem2+q[i][i];
                }
                double[] v = new double[m];
                for(int i=0; i<m; i++){
                    double t =0;
                    for(int j=0; j<n; j++){
                        final double xij = x[i][j];
                        t+=xij*xij*em2;
                    }
                    v[i]=(t+em2)/(dq[i]);
                }

//            Matrix test = addValue(sumRows(X.arrayTimes(X)),em2);
//            Matrix tmp = addValue(dQc,1+em2);
//            Matrix V = addValue(sumRows(X.arrayTimes(X)),em2).arrayRightDivide(tmp);
//
//            tmp = sqrt(tmp);
//            tmp = addValue(Q.copy(),em2).arrayRightDivide(tmp.times(tmp.transpose()));

                for(int i=0; i<m; i++){
                    final double vi = v[i];
                    for(int j=0; j<m; j++){
                        double t =(q[i][j]+em2)/(Math.sqrt(dq[i])*Math.sqrt(dq[j]));
                        final double kij = k[i][j];
                        q[i][j]=-twosf2*((t-(0.5*kij*(vi+v[j])))/Math.sqrt(1-kij*kij));
                    }
                }

//            Matrix tmp2 = new Matrix(m,m);
//            for(int j=0; j<m; j++)
//                tmp2.setMatrix(0,m-1,j,j,V);
//
//            tmp = tmp.minus(K.arrayTimes(tmp2.plus(tmp2.transpose())).times(0.5));
//
//            A = tmp.arrayRightDivide(sqrtOneMinusSqr(K)).times(-twosf2);

                A = new Matrix(q);
//            System.out.println("");
                q=null;
                break;
            case 1:
                for(int i=0; i<m; i++){
                    for(int j=0; j<m; j++){
                        k[i][j]=Math.asin(k[i][j])*twosf2;
                    }
                }
//            A = asin(K).times(twosf2);
//            K=null;
                A = new Matrix(k);
                k=null;

                break;

            case 2:
                double[][] a = new double[m][m];
                for(int i=0; i<m;i++) a[i][i]=twos2;

                A = new Matrix(a);

                break;
            default:
                throw new IllegalArgumentException("the covariance function CovNNoneNoise alllows for a maximum of 3 parameters!!");
        }
        return A;
    }

//    private static Matrix sqrtOneMinusSqr(Matrix in){
//        Matrix out = new Matrix(in.getRowDimension(),in.getColumnDimension());
//        for(int i=0; i<in.getRowDimension(); i++)
//            for(int j=0; j<in.getColumnDimension(); j++) {
//                final double tmp = in.get(i,j);
//                out.set(i,j,Math.sqrt(1-tmp*tmp));
//            }
//        return out;
//    }

    public static void main(String[] args) {

        CovarianceFunction cf = new CovNNoneNoise();
        CovarianceFunction cf2 = new CovSum(6,new CovNNone(), new CovNoise());


        Matrix X = Matrix.identity(10,6);

        for(int i=0; i<X.getRowDimension(); i++)
            for(int j=0; j<X.getColumnDimension(); j++)
                X.set(i,j,Math.random());

        Matrix logtheta = new Matrix(new double[][]{{0.1},{0.2},{Math.log(0.1)}});

        Matrix z =new Matrix(new double[][]{{1,2,3,4,5,6},{1,2,3,4,5,6}});

//        long start = System.currentTimeMillis();
//        Matrix K = cf.compute(logtheta,X);
//        K.print(K.getColumnDimension(), 15);

//        long stop = System.currentTimeMillis();
//        System.out.println(""+(stop-start));
//
//        start = System.currentTimeMillis();
//        K = cf2.compute(logtheta,X);
//        K.print(K.getColumnDimension(), 15);
//        stop = System.currentTimeMillis();
//        System.out.println(""+(stop-start));



//        long start = System.currentTimeMillis();
//        Matrix[] res = cf.compute(logtheta,X,z);
//        res[0].print(res[0].getColumnDimension(), 8);
//        res[1].print(res[1].getColumnDimension(), 8);

//        long stop = System.currentTimeMillis();
//        System.out.println(""+(stop-start));



//        res = cf2.compute(logtheta,X,z);
//        res[0].print(res[0].getColumnDimension(), 8);
//        res[1].print(res[1].getColumnDimension(), 8);

//        long stop = System.currentTimeMillis();
//        System.out.println(""+(stop-start));




//        Matrix d = cf.computeDerivatives(logtheta,X,0);
//        d.print(d.getColumnDimension(), 8);

        //d = cf2.computeDerivatives(logtheta,X,0);
        //d.print(d.getColumnDimension(), 8);


    }
}
