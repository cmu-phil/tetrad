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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.MvpLikelihood;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

/**
 * <p>Performs a test of conditional independence X _||_ Y | Z1...Zn where all
 * variables are either continuous or discrete. This test is valid for both ordinal and non-ordinal discrete
 * searchVariables.</p>
 *
 * <p>Andrews, B., Ramsey, J., & Cooper, G. F. (2018). Scoring Bayesian networks of
 * mixed variables. International journal of data science and analytics, 6, 3-18.</p>
 *
 * @author Bryan Andrews
 */
public class IndTestMvpLrt implements IndependenceTest {
    private final DataSet data;
    private final Map<Node, Integer> nodesHash;
    private double alpha;

    // Likelihood function
    private final MvpLikelihood likelihood;
    private boolean verbose;


    // P Values
    private double pValue = Double.NaN;

    /**
     * Constructor.
     *
     * @param data       The mixed continuous/discrete dataset.
     * @param alpha      The significance level.
     * @param fDegree    The f degree.
     * @param discretize True if continuous children should be discretized.
     */
    public IndTestMvpLrt(DataSet data, double alpha, int fDegree, boolean discretize) {
        this.data = data;
        this.likelihood = new MvpLikelihood(data, -1, fDegree, discretize);

        this.nodesHash = new HashedMap<>();

        List<Node> variables = data.getVariables();

        for (int i = 0; i < variables.size(); i++) {
            this.nodesHash.put(variables.get(i), i);
        }

        this.alpha = alpha;
    }

    /**
     * Returns an independence test for a sublist of the searchVariables.
     *
     * @return this test.
     * @see IndependenceTest
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns an independence result for x _||_ y | z.
     *
     * @return This result.
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        int _x = this.nodesHash.get(x);
        int _y = this.nodesHash.get(y);
        int[] list0 = new int[z.size() + 1];
        int[] list1 = new int[z.size() + 1];
        int[] list2 = new int[z.size()];
        list0[0] = _x;
        list1[0] = _y;
        for (int i = 0; i < z.size(); i++) {
            int __z = this.nodesHash.get(z.get(i));
            list0[i + 1] = __z;
            list1[i + 1] = __z;
            list2[i] = __z;
        }

        double lik_0;
        double dof_0;
        double lik_1;
        double dof_1;

        lik_0 = this.likelihood.getLik(_y, list0) - this.likelihood.getLik(_y, list2);
        dof_0 = this.likelihood.getLik(_y, list0) - this.likelihood.getLik(_y, list2);

        lik_1 = this.likelihood.getLik(_x, list1) - this.likelihood.getLik(_x, list2);
        dof_1 = this.likelihood.getLik(_x, list1) - this.likelihood.getLik(_x, list2);


        if (dof_0 <= 0) {
            dof_0 = 1;
        }
        if (dof_1 <= 0) {
            dof_1 = 1;
        }
        double p_0 = 0;
        double p_1 = 0;
        try {
            p_0 = 1.0 - new ChiSquaredDistribution(dof_0).cumulativeProbability(2.0 * lik_0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            p_1 = 1.0 - new ChiSquaredDistribution(dof_1).cumulativeProbability(2.0 * lik_1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.pValue = FastMath.min(p_0, p_1);

        boolean independent = this.pValue > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, getPValue()));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, _z), independent, pValue, alpha - pValue);
    }

    /**
     * Returns The probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for this test.
     *
     * @return This p-value.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Returns the list of searchVariables over which this independence checker is capable of determinining independence
     * relations.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.data.getVariables();
    }

    /**
     * @throws javax.help.UnsupportedOperationException Method not implemented.
     */
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return This level.
     * @throws UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     *
     * @param alpha This level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns the data.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.data;
    }

    /**
     * Returns true if verbose output is printed.
     *
     * @return True if so.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Returns whether verbose output should be printed.
     *
     * @param verbose True if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}