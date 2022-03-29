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
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.distribution.TDistribution;

import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks d-separations in structural model using t-separations over indicators.
 *
 * @author Adam Brodie
 */
public final class IndTestTrekSep implements IndependenceTest {

    /**
     * The covariance matrix.
     */
    private final ICovarianceMatrix covMatrix;
    private final List<Node> latents;

    private boolean verbose;


    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private final List<List<Node>> clustering;

//    /**
//     * The matrix out of the cov matrix.
//     */
//    private final TetradMatrix _covMatrix;

    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private List<Node> variables;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * The value of the Fisher's Z statistic associated with the las calculated partial correlation.
     */
    private double pValue;

    /**
     * Formats as 0.0000.
     */
    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the dataset being analyzed.
     */
    private DataSet dataSet;

    private PrintStream pValueLogger;
    private final Map<Node, Integer> indexMap;
    private final Map<String, Node> nameMap;
    private TDistribution tDistribution;

    //==========================CONSTRUCTORS=============================//

//        /**
//         * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
//         * given data set (must be continuous). The given significance level is used.
//         *
//         * @param dataSet A data set containing only continuous columns.
//         * @param alpha   The alpha level of the test.
//         */
//        public IndTestTrekSep(DataSet dataSet, double alpha, List<List<Node>> clustering) {
//            if (!(dataSet.isContinuous())) {
//                throw new IllegalArgumentException("Data set must be continuous.");
//            }
//
//            this.clustering = clustering;
//
//            this.covMatrix = new CovarianceMatrix(dataSet);
////        this._covMatrix = covMatrix.getMatrix();
//            List<Node> nodes = covMatrix.getVariable();
//
//            this.variables = Collections.unmodifiableList(nodes);
//            this.indexMap = indexMap(variables);
//            this.nameMap = nameMap(variables);
//            setAlternativePenalty(alpha);
//
//            this.dataSet = DataUtils.center(dataSet);
//
//            tDistribution = new TDistribution(dataSet.getNumRows() - 2);
//        }
//
//        /**
//         * Constructs a new Fisher Z independence test with the listed arguments.
//         *
//         * @param data      A 2D continuous data set with no missing values.
//         * @param variables A list of variables, a subset of the variables of <code>data</code>.
//         * @param alpha     The significance cutoff level. p values less than alpha will be reported as dependent.
//         */
//        public IndTestTrekSep(TetradMatrix data, List<Node> variables, double alpha) {
//            this.dataSet = ColtDataSet.makeContinuousData(variables, data);
//            this.dataSet = DataUtils.center(dataSet);
//            this.covMatrix = new CovarianceMatrix(dataSet);
////        this._covMatrix = covMatrix.getMatrix();
//            this.variables = Collections.unmodifiableList(variables);
//            this.indexMap = indexMap(variables);
//            this.nameMap = nameMap(variables);
//            setAlternativePenalty(alpha);
//        }

    /**
     * Constructs a new independence test that will determine conditional independence facts using the given correlation
     * matrix and the given significance level.
     */
    public IndTestTrekSep(final ICovarianceMatrix covMatrix, final double alpha, final List<List<Node>> clustering, final List<Node> latents) {
        this.clustering = clustering;
        this.covMatrix = covMatrix;
        this.variables = Collections.unmodifiableList(covMatrix.getVariables());
        this.indexMap = indexMap(this.variables, latents);
        this.nameMap = nameMap(this.variables);
        this.latents = latents;
        setAlpha(alpha);
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new independence test instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(final List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (final Node var : vars) {
            if (!this.variables.contains(var)) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }
        }

        final int[] indices = new int[vars.size()];

        for (int i = 0; i < indices.length; i++) {
            indices[i] = this.indexMap.get(vars.get(i));
        }

        final ICovarianceMatrix newCovMatrix = this.covMatrix.getSubmatrix(indices);

        final double alphaNew = getAlpha();
        return new IndTestTrekSep(newCovMatrix, alphaNew, this.clustering, this.latents);
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public boolean isIndependent(final Node x, final Node y, final List<Node> z) {
        final int n = sampleSize();
        final int xi = this.latents.indexOf(x);
        final int yi = this.latents.indexOf(y);
        int nA = this.clustering.get(xi).size();
        int nB = this.clustering.get(yi).size();
        for (int i = 0; i < z.size(); i++) {
            final int s = this.latents.indexOf(z.get(i));
            final int m = this.clustering.get(s).size() / 2;
            nA += m;
            nB += m;
        }
        final int[] A = new int[nA];
        final int[] B = new int[nB];
        int a = 0;
        int b = 0;
        for (int i = 0; i < this.clustering.get(xi).size(); i++) {
            A[i] = this.variables.indexOf(this.clustering.get(xi).get(i));
            a++;
        }
        for (int i = 0; i < this.clustering.get(yi).size(); i++) {
            B[i] = this.variables.indexOf(this.clustering.get(yi).get(i));
            b++;
        }
        for (int i = 0; i < z.size(); i++) {
            final int s = this.latents.indexOf(z.get(i));
            final int m = this.clustering.get(s).size() / 2;
            for (int j = 1; j <= m; j++) {
                A[a] = this.variables.indexOf(this.clustering.get(s).get(j - 1));
                a++;
                B[b] = this.variables.indexOf(this.clustering.get(s).get(m + j - 1));
                b++;
            }
        }


        //With one indicator per latent per set.
//        int a = variables.indexOf(clustering.get(xi).get(0));
//        int b = variables.indexOf(clustering.get(yi).get(0));
//        int[] A = new int[1+z.size()];
//        int[] B = new int[1+z.size()];
//        A[0] = a;
//        B[0] = b;
//        for (int i = 1; i <= z.size(); i++) {
//            int s = latents.indexOf(z.get(i-1));
//            A[i] = variables.indexOf(clustering.get(s).get(0));
//            B[i] = variables.indexOf(clustering.get(s).get(1));
//        }

        final double[][] CovMatrix = this.covMatrix.getMatrix().toArray();

        //double[][] m = covMatrix.getMatrix().getSelection(rows, cols).toArray();


        final int rank = new EstimateRank().Estimate(A, B, CovMatrix, n, this.alpha);
        final boolean independent = rank <= z.size();
//        System.out.println("A: "+Arrays.toString(A));
//        System.out.println("B: "+Arrays.toString(A));
//        System.out.println("Rank: "+rank);
        return independent;
    }

    public boolean isIndependent(final Node x, final Node y, final Node... z) {
        return isIndependent(x, y, Arrays.asList(z));
    }

    public boolean isDependent(final Node x, final Node y, final List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(final Node x, final Node y, final Node... z) {
        final List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    public void setAlpha(final double alpha) {
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
        return this.latents;
    }

    /**
     * @return the variable with the given name.
     */
    public Node getVariable(final String name) {
        return this.nameMap.get(name);
    }

    /**
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        final List<Node> variables = getVariables();
        final List<String> variableNames = new ArrayList<>();
        for (final Node variable1 : variables) {
            variableNames.add(variable1.getName());
        }
        return variableNames;
    }

    /**
     * If <code>isDeterminismAllowed()</code>, deters to IndTestFisherZD; otherwise throws
     * UnsupportedOperationException.
     */
    public boolean determines(final List<Node> z, final Node x) throws UnsupportedOperationException {
        final int[] parents = new int[z.size()];

        for (int j = 0; j < parents.length; j++) {
            parents[j] = this.covMatrix.getVariables().indexOf(z.get(j));
        }

        final int i = this.covMatrix.getVariables().indexOf(x);

        final Matrix matrix2D = this.covMatrix.getMatrix();
        double variance = matrix2D.get(i, i);

        if (parents.length > 0) {

            // Regress z onto i, yielding regression coefficients b.
            final Matrix Czz = matrix2D.getSelection(parents, parents);
            final Matrix inverse;

            try {
                inverse = Czz.inverse();
            } catch (final Exception e) {
                return true;
            }

            Vector Cyz = matrix2D.getColumn(i);
            Cyz = Cyz.viewSelection(parents);
            final Vector b = inverse.times(Cyz);

            variance -= Cyz.dotProduct(b);
        }

        return variance < 1e-20;
    }

    /**
     * @return the data set being analyzed.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    public void shuffleVariables() {
        final ArrayList<Node> nodes = new ArrayList<>(this.variables);
        Collections.shuffle(nodes);
        this.variables = Collections.unmodifiableList(nodes);
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "t-Separation test, alpha = " + IndTestTrekSep.nf.format(getAlpha());
    }

    public void setPValueLogger(final PrintStream pValueLogger) {
        this.pValueLogger = pValueLogger;
    }

    //==========================PRIVATE METHODS============================//

    private int sampleSize() {
        return covMatrix().getSampleSize();
    }

    private ICovarianceMatrix covMatrix() {
        return this.covMatrix;
    }

    private Map<String, Node> nameMap(final List<Node> variables) {
        final Map<String, Node> nameMap = new ConcurrentHashMap<>();

        for (final Node node : variables) {
            nameMap.put(node.getName(), node);
        }

        return nameMap;
    }

    private Map<Node, Integer> indexMap(final List<Node> variables, final List<Node> latents) {
        final Map<Node, Integer> indexMap = new ConcurrentHashMap<>();

        int index = 0;

        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i), index++);
        }

        for (int i = 0; i < latents.size(); i++) {
            indexMap.put(latents.get(i), index++);
        }

        return indexMap;
    }

    public void setVariables(final List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<>(variables);
        this.covMatrix.setVariables(variables);
    }

    public ICovarianceMatrix getCov() {
        return this.covMatrix;
    }

    @Override
    public List<DataSet> getDataSets() {

        final List<DataSet> dataSets = new ArrayList<>();

        dataSets.add(this.dataSet);

        return dataSets;
    }

    @Override
    public int getSampleSize() {
        return this.covMatrix.getSampleSize();
    }

    @Override
    public List<Matrix> getCovMatrices() {
        return null;
    }

    @Override
    public double getScore() {
        return getPValue();
    }

    public TDistribution gettDistribution() {
        return this.tDistribution;
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }
}



