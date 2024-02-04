///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.cluster.KMeans;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.Kernel;
import edu.cmu.tetrad.search.utils.KernelGaussian;
import edu.cmu.tetrad.search.utils.KernelUtils;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks the conditional independence X _||_ Y | S, where S is a set of continuous variable, and X and Y are discrete
 * variable not in S, using the Hilbert-Schmidth Independence Criterion (HSIC), a kernel based nonparametric test for
 * conditional independence.
 * <p>
 * The Kpc algorithm by Tillman had run PC using this test; to run Kpc, simply select this test for PC.
 *
 * @author Robert Tillman
 * @see edu.cmu.tetrad.search.work_in_progress.Kpc
 */

public final class IndTestHsic implements IndependenceTest {

    // Number format for printing p-values.
    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    // The variables of the covariance matrix, in order. (Unmodifiable list.)
    private final List<Node> variables;
    // Stores a reference to the dataset being analyzed.
    private final DataSet dataSet;
    // A cache of results for independence facts.
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    // The significance level of the independence tests.
    private double alpha;
    // The cutoff value for 'alpha'
    private double thresh = Double.NaN;
    // A stored p value, if the deterministic test was used.
    private double pValue = Double.NaN;
    // The regularizer
    private double regularizer = 0.0001;
    // Number of permutations to approximate the null distribution
    private int perms = 100;
    // Use incomplete Choleksy decomposition to calculate Gram matrices
    private double useIncompleteCholesky = 1e-18;
    // Whether to print verbose output.
    private boolean verbose;


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

    /**
     * Constructs a new HSIC Independence test. The given significance level is used.
     *
     * @param data      A matrix of continuous data.
     * @param variables The list of variables for the data.
     * @param alpha     The alpha level of the test.
     */
    public IndTestHsic(Matrix data, List<Node> variables, double alpha) {
        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data.toArray()), variables);

        this.variables = Collections.unmodifiableList(variables);
        setAlpha(alpha);

        this.dataSet = dataSet;
    }


    /**
     * Creates a new IndTestHsic instance for a subset of the variables.
     *
     * @return This sublist.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (Node var : vars) {
            if (!this.variables.contains(var)) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }
        }

        int[] indices = new int[vars.size()];

        for (int i = 0; i < indices.length; i++) {
            indices[i] = this.variables.indexOf(vars.get(i));
        }

        double alphaNew = getAlpha();
        return new IndTestHsic(this.dataSet.subsetColumns(indices), alphaNew);
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x  the one variable being compared.
     * @param y  the second variable being compared.
     * @param _z the list of conditioning variables.
     * @return True iff x _||_ y | z.
     */
    public IndependenceResult checkIndependence(Node y, Node x, Set<Node> _z) {
        if (facts.containsKey(new IndependenceFact(x, y, _z))) {
            return facts.get(new IndependenceFact(x, y, _z));
        }

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        int m = sampleSize();

        // choose kernels using median distance heuristic
        Kernel xKernel = new KernelGaussian(1);
        Kernel yKernel = new KernelGaussian(1);
        List<Kernel> zKernel = new ArrayList<>();
        yKernel.setDefaultBw(this.dataSet, y);
        xKernel.setDefaultBw(this.dataSet, x);
        if (!z.isEmpty()) {
            for (Node node : z) {
                Kernel Zi = new KernelGaussian(1);
                Zi.setDefaultBw(this.dataSet, node);
                zKernel.add(Zi);
            }
        }

        // consruct Gram matricces
        Matrix Ky;
        Matrix Kx;
        Matrix Kz = null;
        // use incomplete Cholesky to approximate
        if (this.useIncompleteCholesky > 0) {
            Ky = KernelUtils.incompleteCholeskyGramMatrix(Collections.singletonList(yKernel), this.dataSet, Collections.singletonList(y), this.useIncompleteCholesky);
            Kx = KernelUtils.incompleteCholeskyGramMatrix(Collections.singletonList(xKernel), this.dataSet, Collections.singletonList(x), this.useIncompleteCholesky);
            if (!z.isEmpty()) {
                Kz = KernelUtils.incompleteCholeskyGramMatrix(zKernel, this.dataSet, z, this.useIncompleteCholesky);
            }
        }
        // otherwise compute directly
        else {
            Ky = KernelUtils.constructCentralizedGramMatrix(Collections.singletonList(yKernel), this.dataSet, Collections.singletonList(y));
            Kx = KernelUtils.constructCentralizedGramMatrix(Collections.singletonList(xKernel), this.dataSet, Collections.singletonList(x));
            if (!z.isEmpty()) {
                Kz = KernelUtils.constructCentralizedGramMatrix(zKernel, this.dataSet, z);
            }
        }

        // get Hilbert-Schmidt dependence measure
        double hsic;
        if (z.isEmpty()) {
            if (this.useIncompleteCholesky > 0) {
                hsic = empiricalHSICincompleteCholesky(Ky, Kx, m);
            } else {
                hsic = empiricalHSIC(Ky, Kx, m);
            }
        } else {
            if (this.useIncompleteCholesky > 0) {
                hsic = empiricalHSICincompleteCholesky(Ky, Kx, Kz, m);
            } else {
                hsic = empiricalHSIC(Ky, Kx, Kz, m);
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
                zind[j] = this.dataSet.getColumn(z.get(j));
            }
            kmeans.cluster(this.dataSet.subsetColumns(z).getDoubleData());
            clusterAssign = kmeans.getClusters();
        }
        for (int i = 0; i < this.perms; i++) {
            DataSet shuffleData = this.dataSet.copy();
            // shuffle data
            if (z.isEmpty()) {
                List<Integer> indicesList = new ArrayList<>();
                for (int j = 0; j < m; j++) {
                    indicesList.add(j);
                }
                RandomUtil.shuffle(indicesList);
                for (int j = 0; j < m; j++) {
                    double shuffleVal = this.dataSet.getDouble(indicesList.get(j), ycol);
                    shuffleData.setDouble(j, ycol, shuffleVal);
                }
            } else {
                // shuffle data within clusters
                assert clusterAssign != null;
                for (List<Integer> integers : clusterAssign) {
                    List<Integer> shuffleCluster = new ArrayList<>(integers);

                    RandomUtil.shuffle(shuffleCluster);

                    for (int k = 0; k < shuffleCluster.size(); k++) {
                        // first swap y;
                        double swapVal = this.dataSet.getDouble(integers.get(k), ycol);
                        shuffleData.setDouble(shuffleCluster.get(k), ycol, swapVal);
                        // now swap z
                        for (int zi = 0; zi < z.size(); zi++) {
                            swapVal = this.dataSet.getDouble(integers.get(k), zind[zi]);
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
            Matrix Kyn;
            if (this.useIncompleteCholesky > 0) {
                Kyn = KernelUtils.incompleteCholeskyGramMatrix(Collections.singletonList(yKernel), shuffleData, Collections.singletonList(y), this.useIncompleteCholesky);
            } else {
                Kyn = KernelUtils.constructCentralizedGramMatrix(Collections.singletonList(yKernel), shuffleData, Collections.singletonList(y));

            }
            if (!z.isEmpty()) {
                if (this.useIncompleteCholesky > 0) {
                    KernelUtils.incompleteCholeskyGramMatrix(zKernel, shuffleData, z, this.useIncompleteCholesky);
                } else {
                    KernelUtils.constructCentralizedGramMatrix(zKernel, shuffleData, z);
                }
            }
            // HSIC
            if (z.isEmpty()) {
                if (this.useIncompleteCholesky > 0) {
                    nullapprox[i] = empiricalHSICincompleteCholesky(Kyn, Kx, m);
                } else {
                    nullapprox[i] = empiricalHSIC(Kyn, Kx, m);
                }
            } else {
                if (this.useIncompleteCholesky > 0) {
                    assert Kz != null;
                    nullapprox[i] = empiricalHSICincompleteCholesky(Kyn, Kx, Kz, m);
                } else {
                    nullapprox[i] = empiricalHSIC(Kyn, Kx, Kz, m);
                }
            }
        }

        // permutation test to get p-value
        double evalCdf = 0.0;
        for (int i = 0; i < this.perms; i++) {
            if (nullapprox[i] <= hsic) {
                evalCdf += 1.0;
            }
        }

        evalCdf /= this.perms;
        this.pValue = 1.0 - evalCdf;

        if (Double.isNaN(this.pValue)) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                    LogUtilsSearch.independenceFact(x, y, _z));
        }

        // reject if pvalue <= alpha
        boolean independent = this.pValue <= this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, _z), independent, this.pValue, alpha - pValue);
        facts.put(new IndependenceFact(x, y, _z), result);
        return result;
    }

    /**
     * Empirical unconditional Hilbert-Schmidt Dependence Measure for X and Y
     *
     * @param Ky centralized Gram matrix for Y
     * @param Kx centralized Gram matrix for X
     * @param m  sample size
     */
    public double empiricalHSIC(Matrix Ky, Matrix Kx, int m) {
        Matrix Kyx = Ky.times(Kx);
        double empHSIC = 0.0;
        for (int i = 0; i < m; i++) {
            empHSIC += Kyx.get(i, i);
        }
        empHSIC /= FastMath.pow(m - 1, 2);
        return empHSIC;
    }

    /**
     * Empirical unconditional Hilbert-Schmidt Dependence Measure for X and Y using incomplete Cholesky decomposition to
     * approximate Gram matrices
     *
     * @param Gy Choleksy approximate Gram matrix for Y
     * @param Gx Choleksy approximate Gram matrix for X
     * @param m  sample size
     */
    public double empiricalHSICincompleteCholesky(Matrix Gy, Matrix Gx, int m) {
        // centralized Choleksy
        Matrix H = KernelUtils.constructH(m);
        Matrix Gcy = H.times(Gy);
        Matrix Gcx = H.times(Gx);

        // multiply gram matrices
        Matrix Gcyt = Gcy.transpose();
        Matrix A = Gcyt.times(Gcx);
        Matrix B = Gcy.times(A);
        Matrix Gcxt = Gcx.transpose();
        double empHSIC = 0.0;
        for (int i = 0; i < m; i++) {
            empHSIC += matrixProductEntry(B, Gcxt, i, i);
        }
        empHSIC /= FastMath.pow(m - 1, 2);
        return empHSIC;
    }

    /**
     * Empirical conditional Hilbert-Schmidt Dependence Measure Y and X given Z
     *
     * @param Ky centralized Gram matrix for Y
     * @param Kx centralized Gram matrix for X
     * @param Kz centralized Gram matrix for Z
     * @param m  sample size
     */
    private double empiricalHSIC(Matrix Ky, Matrix Kx, Matrix Kz, int m) {
        Matrix Kyx = Ky.times(Kx);
        Matrix Kyz = Ky.times(Kz);
        Matrix Kzx = Kz.times(Kx);
        Matrix Kzreg = Kz.copy();
        for (int i = 0; i < m; i++) {
            double ent = (Kzreg.get(i, i) + this.regularizer);
            Kzreg.set(i, i, ent);
        }
        Matrix A = Kzreg.inverse();
        Kzreg = A.times(A);
        Matrix Kyzzregzx;
        A = Kyz.times(Kzreg);
        Kyzzregzx = A.times(Kzx);
        Matrix Kyzzregzxzzregz;
        Kyzzregzxzzregz = Kyzzregzx.times(Kz);
        A = Kyzzregzxzzregz.times(Kzreg);
        Kyzzregzxzzregz = A.times(Kz);
        // get trace
        double empHSIC = 0.0;
        for (int i = 0; i < m; i++) {
            empHSIC += Kyx.get(i, i);
            empHSIC += (-2 * Kyzzregzx.get(i, i));
            empHSIC += Kyzzregzxzzregz.get(i, i);
        }
        empHSIC /= FastMath.pow(m - 1, 2);
        double Bz = 0.0;
        for (int i = 0; i < (m - 1); i++) {
            for (int j = (i + 1); j < m; j++) {
                Bz += FastMath.pow(Kz.get(i, j), 2);
                Bz += FastMath.pow(Kz.get(j, i), 2);
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
     */
    public double empiricalHSICincompleteCholesky(Matrix Gy, Matrix Gx, Matrix Gz, int m) {
        // centralize Choleksy
        int kz = Gz.getNumColumns();

        Matrix H = KernelUtils.constructH(m);
        Matrix Gcy = H.times(Gy);
        Matrix Gcx = H.times(Gx);
        Matrix Gcz = H.times(Gz);

        // multiply gram matrices (first block)
        Matrix A;
        Matrix Gcyt = Gcy.transpose();
        A = Gcyt.times(Gcx);
        Matrix B = Gcy.times(A);
        Matrix Gcxt;
        Gcxt = Gcx.transpose();
        double empHSIC = 0.0;
        for (int i = 0; i < m; i++) {
            empHSIC += matrixProductEntry(B, Gcxt, i, i);
        }

        // second block
        Matrix Gytz = Gcyt.times(Gcz);
        Matrix Gczt = Gcz.transpose();
        Matrix Gztx = Gczt.times(Gcx);
        Matrix Gztz = Gczt.times(Gcz);
        Matrix Gztzr = Gztz.copy();
        for (int i = 0; i < kz; i++) {
            Gztzr.set(i, i, Gztz.get(i, i) + this.regularizer);
        }
        // invert matrix
        Matrix ZI = Gztzr.inverse();
        Matrix ZIzt = ZI.times(Gczt);
        Matrix Gzr = Gcz.copy();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < kz; j++) {
                Gzr.set(i, j, Gcz.get(i, j) * (-1.0 / this.regularizer));
            }
        }
        Matrix Zinv = Gzr.times(ZIzt);
        for (int i = 0; i < m; i++) {
            Zinv.set(i, i, Zinv.get(i, i) + (1.0 / this.regularizer));
        }
        Matrix Gztzinv = Gczt.times(Zinv);
        Matrix Gzinvz = Zinv.times(Gcz);
        Matrix Gztinv2z = Gztzinv.times(Gzinvz);
        Matrix Gytzztzinv2z = Gytz.times(Gztinv2z);
        Matrix Gytzztzinv2zztx = Gytzztzinv2z.times(Gztx);
        Matrix Gyytzztzinv2zztx = Gcy.times(Gytzztzinv2zztx);
        double second = 0.0;
        for (int i = 0; i < m; i++) {
            second += matrixProductEntry(Gyytzztzinv2zztx, Gcxt, i, i);
        }
        empHSIC -= 2 * second;

        // third block
        Matrix Gxtz = Gcxt.times(Gcz);
        Matrix Gxtzztinv2z = Gxtz.times(Gztinv2z);
        Matrix Gyytzztzinv2zztxxtzztinv2z = Gyytzztzinv2zztx.times(Gxtzztinv2z);
        for (int i = 0; i < m; i++) {
            empHSIC += matrixProductEntry(Gyytzztzinv2zztxxtzztinv2z, Gczt, i, i);
        }

        // beta z estimate
        double betaz = 0.0;
        for (int i = 0; i < (m - 1); i++) {
            for (int j = (i + 1); j < m; j++) {
                betaz += FastMath.pow(matrixProductEntry(Gcz, Gczt, i, j), 2);
                betaz += FastMath.pow(matrixProductEntry(Gcz, Gczt, j, i), 2);
            }
        }

        empHSIC *= (m / (betaz * (m - 1)));

        return empHSIC;
    }

    /**
     * Sets the precision for the Incomplete Choleksy factorization method for approximating Gram matrices. A value
     * &lt;= 0 indicates that the Incomplete Cholesky method should not be used and instead use the exact matrices.
     *
     * @param precision This precision.
     */
    public void setIncompleteCholesky(double precision) {
        this.useIncompleteCholesky = precision;
    }

    /**
     * Set the number of bootstrap samples to use
     *
     * @param numBootstraps This number.
     */
    public void setPerms(int numBootstraps) {
        this.perms = numBootstraps;
    }

    /**
     * Sets the regularizer.
     *
     * @param regularizer This value.
     */
    public void setRegularizer(double regularizer) {
        this.regularizer = regularizer;
    }

    /**
     * Gets the getModel significance level.
     *
     * @return This alpha.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level at which independence judgments should be made.
     *
     * @param alpha This alpha.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
        this.thresh = Double.NaN;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns the variable with the given name.
     *
     * @return This string.
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
     * Returns the data set being analyzed.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        return "HSIC, alpha = " + IndTestHsic.nf.format(getAlpha());
    }

    /**
     * @throws UnsupportedOperationException Method not supported.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Method not implemented");
    }


    private int sampleSize() {
        return this.dataSet.getNumRows();
    }

    private double matrixProductEntry(Matrix X, Matrix Y, int i, int j) {
        double entry = 0.0;
        for (int k = 0; k < X.getNumColumns(); k++) {
            entry += X.get(i, k) * Y.get(k, j);
        }
        return entry;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}



