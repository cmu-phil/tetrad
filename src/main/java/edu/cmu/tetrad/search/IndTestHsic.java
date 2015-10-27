///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.cluster.KMeans;
import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.kernel.Kernel;
import edu.cmu.tetrad.search.kernel.KernelGaussian;
import edu.cmu.tetrad.search.kernel.KernelUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.Matrices;
import no.uib.cipr.matrix.Matrix;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks the conditional independence X _||_ Y | S, where S is a set of continuous variable, and X and Y are discrete
 * variable not in S, using the Hilbert-Schmidth Independence Criterion (HSIC), a kernel based nonparametric test for
 * conditional independence.
 *
 * @author Robert Tillman
 */

public final class IndTestHsic implements IndependenceTest {

    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private List<Node> variables;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * The cutoff value for 'alpha'
     */
    private double thresh = Double.NaN;

    /**
     * The value of the empirical estimate of HSIC
     */
    private double hsic;

    /**
     * Formats as 0.0000.
     */
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the dataset being analyzed.
     */
    private DataSet dataSet;

    /**
     * A stored p value, if the deterministic test was used.
     */
    private double pValue = Double.NaN;

    /**
     * The regularizer
     */
    private double regularizer = 0.0001;

    /**
     * Number of permutations to approximate the null distribution
     */
    private int perms = 100;

    /**
     * Use incomplete Choleksy decomposition to calculate Gram matrices
     */
    private double useIncompleteCholesky = 1e-18;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new HSIC Independence test. The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The alpha level of the test.
     */
    public IndTestHsic(DataSet dataSet, double alpha) {
        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        List<Node> nodes = dataSet.getVariables();

        this.variables = Collections.unmodifiableList(nodes);
        setAlpha(alpha);

        this.dataSet = dataSet;
    }

    public IndTestHsic(TetradMatrix data, List<Node> variables, double alpha) {
        DataSet dataSet = ColtDataSet.makeContinuousData(variables, data);

        this.variables = Collections.unmodifiableList(variables);
        setAlpha(alpha);

        this.dataSet = dataSet;
    }


    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new IndTestHsic instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (Node var : vars) {
            if (!variables.contains(var)) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }
        }

        int[] indices = new int[vars.size()];

        for (int i = 0; i < indices.length; i++) {
            indices[i] = variables.indexOf(vars.get(i));
        }

        double alphaNew = getAlpha();
        return new IndTestHsic(this.dataSet.subsetColumns(indices), alphaNew);
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning variables.
     * @return true iff x _||_ y | z.
     */
    public boolean isIndependent(Node y, Node x, List<Node> z) {

        int m = sampleSize();

        // choose kernels using median distance heuristic
        Kernel xKernel = new KernelGaussian(1);
        Kernel yKernel = new KernelGaussian(1);
        List<Kernel> zKernel = new ArrayList<Kernel>();
        yKernel.setDefaultBw(this.dataSet, y);
        xKernel.setDefaultBw(this.dataSet, x);
        if (!z.isEmpty()) {
            for (int i = 0; i < z.size(); i++) {
                Kernel Zi = new KernelGaussian(1);
                Zi.setDefaultBw(this.dataSet, z.get(i));
                zKernel.add(Zi);
            }
        }

        // consruct Gram matricces
        Matrix Ky = null;
        Matrix Kx = null;
        Matrix Kz = null;
        // use incomplete Cholesky to approximate
        if (useIncompleteCholesky > 0) {
            Ky = KernelUtils.incompleteCholeskyGramMatrix(Arrays.asList(yKernel), this.dataSet, Arrays.asList(y), useIncompleteCholesky);
            Kx = KernelUtils.incompleteCholeskyGramMatrix(Arrays.asList(xKernel), this.dataSet, Arrays.asList(x), useIncompleteCholesky);
            if (!z.isEmpty()) {
                Kz = KernelUtils.incompleteCholeskyGramMatrix(zKernel, this.dataSet, z, useIncompleteCholesky);
            }
        }
        // otherwise compute directly
        else {
            Ky = KernelUtils.constructCentralizedGramMatrix(Arrays.asList(yKernel), this.dataSet, Arrays.asList(y));
            Kx = KernelUtils.constructCentralizedGramMatrix(Arrays.asList(xKernel), this.dataSet, Arrays.asList(x));
            if (!z.isEmpty()) {
                Kz = KernelUtils.constructCentralizedGramMatrix(zKernel, this.dataSet, z);
            }
        }

        // get Hilbert-Schmidt dependence measure
        if (z.isEmpty()) {
            if (useIncompleteCholesky > 0) {
                this.hsic = empiricalHSICincompleteCholesky(Ky, Kx, m);
            } else {
                this.hsic = empiricalHSIC(Ky, Kx, m);
            }
        } else {
            if (useIncompleteCholesky > 0) {
                this.hsic = empiricalHSICincompleteCholesky(Ky, Kx, Kz, m);
            } else {
                this.hsic = empiricalHSIC(Ky, Kx, Kz, m);
            }
        }

        // shuffle data for approximate the null distribution
        double[] nullapprox = new double[this.perms];
        int[] zind = null;
        int ycol = this.dataSet.getColumn(y);
        List<List<Integer>> clusterAssign = null;
        if (!z.isEmpty()) {
            // get clusters for z
            KMeans kmeans = KMeans.randomClusters((m / 3));
            zind = new int[z.size()];
            for (int j = 0; j < z.size(); j++) {
                zind[j] = dataSet.getColumn(z.get(j));
            }
            kmeans.cluster(dataSet.subsetColumns(z).getDoubleData());
            clusterAssign = kmeans.getClusters();
        }
        for (int i = 0; i < this.perms; i++) {
            DataSet shuffleData = new ColtDataSet((ColtDataSet) dataSet);
            // shuffle data
            if (z.isEmpty()) {
                List<Integer> indicesList = new ArrayList<Integer>();
                for (int j = 0; j < m; j++) {
                    indicesList.add(j);
                }
                Collections.shuffle(indicesList);
                for (int j = 0; j < m; j++) {
                    double shuffleVal = dataSet.getDouble(indicesList.get(j), ycol);
                    shuffleData.setDouble(j, ycol, shuffleVal);
                }
            } else {
                // shuffle data within clusters
                for (int j = 0; j < clusterAssign.size(); j++) {
                    List<Integer> shuffleCluster = new ArrayList<Integer>(clusterAssign.get(j));

                    Collections.shuffle(shuffleCluster);

                    for (int k = 0; k < shuffleCluster.size(); k++) {
                        // first swap y;
                        double swapVal = dataSet.getDouble(clusterAssign.get(j).get(k), ycol);
                        shuffleData.setDouble(shuffleCluster.get(k), ycol, swapVal);
                        // now swap z
                        for (int zi = 0; zi < z.size(); zi++) {
                            swapVal = dataSet.getDouble(clusterAssign.get(j).get(k), zind[zi]);
                            shuffleData.setDouble(shuffleCluster.get(k), zind[zi], swapVal);
                        }
                    }
                }
            }
            // reset bandwidths
            yKernel.setDefaultBw(shuffleData, y);
            for (int j = 0; j < z.size(); j++) {
                zKernel.get(j).setDefaultBw(shuffleData, z.get(j));
            }
            // Gram matrices
            Matrix Kyn = null;
            if (useIncompleteCholesky > 0) {
                Kyn = KernelUtils.incompleteCholeskyGramMatrix(Arrays.asList(yKernel), shuffleData, Arrays.asList(y), useIncompleteCholesky);
            } else {
                Kyn = KernelUtils.constructCentralizedGramMatrix(Arrays.asList(yKernel), shuffleData, Arrays.asList(y));

            }
            Matrix Kzn = null;
            if (!z.isEmpty()) {
                if (useIncompleteCholesky > 0) {
                    Kzn = KernelUtils.incompleteCholeskyGramMatrix(zKernel, shuffleData, z, useIncompleteCholesky);
                } else {
                    Kzn = KernelUtils.constructCentralizedGramMatrix(zKernel, shuffleData, z);
                }
            }
            // HSIC
            if (z.isEmpty()) {
                if (useIncompleteCholesky > 0) {
                    nullapprox[i] = empiricalHSICincompleteCholesky(Kyn, Kx, m);
                } else {
                    nullapprox[i] = empiricalHSIC(Kyn, Kx, m);
                }
            } else {
                if (useIncompleteCholesky > 0) {
                    nullapprox[i] = empiricalHSICincompleteCholesky(Kyn, Kx, Kz, m);
                } else {
                    nullapprox[i] = empiricalHSIC(Kyn, Kx, Kz, m);
                }
            }
        }

        // permutation test to get p-value
        double evalCdf = 0.0;
        for (int i = 0; i < this.perms; i++) {
            if (nullapprox[i] <= this.hsic) {
                evalCdf += 1.0;
            }
        }

        evalCdf /= (double) this.perms;
        this.pValue = 1.0 - evalCdf;

        // reject if pvalue <= alpha
        if (this.pValue <= this.alpha) {
            TetradLogger.getInstance().log("dependencies", SearchLogUtils
                    .dependenceFactMsg(x, y, z, getPValue()));
            return false;
        }
        TetradLogger.getInstance().log("independencies", SearchLogUtils
                .independenceFactMsg(x, y, z, getPValue()));
        return true;
    }

    /**
     * Empirical unconditional Hilbert-Schmidt Dependence Measure for X and Y
     *
     * @param Ky centralized Gram matrix for Y
     * @param Kx centralized Gram matrix for X
     * @param m  sample size
     * @return
     */
    public double empiricalHSIC(Matrix Ky, Matrix Kx, int m) {
        Matrix Kyx = new DenseMatrix(m, m);
        Ky.mult(Kx, Kyx);
        double empHSIC = 0.0;
        for (int i = 0; i < m; i++) {
            empHSIC += Kyx.get(i, i);
        }
        empHSIC /= Math.pow(m - 1, 2);
        return empHSIC;
    }

    /**
     * Empirical unconditional Hilbert-Schmidt Dependence Measure for X and Y using incomplete Cholesky decomposition to
     * approximate Gram matrices
     *
     * @param Gy Choleksy approximate Gram matrix for Y
     * @param Gx Choleksy approximate Gram matrix for X
     * @param m  sample size
     * @return
     */
    public double empiricalHSICincompleteCholesky(Matrix Gy, Matrix Gx, int m) {
        // centralized Choleksy
        int ky = Gy.numColumns();
        int kx = Gx.numColumns();
        Matrix H = KernelUtils.constructH(m);
        Matrix Gcy = new DenseMatrix(m, ky);
        H.mult(Gy, Gcy);
        Matrix Gcx = new DenseMatrix(m, kx);
        H.mult(Gx, Gcx);

        // multiply gram matrices
        Matrix A = new DenseMatrix(ky, kx);
        Matrix Gcyt = new DenseMatrix(ky, m);
        Gcy.transpose(Gcyt);
        Gcyt.mult(Gcx, A);
        Matrix B = new DenseMatrix(m, kx);
        Gcy.mult(A, B);
        Matrix Gcxt = new DenseMatrix(kx, m);
        Gcx.transpose(Gcxt);
        double empHSIC = 0.0;
        for (int i = 0; i < m; i++) {
            empHSIC += matrixProductEntry(B, Gcxt, i, i);
        }
        empHSIC /= Math.pow(m - 1, 2);
        return empHSIC;
    }

    /**
     * Empirical conditional Hilbert-Schmidt Dependence Measure Y and X given Z
     *
     * @param Ky centralized Gram matrix for Y
     * @param Kx centralized Gram matrix for X
     * @param Kz centralized Gram matrix for Z
     * @param m  sample size
     * @return
     */
    private double empiricalHSIC(Matrix Ky, Matrix Kx, Matrix Kz, int m) {
        Matrix Kyx = new DenseMatrix(m, m);
        Ky.mult(Kx, Kyx);
        Matrix Kyz = new DenseMatrix(m, m);
        Ky.mult(Kz, Kyz);
        Matrix Kzx = new DenseMatrix(m, m);
        Kz.mult(Kx, Kzx);
        Matrix Kzreg = Kz.copy();
        for (int i = 0; i < m; i++) {
            double ent = (Kzreg.get(i, i) + this.regularizer);
            Kzreg.set(i, i, ent);
        }
        Matrix A = new DenseMatrix(m, m);
        Matrix I = Matrices.identity(m);
        Kzreg.solve(I, A);
        A.mult(A, Kzreg);
        Matrix Kyzzregzx = new DenseMatrix(m, m);
        Kyz.mult(Kzreg, A);
        A.mult(Kzx, Kyzzregzx);
        Matrix Kyzzregzxzzregz = Kyzzregzx.copy();
        Kyzzregzx.mult(Kz, Kyzzregzxzzregz);
        Kyzzregzxzzregz.mult(Kzreg, A);
        A.mult(Kz, Kyzzregzxzzregz);
        // get trace
        double empHSIC = 0.0;
        for (int i = 0; i < m; i++) {
            empHSIC += Kyx.get(i, i);
            empHSIC += (-2 * Kyzzregzx.get(i, i));
            empHSIC += Kyzzregzxzzregz.get(i, i);
        }
        empHSIC /= Math.pow(m - 1, 2);
        double Bz = 0.0;
        for (int i = 0; i < (m - 1); i++) {
            for (int j = (i + 1); j < m; j++) {
                Bz += Math.pow(Kz.get(i, j), 2);
                Bz += Math.pow(Kz.get(j, i), 2);
            }
        }
        Bz = (m * (m - 1)) / Bz;
        empHSIC *= Bz;
        return empHSIC;
    }

    /**
     * Empirical unconditional Hilbert-Schmidt Dependence Measure for X and Y given Z using incomplete Cholesky
     * decomposition to approximate Gram matrices
     *
     * @param Gy Choleksy approximate Gram matrix for Y
     * @param Gx Choleksy approximate Gram matrix for X
     * @param Gz Choleksy approximate Gram matrix for Z
     * @param m  sample size
     * @return
     */
    public double empiricalHSICincompleteCholesky(Matrix Gy, Matrix Gx, Matrix Gz, int m) {
        // centralize Choleksy
        int ky = Gy.numColumns();
        int kx = Gx.numColumns();
        int kz = Gz.numColumns();

        Matrix H = KernelUtils.constructH(m);
        Matrix Gcy = new DenseMatrix(m, ky);
        H.mult(Gy, Gcy);
        Matrix Gcx = new DenseMatrix(m, kx);
        H.mult(Gx, Gcx);
        Matrix Gcz = new DenseMatrix(m, kz);
        H.mult(Gz, Gcz);

        // multiply gram matrices (first block)
        Matrix A = new DenseMatrix(ky, kx);
        Matrix Gcyt = new DenseMatrix(ky, m);
        Gcy.transpose(Gcyt);
        Gcyt.mult(Gcx, A);
        Matrix B = new DenseMatrix(m, kx);
        Gcy.mult(A, B);
        Matrix Kyx = new DenseMatrix(m, m);
        Matrix Gcxt = new DenseMatrix(kx, m);
        Gcx.transpose(Gcxt);
        B.mult(Gcxt, Kyx);
        double empHSIC = 0.0;
        double xy = 0.0;
        for (int i = 0; i < m; i++) {
            empHSIC += matrixProductEntry(B, Gcxt, i, i);
        }

        // second block
        Matrix Gytz = new DenseMatrix(ky, kz);
        Gcyt.mult(Gcz, Gytz);
        Matrix Gztx = new DenseMatrix(kz, kx);
        Matrix Gczt = new DenseMatrix(kz, m);
        Gcz.transpose(Gczt);
        Gczt.mult(Gcx, Gztx);
        Matrix Gztz = new DenseMatrix(kz, kz);
        Gczt.mult(Gcz, Gztz);
        Matrix Gztzr = Gztz.copy();
        for (int i = 0; i < kz; i++) {
            Gztzr.set(i, i, Gztz.get(i, i) + this.regularizer);
        }
        Matrix ZI = new DenseMatrix(kz, kz);
        // invert matrix
        Gztzr.solve(Matrices.identity(kz), ZI);
        Matrix ZIzt = new DenseMatrix(kz, m);
        ZI.mult(Gczt, ZIzt);
        Matrix Gzr = Gcz.copy();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < kz; j++) {
                Gzr.set(i, j, Gcz.get(i, j) * (-1.0 / this.regularizer));
            }
        }
        Matrix Zinv = new DenseMatrix(m, m);
        Gzr.mult(ZIzt, Zinv);
        for (int i = 0; i < m; i++) {
            Zinv.set(i, i, Zinv.get(i, i) + (1.0 / this.regularizer));
        }
        Matrix Gztzinv = new DenseMatrix(kz, m);
        Gczt.mult(Zinv, Gztzinv);
        Matrix Gzinvz = new DenseMatrix(m, kz);
        Zinv.mult(Gcz, Gzinvz);
        Matrix Gztinv2z = new DenseMatrix(kz, kz);
        Gztzinv.mult(Gzinvz, Gztinv2z);
        Matrix Gytzztzinv2z = new DenseMatrix(ky, kz);
        Gytz.mult(Gztinv2z, Gytzztzinv2z);
        Matrix Gytzztzinv2zztx = new DenseMatrix(ky, kx);
        Gytzztzinv2z.mult(Gztx, Gytzztzinv2zztx);
        Matrix Gyytzztzinv2zztx = new DenseMatrix(m, kx);
        Gcy.mult(Gytzztzinv2zztx, Gyytzztzinv2zztx);
        double second = 0.0;
        for (int i = 0; i < m; i++) {
            second += matrixProductEntry(Gyytzztzinv2zztx, Gcxt, i, i);
        }
        empHSIC -= 2 * second;

        // third block
        Matrix Gxtz = new DenseMatrix(kx, kz);
        Gcxt.mult(Gcz, Gxtz);
        Matrix Gxtzztinv2z = new DenseMatrix(kx, kz);
        Gxtz.mult(Gztinv2z, Gxtzztinv2z);
        Matrix Gyytzztzinv2zztxxtzztinv2z = new DenseMatrix(m, kz);
        Gyytzztzinv2zztx.mult(Gxtzztinv2z, Gyytzztzinv2zztxxtzztinv2z);
        for (int i = 0; i < m; i++) {
            empHSIC += matrixProductEntry(Gyytzztzinv2zztxxtzztinv2z, Gczt, i, i);
        }

        // beta z estimate
        double betaz = 0.0;
        for (int i = 0; i < (m - 1); i++) {
            for (int j = (i + 1); j < m; j++) {
                betaz += Math.pow(matrixProductEntry(Gcz, Gczt, i, j), 2);
                betaz += Math.pow(matrixProductEntry(Gcz, Gczt, j, i), 2);
            }
        }

        empHSIC *= (m / (betaz * (m - 1)));

        return empHSIC;
    }

    /**
     * Empirical unconditional Hilbert-Schmidt Dependence Measure for X and Y given Z using incomplete Cholesky
     * decomposition to approximate Gram matrices
     *
     * @param Gy Choleksy approximate Gram matrix for Y
     * @param Gx Choleksy approximate Gram matrix for X
     * @param Gz Choleksy approximate Gram matrix for Z
     * @param m  sample size
     * @return
     */
    public double empiricalHSICincompleteCholeskyOLD(Matrix Gy, Matrix Gx, Matrix Gz, int m) {
        // centralize Choleksy
        int ky = Gy.numColumns();
        int kx = Gx.numColumns();
        int kz = Gz.numColumns();

        Matrix H = KernelUtils.constructH(m);
        Matrix Gcy = new DenseMatrix(m, ky);
        H.mult(Gy, Gcy);
        Matrix Gcx = new DenseMatrix(m, kx);
        H.mult(Gx, Gcx);
        Matrix Gcz = new DenseMatrix(m, kz);
        H.mult(Gz, Gcz);

        // multiply gram matrices (first block)
        Matrix A = new DenseMatrix(ky, kx);
        Matrix Gcyt = new DenseMatrix(ky, m);
        Gcy.transpose(Gcyt);
        Gcyt.mult(Gcx, A);
        Matrix B = new DenseMatrix(m, kx);
        Gcy.mult(A, B);
        Matrix Kyx = new DenseMatrix(m, m);
        Matrix Gcxt = new DenseMatrix(kx, m);
        Gcx.transpose(Gcxt);
        B.mult(Gcxt, Kyx);
        double empHSIC = 0.0;
        double xy = 0.0;
        for (int i = 0; i < m; i++) {
            empHSIC += matrixProductEntry(B, Gcxt, i, i);
        }

        // second block
        Matrix Gytz = new DenseMatrix(ky, kz);
        Gcyt.mult(Gcz, Gytz);
        Matrix Gztx = new DenseMatrix(kz, kx);
        Matrix Gczt = new DenseMatrix(kz, m);
        Gcz.transpose(Gczt);
        Gczt.mult(Gcx, Gztx);
        Matrix Gztz = new DenseMatrix(kz, kz);
        Gczt.mult(Gcz, Gztz);
        Matrix Gztzztx = new DenseMatrix(kz, kx);
        Gztz.mult(Gztx, Gztzztx);
        Matrix Gytzztzztx = new DenseMatrix(ky, kx);
        Gytz.mult(Gztzztx, Gytzztzztx);
        Matrix Gyytzztzztx = new DenseMatrix(m, kx);
        Gcy.mult(Gytzztzztx, Gyytzztzztx);
        double second = 0.0;
        for (int i = 0; i < m; i++) {
            second += matrixProductEntry(Gyytzztzztx, Gcxt, i, i);
        }
        Matrix Gztzr = Gztz.copy();
        for (int i = 0; i < kz; i++) {
            Gztzr.set(i, i, Gztz.get(i, i) + this.regularizer);
        }
        Matrix ZI = new DenseMatrix(kz, kz);
        // invert matrix
        Gztzr.solve(Matrices.identity(kz), ZI);
        //
        Matrix GzGZI = new DenseMatrix(m, kz);
        Gcz.mult(ZI, GzGZI);
        Matrix GzGZIGzt = new DenseMatrix(m, m);
        GzGZI.mult(Gczt, GzGZIGzt);
        double inv = 0.0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                GzGZIGzt.set(i, j, GzGZIGzt.get(i, j) * (-1 / this.regularizer));
            }
        }
        for (int i = 0; i < m; i++) {
            GzGZIGzt.set(i, i, GzGZIGzt.get(i, i) + (1 / this.regularizer));
        }
        for (int i = 0; i < m; i++) {
            inv += GzGZIGzt.get(i, i);
        }
        System.out.println("inv " + inv);
        inv = 0.0;
        Matrix ZI2 = new DenseMatrix(m, m);
        GzGZIGzt.mult(GzGZIGzt, ZI2);
        for (int i = 0; i < m; i++) {
            inv += ZI2.get(i, i);
        }
        System.out.println("inv " + inv);
        Matrix Gyytz = new DenseMatrix(m, kz);
        Gcy.mult(Gytz, Gyytz);
        Matrix Gyytzzt = new DenseMatrix(m, m);
        Gyytz.mult(Gczt, Gyytzzt);
        Matrix Gzztx = new DenseMatrix(m, kx);
        Gcz.mult(Gztx, Gzztx);
        Matrix Gzztxxt = new DenseMatrix(m, m);
        Gzztx.mult(Gcxt, Gzztxxt);
        Matrix GyzZI = new DenseMatrix(m, m);
        Gyytzzt.mult(ZI2, GyzZI);
        Matrix GyzZIzx = new DenseMatrix(m, m);
        GyzZI.mult(Gzztxxt, GyzZIzx);
        double sec = 0.0;
        for (int i = 0; i < m; i++) {
            sec += GyzZIzx.get(i, i);
        }
        System.out.println("sec " + sec);
        //
        Matrix Gytzztz = new DenseMatrix(ky, kz);
        Gytz.mult(Gztz, Gytzztz);
        Matrix GytzztzZI = new DenseMatrix(ky, kz);
        Gytzztz.mult(ZI, GytzztzZI);
        Matrix GytzztzZIztzztx = new DenseMatrix(ky, kx);
        GytzztzZI.mult(Gztzztx, GytzztzZIztzztx);
        Matrix GyytzztzZIztzztx = new DenseMatrix(m, kx);
        Gcy.mult(GytzztzZIztzztx, GyytzztzZIztzztx);
        double s1 = 0.0;
        for (int i = 0; i < m; i++) {
            s1 += matrixProductEntry(GyytzztzZIztzztx, Gcxt, i, i);
        }
        second -= 2 * s1;
        Matrix GZIztzztx = new DenseMatrix(kz, kx);
        ZI.mult(Gztzztx, GZIztzztx);
        Matrix GytzztzZIztz = new DenseMatrix(ky, kz);
        GytzztzZI.mult(Gztz, GytzztzZIztz);
        Matrix GytzztzZIztzZIztzztx = new DenseMatrix(ky, kx);
        GytzztzZIztz.mult(GZIztzztx, GytzztzZIztzZIztzztx);
        Matrix GyytzztzZIztzZIztzztx = new DenseMatrix(m, kx);
        Gcy.mult(GytzztzZIztzZIztzztx, GyytzztzZIztzZIztzztx);
        for (int i = 0; i < m; i++) {
            second += matrixProductEntry(GyytzztzZIztzZIztzztx, Gcxt, i, i);
        }
        double reg2 = Math.pow(this.regularizer, 2);
        empHSIC -= (2 / reg2) * second;

        // third block
        Matrix Gxtz = new DenseMatrix(kx, kz);
        Gcxt.mult(Gcz, Gxtz);
        Matrix Gxtzztz = new DenseMatrix(kx, kz);
        Gxtz.mult(Gztz, Gxtzztz);
        Matrix Gxtzztzzt = new DenseMatrix(kx, m);
        Gxtzztz.mult(Gczt, Gxtzztzzt);
        Matrix GxtzztzZI = new DenseMatrix(kx, kz);
        Gxtzztz.mult(ZI, GxtzztzZI);
        Matrix GxtzztzZIztz = new DenseMatrix(kx, kz);
        GxtzztzZI.mult(Gztz, GxtzztzZIztz);
        Matrix GxtzztzZIztzzt = new DenseMatrix(kx, m);
        GxtzztzZIztz.mult(Gczt, GxtzztzZIztzzt);
        Matrix GxtzztzZIztzZI = new DenseMatrix(kx, kz);
        GxtzztzZIztz.mult(ZI, GxtzztzZIztzZI);
        Matrix GxtzztzZIztzZIztz = new DenseMatrix(kx, kz);
        GxtzztzZIztzZI.mult(Gztz, GxtzztzZIztzZIztz);
        Matrix GxtzztzZIztzZIztzzt = new DenseMatrix(kx, m);
        GxtzztzZIztzZIztz.mult(Gczt, GxtzztzZIztzZIztzzt);
        double third = 0.0;
        for (int i = 0; i < m; i++) {
            third += matrixProductEntry(GyytzztzZIztzztx, Gxtzztzzt, i, i);
            third += matrixProductEntry(GyytzztzZIztzztx, GxtzztzZIztzZIztzzt, i, i);
            third += matrixProductEntry(Gyytzztzztx, GxtzztzZIztzzt, i, i);
            third += matrixProductEntry(GyytzztzZIztzZIztzztx, GxtzztzZIztzzt, i, i);
        }
        third *= -2;
        for (int i = 0; i < m; i++) {
            third += matrixProductEntry(Gyytzztzztx, Gxtzztzzt, i, i);
            third += matrixProductEntry(GyytzztzZIztzZIztzztx, Gxtzztzzt, i, i);
            third += matrixProductEntry(Gyytzztzztx, GxtzztzZIztzZIztzzt, i, i);
            third += matrixProductEntry(GyytzztzZIztzZIztzztx, GxtzztzZIztzZIztzzt, i, i);
        }
        double t1 = 0.0;
        for (int i = 0; i < m; i++) {
            t1 += matrixProductEntry(GyytzztzZIztzztx, GxtzztzZIztzzt, i, i);
        }
        third += 4 * t1;
        empHSIC += third / Math.pow(reg2, 2);

        // beta z estimate
        double betaz = 0.0;
        for (int i = 0; i < (m - 1); i++) {
            for (int j = (i + 1); j < m; j++) {
                betaz += Math.pow(matrixProductEntry(Gcz, Gczt, i, j), 2);
                betaz += Math.pow(matrixProductEntry(Gcz, Gczt, j, i), 2);
            }
        }

        empHSIC *= (m / (betaz * (m - 1)));

        return empHSIC;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        return isIndependent(x, y, Arrays.asList(z));
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * Returns the HSIC test statistic
     *
     * @return
     */
    public double getHsic() {
        return this.hsic;
    }

    /**
     * Returns the alpha level threshold
     *
     * @return
     */
    public double getThreshold() {
        return this.thresh;
    }

    /**
     * Returns the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Sets the significance level at which independence judgments should be made.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
        this.thresh = Double.NaN;
    }

    /**
     * Sets the precision for the Incomplete Choleksy factorization method for approximating Gram matrices. A value <= 0
     * indicates that the Incomplete Cholesky method should not be used and instead use the exact matrices.
     */
    public void setIncompleteCholesky(double precision) {
        this.useIncompleteCholesky = precision;
    }

    /**
     * Set the number of bootstrap samples to use
     */
    public void setPerms(int perms) {
        this.perms = perms;
    }

    /**
     * Sets the regularizer
     */
    public void setRegularizer(double regularizer) {
        this.regularizer = regularizer;
    }

    /**
     * Gets the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Gets the getModel precision for the Incomplete Cholesky
     */
    public double getPrecision() {
        return this.useIncompleteCholesky;
    }

    /**
     * Gets the getModel number of bootstrap samples used
     */
    public int getPerms() {
        return this.perms;
    }

    /**
     * Gets the getModel regularizer
     */
    public double getRegularizer() {
        return this.regularizer;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns the variable with the given name.
     */
    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (variable.getName().equals(name)) {
                return variable;
            }
        }
        return null;
    }

    /**
     * Returns the list of variable varNames.
     */
    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> variableNames = new ArrayList<String>();
        for (Node variable1 : variables) {
            variableNames.add(variable1.getName());
        }
        return variableNames;
    }

    /**
     * Returns the data set being analyzed.
     */
    public DataSet getData() {
        return dataSet;
    }

    @Override
    public ICovarianceMatrix getCov() {
        return null;
    }

    @Override
    public List<DataSet> getDataSets() {
        return null;
    }

    @Override
    public int getSampleSize() {
        return 0;
    }

    @Override
    public List<TetradMatrix> getCovMatrices() {
        return null;
    }

    public void shuffleVariables() {
        List<Node> nodes = new ArrayList(this.variables);
        Collections.shuffle(nodes);
        this.variables = Collections.unmodifiableList(nodes);
    }

    /**
     * Returns a string representation of this test.
     */
    public String toString() {
        return "HSIC, alpha = " + nf.format(getAlpha());
    }

    public boolean determines(List z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Method not implemented");
    }

    //==========================PRIVATE METHODS============================//


    private int sampleSize() {
        return this.dataSet.getNumRows();
    }

    private double matrixProductEntry(Matrix X, Matrix Y, int i, int j) {
        double entry = 0.0;
        for (int k = 0; k < X.numColumns(); k++) {
            entry += X.get(i, k) * Y.get(k, j);
        }
        return entry;
    }

    private static double trace(Matrix A, int m) {
        double trace = 0.0;
        for (int i = 0; i < m; i++) {
            trace += A.get(i, i);
        }
        return trace;
    }

}



