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
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.util.FastMath;

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
public class IndTestMixedMultipleTTest implements IndependenceTest {
    /**
     * The original data set.
     */
    private final DataSet originalData;
    /**
     * The searchVariables.
     */
    private final List<Node> searchVariables;
    /**
     * The modified data set.
     */
    private final DataSet internalData;
    /**
     * A map from searchVariables to their dummy variables.
     */
    private final Map<Node, List<Node>> variablesPerNode = new HashMap<>();
    /**
     * The logistic regression.
     */
    private final LogisticRegression logisticRegression;
    /**
     * The regression.
     */
    private final RegressionDataset regression;
    /**
     * The rows.
     */
    private int[] _rows;
    /**
     * The significance level of the test.
     */
    private double alpha;
    /**
     * The probability associated with the most recently executed independence test.
     */
    private double lastP;
    /**
     * Whether verbose output should be printed.
     */
    private boolean verbose;
    /**
     * Represents a boolean flag indicating whether linear dependencies should be preferred in the independence test. If
     * set to true, the test will prioritize linear dependencies over other types of dependencies. If set to false, the
     * test will consider all types of dependencies equally.
     */
    private boolean preferLinear = true;

    /**
     * <p>Constructor for IndTestMixedMultipleTTest.</p>
     *
     * @param data  a {@link edu.cmu.tetrad.data.DataSet} object
     * @param alpha a double
     */
    public IndTestMixedMultipleTTest(DataSet data, double alpha) {
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
     * <p>Setter for the field <code>preferLinear</code>.</p>
     *
     * @param preferLinear a boolean
     */
    public void setPreferLinear(boolean preferLinear) {
        this.preferLinear = preferLinear;
    }

    /**
     * @param vars The sublist of variables.
     * @return an IndependenceTest object
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks for independence between two nodes.
     *
     * @param x the first node to check independence for
     * @param y the second node to check independence for
     * @param z a set of conditioning nodes
     * @return the result of the independence test
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        if (x instanceof DiscreteVariable && y instanceof DiscreteVariable) {
            return isIndependentMultinomialLogisticRegression(x, y, z);
        } else if (x instanceof DiscreteVariable) {
            if (this.preferLinear) {
                return isIndependentRegression(y, x, z);
            } else {
                return isIndependentMultinomialLogisticRegression(x, y, z);
            }
        } else {
            if (y instanceof DiscreteVariable && !this.preferLinear) {
                return isIndependentMultinomialLogisticRegression(y, x, z);
            } else {
                return isIndependentRegression(x, y, z);
            }
        }
    }

    /**
     * Returns the p-value from the last independence test.
     *
     * @return the p-value
     */
    public double getPValue() {
        return this.lastP; //STUB
    }

    /**
     * Retrieves the list of variables used in the original data set. Note that it returns the variables from the
     * original data set, not the modified dataset.
     *
     * @return The list of variables from the original data set.
     */
    public List<Node> getVariables() {
        return this.searchVariables; // Make sure the variables from the ORIGINAL data set are returned, not the modified dataset!
    }

    /**
     * Determines if a given set of nodes z determines the node y.
     *
     * @param z The set of nodes to check if they determine y.
     * @param y The node to check if it is determined by z.
     * @return true if z determines y, false otherwise.
     * @throws java.lang.UnsupportedOperationException since not implemented.
     */
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    /**
     * @throws java.lang.UnsupportedOperationException since not implemented.
     */
    public double getAlpha() {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    /**
     * Sets the significance level for the independence test.
     *
     * @param alpha This level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returne the original data for the method.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.originalData;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return This.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Multinomial Logistic Regression, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns whether verbose output should be printed.
     *
     * @return This.
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

        // first category is reference
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

    private double[] dependencePvalsLogit(Node x, Node y, Set<Node> z) {
        if (!this.variablesPerNode.containsKey(x)) {
            throw new IllegalArgumentException("Unrecogized node: " + x);
        }

        if (!this.variablesPerNode.containsKey(y)) {
            throw new IllegalArgumentException("Unrecogized node: " + y);
        }

        for (Node node : z) {
            if (!this.variablesPerNode.containsKey(node)) {
                throw new IllegalArgumentException("Unrecogized node: " + node);
            }
        }

        int[] _rows = getNonMissingRows();
        this.logisticRegression.setRows(_rows);

        List<Node> yzList = new ArrayList<>();
        yzList.add(y);
        yzList.addAll(z);
        //List<Node> zList = new ArrayList<>();

        List<Node> yzDumList = new ArrayList<>(this.variablesPerNode.get(y));
        for (Node _z : z) {
            yzDumList.addAll(this.variablesPerNode.get(_z));
            //zList.addAll(variablesPerNode.get(_z));
        }

        double[] sumLnP = new double[yzList.size()];

        for (int i = 0; i < this.variablesPerNode.get(x).size(); i++) {
            Node _x = this.variablesPerNode.get(x).get(i);

            LogisticRegression.Result result1 = this.logisticRegression.regress((DiscreteVariable) _x, yzDumList);

            int n = this.originalData.getNumRows();
            int k = yzDumList.size();

            //skip intercept at index 0
            int coefIndex = 1;
            for (int j = 0; j < yzList.size(); j++) {
                for (int dum = 0; dum < this.variablesPerNode.get(yzList.get(j)).size(); dum++) {

                    double wald = FastMath.abs(result1.getCoefs()[coefIndex] / result1.getStdErrs()[coefIndex]);
                    //double val = (1.0 - new NormalDistribution(0,1).cumulativeProbability(wald))*2;//two-tailed test
                    //double val = 1-result1.getProbs()[i+1];

                    //this is exactly the same test as the linear case
                    double val = (1.0 - ProbUtils.tCdf(wald, n - k)) * 2;
                    sumLnP[j] += FastMath.log(val);
                    coefIndex++;
                }
            }
        }

        double[] pVec = new double[sumLnP.length];
        for (int i = 0; i < pVec.length; i++) {
            if (sumLnP[i] == Double.NEGATIVE_INFINITY) pVec[i] = 0.0;
            else {
                int df = 2 * this.variablesPerNode.get(x).size() * this.variablesPerNode.get(yzList.get(i)).size();
//                pVec[i] = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(-2 * sumLnP[i]);
                pVec[i] = StatUtils.getChiSquareP(df, -2 * sumLnP[i]);
            }
        }

        return pVec;
    }

    private IndependenceResult isIndependentMultinomialLogisticRegression(Node x, Node y, Set<Node> z) {
        double p = dependencePvalsLogit(x, y, z)[0];
        boolean independent = p > this.alpha;
        //0 corresponds to y
        this.lastP = p;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFactMsg(x, y, z, getPValue()));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, p, alpha - p);
    }

    // This takes an inordinate amount of time. -jdramsey 20150929
    private int[] getNonMissingRows() {

        if (this._rows == null) {
            this._rows = new int[this.internalData.getNumRows()];
            for (int k = 0; k < this._rows.length; k++) this._rows[k] = k;
        }

        return this._rows;
    }

    private double[] dependencePvalsLinear(Node x, Node y, Set<Node> z) {
        if (!this.variablesPerNode.containsKey(x)) {
            throw new IllegalArgumentException("Unrecogized node: " + x);
        }

        if (!this.variablesPerNode.containsKey(y)) {
            throw new IllegalArgumentException("Unrecogized node: " + y);
        }

        for (Node node : z) {
            if (!this.variablesPerNode.containsKey(node)) {
                throw new IllegalArgumentException("Unrecogized node: " + node);
            }
        }

        List<Node> yzList = new ArrayList<>();
        yzList.add(y);
        yzList.addAll(z);
        //List<Node> zList = new ArrayList<>();

        List<Node> yzDumList = new ArrayList<>(this.variablesPerNode.get(y));
        for (Node _z : z) {
            yzDumList.addAll(this.variablesPerNode.get(_z));
            //zList.addAll(variablesPerNode.get(_z));
        }

        int[] _rows = getNonMissingRows();
        this.regression.setRows(_rows);

        RegressionResult result;

        try {
            result = this.regression.regress(x, yzDumList);
        } catch (Exception e) {
            return null;
        }

        double[] pVec = new double[yzList.size()];
        double[] pCoef = result.getP();

        //skip intercept at 0
        int coeffInd = 1;

        for (int i = 0; i < pVec.length; i++) {
            List<Node> curDummy = this.variablesPerNode.get(yzList.get(i));
            if (curDummy.size() == 1) {
                pVec[i] = pCoef[coeffInd];
                coeffInd++;
                continue;
            } else {
                pVec[i] = 0;
            }

            for (Node ignored : curDummy) {
                pVec[i] += FastMath.log(pCoef[coeffInd]);
                coeffInd++;
            }

            if (pVec[i] == Double.NEGATIVE_INFINITY)
                pVec[i] = 0.0;
            else
//                pVec[i] = 1.0 - new ChiSquaredDistribution(2 * curDummy.size()).cumulativeProbability(-2 * pVec[i]);
                pVec[i] = StatUtils.getChiSquareP(2 * curDummy.size(), -2 * pVec[i]);
        }

        return pVec;
    }

    private IndependenceResult isIndependentRegression(Node x, Node y, Set<Node> z) {
        double p = Objects.requireNonNull(dependencePvalsLinear(x, y, z))[0];
        //result.getP()[1];
        this.lastP = p;

        boolean independent = p > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFactMsg(x, y, z, getPValue()));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, p, alpha - p);
    }
}
