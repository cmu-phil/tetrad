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

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Functions;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SearchLogUtils;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Performs a test of conditional independence X _||_ Y | Z1...Zn where all searchVariables are either continuous or discrete.
 * This test is valid for both ordinal and non-ordinal discrete searchVariables.
 * <p>
 * This logisticRegression makes multiple assumptions: 1. IIA 2. Large sample size (multiple regressions needed on subsets of
 * sample)
 *
 * @author Joseph Ramsey
 * @author Augustus Mayo.
 */
public class IndTestMixedMultipleTTest implements IndependenceTest {
    private DataSet originalData;
    private List<Node> searchVariables;
    private DataSet internalData;
    private double alpha;
    private double lastP;
    private Map<Node, List<Node>> variablesPerNode = new HashMap<>();
    private LogisticRegression logisticRegression;
    private RegressionDataset regression;
    private boolean verbose = false;
    private DoubleFactory2D factory2D = DoubleFactory2D.dense;
    private boolean flipLast;
    private boolean preferLinear = true;

    public IndTestMixedMultipleTTest(DataSet data, double alpha) {
        this.searchVariables = data.getVariables();
        this.originalData = data.copy();
        DataSet internalData = data.copy();
        this.alpha = alpha;

        List<Node> variables = internalData.getVariables();

        for (Node node : variables) {
            List<Node> nodes = expandVariable(internalData, node);
            variablesPerNode.put(node, nodes);
        }

        this.internalData = internalData;
        this.logisticRegression = new LogisticRegression(internalData);
        this.regression = new RegressionDataset(internalData);
    }

    public void setPreferLinear(boolean preferLinear){
        this.preferLinear = preferLinear;
    }

    /**
     * @return an Independence test for a subset of the searchVariables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true if the given independence question is judged true, false if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are searchVariables in the list returned by
     * getVariableNames().
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        if (x instanceof DiscreteVariable && y instanceof DiscreteVariable) {
            flipLast = false;
            return isIndependentMultinomialLogisticRegression(x, y, z);
        } else if (x instanceof DiscreteVariable) {
            if(preferLinear) {
                flipLast = true;
                return isIndependentRegression(y, x, z);
            } else {
                flipLast = false;
                return isIndependentMultinomialLogisticRegression(x, y, z);
            }
        } else {
            if(y instanceof DiscreteVariable && !preferLinear) {
                flipLast = true;
                return isIndependentMultinomialLogisticRegression(y, x, z);
            } else {
                flipLast = false;
                return isIndependentRegression(x, y, z);
            }
        }
    }

    public double[] dependencePvals(Node x, Node y, List<Node> z) {
        if (x instanceof DiscreteVariable && y instanceof DiscreteVariable) {
            flipLast = false;
            return dependencePvalsLogit(x, y, z);
        } else if (x instanceof DiscreteVariable) {
            if(preferLinear) {
                flipLast = true;
                return dependencePvalsLinear(y, x, z);
            } else {
                flipLast = false;
                return dependencePvalsLogit(x, y, z);
            }
        } else {
            if(y instanceof DiscreteVariable && !preferLinear) {
                flipLast = true;
                return dependencePvalsLogit(y, x, z);
            } else {
                flipLast = false;
                return dependencePvalsLinear(x, y, z);
            }
        }
    }

    public boolean getFlipLast(){
        return flipLast;
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

    private double[] dependencePvalsLogit(Node x, Node y, List<Node> z) {
        if (!variablesPerNode.containsKey(x)) {
            throw new IllegalArgumentException("Unrecogized node: " + x);
        }

        if (!variablesPerNode.containsKey(y)) {
            throw new IllegalArgumentException("Unrecogized node: " + y);
        }

        for (Node node : z) {
            if (!variablesPerNode.containsKey(node)) {
                throw new IllegalArgumentException("Unrecogized node: " + node);
            }
        }

        List<Double> pValues = new ArrayList<>();

        int[] _rows = getNonMissingRows(x, y, z);
        logisticRegression.setRows(_rows);

        List<Node> yzDumList = new ArrayList<>();
        List<Node> yzList = new ArrayList<>();
        yzList.add(y);
        yzList.addAll(z);
        //List<Node> zList = new ArrayList<>();

        yzDumList.addAll(variablesPerNode.get(y));
        for (Node _z : z) {
            yzDumList.addAll(variablesPerNode.get(_z));
            //zList.addAll(variablesPerNode.get(_z));
        }

        //double[][] coeffsDep = new double[variablesPerNode.get(x).size()][];
        //DoubleMatrix2D coeffsNull = DoubleFactory2D.dense.make(zList.size(), variablesPerNode.get(x).size());
        //DoubleMatrix2D coeffsDep = DoubleFactory2D.dense.make(yzDumList.size()+1, variablesPerNode.get(x).size());
        double[] sumLnP = new double[yzList.size()];
        for(int i = 0; i < sumLnP.length; i++)
            sumLnP[i] = 0.0;

        for (int i = 0; i < variablesPerNode.get(x).size(); i++) {
            Node _x = variablesPerNode.get(x).get(i);

            LogisticRegression.Result result1 = logisticRegression.regress((DiscreteVariable) _x, yzDumList);

            int n = originalData.getNumRows();
            int k = yzDumList.size();

            //skip intercept at index 0
            int coefIndex = 1;
            for (int j = 0; j < yzList.size(); j++) {
                for (int dum = 0; dum < variablesPerNode.get(yzList.get(j)).size(); dum++) {

                    double wald = Math.abs(result1.getCoefs()[coefIndex] / result1.getStdErrs()[coefIndex]);
                    //double val = (1.0 - new NormalDistribution(0,1).cumulativeProbability(wald))*2;//two-tailed test
                    //double val = 1-result1.getProbs()[i+1];

                    //this is exactly the same test as the linear case
                    double val = (1.0 - ProbUtils.tCdf(wald, n - k)) * 2;
                    //System.out.println(_x.getName() + "\t" + yzDumList.get(coefIndex-1).getName() + "\t" + val + "\t" + (n-k));
                    //if(val <= 0) System.out.println("Zero p-val t-test: p " + val + " stat " + wald + " k " + k + " n " + n);
                    sumLnP[j] += Math.log(val);
                    coefIndex++;
                }
            }
        }

        double[] pVec = new double[sumLnP.length];
        for (int i = 0; i < pVec.length; i++) {
            if(sumLnP[i]==Double.NEGATIVE_INFINITY) pVec[i] = 0.0;
            else {
                int df = 2 * variablesPerNode.get(x).size() * variablesPerNode.get(yzList.get(i)).size();
                pVec[i] = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(-2 * sumLnP[i]);
            }
        }

        return pVec;
    }

    private boolean isIndependentMultinomialLogisticRegression(Node x, Node y, List<Node> z) {
        double p = dependencePvalsLogit(x,y,z)[0];
        boolean indep = p > alpha;
        //0 corresponds to y
        this.lastP = p;

        if (indep) {
            TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFactMsg(x, y, z, p));
        } else {
            TetradLogger.getInstance().log("dependencies", SearchLogUtils.dependenceFactMsg(x, y, z, p));
        }

        return indep;
    }

    int[] _rows = null;

    // This takes an inordinate amount of time. -jdramsey 20150929
    private int[] getNonMissingRows(Node x, Node y, List<Node> z) {
//        List<Integer> rows = new ArrayList<Integer>();
//
//        I:
//        for (int i = 0; i < internalData.getNumRows(); i++) {
//            for (Node node : variablesPerNode.get(x)) {
//                if (isMissing(node, i)) continue I;
//            }
//
//            for (Node node : variablesPerNode.get(y)) {
//                if (isMissing(node, i)) continue I;
//            }
//
//            for (Node _z : z) {
//                for (Node node : variablesPerNode.get(_z)) {
//                    if (isMissing(node, i)) continue I;
//                }
//            }
//
//            rows.add(i);
//        }

//        int[] _rows = new int[rows.size()];
//        for (int k = 0; k < rows.size(); k++) _rows[k] = rows.get(k);

        if (_rows == null) {
            _rows = new int[internalData.getNumRows()];
            for (int k = 0; k < _rows.length; k++) _rows[k] = k;
        }

        return _rows;
    }

    private boolean isMissing(Node x, int i) {
        int j = internalData.getColumn(x);

        if (x instanceof DiscreteVariable) {
            int v = internalData.getInt(i, j);

            if (v == -99) {
                return true;
            }
        }

        if (x instanceof ContinuousVariable) {
            double v = internalData.getDouble(i, j);

            if (Double.isNaN(v)) {
                return true;
            }
        }

        return false;
    }

    private double multiLL(DoubleMatrix2D coeffs, Node dep, List<Node> indep){

        DoubleMatrix2D indepData = factory2D.make(internalData.subsetColumns(indep).getDoubleData().toArray());
        List<Node> depList = new ArrayList<>();
        depList.add(dep);
        DoubleMatrix2D depData = factory2D.make(internalData.subsetColumns(depList).getDoubleData().toArray());

        int N = indepData.rows();
        DoubleMatrix2D probs = Algebra.DEFAULT.mult(factory2D.appendColumns(factory2D.make(N, 1, 1.0), indepData), coeffs);

        probs = factory2D.appendColumns(factory2D.make(indepData.rows(), 1, 1.0), probs).assign(Functions.exp);
        double ll = 0;
        for(int i = 0; i < N; i++){
            DoubleMatrix1D curRow = probs.viewRow(i);
            curRow.assign(Functions.div(curRow.zSum()));
            ll += Math.log(curRow.get((int)depData.get(i,0)));
        }
        return ll;
    }

    private double[] dependencePvalsLinear(Node x, Node y, List<Node> z) {
        if (!variablesPerNode.containsKey(x)) {
            throw new IllegalArgumentException("Unrecogized node: " + x);
        }

        if (!variablesPerNode.containsKey(y)) {
            throw new IllegalArgumentException("Unrecogized node: " + y);
        }

        for (Node node : z) {
            if (!variablesPerNode.containsKey(node)) {
                throw new IllegalArgumentException("Unrecogized node: " + node);
            }
        }

        List<Node> yzDumList = new ArrayList<>();
        List<Node> yzList = new ArrayList<>();
        yzList.add(y);
        yzList.addAll(z);
        //List<Node> zList = new ArrayList<>();

        yzDumList.addAll(variablesPerNode.get(y));
        for (Node _z : z) {
            yzDumList.addAll(variablesPerNode.get(_z));
            //zList.addAll(variablesPerNode.get(_z));
        }

        int[] _rows = getNonMissingRows(x, y, z);
        regression.setRows(_rows);

        RegressionResult result;

        try {
            result = regression.regress(x, yzDumList);
        } catch (Exception e) {
            return null;
        }

        double[] pVec = new double[yzList.size()];
        double[] pCoef = result.getP();

        //skip intercept at 0
        int coeffInd = 1;

        for (int i = 0; i < pVec.length; i++) {
            List<Node> curDummy = variablesPerNode.get(yzList.get(i));
            if (curDummy.size() == 1) {
                pVec[i] = pCoef[coeffInd];
                coeffInd++;
                continue;
            } else {
                pVec[i] = 0;
            }

            for (Node n : curDummy) {
                pVec[i] += Math.log(pCoef[coeffInd]);
                coeffInd++;
            }

            if(pVec[i]==Double.NEGATIVE_INFINITY)
                pVec[i] = 0.0;
            else
                pVec[i] = 1.0 - new ChiSquaredDistribution(2 * curDummy.size()).cumulativeProbability(-2 * pVec[i]);
        }

        return pVec;
    }

    private boolean isIndependentRegression(Node x, Node y, List<Node> z) {
        double p = dependencePvalsLinear(x,y,z)[0];
        //result.getP()[1];
        this.lastP = p;

        boolean indep = p > alpha;

        if (verbose) {
            if (indep) {
                TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFactMsg(x, y, z, p));
            } else {
                TetradLogger.getInstance().log("dependencies", SearchLogUtils.dependenceFactMsg(x, y, z, p));
            }
        }

        return indep;
    }


    public boolean isIndependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isIndependent(x, y, zList);
    }

    /**
     * @return true if the given independence question is judged false, true if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are searchVariables in the list returned by
     * getVariableNames().
     */
    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !this.isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * @return the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for tis test.
     */
    public double getPValue() {
        return this.lastP; //STUB
    }

    /**
     * @return the list of searchVariables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return searchVariables; // Make sure the variables from the ORIGINAL data set are returned, not the modified dataset!
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
     * @return true if y is determined the variable in z.
     */
    public boolean determines(List<Node> z, Node y) {
        return false; //stub
    }

    /**
     * @return the significance level of the independence test.
     * @throws UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return this.alpha; //STUB
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public DataSet getData() {
        return this.originalData;
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
        return getPValue();
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Multinomial Logistic Regression, alpha = " + nf.format(getAlpha());
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
