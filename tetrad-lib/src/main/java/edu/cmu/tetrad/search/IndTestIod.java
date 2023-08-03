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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.ResolveSepsets;
import org.jetbrains.annotations.NotNull;

import javax.help.UnsupportedOperationException;
import java.util.*;


/**
 * <p>Checks independence result by listing all tests with those variables, testing each one, and returning the
 * resolution of these test results. The reference is here:</p>
 *
 * <p>Tillman, R., & Spirtes, P. (2011, June). Learning equivalence classes of acyclic models with latent and selection
 * variables from multiple datasets with overlapping variables. In Proceedings of the Fourteenth International
 * Conference on Artificial Intelligence and Statistics (pp. 3-15). JMLR Workshop and Conference Proceedings.</p>
 *
 * <p>The idea of this implementation is that one initializes this test with multiple independence tests (for multiple
 * datasets), then a call to the independence check method for X _||_ Y | Z list the independence tests from among
 * these, calls each and gets a p-value, then uses a resolution method (such as Fisher's) to resolve these
 * p-values.</p>
 *
 * <p>Based on work by Rob Tillman, Peter Spirtes, and referencing earlier work by David Danks.</p>
 *
 * @author josephramsey
 */
public class IndTestIod implements IndependenceTest {
    private final List<Node> nodeList;
    private final List<IndependenceTest> tests;
    private boolean verbose;

    /**
     * Constructs a new pooled independence test from the given list of independence tests.
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
     * @throws UnsupportedOperationException Since the method is not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Checks the indicated independence fact by pooling available tests for the given variables.
     *
     * @param x one node.
     * @param y a second node.
     * @param z a List of nodes (conditioning variables)
     * @return True iff x _||_ y | z
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
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
     * Returns true if z contains x.
     *
     * @param z A list of nodes.
     * @param x The target node.
     * @return True if z contains x.
     */
    public boolean determines(List<Node> z, Node x) {
        return z.contains(x);
    }

    /**
     * Returns the alpha level for this test.
     *
     * @return This level.
     */
    public double getAlpha() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the alpha level for this test.
     *
     * @param alpha This level.
     */
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the variable associated with the given name in the graph.
     *
     * @param name The name of the variable.
     * @return This variable.
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
     * @return This variable.
     */
    public Node getVariable(Node node) {
        return getVariable(node.getName());
    }

    /**
     * Return the node associated with the given variable in the graph.
     *
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
     * The data set for this test is undefined, since the test is pooled over multiple datasets.
     *
     * @return null.
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
     * Sets the test to verbose or not.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        for (IndependenceTest test : tests) {
            test.setVerbose(verbose);
        }

        this.verbose = verbose;
    }

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








