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
 * <p>
 * k(x^p,x^q) = sf2 * asin(x^p'*P*x^q / sqrt[(1+x^p'*P*x^p)*(1+x^q'*P*x^q)]) + s2 * \delta(p,q)
 * <p>
 * where the x^p and x^q vectors on the right hand side have an added extra bias
 * entry with unit value. P is ell^-2 times the unit matrix and sf2 controls the
 * signal variance. The hyperparameters are:
 * <p>
 * [ log(ell)
 * log(sqrt(sf2)
 * log(s2)]
 *
 * <p>
 * For reson of speed consider to use this covariance function instead of <code>CovSum(CovNNone,CovNoise)</code>
 */

public class CovNNoneNoise implements CovarianceFunction {

    double[][] k;
    double[][] q;

    public CovNNoneNoise() {
    }


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

        if (loghyper.getColumnDimension() != 1 || loghyper.getRowDimension() != numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, " + loghyper.getRowDimension() + " instead of " + numParameters());

        double ell = Math.exp(loghyper.get(0, 0));
        double em2 = 1 / (ell * ell);
        double oneplusem2 = 1 + em2;
        double sf2 = Math.exp(2 * loghyper.get(1, 0));
        double s2 = Math.exp(2 * loghyper.get(2, 0));

        int m = X.getRowDimension();
        int n = X.getColumnDimension();
        double[][] x = X.getArray();

        this.q = new double[m][m];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                double t = 0;
                for (int k = 0; k < n; k++) {
                    t += x[i][k] * x[j][k] * em2;
                }
                this.q[i][j] = t;
            }
        }

        double[] dq = new double[m];
        for (int i = 0; i < m; i++) {
            dq[i] = Math.sqrt(oneplusem2 + this.q[i][i]);
        }

        //K = new Matrix(m,m);
        Matrix A = new Matrix(m, m);
        //double[][] k;
        this.k = new double[m][m];//K.getArray();
        double[][] a = A.getArray();
        for (int i = 0; i < m; i++) {
            double dqi = dq[i];
            for (int j = 0; j < m; j++) {
                double t = (em2 + this.q[i][j]) / (dqi * dq[j]);
                this.k[i][j] = t;
                a[i][j] = sf2 * Math.asin(t);
            }
            a[i][i] += s2;
        }

        return A;
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

        double ell = Math.exp(loghyper.get(0, 0));
        double em2 = 1 / (ell * ell);
        double oneplusem2 = 1 + em2;
        double sf2 = Math.exp(2 * loghyper.get(1, 0));
        double s2 = Math.exp(2 * loghyper.get(2, 0));


        int m = X.getRowDimension();
        int n = X.getColumnDimension();
        double[][] x = X.getArray();
        int mstar = Xstar.getRowDimension();
        int nstar = Xstar.getColumnDimension();
        double[][] xstar = Xstar.getArray();


        double[] sumxstardotTimesxstar = new double[mstar];
        for (int i = 0; i < mstar; i++) {
            double t = 0;
            for (int j = 0; j < nstar; j++) {
                double tt = xstar[i][j];
                t += tt * tt * em2;
            }
            sumxstardotTimesxstar[i] = t;
        }

        Matrix A = new Matrix(mstar, 1);
        double[][] a = A.getArray();
        for (int i = 0; i < mstar; i++) {
            a[i][0] = sf2 * Math.asin((em2 + sumxstardotTimesxstar[i]) / (oneplusem2 + sumxstardotTimesxstar[i])) + s2;
        }


        double[] sumxdotTimesx = new double[m];
        for (int i = 0; i < m; i++) {
            double t = 0;
            for (int j = 0; j < n; j++) {
                double tt = x[i][j];
                t += tt * tt * em2;
            }
            sumxdotTimesx[i] = t + oneplusem2;
        }

        Matrix B = new Matrix(m, mstar);
        double[][] b = B.getArray();
        for (int i = 0; i < m; i++) {
            double[] xi = x[i];
            for (int j = 0; j < mstar; j++) {
                double t = 0;
                double[] xstarj = xstar[j];
                for (int k = 0; k < n; k++) {
                    t += xi[k] * xstarj[k] * em2;
                }
                b[i][j] = t + em2;
            }
        }

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < mstar; j++) {
                b[i][j] = sf2 * Math.asin(b[i][j] / Math.sqrt((sumxstardotTimesxstar[j] + oneplusem2) * sumxdotTimesx[i]));
            }
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

        if (loghyper.getColumnDimension() != 1 || loghyper.getRowDimension() != numParameters())
            throw new IllegalArgumentException("Wrong number of hyperparameters, " + loghyper.getRowDimension() + " instead of " + numParameters());
        if (index > numParameters() - 1)
            throw new IllegalArgumentException("Wrong hyperparameters index " + index + " it should be smaller or equal to " + (numParameters() - 1));

        double ell = Math.exp(loghyper.get(0, 0));
        double em2 = 1 / (ell * ell);
        double oneplusem2 = 1 + em2;
        double twosf2 = 2 * Math.exp(2 * loghyper.get(1, 0));
        double twos2 = 2 * Math.exp(2 * loghyper.get(2, 0));

        int m = X.getRowDimension();
        int n = X.getColumnDimension();
        double[][] x = X.getArray();

        if (this.q == null || this.q.length != m || this.q[0].length != m) {
            this.q = new double[m][m];

            for (int i = 0; i < m; i++) {
                for (int j = 0; j < m; j++) {
                    double t = 0;
                    for (int k = 0; k < n; k++) {
                        t += x[i][k] * x[j][k] * em2;
                    }
                    this.q[i][j] = t;
                }
            }
        }

        double[] dq = new double[m];
        for (int i = 0; i < m; i++) {
            dq[i] = Math.sqrt(oneplusem2 + this.q[i][i]);
        }
//        double[][] k=null;
        if (this.k == null || this.k.length != m || this.k[0].length != m) {
            this.k = new double[m][m];
            for (int i = 0; i < m; i++) {
                double dqi = dq[i];
                for (int j = 0; j < m; j++) {
                    double t = (em2 + this.q[i][j]) / (dqi * dq[j]);
                    this.k[i][j] = t;
                }
            }
        }

        Matrix A = null;
        switch (index) {
            case 0:
                for (int i = 0; i < m; i++) {
                    dq[i] = oneplusem2 + this.q[i][i];
                }
                double[] v = new double[m];
                for (int i = 0; i < m; i++) {
                    double t = 0;
                    for (int j = 0; j < n; j++) {
                        double xij = x[i][j];
                        t += xij * xij * em2;
                    }
                    v[i] = (t + em2) / (dq[i]);
                }

                for (int i = 0; i < m; i++) {
                    double vi = v[i];
                    for (int j = 0; j < m; j++) {
                        double t = (this.q[i][j] + em2) / (Math.sqrt(dq[i]) * Math.sqrt(dq[j]));
                        double kij = this.k[i][j];
                        this.q[i][j] = -twosf2 * ((t - (0.5 * kij * (vi + v[j]))) / Math.sqrt(1 - kij * kij));
                    }
                }

                A = new Matrix(this.q);
//            System.out.println("");
                this.q = null;
                break;
            case 1:
                for (int i = 0; i < m; i++) {
                    for (int j = 0; j < m; j++) {
                        this.k[i][j] = Math.asin(this.k[i][j]) * twosf2;
                    }
                }
                A = new Matrix(this.k);
                this.k = null;

                break;

            case 2:
                double[][] a = new double[m][m];
                for (int i = 0; i < m; i++) a[i][i] = twos2;

                A = new Matrix(a);

                break;
            default:
                throw new IllegalArgumentException("the covariance function CovNNoneNoise alllows for a maximum of 3 parameters!!");
        }
        return A;
    }

    public static void main(String[] args) {

        CovarianceFunction cf = new CovNNoneNoise();
        CovarianceFunction cf2 = new CovSum(6, new CovNNone(), new CovNoise());


        Matrix X = Matrix.identity(10, 6);

        for (int i = 0; i < X.getRowDimension(); i++)
            for (int j = 0; j < X.getColumnDimension(); j++)
                X.set(i, j, Math.random());

        Matrix logtheta = new Matrix(new double[][]{{0.1}, {0.2}, {Math.log(0.1)}});

        Matrix z = new Matrix(new double[][]{{1, 2, 3, 4, 5, 6}, {1, 2, 3, 4, 5, 6}});


    }
}
