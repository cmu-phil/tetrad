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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Performs a test of conditional independence X _||_ Y | Z1...Zn where all variables are either continuous or binary.
 *
 * @author Joseph Ramsey
 * @author Mike Freenor.
 */
public class IndTestLogisticRegression implements IndependenceTest {
    private DataSet dataSet;
    private double alpha;
    private double lastP;

    public IndTestLogisticRegression(DataSet dataSet, double alpha) {
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node variable = dataSet.getVariable(j);

            if (variable instanceof ContinuousVariable) {
                continue;
            }

            DiscreteVariable discreteVariable = (DiscreteVariable) variable;

            if (discreteVariable.getNumCategories() != 2) {
                throw new IllegalArgumentException("Only continuous or binary " +
                        "variables permitted.");
            }
        }

        this.dataSet = dataSet;
        this.alpha = alpha;
    }

    /**
     * @return an Independence test for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true if the given independence question is judged true, false if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        DiscreteVariable binaryTarget;

        if (x instanceof DiscreteVariable && ((DiscreteVariable) x).getNumCategories() == 2) {
            binaryTarget = (DiscreteVariable) x;
        } else if (y instanceof DiscreteVariable && ((DiscreteVariable) y).getNumCategories() == 2) {
            binaryTarget = (DiscreteVariable) y;
        } else {
            binaryTarget = null;
        }

        if (binaryTarget == x) {
            return isIndependentLogisticRegression(x, y, z);
        } else if (binaryTarget == y) {
            return isIndependentLogisticRegression(y, x, z);
        } else {
            return isIndependentRegression(x, y, z);
        }
    }

    private boolean isIndependentLogisticRegression(Node x, Node y, List<Node> z) {
        if (z == null) {
            throw new NullPointerException();
        }

        for (Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        DataSet regressors = new ColtDataSet((ColtDataSet) dataSet);
        List<String> regressorList = new ArrayList<String>();
        int targetIndex = dataSet.getVariables().indexOf(x);
        regressors.removeColumn(x);

        List<Node> dataSetVariables = regressors.getVariables();

        regressorList.add(dataSet.getVariable(y.getName()).getName());

        for (Node zVar : z) {
            regressorList.add(dataSet.getVariable(zVar.getName()).getName());
        }

        Object[] regNamesAsObj = regressorList.toArray();
        String[] regressorNames = new String[regNamesAsObj.length];
        int returnPIndex = 0;
        int k = 0;
        //for (int i = 0; i < regressorNames.length; i++) {
        //    regressorNames[i] = (String) regNamesAsObj[i];
        //}

        for (Node var : dataSetVariables) {
            if (!regressorList.contains(var.getName())) {
                regressors.removeColumn(var);
            } else {
                if (var.getName().equals(y.getName()))
                    returnPIndex = k;
                regressorNames[k] = var.getName();
                k++;
            }
        }

        int numcases = regressors.getNumRows();
        int numvars = regressors.getNumColumns();

        double[][] regressorData = new double[numvars][numcases];

        for (int i = 0; i < numvars; i++) {
            for (int j = 0; j < numcases; j++) {
                regressorData[i][j] = regressors.getDouble(j, i);
            }
        }

        int[] target = new int[numcases];
        for (int j = 0; j < numcases; j++) {
            target[j] = dataSet.getInt(j, targetIndex);
        }

        LogisticRegression regression = new LogisticRegression(dataSet);
        regression.setAlpha(this.alpha);

        LogisticRegression.Result result;

        try {
            result = regression.regress((DiscreteVariable) x, regressors.getVariables());
        } catch (Exception e) {
            return false;
        }

        double p = result.getProbs()[returnPIndex + 1];
        this.lastP = p;

        boolean indep = p > alpha;

        if (indep) {
            TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFactMsg(x, y, z, p));
        } else {
            TetradLogger.getInstance().log("dependencies", SearchLogUtils.dependenceFactMsg(x, y, z, p));
        }

        return indep;
    }

    private boolean isIndependentRegression(Node x, Node y, List<Node> z) {
        if (z == null) {
            throw new NullPointerException();
        }

        for (Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        List<Node> regressors = new ArrayList<Node>();
        regressors.add(dataSet.getVariable(y.getName()));

        for (Node zVar : z) {
            regressors.add(dataSet.getVariable(zVar.getName()));
        }

        Regression regression = new RegressionDataset(dataSet);
        RegressionResult result = null;

        try {
            result = regression.regress(x, regressors);
        } catch (Exception e) {
            return false;
        }

        double p = result.getP()[1];

        boolean indep = p > alpha;

        if (indep) {
            TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFactMsg(x, y, z, p));
        } else {
            TetradLogger.getInstance().log("dependencies", SearchLogUtils.dependenceFactMsg(x, y, z, p));
        }

        return indep;
    }


    public boolean isIndependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isIndependent(x, y, zList);
    }

    /**
     * @return true if the given independence question is judged false, true if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
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
     * @return the list of variables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return dataSet.getVariables(); //STUB
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

    /**
     * @return true if y is determined the variable in z.
     */
    public boolean determines(List<Node> z, Node y) {
        return false; //stub
    }

    /**
     * @return the significance level of the independence test.
     *
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
        return null; //STUB
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
}



