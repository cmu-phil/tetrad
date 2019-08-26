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
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.log;

/**
 * Implements a degenerate Gaussian BIC score for FGES.
 *
 * http://proceedings.mlr.press/v104/andrews19a/andrews19a.pdf
 *
 * @author Bryan Andrews
 */
public class DegenerateGaussianScore implements Score {

    private DataSet dataSet;

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

    // A constant.
    private static double L2PE = log(2.0*Math.PI*Math.E);

    /**
     * Constructs the score using a covariance matrix.
     */
    public DegenerateGaussianScore(DataSet dataSet) {
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
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {

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

        double dof = (A_.length*(A_.length + 1) - B_.length*(B_.length + 1)) / 2.0;
        double ldetA = log(this.cov.getSelection(A_, A_).det());
        double ldetB = log(this.cov.getSelection(B_, B_).det());
        double lik = this.N *(ldetB - ldetA + L2PE*(B_.length - A_.length));

        return lik + 2*calculateStructurePrior(parents.length) - dof*getPenaltyDiscount()*log(this.N);
    }

    private double calculateStructurePrior(int k) {
        if (structurePrior <= 0) {
            return 0;
        } else {
            double n = variables.size() - 1;
            double p = structurePrior / n;
            return k*log(p) + (n - k)*log(1.0 - p);
        }
    }


    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScore(y, x) - localScore(y);
    }

    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */
    public double localScore(int i, int parent) {
        return localScore(i, new int[]{parent});
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(int i) {
        return localScore(i, new int[0]);
    }

    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(log(dataSet.getNumRows()));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public double getStructurePrior() {
        return structurePrior;
    }

    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

}



