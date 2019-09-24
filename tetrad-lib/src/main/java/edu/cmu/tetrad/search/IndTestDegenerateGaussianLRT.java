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
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static edu.cmu.tetrad.util.MathUtils.logChoose;
import static java.lang.Math.exp;
import static java.lang.Math.log;

/*
 * Implements a degenerate Gaussian score as a LRT.
 *
 * http://proceedings.mlr.press/v104/andrews19a/andrews19a.pdf
 *
 * @author Bryan Andrews
 */
public class IndTestDegenerateGaussianLRT implements IndependenceTest {

    private DataSet dataSet;

    // The alpha level.
    private double alpha = 0.001;

    // The p value.
    private double pValue = Double.NaN;

    // The mixed variables of the original dataset.
    private List<Node> variables;

    // The continuous variables of the post-embedding dataset.
    private List<Node> continuousVariables;

    // The penalty discount.
    private double penaltyDiscount = 1.0;

    // The structure prior.
    private double structurePrior = 0.0;

    // The number of instances.
    private int N;

    // The embedding map.
    private Map<Integer, List<Integer>> embedding;

    // The covariance matrix.
    private TetradMatrix cov;

    /**
     * A return value for a likelihood--returns a likelihood value and the degrees of freedom
     * for it.
     */
    public class Ret {
        private double lik;
        private int dof;

        private Ret(double lik, int dof) {
            this.lik = lik;
            this.dof = dof;
        }

        public double getLik() {
            return lik;
        }

        public int getDof() {
            return dof;
        }

        public String toString() {
            return "lik = " + lik + " dof = " + dof;
        }
    }

    // A constant.
    private static double L2PE = log(2.0*Math.PI*Math.E);

    private boolean verbose = false;

    /**
     * Constructs the score using a covariance matrix.
     */
    public IndTestDegenerateGaussianLRT(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.N = dataSet.getNumRows();
        this.embedding = new HashMap<>();

        List<Node> A = new ArrayList<>();
        List<double[]> B = new ArrayList<>();

        int i = 0;
        int i_ = 0;
        while (i_ < this.variables.size()) {

            Node v = this.variables.get(i_);

            if (v instanceof DiscreteVariable) {

                Map<String, Integer> keys = new HashMap<>();
                for (int j = 0; j < this.N; j++) {
                    String key = v.getName().concat("_");
                    key = key.concat(Integer.toString(this.dataSet.getInt(j, i_)));
                    if (!keys.containsKey(key)) {
                        keys.put(key, i);
                        Node v_ = new ContinuousVariable(key);
                        A.add(v_);
                        B.add(new double[this.N]);
                        i++;
                    }
                    B.get(keys.get(key))[j] = 1;
                }

                /*
                 * Remove a degenerate dimension.
                 */
                i--;
                keys.remove(A.get(i).getName());
                A.remove(i);
                B.remove(i);

                this.embedding.put(i_, new ArrayList<>(keys.values()));
                i_++;

            } else {

                A.add(v);
                double[] b = new double[this.N];
                for (int j = 0; j < this.N; j++) {
                    b[j] = this.dataSet.getDouble(j,i_);
                }

                B.add(b);
                List<Integer> index = new ArrayList<>();
                index.add(i);
                this.embedding.put(i_, index);
                i++;
                i_++;

            }
        }

        double[][] B_ = new double[this.N][B.size()];
        for (int j = 0; j < B.size(); j++) {
            for (int k = 0; k < this.N; k++) {
                B_[k][j] = B.get(j)[k];
            }
        }

        this.continuousVariables = A;
        RealMatrix D = new BlockRealMatrix(B_);
        this.cov = new BoxDataSet(new DoubleDataBox(D.getData()), A).getCovarianceMatrix();

    }

    /**
     * Calculates the sample log likelihood
     */
    private Ret getlldof(int i, int... parents) {

        List<Integer> A = new ArrayList();
        List<Integer> B = new ArrayList();
        A.addAll(this.embedding.get(i));
        for (int i_ : parents) {
            B.addAll(this.embedding.get(i_));
        }

        int[] A_ = new int[A.size() + B.size()];
        int[] B_ = new int[B.size()];
        for (int i_ = 0; i_ < A.size(); i_++) {
            A_[i_] = A.get(i_);
        }
        for (int i_ = 0; i_ < B.size(); i_++) {
            A_[A.size() + i_] = B.get(i_);
            B_[i_] = B.get(i_);
        }

        int dof = (A_.length*(A_.length + 1) - B_.length*(B_.length + 1)) / 2;
        double ldetA = log(this.cov.getSelection(A_, A_).det());
        double ldetB = log(this.cov.getSelection(B_, B_).det());
        double lik = this.N *(ldetB - ldetA + L2PE*(B_.length - A_.length));

        return new Ret(lik, dof);
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

        int _x = variables.indexOf(x);
        int _y = variables.indexOf(y);

        int[] list0 = new int[z.size() + 1];
        int[] list1 = new int[z.size() + 1];
        int[] list2 = new int[z.size()];

        list0[0] = _x;
        list1[0] = _y;

        for (int i = 0; i < z.size(); i++) {
            int _z = variables.indexOf(z.get(i));
            list0[i + 1] = _z;
            list1[i + 1] = _z;
            list2[i] = _z;
        }

        Ret ret1 = getlldof(_y, list0);
        Ret ret2 = getlldof(_y, list2);
        Ret ret3 = getlldof(_x, list1);
        Ret ret4 = getlldof(_x, list2);

        double lik0 = ret1.getLik() - ret2.getLik();
        double dof0 = ret1.getDof() - ret2.getDof();
        double lik1 = ret3.getLik() - ret4.getLik();
        double dof1 = ret3.getDof() - ret4.getDof();

        if (dof0 <= 0) {
            dof0 = 1;
//            throw new IllegalArgumentException("DOF must be >= 1");
        }
        if (dof1 <= 0) {
            dof1 = 1;
//            throw new IllegalArgumentException("DOF must be >= 1");
        }

        double p0 = 0;
        double p1 = 0;
        try {
            p0 = 1.0 - new ChiSquaredDistribution(dof0).cumulativeProbability(2.0 * lik0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            p1 = 1.0 - new ChiSquaredDistribution(dof1).cumulativeProbability(2.0 * lik1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.pValue = Math.min(p0, p1);
        return this.pValue > alpha;
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
        return variables;
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
        return getAlpha() - getPValue();
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Degenerate Gaussian, alpha = " + nf.format(getAlpha());
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

}