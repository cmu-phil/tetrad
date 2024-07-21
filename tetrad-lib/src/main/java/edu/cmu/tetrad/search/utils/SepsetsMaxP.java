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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.MsepTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The class SepsetsMaxP implements the SepsetProducer interface and provides methods for generating sepsets based on a
 * given graph and an independence test. It also allows for checking conditional independencies and calculating p-values
 * for statistical tests.
 * <p>
 * This class tries to maximize the p-value of the independence test result when selecting sepsets.
 */
public class SepsetsMaxP implements SepsetProducer {
    private final IndependenceTest independenceTest;
    private Graph graph;
    private boolean verbose;
    private IndependenceResult result;

    /**
     * Constructs a SepsetsMaxP object with the given graph, independence test, and depth.
     *
     * @param graph            The graph representing the causal relationships between nodes.
     * @param independenceTest The independence test used to determine the conditional independence between variables.
     * @param depth            The depth of the sepsets search.
     */
    public SepsetsMaxP(Graph graph, IndependenceTest independenceTest, int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
    }

    /**
     * Retrieves the sepset (separating set) between two nodes which contains a set of nodes. If no such sepset is
     * found, it returns null.
     *
     * @param i     The first node.
     * @param k     The second node.
     * @param depth
     * @return The sepset between the two nodes containing the specified set of nodes.
     */
    public Set<Node> getSepset(Node i, Node k, int depth) {
        return SepsetFinder.getSepsetContainingMaxP(graph, i, k, null, this.independenceTest, depth);
    }

    /**
     * Retrieves a sepset (separating set) between two nodes containing a set of nodes containing the nodes in s, or
     * null if no such sepset is found. If there is no required set of nodes, pass null for the set.
     *
     * @param i     The first node
     * @param k     The second node
     * @param s     The set of nodes that the sepset must contain
     * @param depth
     * @return The sepset between the two nodes containing the specified set of nodes
     */
    @Override
    public Set<Node> getSepsetContaining(Node i, Node k, Set<Node> s, int depth) {
        return SepsetFinder.getSepsetContainingMaxP(graph, i, k, s, this.independenceTest, depth);
    }

    /**
     * Determines if a node is an unshielded collider between two other nodes.
     *
     * @param i     The first node.
     * @param j     The node to check.
     * @param k     The second node.
     * @param depth
     * @return true if the node j is an unshielded collider between nodes i and k, false otherwise.
     */
    public boolean isUnshieldedCollider(Node i, Node j, Node k, int depth) {
        Set<Node> set = SepsetFinder.getSepsetContainingMaxP(graph, i, k, null, this.independenceTest, depth);
        return set != null && !set.contains(j);
    }

    /**
     * Determines if two nodes are independent given a set of separating nodes.
     *
     * @param a      The first node
     * @param b      The second node
     * @param sepset The set of separating nodes
     * @return true if the nodes a and b are independent, false otherwise
     */
    @Override
    public boolean isIndependent(Node a, Node b, Set<Node> sepset) {
        IndependenceResult result = this.independenceTest.checkIndependence(a, b, sepset);
        this.result = result;
        return result.isIndependent();
    }

    /**
     * Retrieves the p-value from the result of an independence test between two nodes, given a set of separating
     * nodes.
     *
     * @param a      The first node
     * @param b      The second node
     * @param sepset The set of separating nodes
     * @return The p-value from the independence test result
     */
    @Override
    public double getPValue(Node a, Node b, Set<Node> sepset) {
        IndependenceResult result = this.independenceTest.checkIndependence(a, b, sepset);
        return result.getPValue();
    }

    /**
     * Sets the graph for the SepsetsMaxP object.
     *
     * @param graph The graph to set
     */
    @Override
    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    /**
     * Calculates the score for the given Sepsets object.
     *
     * @return The score calculated based on the result's p-value and the independence test's alpha value.
     */
    @Override
    public double getScore() {
        return -(result.getPValue() - this.independenceTest.getAlpha());
    }

    /**
     * Retrieves the variables used in the independence test.
     *
     * @return A list of Node objects representing the variables used in the independence test.
     */
    @Override
    public List<Node> getVariables() {
        return this.independenceTest.getVariables();
    }

    /**
     * Returns whether the object is in verbose mode.
     *
     * @return true if the object is in verbose mode, false otherwise
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbosity level for this object. When verbose mode is set to true, additional debugging information will
     * be printed during the execution of this method.
     *
     * @param verbose The verbosity level to set. Set to true for verbose output, false otherwise.
     */
    @Override
    public void setVerbose(boolean verbose) {
        independenceTest.setVerbose(verbose);
        this.verbose = verbose;
    }

    /**
     * Retrieves the Directed Acyclic Graph (DAG) produced by the Sepset algorithm.
     *
     * @return The DAG produced by the Sepsets algorithm, or null if the independence test is not an instance of
     * MsepTest.
     */
    public Graph getDag() {
        if (this.independenceTest instanceof MsepTest) {
            return ((MsepTest) this.independenceTest).getGraph();
        } else {
            return null;
        }
    }

    private Set<Node> possibleParents(Node x, Set<Node> adjx,
                                      Knowledge knowledge, Node y) {
        Set<Node> possibleParents = new HashSet<>();
        String _x = x.getName();

        for (Node z : adjx) {
            if (z == x) continue;
            if (z == y) continue;
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

}

