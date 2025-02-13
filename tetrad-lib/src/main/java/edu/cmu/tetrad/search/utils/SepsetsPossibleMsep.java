/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.util.SublistGenerator;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>Provides a sepset producer using conditional independence tests to generate
 * the Sepset map, for the case where possible msep sets are required.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see SepsetProducer
 * @see SepsetMap
 */
public class SepsetsPossibleMsep implements SepsetProducer {
    private final Graph graph;
    private final int maxDiscriminatingPathLength;
    private final Knowledge knowledge;
    private final int depth;
    private final IndependenceTest test;
    private boolean verbose;
    private IndependenceResult result;

    /**
     * <p>Constructor for SepsetsPossibleMsep.</p>
     *
     * @param graph                       a {@link edu.cmu.tetrad.graph.Graph} object
     * @param test                        a {@link edu.cmu.tetrad.search.IndependenceTest} object
     * @param knowledge                   a {@link edu.cmu.tetrad.data.Knowledge} object
     * @param depth                       the depth of the search
     * @param maxDiscriminatingPathLength the maximum length of discriminating paths
     */
    public SepsetsPossibleMsep(Graph graph, IndependenceTest test, Knowledge knowledge,
                               int depth, int maxDiscriminatingPathLength) {
        this.graph = graph;
        this.test = test;
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
        this.knowledge = knowledge;
        this.depth = depth;
    }

    /**
     * Retrieves the separation set (sepset) between two nodes.
     *
     * @param i     The first node
     * @param k     The second node
     * @param depth The depth of the search
     * @param order The order of the nodes, used for some implementations.
     * @return The set of nodes that form the sepset between node i and node k, or null if no sepset exists
     */
    public Set<Node> getSepset(Node i, Node k, int depth, List<Node> order) throws InterruptedException {
        Set<Node> condSet = getCondSetContaining(i, k, null, this.maxDiscriminatingPathLength);

        if (condSet == null) {
            condSet = getCondSetContaining(k, i, null, this.maxDiscriminatingPathLength);
        }

        return condSet;
    }

    /**
     * Retrieves the separation set (sepset) between two nodes i and k that contains a given set of nodes s. If there is
     * no required set of nodes, pass null for the set.
     *
     * @param i     The first node
     * @param k     The second node
     * @param s     The set of nodes to be contained in the sepset
     * @param depth The depth of the search
     * @return The set of nodes that form the sepset between node i and node k and contains all nodes from set s, or
     * null if no sepset exists
     */
    @Override
    public Set<Node> getSepsetContaining(Node i, Node k, Set<Node> s, int depth) throws InterruptedException {
        Set<Node> condSet = getCondSetContaining(i, k, s, this.maxDiscriminatingPathLength);

        if (condSet == null) {
            condSet = getCondSetContaining(k, i, s, this.maxDiscriminatingPathLength);
        }

        return condSet;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isUnshieldedCollider(Node i, Node j, Node k, int depth) throws InterruptedException {
        Set<Node> sepset = getSepset(i, k, this.depth, null);
        return sepset != null && !sepset.contains(j);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getScore() {
        return -(this.result.getPValue() - this.test.getAlpha());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return this.test.getVariables();
    }

    /**
     * <p>isVerbose.</p>
     *
     * @return a boolean
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * {@inheritDoc}
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIndependent(Node d, Node c, Set<Node> sepset) throws InterruptedException {
        IndependenceResult result = this.test.checkIndependence(d, c, sepset);
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
        IndependenceResult result = this.test.checkIndependence(a, b, sepset);
        return result.getPValue();
    }

    @Override
    public void setGraph(Graph graph) {
        // Ignored.
    }

    private Set<Node> getCondSetContaining(Node node1, Node node2, Set<Node> s, int maxPathLength) throws InterruptedException {
        List<Node> possibleMsepSet = getPossibleMsep(node1, node2, maxPathLength);
        List<Node> possibleMsep = new ArrayList<>(possibleMsepSet);
        boolean noEdgeRequired = this.knowledge.noEdgeRequired(node1.getName(), node2.getName());

        int _depth = this.depth == -1 ? 1000 : this.depth;
        _depth = FastMath.min(_depth, possibleMsep.size());

        SublistGenerator cg = new SublistGenerator(possibleMsep.size(), _depth);
        int[] choice;

        while ((choice = cg.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (choice.length < 1) continue;

            Set<Node> condSet = GraphUtils.asSet(choice, possibleMsep);

            if (s != null && !condSet.containsAll(s)) {
                continue;
            }

            // check against bk knowledge added by DMalinsky 07/24/17 **/
            //  if (knowledge.isForbidden(node1.getName(), node2.getName())) continue;
            boolean flagForbid = false;
            for (Node j : condSet) {
                if (this.knowledge.isInWhichTier(j) > FastMath.max(this.knowledge.isInWhichTier(node1), this.knowledge.isInWhichTier(node2))) { // condSet cannot be in the future of both endpoints
//                        if (knowledge.isForbidden(j.getName(), node1.getName()) && knowledge.isForbidden(j.getName(), node2.getName())) {
                    flagForbid = true;
                    break;
                }
            }
            if (flagForbid) continue;

            IndependenceResult result = this.test.checkIndependence(node1, node2, condSet);
            this.result = result;

            if (result.isIndependent() && noEdgeRequired) {
                return condSet;
            }
        }

        return null;
    }

    private List<Node> getPossibleMsep(Node x, Node y, int maxPossibleDsepPathLength) {
        List<Node> msep = this.graph.paths().possibleDsep(x, y, maxPossibleDsepPathLength);

        if (this.verbose) {
            System.out.println("Possible-D-Sep(" + x + ", " + y + ") = " + msep);
        }

        return msep;
    }
}

