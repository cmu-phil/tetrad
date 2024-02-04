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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;

import java.util.List;

/**
 * Implements a mixed variable polynomial BIC score. The reference is here:
 * <p>
 * Andrews, B., Ramsey, J., &amp; Cooper, G. F. (2018). Scoring Bayesian networks of mixed variables. International
 * journal of data science and analytics, 6, 3-18.
 *
 * @author Bryan Andrews
 */
public class MvpScore implements Score {
    // The mixed variables of the original dataset.
    private final DataSet dataSet;
    // The variables of the continuousData set.
    private final List<Node> variables;
    // Likelihood function
    private final MvpLikelihood likelihood;
    // Log number of instances
    private final double logn;

    /**
     * Constructor.
     *
     * @param dataSet        The mixed dataset being analyzed.
     * @param structurePrior The structure prior
     * @param fDegree        The f degree.
     */
    public MvpScore(DataSet dataSet, double structurePrior, int fDegree, boolean discretize) {

        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.likelihood = new MvpLikelihood(dataSet, structurePrior, fDegree, discretize);
        this.logn = FastMath.log(dataSet.getNumRows());
    }

    /**
     * The local score of the child given its parents.
     *
     * @param i       The child.
     * @param parents The parents.
     * @return The local score.
     */
    public double localScore(int i, int... parents) {

        double lik = this.likelihood.getLik(i, parents);
        double dof = this.likelihood.getDoF(i, parents);
        double sp = this.likelihood.getStructurePrior(parents.length);

        if (sp > 0) {
            sp = -2 * dof * sp;
        }

        double score = 2.0 * lik - dof * this.logn + sp;

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    /**
     * Returns localScore(y | z, x) - localScore(y | z).
     *
     * @param x A node.
     * @param y The node.
     * @param z A set of nodes.
     * @return The score difference.
     */
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Returns the sample size.
     *
     * @return This size.
     */
    public int getSampleSize() {
        return this.dataSet.getNumRows();
    }

    /**
     * A method for FGES returning a judgment of whether an edge with a given bump counts as a effect edge.
     *
     * @param bump The bump.
     * @return True, if so.
     * @see edu.cmu.tetrad.search.Fges
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Returns the list of variables.
     *
     * @return This list.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns an estimate of the maximum degree of the graph for some algorithms.
     *
     * @return This maximum.
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(FastMath.log(this.dataSet.getNumRows()));
    }

    /**
     * Returns a judgment of whether the variable in z determine y exactly.
     * @param z The set of nodes.
     * @param y The node.
     * @return This judgment.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }
}



