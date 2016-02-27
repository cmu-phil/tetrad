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
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
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
    private List<Node> latents;


    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private List<List<Node>> clustering;

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
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the dataset being analyzed.
     */
    private DataSet dataSet;

    private PrintStream pValueLogger;
    private Map<Node, Integer> indexMap;
    private Map<String, Node> nameMap;
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
//            this.covMatrix = new CovarianceMatrixOnTheFly(dataSet);
////        this._covMatrix = covMatrix.getMatrix();
//            List<Node> nodes = covMatrix.getVariables();
//
//            this.variables = Collections.unmodifiableList(nodes);
//            this.indexMap = indexMap(variables);
//            this.nameMap = nameMap(variables);
//            setParameter1(alpha);
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
//            setParameter1(alpha);
//        }

    /**
     * Constructs a new independence test that will determine conditional independence facts using the given correlation
     * matrix and the given significance level.
     */
    public IndTestTrekSep(ICovarianceMatrix covMatrix, double alpha, List<List<Node>> clustering, List<Node> latents) {
        this.clustering = clustering;
        this.covMatrix = covMatrix;
        this.variables = Collections.unmodifiableList(covMatrix.getVariables());
        this.indexMap = indexMap(variables, latents);
        this.nameMap = nameMap(variables);
        this.latents = latents;
        setAlpha(alpha);
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new independence test instance for a subset of the variables.
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
            indices[i] = indexMap.get(vars.get(i));
        }

        ICovarianceMatrix newCovMatrix = covMatrix.getSubmatrix(indices);

        double alphaNew = getAlpha();
        return new IndTestTrekSep(newCovMatrix, alphaNew, clustering, latents);
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
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        int n = sampleSize();
        int xi = latents.indexOf(x);
        int yi = latents.indexOf(y);
        int nA = clustering.get(xi).size();
        int nB = clustering.get(yi).size();
        for (int i = 0; i < z.size(); i++) {
            int s = latents.indexOf(z.get(i));
            int m = clustering.get(s).size() / 2;
            nA += m;
            nB += m;
        }
        int[] A = new int[nA];
        int[] B = new int[nB];
        int a = 0;
        int b = 0;
        for (int i = 0; i < clustering.get(xi).size(); i++) {
            A[i] = variables.indexOf(clustering.get(xi).get(i));
            a++;
        }
        for (int i = 0; i < clustering.get(yi).size(); i++) {
            B[i] = variables.indexOf(clustering.get(yi).get(i));
            b++;
        }
        for (int i = 0; i < z.size(); i++) {
            int s = latents.indexOf(z.get(i));
            int m = clustering.get(s).size() / 2;
            for (int j = 1; j <= m; j++) {
                A[a] = variables.indexOf(clustering.get(s).get(j - 1));
                a++;
                B[b] = variables.indexOf(clustering.get(s).get(m + j - 1));
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

        double[][] CovMatrix = covMatrix.getMatrix().toArray();

        //double[][] m = covMatrix.getMatrix().getSelection(rows, cols).toArray();


        int rank = new EstimateRank().Estimate(A, B, CovMatrix, n, alpha);
        boolean independent = rank <= z.size();
//        System.out.println("A: "+Arrays.toString(A));
//        System.out.println("B: "+Arrays.toString(A));
//        System.out.println("Rank: "+rank);
        return independent;
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
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        return pValue;
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
        return this.latents;
    }

    /**
     * @return the variable with the given name.
     */
    public Node getVariable(String name) {
        return nameMap.get(name);
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

    /**
     * If <code>isDeterminismAllowed()</code>, deters to IndTestFisherZD; otherwise throws
     * UnsupportedOperationException.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        int[] parents = new int[z.size()];

        for (int j = 0; j < parents.length; j++) {
            parents[j] = covMatrix.getVariables().indexOf(z.get(j));
        }

        int i = covMatrix.getVariables().indexOf(x);

        TetradMatrix matrix2D = covMatrix.getMatrix();
        double variance = matrix2D.get(i, i);

        if (parents.length > 0) {

            // Regress z onto i, yielding regression coefficients b.
            TetradMatrix Czz = matrix2D.getSelection(parents, parents);
            TetradMatrix inverse;

            try {
                inverse = Czz.inverse();
            } catch (Exception e) {
                return true;
            }

            TetradVector Cyz = matrix2D.getColumn(i);
            Cyz = Cyz.viewSelection(parents);
            TetradVector b = inverse.times(Cyz);

            variance -= Cyz.dotProduct(b);
        }

        return variance < 1e-20;
    }

    /**
     * @return the data set being analyzed.
     */
    public DataSet getData() {
        return dataSet;
    }

    public void shuffleVariables() {
        ArrayList<Node> nodes = new ArrayList<Node>(this.variables);
        Collections.shuffle(nodes);
        this.variables = Collections.unmodifiableList(nodes);
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "t-Separation test, alpha = " + nf.format(getAlpha());
    }

    public void setPValueLogger(PrintStream pValueLogger) {
        this.pValueLogger = pValueLogger;
    }

    //==========================PRIVATE METHODS============================//

    private int sampleSize() {
        return covMatrix().getSampleSize();
    }

    private ICovarianceMatrix covMatrix() {
        return covMatrix;
    }

    private Map<String, Node> nameMap(List<Node> variables) {
        Map<String, Node> nameMap = new ConcurrentHashMap<String, Node>();

        for (Node node : variables) {
            nameMap.put(node.getName(), node);
        }

        return nameMap;
    }

    private Map<Node, Integer> indexMap(List<Node> variables, List<Node> latents) {
        Map<Node, Integer> indexMap = new ConcurrentHashMap<Node, Integer>();

        int index = 0;

        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i), index++);
        }

        for (int i = 0; i < latents.size(); i++) {
            indexMap.put(latents.get(i), index++);
        }

        return indexMap;
    }

    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<Node>(variables);
        covMatrix.setVariables(variables);
    }

    public ICovarianceMatrix getCov() {
        return covMatrix;
    }

    @Override
    public List<DataSet> getDataSets() {

        List<DataSet> dataSets = new ArrayList<DataSet>();

        dataSets.add(dataSet);

        return dataSets;
    }

    @Override
    public int getSampleSize() {
        return covMatrix.getSampleSize();
    }

    @Override
    public List<TetradMatrix> getCovMatrices() {
        return null;
    }

    @Override
    public double getScore() {
        return getPValue();
    }

    public TDistribution gettDistribution() {
        return tDistribution;
    }
}




