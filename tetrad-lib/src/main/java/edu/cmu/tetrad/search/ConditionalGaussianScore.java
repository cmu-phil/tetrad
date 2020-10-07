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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Implements a conditional Gaussian BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianScore implements Score {

    private final DataSet dataSet;

    // The variables of the continuousData set.
    private final List<Node> variables;
    private final boolean discretize;

    // Likelihood function
    private ConditionalGaussianLikelihood likelihood;

    private double penaltyDiscount;
    private int numCategoriesToDiscretize = 3;
    private final double sp;

    /**
     * Constructs the score using a covariance matrix.
     */
    public ConditionalGaussianScore(DataSet dataSet, double penaltyDiscount, double sp, boolean discretize) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.penaltyDiscount = penaltyDiscount;
        this.sp = sp;

//        this.likelihood = new ConditionalGaussianLikelihood(dataSet);
//        this.likelihood.setDiscretize(discretize);

        this.discretize = discretize;
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        List<Node> variables = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int j = 0; j < variables.size(); j++) {
            nodesHash.put(variables.get(j), j);
        }

        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < dataSet.getNumRows(); k++) {
            for (Node node : dataSet.getVariables()) {
                if (node instanceof ContinuousVariable) {
                    if (Double.isNaN(dataSet.getDouble(k, nodesHash.get(node)))) continue K;
                } else if (node instanceof DiscreteVariable) {
                    if (dataSet.getInt(k, nodesHash.get(node)) == -99) continue K;
                }
            }

            rows.add(k);
        }

//        if (rows.size() < dataSet.getNumRows()) {
            int[] _rows = new int[rows.size()];
            for (int k = 0; k < rows.size(); k++) _rows[k] = rows.get(k);

            int[] cols = new int[parents.length + 1];
            System.arraycopy(parents, 0, cols, 1, parents.length);
            cols[0] = i;

            DataSet data2 = dataSet.subsetRowsColumns(_rows, cols);

            nodesHash = new HashMap<>();

            for (int j = 0; j < data2.getVariables().size(); j++) {
                nodesHash.put(data2.getVariables().get(j), j);
            }

            i = 0;
            for (int j = 0; j < parents.length; j++) {
                parents[j] = j + 1;
            }

            likelihood = new ConditionalGaussianLikelihood(data2);
//        }

        likelihood.setNumCategoriesToDiscretize(numCategoriesToDiscretize);
        likelihood.setPenaltyDiscount(penaltyDiscount);
        likelihood.setDiscretize(discretize);

        ConditionalGaussianLikelihood.Ret ret = likelihood.getLikelihood(i, parents);

        int N = dataSet.getNumRows();
        double lik = ret.getLik();
        int k = ret.getDof();

        return 2.0 * (lik + getStructurePrior(parents)) - getPenaltyDiscount() * k * Math.log(N);
    }

    private double getStructurePrior(int[] parents) {
        if (sp <= 0) { return 0; }
        else {
            int k = parents.length;
            double n = dataSet.getNumColumns() - 1;
            double p = sp / n;
            return k * Math.log(p) + (n - k) * Math.log(1.0 - p);
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
        return (int) Math.ceil(Math.log(dataSet.getNumRows()));
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

    public void setNumCategoriesToDiscretize(int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Conditional Gaussian Score Penalty " + nf.format(penaltyDiscount);
    }
}



