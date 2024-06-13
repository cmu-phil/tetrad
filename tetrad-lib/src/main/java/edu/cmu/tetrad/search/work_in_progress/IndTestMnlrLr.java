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
    /**
     * The data set.
     */
    private final DataSet data;
    /**
     * A map from searchVariables to their indices in the data set.
     */
    private final Map<Node, Integer> nodesHash;
    /**
     * The likelihood function.
     */
    private final MnlrLikelihood likelihood;
    /**
     * The significance level of the test.
     */
    private double alpha;
    /**
     * Whether verbose output should be printed.
     */
    private boolean verbose;

    /**
     * Constructs a new independence test for the given data set and significance level.
     *
     * @param data  the data set.
     * @param alpha the significance level.
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

    /**
     * This method returns an independence test for a sublist of variables.
     *
     * @param vars The sublist of variables.
     * @return An object of type IndependenceTest.
     * @throws UnsupportedOperationException If this feature is not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks the independence between two nodes given a set of conditioning nodes.
     *
     * @param x  the first node
     * @param y  the second node
     * @param _z the set of conditioning nodes
     * @return the result of the independence test
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
                TetradLogger.getInstance().log(
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


    /**
     * Determines the independence relation between a list of conditioning nodes and a target node.
     *
     * @param z The list of conditioning nodes.
     * @param y The target node.
     * @return true if the target node is independent of the conditioning nodes; false otherwise.
     */
    public boolean determines(List<Node> z, Node y) {
        return false; //stub
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return The significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level for the independence test.
     *
     * @param alpha The significance level to set.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns the dataset.
     *
     * @return The dataset.
     */
    public DataSet getData() {
        return this.data;
    }

    /**
     * Determines if the test prints verbose output.
     *
     * @return True if the test prints verbose output, false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether this test will print verbose output.
     *
     * @param verbose True, if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns a string representation of this object.
     *
     * @return the string representation of this object.
     */
    public String toString() {
        return "IndTestMnlrLr";
    }
}
