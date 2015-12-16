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

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Functions;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.*;

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
public final class IndTestFisherZGeneralizedInverse implements IndependenceTest {

    /**
     * The correlation matrix.
     */
    private final DoubleMatrix2D data;

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
     * The value of the Fisher's Z statistic associated with the las calculated partial correlation.
     */
    private double fishersZ;

    /**
     * Formats as 0.0000.
     */
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private DataSet dataSet;
    private boolean verbose = false;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The alpha level of the test.
     */
    public IndTestFisherZGeneralizedInverse(DataSet dataSet, double alpha) {
        this.dataSet = dataSet;
        this.data = new DenseDoubleMatrix2D(dataSet.getDoubleData().toArray());
        this.variables = Collections.unmodifiableList(dataSet.getVariables());
        setAlpha(alpha);
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
     * @param xVar the one variable being compared.
     * @param yVar the second variable being compared.
     * @param z    the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public boolean isIndependent(Node xVar, Node yVar, List<Node> z) {
        if (z == null) {
            throw new NullPointerException();
        }

        for (Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        int size = z.size();
        int[] zCols = new int[size];

        int xIndex = getVariables().indexOf(xVar);
        int yIndex = getVariables().indexOf(yVar);

        for (int i = 0; i < z.size(); i++) {
            zCols[i] = getVariables().indexOf(z.get(i));
        }

        int[] zRows = new int[data.rows()];
        for (int i = 0; i < data.rows(); i++) {
            zRows[i] = i;
        }

        DoubleMatrix2D Z = data.viewSelection(zRows, zCols);
        DoubleMatrix1D x = data.viewColumn(xIndex);
        DoubleMatrix1D y = data.viewColumn(yIndex);
        DoubleMatrix2D Zt = new Algebra().transpose(Z);
        DoubleMatrix2D ZtZ = new Algebra().mult(Zt, Z);
        TetradMatrix _ZtZ = new TetradMatrix(ZtZ.toArray());
        TetradMatrix ginverse = _ZtZ.inverse();
        DoubleMatrix2D G = new DenseDoubleMatrix2D(ginverse.toArray());

        DoubleMatrix2D Zt2 = Zt.like();
        Zt2.assign(Zt);
        DoubleMatrix2D GZt = new Algebra().mult(G, Zt2);

        DoubleMatrix1D b_x = new Algebra().mult(GZt, x);
        DoubleMatrix1D b_y = new Algebra().mult(GZt, y);

        DoubleMatrix1D xPred = new Algebra().mult(Z, b_x);
        DoubleMatrix1D yPred = new Algebra().mult(Z, b_y);

        DoubleMatrix1D xRes = xPred.copy().assign(x, Functions.minus);
        DoubleMatrix1D yRes = yPred.copy().assign(y, Functions.minus);

        // Note that r will be NaN if either xRes or yRes is constant.
        double r = StatUtils.correlation(xRes.toArray(), yRes.toArray());

        if (Double.isNaN(thresh)) {
            this.thresh = cutoffGaussian();
        }

        if (Double.isNaN(r)) {
            if (verbose) {
                TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFactMsg(xVar, yVar, z, getPValue()));
            }
            return true;
        }

        if (r > 1) r = 1;
        if (r < -1) r = -1;

        this.fishersZ = Math.sqrt(sampleSize() - z.size() - 3.0) *
                0.5 * (Math.log(1.0 + r) - Math.log(1.0 - r));

//        this.fishersZ = 0.5 * Math.sqrt(sampleSize() - z.size() - 3.0) *
//                Math.log(Math.abs(1.0 + r) / Math.abs(1.0 - r));

        if (Double.isNaN(this.fishersZ)) {
            throw new IllegalArgumentException("The Fisher's Z " +
                    "score for independence fact " + xVar + " _||_ " + yVar +
                    " | " + z + " is undefined.");
        }

        boolean indFisher = true;

        //System.out.println("thresh = " + thresh);
        //if(Math.abs(fishersZ) > 1.96) indFisher = false; //Two sided with alpha = 0.05
        if (Math.abs(fishersZ) > thresh) {
            indFisher = false;  //Two sided
        }

        if (verbose) {
            if (indFisher) {
                TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFactMsg(xVar, yVar, z, getPValue()));
            } else {
                TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFactMsg(xVar, yVar, z, getPValue()));
            }
        }

        return indFisher;
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

//        double q = 2.0 * Integrator.getArea(npdf, 0.0, Math.abs(fishersZ), 100);
//        if (q > 1.0) {
//            q = 1.0;
//        }
//        return 1.0 - q;
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
        for (Node variable1 : variables) {
            variableNames.add(variable1.getName());
        }
        return variableNames;
    }

    public String toString() {
        return "Fisher's Z - Generalized Inverse, alpha = " + nf.format(getAlpha());
    }

    //==========================PRIVATE METHODS============================//

    /**
     * Computes that value x such that P(abs(N(0,1) > x) < alpha.  Note that this is a two sided test of the null
     * hypothesis that the Fisher's Z value, which is distributed as N(0,1) is not equal to 0.0.
     */
    private double cutoffGaussian() {
        double upperTail = 1.0 - getAlpha() / 2.0;
        double epsilon = 1e-14;

        // Find an upper bound.
        double lowerBound = -1.0;
        double upperBound = 0.0;

        while (RandomUtil.getInstance().normalCdf(0, 1, upperBound) < upperTail) {
            lowerBound += 1.0;
            upperBound += 1.0;
        }

        while (upperBound >= lowerBound + epsilon) {
            double midPoint = lowerBound + (upperBound - lowerBound) / 2.0;

            if (RandomUtil.getInstance().normalCdf(0, 1, midPoint) <= upperTail) {
                lowerBound = midPoint;
            } else {
                upperBound = midPoint;
            }
        }

        return lowerBound;

//        npdf = new NormalPdf();
//        final double upperBound = 9.0;
//        final double delta = 0.001;
//                double alpha = this.alpha/2.0;    //Two sided test
//        return CutoffFinder.getCutoff(npdf, upperBound, alpha, delta);
    }

    private int sampleSize() {
        return data.rows();
    }

    public boolean determines(List<Node> zList, Node xVar) {
        if (zList == null) {
            throw new NullPointerException();
        }

        if (zList.isEmpty()) {
            return false;
        }

        for (Node node : zList) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        int size = zList.size();
        int[] zCols = new int[size];

        int xIndex = getVariables().indexOf(xVar);

        for (int i = 0; i < zList.size(); i++) {
            zCols[i] = getVariables().indexOf(zList.get(i));
        }

        int[] zRows = new int[data.rows()];
        for (int i = 0; i < data.rows(); i++) {
            zRows[i] = i;
        }

        DoubleMatrix2D Z = data.viewSelection(zRows, zCols);
        DoubleMatrix1D x = data.viewColumn(xIndex);
        DoubleMatrix2D Zt = new Algebra().transpose(Z);
        DoubleMatrix2D ZtZ = new Algebra().mult(Zt, Z);

        TetradMatrix _ZtZ = new TetradMatrix(ZtZ.toArray());
        TetradMatrix ginverse = _ZtZ.inverse();
        DoubleMatrix2D G = new DenseDoubleMatrix2D(ginverse.toArray());

//        DoubleMatrix2D G = MatrixUtils.ginverse(ZtZ);
        DoubleMatrix2D Zt2 = Zt.copy();
        DoubleMatrix2D GZt = new Algebra().mult(G, Zt2);
        DoubleMatrix1D b_x = new Algebra().mult(GZt, x);
        DoubleMatrix1D xPred = new Algebra().mult(Z, b_x);
        DoubleMatrix1D xRes = xPred.copy().assign(x, Functions.minus);
        double SSE = xRes.aggregate(Functions.plus, Functions.square);

        double variance = SSE / (data.rows() - (zList.size() + 1));

//        ChiSquare chiSquare = new ChiSquare(data.rows(),
//                PersistentRandomUtil.getInstance().getEngine());
//
//        double p = chiSquare.cdf(sum);
//        boolean determined = p < 1 - getAlpha();
//
        boolean determined = variance < getAlpha();

        if (determined) {
            StringBuilder sb = new StringBuilder();
            sb.append("Determination found: ").append(xVar).append(
                    " is determined by {");

            for (int i = 0; i < zList.size(); i++) {
                sb.append(zList.get(i));

                if (i < zList.size() - 1) {
                    sb.append(", ");
                }
            }

            sb.append("}");

//            sb.append(" p = ").append(nf.format(p));
            sb.append(" SSE = ").append(nf.format(SSE));

            TetradLogger.getInstance().log("independencies", sb.toString());
            System.out.println(sb);
        }

        return determined;
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





