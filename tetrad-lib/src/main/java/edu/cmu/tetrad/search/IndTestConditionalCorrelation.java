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
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.text.NumberFormat;
import java.util.*;

import static edu.cmu.tetrad.util.MathUtils.logChoose;
import static java.lang.Math.exp;
import static java.lang.Math.log;

/**
 * Checks conditional independence of variable in a continuous data set using a conditional correlation test
 * for the nonlinear nonGaussian case.
 *
 * @author Joseph Ramsey
 */
public final class IndTestConditionalCorrelation implements IndependenceTest, ScoreForFact {

    /**
     * The instance of CCI that is wrapped.
     */
    private final ConditionalCorrelationIndependence cci;

    /**
     * The variables of the covariance data, in order. (Unmodifiable list.)
     */
    private final List<Node> variables;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * Formats as 0.0000.
     */
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the data set passed in through the constructor.
     */
    private final DataSet dataSet;

    /**
     * Map from nodes to the indices.
     */
    private final Map<Node, Integer> indices;

    /**
     * True iff the fast FDR adjustment should be made.
     */
    private boolean fastFDR = false;

    /**
     * True if verbose output should be printed.
     */
    private boolean verbose = false;
    private double score = Double.NaN;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation data implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The q level of the test.
     */
    public IndTestConditionalCorrelation(DataSet dataSet, double alpha) {
        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Q mut be in [0, 1]");
        }

        List<Node> nodes = dataSet.getVariables();

        this.variables = Collections.unmodifiableList(nodes);

        this.cci = new ConditionalCorrelationIndependence(dataSet, alpha);
        this.alpha = alpha;

        indices = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            indices.put(nodes.get(i), i);
        }

        this.dataSet = dataSet;
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new IndTestCramerT instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    public boolean isIndependent(Node x, Node y, List<Node> z) {
        String _x = x.getName();
        String _y = y.getName();
        List<String> _z = new ArrayList<>();
        for (Node node : z) _z.add(node.getName());

        if(fastFDR) {
            final int d1 = 0; // reference
            final int d2 = z.size();
            final int v = variables.size() - 2;

            double alpha2 = (exp(log(alpha) + logChoose(v, d1) - logChoose(v, d2)));

            cci.setAlpha(alpha2);
            double p = cci.isIndependent(_x, _y, _z);
            this.score = alpha2 - p;// cci.getScore();

            if (verbose) {
                IndependenceFact fact = new IndependenceFact(x, y, z);

                if (p > alpha2) {
                    final String s = fact + " INDEPENDENT p = " + p;
                    System.out.println(s);
                    TetradLogger.getInstance().log("info", s);

                } else {
                    final String s = fact + " dependent p = " + p;
                    System.out.println(s);
                    TetradLogger.getInstance().log("info", s);
                }
            }

            return p > alpha2;
        } else {
            cci.setAlpha(alpha);
            double p = cci.isIndependent(_x, _y, _z);
            this.score = alpha - p;// cci.getScore();

            if (verbose) {
                IndependenceFact fact = new IndependenceFact(x, y, z);

                if (p > alpha) {
                    final String s = fact + " INDEPENDENT p = " + p;
                    System.out.println(s);
                    TetradLogger.getInstance().log("info", s);

                } else {
                    final String s = fact + " dependent p = " + p;
                    System.out.println(s);
                    TetradLogger.getInstance().log("info", s);
                }
            }

            return p > alpha;
        }
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        return isIndependent(x, y, Arrays.asList(z));
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        String _x = x.getName();
        String _y = y.getName();
        List<String> _z = new ArrayList<>();
        for (Node node : z) _z.add(node.getName());
        double p = cci.isIndependent(_x, _y, _z);

//        if (verbose) {
//            if (independent) {
//                TetradLogger.getInstance().log("independencies",
//                        SearchLogUtils.independenceFactMsg(x, y, z, getPValue()));
//            } else {
//                TetradLogger.getInstance().log("dependencies",
//                        SearchLogUtils.dependenceFactMsg(x, y, z, getPValue()));
//            }
//        }

        return p < alpha;
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    public double getPValue() {
        return cci.getPValue();
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
        cci.setAlpha(alpha);
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
        for (Node node : variables) {
            if (node.getName().equals(name)) return node;
        }

        throw new IllegalArgumentException();
    }

    /**
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> variableNames = new ArrayList<>();
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
        throw new UnsupportedOperationException();
    }

    /**
     * @return the data set being analyzed.
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

    @Override
    public double getScore() {
        return score;
    }

    @Override
    public double getScoreForFact(IndependenceFact fact) {
        List<String> zNames = new ArrayList<>();
        for (Node node : fact.getZ()) zNames.add(node.getName());
        double p = cci.isIndependent(fact.getX().getName(), fact.getY().getName(), zNames);
        double score = alpha - p;// cci.getScore();
        this.score = score;
        return score;
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "Conditional Correlation, q = " + nf.format(getAlpha());
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Number of functions to use in (truncated) basis.
     */
    public int getNumFunctions() {
        return this.cci.getNumFunctions();
    }

    public void setNumFunctions(int numFunctions) {
        this.cci.setNumFunctions(numFunctions);
    }

    public double getWeight() {
        return this.cci.getWidth();
    }

    public void setKernelMultiplier(double multiplier) {
        this.cci.setWidth(multiplier);
    }

    public void setKernel(ConditionalCorrelationIndependence.Kernel kernel) {
        cci.setKernelMultiplier(kernel);
    }

    public void setBasis(ConditionalCorrelationIndependence.Basis basis) {
        cci.setBasis(basis);
    }

    public void setFastFDR(boolean fastFDR) {
        this.fastFDR = fastFDR;
    }

    public void setKernelRegressionSampleSize(int i) {
        cci.setKernelRegressionSampleSize(i);
    }

    public void setEarlyReturn(boolean b) {
        cci.setEarlyReturn(b);
    }

    public void setNumDependenceSpotChecks(int numDependenceSpotChecks) {
        cci.setNumDependenceSpotChecks(numDependenceSpotChecks);
    }
}



