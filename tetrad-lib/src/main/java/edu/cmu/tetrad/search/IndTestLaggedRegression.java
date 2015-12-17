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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks independence of X _||_ Y | Z for variables X and Y and list Z of variables. Partial correlations are
 * calculated using generalized inverses, so linearly dependent variables do not throw exceptions. Must supply a
 * continuous data set; don't know how to do this with covariance or correlation matrices.
 *
 * @author Joseph Ramsey
 * @author Frank Wimberly adapted IndTestCramerT for Fisher's Z
 */
public final class IndTestLaggedRegression implements IndependenceTest {

//    /**
//     * The correlation matrix.
//     */
//    private final DoubleMatrix2D data;

    /**
     * The variables of the correlation matrix, in order. (Unmodifiable list.)
     */
    private final List<Node> variables;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * The cutoff value for 'alpha' area in the two tails of the partial correlation distribution function.
     */
    private double thresh = Double.NaN;

    /**
     * The last calculated partial correlation, needed to calculate relative strength.
     */
    private double storedR = 0.;

    /**
     * The value of the Fisher's Z statistic associated with the las calculated partial correlation.
     */
    private double fishersZ;

    /**
     * The standard number formatter for Tetrad.
     */
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private DataSet dataSet;

    private DataSet timeLags;
    private Regression regression;
    private final DataSet timeSeries;
    private boolean verbose = false;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     */
    public IndTestLaggedRegression(DataSet timeSeries, double alpha, int numLags) {
        this.timeSeries = timeSeries;
//        this.data = new DenseDoubleMatrix2D(timeSeries.getDoubleData().toArray());
        this.variables = Collections.unmodifiableList(timeSeries.getVariables());
        setAlpha(alpha);

        timeLags = TimeSeriesUtils.createLagData(timeSeries, numLags);

        regression = new RegressionDataset(timeLags);
//        Regression regression = new RegressionDatasetGeneralized(timeLags);


    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new IndTestCramerT instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List vars) {
//        if (vars.isEmpty()) {
//            throw new IllegalArgumentException("Subset may not be empty.");
//        }
//
//        for (int i = 0; i < vars.size(); i++) {
//            if (!variables.contains(vars.get(i))) {
//                throw new IllegalArgumentException(
//                        "All vars must be original vars");
//            }
//        }
//
//        double[][] m = new double[vars.size()][vars.size()];
//
//        for (int i = 0; i < vars.size(); i++) {
//            for (int j = 0; j < vars.size(); j++) {
//                double val = data.getValue(variables.indexOf(vars.get(i)),
//                        variables.indexOf(vars.get(j)));
//                m[i][j] = val;
//            }
//        }
//
//        int sampleSize = covMatrix().getN();
//        CorrelationMatrix newCorrMatrix = new CorrelationMatrix(vars, m,
//                sampleSize);
//
//        double alphaNew = getAlpha();
//        IndependenceTest newIndTest = new IndTestCramerT(newCorrMatrix,
//                alphaNew);
//        return newIndTest;
//
        return null;
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param xVar  the one variable being compared.
     * @param yVar  the second variable being compared.
     * @param zList the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public boolean isIndependent(Node xVar, Node yVar, List<Node> zList) {
        if (zList == null) {
            throw new NullPointerException();
        }

        for (Node node : zList) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        List<Node> regressors = new ArrayList<Node>();
//        regressors.add(dataSet.getVariable(yVar.getName()));
//
//        for (Node zVar : zList) {
//            regressors.add(dataSet.getVariable(zVar.getName()));
//        }

//        TetradMatrix residuals = new TetradMatrix(timeLags.getNumRows(), timeSeries.getNumColumns());

        xVar = timeLags.getVariable(xVar.getName());

        regressors.add(timeLags.getVariable(yVar.getName()));

        int yIndex = timeLags.getColumn(timeLags.getVariable(yVar.getName()));

        for (int i2 = yIndex + variables.size(); i2 < timeLags.getNumColumns(); i2 += variables.size()) {
            regressors.add(timeLags.getVariable(i2));
        }

        int xIndex = timeLags.getColumn(timeLags.getVariable(xVar.getName()));

        for (int i2 = xIndex + variables.size(); i2 < timeLags.getNumColumns(); i2 += variables.size()) {
            regressors.add(timeLags.getVariable(i2));
        }

        for (Node z : zList) {
            int zIndex = timeLags.getColumn(timeLags.getVariable(z.getName()));

            for (int i2 = zIndex; i2 < timeLags.getNumColumns(); i2 += variables.size()) {
                final List<Node> variables = timeLags.getVariables();
                regressors.add(variables.get(i2));
            }
        }

        System.out.println(xVar + " conditioning on " + regressors);

//        Regression regression = new RegressionDataset(dataSet);
        RegressionResult result = null;

        try {
            result = regression.regress(xVar, regressors);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        double p = result.getP()[1];

        System.out.println("p = " + p);

        boolean independent = p > alpha;

        if (verbose) {
            if (independent) {
                System.out.println(SearchLogUtils.independenceFactMsg(xVar, yVar, zList, p));
            }
        }

        return independent;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isIndependent(x, y, zList);
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(fishersZ)));
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    /**
     * Gets the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @return the variable with the given name.
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
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> variableNames = new ArrayList<String>();

        for (Node variable : variables) {
            variableNames.add(variable.getName());
        }

        return variableNames;
    }

    public String toString() {
        return "Linear Regression Test, alpha = " + nf.format(getAlpha());
    }

    //==========================PRIVATE METHODS============================//

//    /**
//     * Return the p-value of the last calculated independence fact.
//     *
//     * @return this p-value.  When accessed through the IndependenceChecker
//     *         interface, this p-value should only be considered to be a
//     *         relative strength.
//     */
//    private double getRelativeStrength() {
//
//        // precondition:  pdf is the most recently used partial
//        // correlation distribution function, and storedR is the most
//        // recently calculated partial correlation.
//        return 2.0 * Integrator.getArea(npdf, Math.abs(storedR), 9.0, 100);
//    }

//    /**
//     * Computes that value x such that P(abs(N(0,1) > x) < alpha.  Note that
//     * this is a two sided test of the null hypothesis that the Fisher's Z
//     * value, which is distributed as N(0,1) is not equal to 0.0.
//     */
//    private double cutoffGaussian() {
//        npdf = new NormalPdf();
//        final double upperBound = 9.0;
//        final double delta = 0.001;
//        //        double alpha = this.alpha/2.0;    //Two sided test
//        return CutoffFinder.getCutoff(npdf, upperBound, alpha, delta);
//    }

//    private int sampleSize() {
//        return data.rows();
//    }

    public boolean determines(List<Node> zList, Node xVar) {
        throw new UnsupportedOperationException();

//        if (zList == null) {
//            throw new NullPointerException();
//        }
//
//        for (Node node : zList) {
//            if (node == null) {
//                throw new NullPointerException();
//            }
//        }
//
//        int size = zList.size();
//        int[] zCols = new int[size];
//
//        int xIndex = getVariables().indexOf(xVar);
//
//        for (int i = 0; i < zList.size(); i++) {
//            zCols[i] = getVariables().indexOf(zList.get(i));
//        }
//
//        int[] zRows = new int[data.rows()];
//        for (int i = 0; i < data.rows(); i++) {
//            zRows[i] = i;
//        }
//
//        DoubleMatrix2D Z = data.viewSelection(zRows, zCols);
//        DoubleMatrix1D x = data.viewColumn(xIndex);
//        DoubleMatrix2D Zt = new Algebra().transpose(Z);
//        DoubleMatrix2D ZtZ = new Algebra().mult(Zt, Z);
//        DoubleMatrix2D G = new DenseDoubleMatrix2D(new TetradMatrix(ZtZ.toArray()).inverse().toArray());
//
//        // Bug in Colt? Need to make a copy before multiplying to avoid
//        // a ClassCastException.
//        DoubleMatrix2D Zt2 = Zt.like();
//        Zt2.assign(Zt);
//        DoubleMatrix2D GZt = new Algebra().mult(G, Zt2);
//
//        DoubleMatrix1D b_x = new Algebra().mult(GZt, x);
//
//        DoubleMatrix1D xPred = new Algebra().mult(Z, b_x);
//
//        DoubleMatrix1D xRes = xPred.copy().assign(x, Functions.minus);
//
//        double SSE = xRes.aggregate(Functions.plus, Functions.square);
//        boolean determined = SSE < 0.0001;
//
//        if (determined) {
//            StringBuilder sb = new StringBuilder();
//            sb.append("Determination found: ").append(xVar).append(
//                    " is determined by {");
//
//            for (int i = 0; i < zList.size(); i++) {
//                sb.append(zList.get(i));
//
//                if (i < zList.size() - 1) {
//                    sb.append(", ");
//                }
//            }
//
//            sb.append("}");
//
//            TetradLogger.getInstance().log("independencies", sb.toString());
//        }
//
//        return determined;
    }

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

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}




