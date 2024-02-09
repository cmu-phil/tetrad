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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performs a test of conditional independence X _||_ Y | Z1...Zn where all searchVariables are either continuous or
 * discrete. This test is valid for both ordinal and non-ordinal discrete searchVariables.
 *
 * @author bryanandrews
 * @version $Id: $Id
 */
public class IndTestMnlrLr implements IndependenceTest {
    private final DataSet data;
    private final Map<Node, Integer> nodesHash;
    // Likelihood function
    private final MnlrLikelihood likelihood;
    private double alpha;
    private boolean verbose;

    /**
     * <p>Constructor for IndTestMnlrLr.</p>
     *
     * @param data a {@link edu.cmu.tetrad.data.DataSet} object
     * @param alpha a double
     */
    public IndTestMnlrLr(DataSet data, double alpha) {
        this.data = data;
        this.likelihood = new MnlrLikelihood(data, -1, 1);

        this.nodesHash = new HashedMap<>();

        List<Node> variables = data.getVariables();

        for (int i = 0; i < variables.size(); i++) {
            this.nodesHash.put(variables.get(i), i);
        }

        this.alpha = alpha;
    }

    /** {@inheritDoc} */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param _z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {

        List<Node> z = new ArrayList<>(_z);

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

        double pValue = FastMath.min(p_0, p_1);

        if (Double.isNaN(pValue)) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                    LogUtilsSearch.independenceFact(x, y, _z));
        }

        boolean independent = pValue > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, _z),
                independent, pValue, alpha - pValue);
    }

    /**
     * <p>getVariables.</p>
     *
     * @return the list of searchVariables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return this.data.getVariables();
    }


    /** {@inheritDoc} */
    public boolean determines(List<Node> z, Node y) {
        return false; //stub
    }

    /**
     * <p>Getter for the field <code>alpha</code>.</p>
     *
     * @return the significance level of the independence test.
     * @throws java.lang.UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * {@inheritDoc}
     *
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns the data.
     *
     * @return This.
     */
    public DataSet getData() {
        return this.data;
    }

    /**
     * {@inheritDoc}
     *
     * Returns whether verbose output should be printed.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * {@inheritDoc}
     *
     * Sets whether this test will print verbose output.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "IndTestMnlrLr";
    }
}
