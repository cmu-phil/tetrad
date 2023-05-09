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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * <p>This implements the degenerate Gaussian BIC score for FGES. The degenerate Gaussian score
 * replaces each discrete variable in the data with a list of 0/1 continuous indicator columns for
 * each of the categories but one (the last one implied). This data, now all continuous, is given
 * to the SEM BIC score and methods used to help determine conditional independence for the
 * mixed continuous/discrete case from this information. The references is as follows:</p>
 *
 * <p>Andrews, B., Ramsey, J., & Cooper, G. F. (2019, July). Learning high-dimensional
 * directed acyclic graphs with mixed data-types. In The 2019 ACM SIGKDD Workshop on
 * Causal Discovery (pp. 4-21). PMLR.</p>
 *
 * <p>As for all scores in Tetrad, higher scores mean more dependence, and negative
 * scores indicate independence.</p>
 *
 * @author Bryan Andrews
 */
public class DegenerateGaussianScore implements Score {
    // The mixed variables of the original dataset.
    private final List<Node> variables;

    // The structure prior.
    private double structurePrior;

    // The embedding map.
    private final Map<Integer, List<Integer>> embedding;

    private final SemBicScore bic;

    public DegenerateGaussianScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.variables = dataSet.getVariables();
        // The number of instances.
        int n = dataSet.getNumRows();
        this.embedding = new HashMap<>();

        List<Node> A = new ArrayList<>();
        List<double[]> B = new ArrayList<>();

        int index = 0;

        int i = 0;
        int i_ = 0;
        while (i_ < this.variables.size()) {

            Node v = this.variables.get(i_);

            if (v instanceof DiscreteVariable) {

                Map<List<Integer>, Integer> keys = new HashMap<>();
                Map<Integer, List<Integer>> keysReverse = new HashMap<>();
                for (int j = 0; j < n; j++) {
                    List<Integer> key = new ArrayList<>();
                    key.add(dataSet.getInt(j, i_));
                    if (!keys.containsKey(key)) {
                        keys.put(key, i);
                        keysReverse.put(i, key);
                        Node v_ = new ContinuousVariable("V__" + ++index);
                        A.add(v_);
                        B.add(new double[n]);
                        i++;
                    }
                    B.get(keys.get(key))[j] = 1;
                }

                // Remove a degenerate dimension.
                i--;
                keys.remove(keysReverse.get(i));
                A.remove(i);
                B.remove(i);

                this.embedding.put(i_, new ArrayList<>(keys.values()));

            } else {

                A.add(v);
                double[] b = new double[n];
                for (int j = 0; j < n; j++) {
                    b[j] = dataSet.getDouble(j, i_);
                }

                B.add(b);
                List<Integer> index2 = new ArrayList<>();
                index2.add(i);
                this.embedding.put(i_, index2);
                i++;

            }
            i_++;
        }
        double[][] B_ = new double[n][B.size()];
        for (int j = 0; j < B.size(); j++) {
            for (int k = 0; k < n; k++) {
                B_[k][j] = B.get(j)[k];
            }
        }

        RealMatrix D = new BlockRealMatrix(B_);
        this.bic = new SemBicScore(new BoxDataSet(new DoubleDataBox(D.getData()), A));
        this.bic.setStructurePrior(0);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model.
     *
     * @param i       The child indes.
     * @param parents The indices of the parents.
     */
    public double localScore(int i, int... parents) {
        double score = 0;

        List<Integer> A = new ArrayList<>(this.embedding.get(i));
        List<Integer> B = new ArrayList<>();
        for (int i_ : parents) {
            B.addAll(this.embedding.get(i_));
        }

        int[] parents_ = new int[B.size()];
        for (int i_ = 0; i_ < B.size(); i_++) {
            parents_[i_] = B.get(i_);
        }

        for (Integer i_ : A) {
            score += this.bic.localScore(i_, parents_);
        }


        return score;
    }

    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return this.bic.isEffectEdge(bump);
    }

    @Override
    public int getSampleSize() {
        return this.bic.getSampleSize();
    }

    @Override
    public int getMaxDegree() {
        return this.bic.getMaxDegree();
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Degenerate Gaussian Score Penalty " + nf.format(this.bic.getPenaltyDiscount());
    }

    public double getPenaltyDiscount() {
        return this.bic.getPenaltyDiscount();
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.bic.setPenaltyDiscount(penaltyDiscount);
    }

    public double getStructurePrior() {
        return structurePrior;
    }

    public void setStructurePrior(double structurePrior) {
        System.out.println("STRUCTURE PRIOR IS NOT IMPLEMENTED!");
        this.structurePrior = structurePrior;
    }
}