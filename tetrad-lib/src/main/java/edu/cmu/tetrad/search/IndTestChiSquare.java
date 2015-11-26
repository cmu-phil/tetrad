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
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.text.NumberFormat;
import java.util.*;

/**
 * Checks the conditional independence X _||_ Y | S, where S is a set of discrete variable, and X and Y are discrete
 * variable not in S, by applying a conditional Chi Square test. A description of such a test is given in Fienberg, "The
 * Analysis of Cross-Classified Categorical Data," 2nd edition. The formula for degrees of freedom used in this test are
 * equivalent to the formulation on page 142 of Fienberg.
 *
 * @author Joseph Ramsey
 * @see ChiSquareTest
 */
public final class IndTestChiSquare implements IndependenceTest {

    /**
     * The X Square tester.
     */
    private final ChiSquareTest chiSquareTest;

    /**
     * The variables in the discrete data sets for which conditional independence judgements are desired.
     */
    private final List<Node> variables;

    /**
     * The dataset of discrete variables.
     */
    private final DataSet dataSet;

    /**
     * The G Square value associted with a particular call of isIndependent. Set in that method and not in the
     * constructor.
     */
    private double xSquare;

    /**
     * The degrees of freedom associated with a particular call of isIndependent. Set in the method and not in the
     * constructor.
     */
    private int df;

    /**
     */
    private double pValue;

    /**
     * The lower bound of percentages of observation of some category in the data, given some particular combination of
     * values of conditioning variables, that coefs as 'determining."
     */
    private double determinationP = 0.99;
    private HashSet<IndependenceFact> facts;

    /**
     * Constructs a new independence checker to check conditional independence facts for discrete data using a g square
     * test.
     *
     * @param dataSet the discrete data set.
     * @param alpha   the significance level of the tests.
     */
    public IndTestChiSquare(DataSet dataSet, double alpha) {

        // The g square test requires as parameters: (a) the data set
        // itself, (b) an array containing the number of values for
        // each variable in order, and (c) the significance level of
        // the test. Also, in order to perform specific conditional
        // independence tests, it is necessary to construct an array
        // containing the variables of the requested test, in
        // order. Specifically, to test whether X _||_ Y | Z1, ...,
        // Zn, an array is constructed with the indices, in order of
        // X, Y, Z1, ..., Zn. Therefore, the indices of these
        // variables must be stored. We do this by storing the
        // variables themselves in a List.
        this.dataSet = dataSet;

        this.variables = new ArrayList<Node>(dataSet.getVariables());

        int[] numVals = new int[this.variables.size()];

        for (int i = 0; i < this.variables.size(); i++) {
            DiscreteVariable v = (DiscreteVariable) (this.variables.get(i));
            numVals[i] = v.getNumCategories();
        }

        this.chiSquareTest = new ChiSquareTest(dataSet, alpha);
    }

    /**
     * Creates a new IndTestChiSquare for a subset of the nodes.
     */
    public IndependenceTest indTestSubset(List<Node> nodes) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (Node variable : nodes) {
            if (!this.variables.contains(variable)) {
                throw new IllegalArgumentException(
                        "All nodes must be original nodes");
            }
        }

        int[] indices = new int[nodes.size()];
        int j = -1;

        for (int i = 0; i < this.variables.size(); i++) {
            if (!nodes.contains(this.variables.get(i))) {
                continue;
            }

            indices[++j] = i;
        }

        DataSet newDataSet = dataSet.subsetColumns(indices);
        double alpha = this.chiSquareTest.getAlpha();
        return new IndTestChiSquare(newDataSet, alpha);
    }

    /**
     * @return the value of the G Square statistic associated with the most recent call of isIndependent.
     *
     * @return the G Square value.
     */
    public double getXSquare() {
        return xSquare;
    }

    /**
     * @return degrees of freedom associated with the most recent call of isIndependent
     *
     * @ return degrees of freedom
     */
    public int getDf() {
        return df;
    }

    /**
     * @return the p value associated with the most recent call of isIndependent.
     */
    public double getPValue() {
        return pValue;
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning varNames z.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning varNames.
     * @return true iff x _||_ y | z.
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        if (z == null) {
            throw new NullPointerException();
        }

        for (Node v : z) {
            if (v == null) {
                throw new NullPointerException();
            }
        }

        // For testing x, y given z1,...,zn, set up an array of length
        // n + 2 containing the indices of these variables in order.
        int[] testIndices = new int[2 + z.size()];

        testIndices[0] = variables.indexOf(x);
        testIndices[1] = variables.indexOf(y);

        for (int i = 0; i < z.size(); i++) {
            testIndices[i + 2] = variables.indexOf(z.get(i));
        }

        // the following is lame code--need a better test
        for (int i = 0; i < testIndices.length; i++) {
            if (testIndices[i] < 0) {
                throw new IllegalArgumentException("Variable " + i +
                        " was not used in the constructor.");
            }
        }

        ChiSquareTest.Result result = chiSquareTest.calcChiSquare(testIndices);
        this.xSquare = result.getXSquare();
        this.df = result.getDf();
        this.pValue = result.getPValue();

        if (result.isIndep()) {
            StringBuilder sb = new StringBuilder();
            sb.append("INDEPENDENCE ACCEPTED: ");
            sb.append(SearchLogUtils.independenceFact(x, y, z));
            sb.append("\tp = ").append(nf.format(result.getPValue())).append(
                    "\tx^2 = ").append(nf.format(result.getXSquare())).append(
                    "\tdf = ").append(result.getDf());

            TetradLogger.getInstance().log("independencies", sb.toString());
        }
//        else {
//            StringBuilder sb = new StringBuilder();
//            sb.append("Not independent: ");
//            sb.append(SearchLogUtils.independenceFact(x, y, z));
//            sb.append("\tp = ").append(nf.format(result.getLikelihoodRatioP())).append(
//                    "\tx^2 = ").append(nf.format(result.getXSquare())).append(
//                    "\tdf = ").append(result.getDof());
//            TetradLogger.getInstance().independenceDetails(sb.toString());
//        }

        if (facts != null) {
            this.facts.add(new IndependenceFact(x, y, z));
        }

        return result.isIndep();
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
     * @return true iff z determines x or z, with lower bound percentage p. Taht is, iff for either v = x or v = y, the
     * percentage p of some category c of y is >= p, for each assignment of values of z1,...,zn.
     *
     * @param z  The list of variables z1,...,zn with respect to which we want to know whether z determines x oir z.
     * @param x1 The one variable whose determination by z we want to know.
     * @return true if it is estimated that z determines x or z determines y.
     */
    public boolean determines(List<Node> z, Node x1) {
        if (z == null) {
            throw new NullPointerException();
        }

        for (Node aZ : z) {
            if (aZ == null) {
                throw new NullPointerException();
            }
        }

        // For testing x, y given z1,...,zn, set up an array of length
        // n + 2 containing the indices of these variables in order.
        int[] testIndices = new int[1 + z.size()];
        testIndices[0] = variables.indexOf(x1);

        for (int i = 0; i < z.size(); i++) {
            testIndices[i + 1] = variables.indexOf(z.get(i));
        }

        // the following is lame code--need a better test
        for (int i = 0; i < testIndices.length; i++) {
            if (testIndices[i] < 0) {
                throw new IllegalArgumentException(
                        "Variable " + i + "was not used in the constructor.");
            }
        }

        //        System.out.println("Testing " + x + " _||_ " + y + " | " + z);

        boolean countDetermined =
                chiSquareTest.isDetermined(testIndices, getDeterminationP());

        if (countDetermined) {
            StringBuilder sb = new StringBuilder();
            sb.append("Determination found: ").append(x1).append(
                    " is determined by {");

            for (int i = 0; i < z.size(); i++) {
                sb.append(z.get(i));

                if (i < z.size() - 1) {
                    sb.append(", ");
                }
            }

            sb.append("}");

            TetradLogger.getInstance().log("independencies", sb.toString());
        }

        return countDetermined;
    }

    public double getAlpha() {
        return chiSquareTest.getAlpha();
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     *
     * @param alpha the new significance level.
     */
    public void setAlpha(double alpha) {
        this.chiSquareTest.setAlpha(alpha);
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return Collections.unmodifiableList(variables);
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

    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        return null;
    }

    public String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        return "Chi Square, alpha = " + nf.format(getAlpha());
    }

    private double getDeterminationP() {
        return determinationP;
    }

    public void setDeterminationP(double determinationP) {
        this.determinationP = determinationP;
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

    public void startRecordingFacts() {
        this.facts = new HashSet<IndependenceFact>();

    }

    public HashSet<IndependenceFact> getFacts() {
        return facts;
    }
}





