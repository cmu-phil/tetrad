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
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Performs a test of conditional independence X _||_ Y | Z1...Zn where all searchVariables are either continuous or discrete.
 * This test is valid for both ordinal and non-ordinal discrete searchVariables.
 * <p>
 * Assumed a conditional Gaussain model and uses a likelihood rat test.
 *
 * @author Joseph Ramsey
 */
public class IndTestConditionalGaussianLRT implements IndependenceTest {
    private DataSet data;
    private Map<Node, Integer> nodesHash;
    private double alpha = 0.001;

    // Likelihood function
    private ConditionalGaussianLikelihood likelihood;
    private double pValue = Double.NaN;
    private boolean denominatorMixed = true;
    private double penaltyDiscount;

    public IndTestConditionalGaussianLRT(DataSet data, double alpha) {
        this.data = data;
        this.likelihood = new ConditionalGaussianLikelihood(data);

        nodesHash = new HashedMap<>();

        List<Node> variables = data.getVariables();

        for (int i = 0; i < variables.size(); i++) {
            nodesHash.put(variables.get(i), i);
        }

        this.alpha = alpha;
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
        likelihood.setDenominatorMixed(denominatorMixed);

        int _x = nodesHash.get(x);
        int _y = nodesHash.get(y);

        int[] list1 = new int[z.size() + 1];
        int[] list2 = new int[z.size()];

        list1[0] = _y;

        for (int i = 0; i < z.size(); i++) {
            int _z = nodesHash.get(z.get(i));
            list1[i + 1] = _z;
            list2[i] = _z;
        }

        ConditionalGaussianLikelihood.Ret ret1 = likelihood.getLikelihood(_x, list1);
        ConditionalGaussianLikelihood.Ret ret2 = likelihood.getLikelihood(_x, list2);

        double lik = ret1.getLik() - ret2.getLik();
        double dof = ret1.getDof() - ret2.getDof();

        if (dof <= 0) {
            dof = 1;
//            throw new IllegalArgumentException("DOF must be >= 1");
        }

        double p = 0;
        try {
            p = 1.0 - new ChiSquaredDistribution(dof).cumulativeProbability(2.0 * lik);
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.pValue = p;

        return p > alpha;
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
        return this.pValue;
    }

    /**
     * @return the list of searchVariables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return data.getVariables();
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
        return alpha;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public DataSet getData() {
        return data;
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
        return getAlpha() - getPValue();
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Multinomial Logistic Regression, alpha = " + nf.format(getAlpha());
    }

    public void setDenominatorMixed(boolean denominatorMixed) {
        this.denominatorMixed = denominatorMixed;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        likelihood.setPenaltyDiscount(penaltyDiscount);
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }
}