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
 * The SepsetsMinP class is a concrete implementation of the SepsetProducer interface. It calculates the separating sets
 * (sepsets) between nodes in a given graph using a minimum p-value approach. The sepsets are calculated based on an
 * independence test provided to the class.
 * <p>
 * This class tries to minimize the p-value of the independence test result when selecting sepsets.
 */
public class SepsetsMinP implements SepsetProducer {
    /**
     * The independenceTest variable represents an object that performs an independence test between two nodes given a
     * set of separator nodes. It provides methods for retrieving the sepset (separating set) between two nodes,
     * checking if two nodes are independent, calculating the p-value for the independence test, setting the graph,
     * getting the score, retrieving the variables used in the independence test, setting verbosity level, and
     * retrieving the produced DAG (Directed Acyclic Graph) by the Sepsets algorithm.
     * <p>
     * This variable is used in the SepsetsMinP class as one of its fields to perform various operations related to
     * independence tests and separation sets.
     *
     * @see SepsetsMinP
     */
    private final IndependenceTest independenceTest;
    /**
     * This private variable represents a graph.
     * <p>
     * The graph is used within the SepsetsMinP class for storing and manipulating nodes and their relationships. It is
     * a directed acyclic graph (DAG) produced by the Sepsets algorithm.
     * <p>
     * Methods within the SepsetsMinP class may use this variable to perform calculations and retrieve information
     * related to nodes and their relationships. The graph is set using the setGraph() method.
     * <p>
     * It is important to note that this variable is declared as private, which means it can only be accessed within the
     * same class.
     */
    private Graph graph;
    /**
     * Represents the result of an independence test in the context of the SepsetsMinP class. This variable stores
     * information about the sepsets (separating sets) between different nodes in a graph.
     */
    private IndependenceResult result;
    /**
     * Returns whether the object is in verbose mode.
     */
    private boolean verbose;

    /**
     * Initializes a new instance of the SepsetsMinP class.
     *
     * @param graph            The graph to set.
     * @param independenceTest The independence test used for calculating sepsets.
     * @param depth            The depth of the sepsets search algorithm.
     */
    public SepsetsMinP(Graph graph, IndependenceTest independenceTest, int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
    }

    /**
     * Retrieves the sepset (separating set) between two nodes, or null if no such sepset is found.
     *
     * @param i     The first node
     * @param k     The second node
     * @param depth
     * @return The sepset between the two nodes
     */
    public Set<Node> getSepset(Node i, Node k, int depth) {
        return SepsetFinder.getSepsetContainingMinP(graph, i, k, null, this.independenceTest, depth);
    }

    /**
     * Retrieves a sepset (separating set) between two nodes containing a set of nodes containing the nodes in s, or
     * null if no such sepset is found. If there is no required set of nodes, pass null for the set.
     *
     * @param i     The first node
     * @param k     The second node
     * @param s     The set of nodes that must be contained in the sepset, or null if no such set is required.
     * @param depth
     * @return The sepset between the two nodes
     */
    @Override
    public Set<Node> getSepsetContaining(Node i, Node k, Set<Node> s, int depth) {
        return SepsetFinder.getSepsetContainingMinP(graph, i, k, s, this.independenceTest, depth);
    }

    /**
     * Checks if a given collider node is unshielded between two other nodes.
     *
     * @param i     The first node.
     * @param j     The collider node.
     * @param k     The second node.
     * @param depth
     * @return true if the collider node is unshielded between the two nodes, false otherwise.
     */
    public boolean isUnshieldedCollider(Node i, Node j, Node k, int depth) {
        Set<Node> set = SepsetFinder.getSepsetContainingMinP(graph, i, k, null, this.independenceTest, depth);
        return set != null && !set.contains(j);
    }

    /**
     * Determines if two nodes are independent given a set of separating nodes.
     *
     * @param a      The first node.
     * @param b      The second node.
     * @param sepset The set of separating nodes.
     * @return true if the two nodes are independent, false otherwise.
     */
    @Override
    public boolean isIndependent(Node a, Node b, Set<Node> sepset) {
        IndependenceResult result = this.independenceTest.checkIndependence(a, b, sepset);
        this.result = result;
        return result.isIndependent();
    }

    /**
     * Returns the p-value for the independence test between two nodes, given a set of separator nodes.
     *
     * @param a      the first node
     * @param b      the second node
     * @param sepset the set of separator nodes
     * @return the p-value for the independence test
     */
    @Override
    public double getPValue(Node a, Node b, Set<Node> sepset) {
        IndependenceResult result = this.independenceTest.checkIndependence(a, b, sepset);
        return result.getPValue();
    }

    /**
     * Sets the graph for the Sepsets object.
     *
     * @param graph The graph to set.
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
     * Retrieves the Directed Acyclic Graph (DAG) produced by the Sepsets algorithm.
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
