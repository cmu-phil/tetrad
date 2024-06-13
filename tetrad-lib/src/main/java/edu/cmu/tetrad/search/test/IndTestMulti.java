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
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.ResolveSepsets;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class that represents a pooled independence test for multiple data sets.
 */
public final class IndTestMulti implements IndependenceTest {

    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private final List<Node> variables;
    /**
     * The independence test associated with each data set.
     */
    private final List<IndependenceTest> independenceTests;
    /**
     * Pooling method
     */
    private final ResolveSepsets.Method method;
    /**
     * A cache of results for independence facts.
     */
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;

    /**
     * Constructs a new pooled independence test for the given data sets.
     *
     * @param independenceTests the independence tests to pool.
     * @param method            the method to use for pooling.
     * @see ResolveSepsets.Method
     */
    public IndTestMulti(List<IndependenceTest> independenceTests, ResolveSepsets.Method method) {
        Set<String> nodeNames = new HashSet<>();
        for (IndependenceTest independenceTest : independenceTests) {
            nodeNames.addAll(independenceTest.getVariableNames());
        }
        if (independenceTests.iterator().next().getVariables().size() != nodeNames.size()) {
            throw new IllegalArgumentException("Data sets must have same variables.");
        }
        this.variables = independenceTests.iterator().next().getVariables();
        this.independenceTests = independenceTests;
        this.method = method;
    }

    /**
     * Returns an Independence test for a sublist of the variables.
     *
     * @param vars The sublist of variables.
     * @return an {@link IndependenceTest} object
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        if (facts.containsKey(new IndependenceFact(x, y, z))) {
            return facts.get(new IndependenceFact(x, y, z));
        }

        boolean independent = ResolveSepsets.isIndependentPooled(this.method, this.independenceTests, x, y, z);

        if (independent) {
            String message = "In aggregate independent: " + LogUtilsSearch.independenceFact(x, y, z);
            TetradLogger.getInstance().log(message);
        } else {
            String message = "In aggregate dependent: " + LogUtilsSearch.independenceFact(x, y, z);
            TetradLogger.getInstance().log(message);
        }

        IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, z), independent,
                Double.NaN, Double.NaN);
        facts.put(new IndependenceFact(x, y, z), result);
        return result;
    }

    /**
     * Gets the getModel significance level.
     *
     * @return a double
     */
    public double getAlpha() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the significance level for the independence test.
     *
     * @param alpha The significance level to set.
     */
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the list of variables associated with this object.
     *
     * @return the list of variables associated with this object.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param z A list of conditioning variables.
     * @param x The variable x.
     * @return True if variable x is independent of variable y given the conditioning variables z, false otherwise.
     * @throws UnsupportedOperationException if the method is not implemented.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the data set.
     *
     * @return the data set.
     */
    public DataSet getData() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a string representation of this object, which includes the alpha value of the independence test.
     *
     * @return the string representation of this object
     */
    public String toString() {
        return "Pooled Independence Test:  alpha = " + this.independenceTests.iterator().next().getAlpha();
    }

    /**
     * Returns true if the test prints verbose output.
     *
     * @return True if the test prints verbose output, false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed during the test.
     *
     * @param verbose True to enable verbose output, false otherwise.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}

