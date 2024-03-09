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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Performs a test of conditional independence X _||_ Y | Z1...Zn where all searchVariables are either continuous or
 * discrete. This test is valid for both ordinal and non-ordinal discrete searchVariables.
 * <p>
 * This logisticRegression makes multiple assumptions: 1. IIA 2. Large sample size (multiple regressions needed on
 * subsets of sample)
 *
 * @author josephramsey
 * @author Augustus Mayo.
 * @version $Id: $Id
 */
public class IndTestMultinomialLogisticRegression implements IndependenceTest {
    /**
     * The original data set.
     */
    private final DataSet originalData;
    /**
     * The searchVariables over which this independence checker is capable of determining independence relations.
     */
    private final List<Node> searchVariables;
    /**
     * This variable represents a DataSet object that holds the internal data used in the class.
     */
    private final DataSet internalData;
    /**
     * A private final map that stores the variables per node in a HashMap. The keys of the map are instances of Node
     * class, and the values are the corresponding list of Node objects. This map is used in the class
     * IndTestMultinomialLogisticRegression.
     */
    private final Map<Node, List<Node>> variablesPerNode = new HashMap<>();
    /**
     * A private final LogisticRegression object that is used in the class IndTestMultinomialLogisticRegression.
     */
    private final LogisticRegression logisticRegression;
    /**
     * A private final RegressionDataset object that is used in the class IndTestMultinomialLogisticRegression.
     */
    private final RegressionDataset regression;
    /**
     * Private field to hold an array of integers representing rows.
     */
    private int[] _rows;
    /**
     * A private double field that holds the value of alpha.
     */
    private double alpha;
    /**
     * A private double field that holds the value of the last p-value.
     */
    private double lastP;
    /**
     * A private boolean field that holds the value of verbose.
     */
    private boolean verbose;

    /**
     * <p>Constructor for IndTestMultinomialLogisticRegression.</p>
     *
     * @param data  a {@link edu.cmu.tetrad.data.DataSet} object
     * @param alpha a double
     */
    public IndTestMultinomialLogisticRegression(DataSet data, double alpha) {
        this.searchVariables = data.getVariables();
        this.originalData = data.copy();
        DataSet internalData = data.copy();
        this.alpha = alpha;

        List<Node> variables = internalData.getVariables();

        for (Node node : variables) {
            List<Node> nodes = expandVariable(internalData, node);
            this.variablesPerNode.put(node, nodes);
        }

        this.internalData = internalData;
        this.logisticRegression = new LogisticRegression(internalData);
        this.regression = new RegressionDataset(internalData);
    }

    /**
     * Performs an independence test for a sublist of variables.
     *
     * @param vars The sublist of variables.
     * @return An object of type IndependenceTest.
     * @throws UnsupportedOperationException if the independence subset feature is not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks for independence between two nodes, given a set of conditioning nodes.
     *
     * @param x The first node.
     * @param y The second node.
     * @param z The set of conditioning nodes.
     * @return An object of type IndependenceResult indicating the independence relationship between x and y, given z.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        if (x instanceof DiscreteVariable) {
            return isIndependentMultinomialLogisticRegression(x, y, z);
        } else if (y instanceof DiscreteVariable) {
            return isIndependentMultinomialLogisticRegression(y, x, z);
        } else {
            return isIndependentRegression(x, y, z);
        }
    }

    private List<Node> expandVariable(DataSet dataSet, Node node) {
        if (node instanceof ContinuousVariable) {
            return Collections.singletonList(node);
        }

        if (node instanceof DiscreteVariable && ((DiscreteVariable) node).getNumCategories() < 3) {
            return Collections.singletonList(node);
        }

        if (!(node instanceof DiscreteVariable)) {
            throw new IllegalArgumentException();
        }

        List<String> varCats = new ArrayList<>(((DiscreteVariable) node).getCategories());
        varCats.remove(0);
        List<Node> variables = new ArrayList<>();

        for (String cat : varCats) {

            Node newVar;

            do {
                String newVarName = node.getName() + "MULTINOM" + "." + cat;
                newVar = new DiscreteVariable(newVarName, 2);
            } while (dataSet.getVariable(newVar.getName()) != null);

            variables.add(newVar);

            dataSet.addVariable(newVar);
            int newVarIndex = dataSet.getColumn(newVar);
            int numCases = dataSet.getNumRows();

            for (int l = 0; l < numCases; l++) {
                Object dataCell = dataSet.getObject(l, dataSet.getColumn(node));
                int dataCellIndex = ((DiscreteVariable) node).getIndex(dataCell.toString());

                if (dataCellIndex == ((DiscreteVariable) node).getIndex(cat))
                    dataSet.setInt(l, newVarIndex, 1);
                else
                    dataSet.setInt(l, newVarIndex, 0);
            }
        }

        return variables;
    }

    private IndependenceResult isIndependentMultinomialLogisticRegression(Node x, Node y, Set<Node> z) {
        if (!this.variablesPerNode.containsKey(x)) {
            throw new IllegalArgumentException("Unrecogized node: " + x);
        }

        if (!this.variablesPerNode.containsKey(y)) {
            throw new IllegalArgumentException("Unrecogized node: " + y);
        }

        for (Node node : z) {
            if (!this.variablesPerNode.containsKey(x)) {
                throw new IllegalArgumentException("Unrecogized node: " + node);
            }
        }

        List<Double> pValues = new ArrayList<>();

        int[] _rows = getNonMissingRows();
        this.logisticRegression.setRows(_rows);

        for (Node _x : this.variablesPerNode.get(x)) {

            // Without y
            List<Node> regressors0 = new ArrayList<>();

            for (Node _z : z) {
                regressors0.addAll(this.variablesPerNode.get(_z));
            }

            LogisticRegression.Result result0 = logisticRegression.regress((DiscreteVariable) _x, regressors0);

            // With y.
            List<Node> regressors1 = new ArrayList<>(variablesPerNode.get(y));

            for (Node _z : z) {
                regressors1.addAll(variablesPerNode.get(_z));
            }

            LogisticRegression.Result result1 = this.logisticRegression.regress((DiscreteVariable) _x, regressors1);

            // Returns -2 LL
            double ll0 = result0.getLogLikelihood();
            double ll1 = result1.getLogLikelihood();

            double chisq = (ll0 - ll1);
            int df = this.variablesPerNode.get(y).size();
            double p = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(chisq);

            if (Double.isNaN(p)) {
                throw new RuntimeException("Undefined p-value encountered when testing " +
                        LogUtilsSearch.independenceFact(x, y, z));
            }

            pValues.add(p);
        }

        double p = 1.0;

        // Choose the minimum of the p-values
        // This is only one method that can be used, this requires every coefficient to be significant
        for (double val : pValues) {
            if (val < p) p = val;
        }

        boolean independent = p > this.alpha;

        this.lastP = p;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, z, p));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, p, alpha - p);
    }

    private int[] getNonMissingRows() {
        if (this._rows == null) {
            this._rows = new int[this.internalData.getNumRows()];
            for (int k = 0; k < this._rows.length; k++) this._rows[k] = k;
        }

        return this._rows;
    }

    private IndependenceResult isIndependentRegression(Node x, Node y, Set<Node> z) {
        if (!this.variablesPerNode.containsKey(x)) {
            throw new IllegalArgumentException("Unrecogized node: " + x);
        }

        if (!this.variablesPerNode.containsKey(y)) {
            throw new IllegalArgumentException("Unrecogized node: " + y);
        }

        for (Node node : z) {
            if (!this.variablesPerNode.containsKey(x)) {
                throw new IllegalArgumentException("Unrecogized node: " + node);
            }
        }

        List<Node> regressors = new ArrayList<>();
        regressors.add(this.internalData.getVariable(y.getName()));

        for (Node _z : z) {
            regressors.addAll(this.variablesPerNode.get(_z));
        }

        int[] _rows = getNonMissingRows();
        this.regression.setRows(_rows);

        RegressionResult result;

        try {
            result = this.regression.regress(x, regressors);
        } catch (Exception e) {
            return new IndependenceResult(new IndependenceFact(x, y, z), false, Double.NaN, Double.NaN);
        }

        double p = result.getP()[1];
        this.lastP = p;

        if (Double.isNaN(p)) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                    LogUtilsSearch.independenceFact(x, y, z));
        }

        boolean indep = p > this.alpha;

        if (this.verbose) {
            if (indep) {
                String message = LogUtilsSearch.independenceFactMsg(x, y, z, p);
                TetradLogger.getInstance().forceLogMessage(message);
            } else {
                String message = LogUtilsSearch.dependenceFactMsg(x, y, z, p);
                TetradLogger.getInstance().forceLogMessage(message);
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), indep, p, alpha - p);
    }

    /**
     * <p>getPValue.</p>
     *
     * @return the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for tis test.
     */
    public double getPValue() {
        return this.lastP; //STUB
    }

    /**
     * <p>getVariables.</p>
     *
     * @return the list of searchVariables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return this.searchVariables; // Make sure the variables from the ORIGINAL data set are returned, not the modified dataset!
    }


    /**
     * Determines if Node y is determined by the given list of Nodes z.
     *
     * @param z The list of Nodes to condition on.
     * @param y The Node to be determined.
     * @return True if y is determined by z, false otherwise.
     */
    public boolean determines(List<Node> z, Node y) {
        return false; //stub
    }

    /**
     * Retrieves the significance level of the independence test.
     *
     * @return The significance level.
     */
    public double getAlpha() {
        return this.alpha; //STUB
    }

    /**
     * Sets the significance level for the independence test.
     *
     * @param alpha The significance level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Retrieves the original data used for the independence test.
     *
     * @return The original DataSet object used for the independence test.
     */
    public DataSet getData() {
        return this.originalData;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Multinomial Logistic Regression, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns true if the test prints verbose output.
     *
     * @return True if the case.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether this test will print verbose output.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}


