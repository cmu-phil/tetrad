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
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Implements a conditional Gaussian BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class DiscreteMixedScore implements Score {

    private DataSet dataSet;

    // The variables of the continuousData set.
    private List<Node> variables;

    // Likelihood function
    private DiscreteMixedLikelihood likelihood;

    private double penaltyDiscount = 1;
    private int numCategoriesToDiscretize = 3;
    private double sp;

    /**
     * Constructs the score using a covariance matrix.
     */
    public DiscreteMixedScore(DataSet dataSet, double sp) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sp = sp;

        this.likelihood = new DiscreteMixedLikelihood(dataSet);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        likelihood.setNumCategoriesToDiscretize(numCategoriesToDiscretize);
        likelihood.setPenaltyDiscount(penaltyDiscount);

        DiscreteMixedLikelihood.Ret ret = likelihood.getLikelihood(i, parents);

        int N = dataSet.getNumRows();
        double lik = ret.getLik();
        int k = ret.getDof();

        double strucPrior = getStructurePrior(parents);
        if (strucPrior > 0) {
            strucPrior = -2 * k * strucPrior;
        }

        return 2.0 * lik - /*getPenaltyDiscount() **/ k * Math.log(N) + strucPrior;
    }

    private double getStructurePrior(int[] parents) {
        if (sp < 0) { return getEBICprior(); }
        else if (sp == 0) { return 0; }
        else {
            int i = parents.length;
            int c = dataSet.getNumColumns() - 1;
            double p = sp / (double) c;
            return i * Math.log(p) + (c - i) * Math.log(1.0 - p);
        }
    }

    private double getEBICprior() {

        double n = dataSet.getNumColumns();
        double gamma = -sp;
        return gamma * Math.log(n);

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
}



