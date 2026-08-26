///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
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
        List<IndependenceTest> subs = new java.util.ArrayList<>(this.independenceTests.size());
        for (IndependenceTest test : this.independenceTests) {
            List<Node> local = new java.util.ArrayList<>(vars.size());
            for (Node node : vars) local.add(test.getVariable(node.getName()));
            subs.add(test.indTestSubset(local));
        }
        return new IndTestMulti(subs, this.method);
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        IndependenceFact fact = new IndependenceFact(x, y, z);
        IndependenceResult cached = facts.get(fact);
        if (cached != null) return cached;

        // The pooled p-value is reported where the method defines one (fisher, fisher2, tippett); for the
        // vote-style methods getPValuePooled returns 1.0/0.0, which is still a usable indicator.
        double p = ResolveSepsets.getPValuePooled(this.method, this.independenceTests, x, y, z);
        boolean independent = p > getAlpha();

        if (this.verbose) {
            String message = (independent ? "In aggregate independent: " : "In aggregate dependent: ")
                             + LogUtilsSearch.independenceFact(x, y, z) + " p = " + p;
            TetradLogger.getInstance().log(message);
        }

        IndependenceResult result = new IndependenceResult(fact, independent, p, getAlpha() - p);
        facts.put(fact, result);
        return result;
    }

    /**
     * Returns the alpha of the component tests (they are required to agree; the first is reported).
     *
     * @return the alpha level.
     */
    public double getAlpha() {
        return this.independenceTests.getFirst().getAlpha();
    }

    /**
     * Sets the alpha level on every component test and clears the cache.
     *
     * @param alpha the alpha level.
     */
    public void setAlpha(double alpha) {
        for (IndependenceTest test : this.independenceTests) test.setAlpha(alpha);
        this.facts.clear();
    }

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
        for (IndependenceTest test : this.independenceTests) {
            Set<Node> localZ = new HashSet<>();
            for (Node node : z) localZ.add(test.getVariable(node.getName()));
            if (test.determines(localZ, test.getVariable(x.getName()))) return true;
        }
        return false;
    }

    /**
     * Returns the data of the first component test; the pooled test has no single data set.
     *
     * @return the first component test's data.
     */
    public edu.cmu.tetrad.data.DataModel getData() {
        return this.independenceTests.getFirst().getData();
    }

    /**
     * Retrieves the list of independence tests managed by this instance.
     * The returned list is unmodifiable to ensure the integrity of the underlying data.
     *
     * @return an unmodifiable list of {@link IndependenceTest} objects.
     */
    public List<IndependenceTest> getIndependenceTests() {
        return java.util.Collections.unmodifiableList(this.independenceTests);
    }

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


