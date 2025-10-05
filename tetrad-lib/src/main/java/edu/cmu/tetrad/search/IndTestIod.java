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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.ResolveSepsets;
import org.jetbrains.annotations.NotNull;

import java.util.*;


/**
 * Checks independence results by listing all tests with those variables, testing each one, and returning the resolution
 * of these test results. The reference is here:
 * <p>
 * Tillman, R., &amp; Spirtes, P. (2011, June). Learning equivalence classes of acyclic models with latent and selection
 * variables from multiple datasets with overlapping variables. In Proceedings of the Fourteenth International
 * Conference on Artificial Intelligence and Statistics (pp. 3-15). JMLR Workshop and Conference Proceedings.
 * <p>
 * The idea of this implementation is that one initializes this test with multiple independence tests (for multiple
 * datasets), then a call to the independence check method for X _||_ Y | Z list the independence tests from among
 * these, calls each and gets a p-value, then uses a resolution method (such as Fisher's) to resolve these p-values.
 * <p>
 * Based on work by Rob Tillman, Peter Spirtes, and referencing earlier work by David Danks.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IndTestIod implements IndependenceTest {
    /**
     * The list of nodes over which this independence checker is capable of determining independence relations.
     */
    private final List<Node> nodeList;
    /**
     * The list of independence tests.
     */
    private final List<IndependenceTest> tests;
    /**
     * Whether the test is verbose.
     */
    private boolean verbose;

    /**
     * Constructs a new pooled independence test from the given list of independence tests.
     *
     * @param tests a {@link java.util.List} object
     */
    public IndTestIod(List<IndependenceTest> tests) {
        for (IndependenceTest test : tests) {
            if (test == null) {
                throw new NullPointerException("Test is null");
            }

            if (test instanceof IndTestIod) {
                throw new IllegalArgumentException("Cannot have IndTestIod as a test");
            }
        }

        this.tests = tests;

        for (IndependenceTest test : tests) {
            if (test instanceof IndTestIod) {
                throw new IllegalArgumentException("Cannot have IndTestIod as a test");
            }
        }

        Set<String> nameSet = new HashSet<>();
        List<Node> nodeList = new ArrayList<>();

        for (IndependenceTest test : tests) {
            List<Node> vars = test.getVariables();

            for (Node v : vars) {
                if (!nameSet.contains(v.getName())) {
                    nodeList.add(v);
                    nameSet.add(v.getName());
                }
            }
        }

        Collections.sort(nodeList);
        this.nodeList = nodeList;
    }

    /**
     * Calculates the independence test for a subset of variables.
     *
     * @param vars The sublist of variables.
     * @return The independence test result.
     * @throws java.lang.UnsupportedOperationException if this method is not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Checks the independence between two nodes given a set of nodes.
     *
     * @param x The first node.
     * @param y The second node.
     * @param z The set of nodes.
     * @return The result of the independence test.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        List<IndependenceTest> tests = new ArrayList<>();

        for (IndependenceTest test : this.tests) {
            if (containsAll(x, y, z, test)) {
                tests.add(test);
            }
        }

        boolean independent = ResolveSepsets.isIndependentPooled(ResolveSepsets.Method.fisher, tests, x, y, z);

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, Double.NaN, Double.NaN);
    }

    /**
     * Returns the list of TetradNodes over which this independence checker is capable of determining independence
     * relations-- that is, all the variables in the given graph or the given data set.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(nodeList);
    }

    /**
     * Determines whether the variables in z determine x.
     *
     * @param z The list of nodes to search.
     * @param x The node to check for containment.
     * @return True if the node is contained in the list, false otherwise.
     */
    public boolean determines(List<Node> z, Node x) {
        return z.contains(x);
    }

    /**
     * @throws java.lang.UnsupportedOperationException since the method is not implemented.
     */
    public double getAlpha() {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws java.lang.UnsupportedOperationException since the method is not implemented.
     */
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves a variable with the given name.
     *
     * @param name the name of the variable
     * @return the variable with the given name
     * @throws IllegalArgumentException if the variable is not found
     */
    public Node getVariable(String name) {
        for (Node variable : nodeList) {
            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        throw new IllegalArgumentException("Variable not found: " + name);
    }

    /**
     * Returns the variable associated with the given node in the graph.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return This variable.
     */
    public Node getVariable(Node node) {
        return getVariable(node.getName());
    }

    /**
     * Return the node associated with the given variable in the graph.
     *
     * @param variable a {@link edu.cmu.tetrad.graph.Node} object
     * @return This node.
     */
    public Node getNode(Node variable) {
        for (Node node : nodeList) {
            if (node.getName().equals(variable.getName())) {
                return node;
            }
        }

        throw new IllegalArgumentException("Variable not found: " + variable);
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        return "IOD independence test (pooled over datasets)";
    }

    /**
     * @throws UnsupportedOperationException since the method is not implemented.
     */
    public DataSet getData() {
        throw new UnsupportedOperationException("No single dataset; this test pools over multiple datasets.");
    }

    /**
     * Returns true if the test is verbose.
     *
     * @return True, if so.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether this test will print verbose output.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        for (IndependenceTest test : tests) {
            test.setVerbose(verbose);
        }

        this.verbose = verbose;
    }

    /**
     * Determines whether the given nodes and independence test contain all the required variables.
     *
     * @param x    The first node.
     * @param y    The second node.
     * @param z    The set of nodes.
     * @param test The independence test to be performed.
     * @return True if all the required variables are present, false otherwise.
     */
    private boolean containsAll(@NotNull Node x, Node y, Set<Node> z, @NotNull IndependenceTest test) {
        if (test.getVariable(x.getName()) == null) {
            return false;
        }

        if (test.getVariable(y.getName()) == null) {
            return false;
        }

        for (Node _z : z) {
            if (test.getVariable(_z.getName()) == null) {
                return false;
            }
        }

        return true;
    }
}









