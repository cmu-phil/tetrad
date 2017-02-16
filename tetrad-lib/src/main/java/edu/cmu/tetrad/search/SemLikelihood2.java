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
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

import static java.lang.Math.log;

/**
 * Implements a conditional Gaussian likelihood. Please note that this this likelihood will be maximal only if the
 * the continuous variables are jointly Gaussian conditional on the discrete variables; in all other cases, it will
 * be less than maximal. For an algorithm like FGS this is fine.
 *
 * @author Joseph Ramsey
 */
public class SemLikelihood2 {

    // The covariance matrix.
    private ICovarianceMatrix covMatrix;

    // The variables of the mixed data set.
    private List<Node> variables;

    // Indices of variables.
    private Map<Node, Integer> nodesHash;

    // A constant.
    private static double LOG2PI = log(2.0 * Math.PI);

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

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemLikelihood2(ICovarianceMatrix covMatrix) {
        if (covMatrix == null) {
            throw new NullPointerException();
        }

        nodesHash = new HashMap<>();
        List<Node> nodes = covMatrix.getVariables();

        for (int j = 0; j < nodes.size(); j++) {
            Node v = nodes.get(j);
            nodesHash.put(v, j);
        }

        this.covMatrix = covMatrix;
        this.variables = covMatrix.getVariables();
    }

    /**
     * Returns the likelihood of variable i conditional on the given parents, assuming the continuous variables
     * index by i or by the parents are jointly Gaussian conditional on the discrete comparison.
     *
     * @param i       The index of the conditioned variable.
     * @param parents The indices of the conditioning variables.
     * @return The likelihood.
     */
    public Ret getLikelihood(int i, int[] parents) {
        Node target = variables.get(i);

        List<ContinuousVariable> X = new ArrayList<>();

        for (int p : parents) {
            X.add((ContinuousVariable) variables.get(p));
        }

        List<ContinuousVariable> XPlus = new ArrayList<>(X);
        XPlus.add((ContinuousVariable) target);

        Ret ret1 = likelihoodJoint(XPlus);
        Ret ret2 = likelihoodJoint(X);

        return new Ret(ret1.getLik() - ret2.getLik(), ret1.getDof() - ret2.getDof());
    }

    // The likelihood of the joint over all of these variables, assuming conditional Gaussian,
    // continuous and discrete.
    private Ret likelihoodJoint(List<ContinuousVariable> X) {
        X = new ArrayList<>(X);

        int k = X.size();

        int[] cols = new int[k];
        for (int j = 0; j < k; j++) cols[j] = nodesHash.get(X.get(j));
        int N = covMatrix.getSampleSize();

        TetradMatrix cov = covMatrix.getSelection(cols, cols);
        double lnL = N * gaussianLikelihood(k, cov);

        final int dof = h(X);
        return new Ret(lnL, dof);
    }

    // One record.
    private double gaussianLikelihood(int k, TetradMatrix sigma) {
        return -0.5 * logdet(sigma) - 0.5 * k * (1.0 + LOG2PI);
    }

    private double logdet(TetradMatrix m) {
        if (m.rows() == 0) return 0;
        RealMatrix M = m.getRealMatrix();
        final double tol = 1e-9;
        RealMatrix LT = new org.apache.commons.math3.linear.CholeskyDecomposition(M, tol, tol).getLT();

        double sum = 0.0;

        for (int i = 0; i < LT.getRowDimension(); i++) {
            sum += FastMath.log(LT.getEntry(i, i));
        }

        return 2.0 * sum;
    }

    // Degrees of freedom for a discrete distribution is the product of the number of categories for each
    // variable.
    private int f(List<DiscreteVariable> A) {
        int f = 1;

        for (DiscreteVariable V : A) {
            f *= V.getNumCategories();
        }

        return f;
    }

    // Degrees of freedom for a multivariate Gaussian distribution is p * (p + 1) / 2, where p is the number
    // of variables. This is the number of unique entries in the covariance matrix over X.
    private int h(List<ContinuousVariable> X) {
        int p = X.size();
        return p * (p + 1) / 2;
    }
}



