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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides a SepsetProducer that selects the first sepset it comes to from among the extra sepsets or the adjacents of
 * i or k, or null if none is found.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see SepsetProducer
 * @see SepsetMap
 */
public class SepsetsGreedyMb implements SepsetProducer {
    private final IndependenceTest independenceTest;
    private Graph graph;
    private boolean verbose;
    private IndependenceResult result;
    private Graph cpdag;

    /**
     * <p>Constructor for Sepsets.</p>
     *
     * @param graph            a {@link Graph} object
     * @param cpdag            The cpdag.
     * @param independenceTest a {@link IndependenceTest} object
     * @param depth            a int
     */
    public SepsetsGreedyMb(Graph graph, Graph cpdag, IndependenceTest independenceTest, int depth) {
        this.graph = graph;
        this.cpdag = cpdag;
        this.independenceTest = independenceTest;
    }

    private static double getPValue(Node x, Node y, Set<Node> combination, IndependenceTest test) throws InterruptedException {
        return test.checkIndependence(x, y, combination).getPValue();
    }

    /**
     * Retrieves the separating set (sepset) between two nodes in the graph. This method finds a subset of adjacent
     * nodes that separate the specified nodes based on the provided parameters.
     *
     * @param i     the first node
     * @param k     the second node
     * @param depth the maximum depth of the search
     * @param order the list representing the order in which nodes are processed
     * @return a set of nodes representing the separating set, or an empty set if no such separating set is found
     */
    public Set<Node> getSepset(Node i, Node k, int depth, List<Node> order) {
        return SepsetFinder.findSepsetSubsetOfAdjxOrAdjy(graph, i, k, new HashSet<>(), this.independenceTest, depth);
    }

    /**
     * Retrieves a sepset (separating set) between two nodes containing a set of nodes, containing the nodes in s, or
     * null if no such sepset is found. If there is no required set of nodes, pass null for the set.
     *
     * @param i     The first node
     * @param k     The second node
     * @param s     The set of nodes that must be contained in the sepset, or null if no such set is required.
     * @param depth The depth of the search
     * @return The sepset between the two nodes
     */
    @Override
    public Set<Node> getSepsetContaining(Node i, Node k, Set<Node> s, int depth) {
        return SepsetFinder.getSepsetContainingGreedySubsetMb(graph, cpdag, i, k, s, this.independenceTest, depth);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isUnshieldedCollider(Node i, Node j, Node k, int depth) {
        Set<Node> set = SepsetFinder.getSepsetContainingGreedySubsetMb(graph, cpdag, i, k, null, this.independenceTest, depth);
        return set != null && !set.contains(j);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIndependent(Node a, Node b, Set<Node> sepset) throws InterruptedException {
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
    public double getPValue(Node a, Node b, Set<Node> sepset) throws InterruptedException {
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

    private Set<Node> possibleParents(Node x, Set<Node> adjx, Knowledge knowledge, Node y) {
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


