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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Cpc;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.util.ChoiceGenerator;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>Provides a SepsetProcuder that selects the first sepset it comes to from
 * among the extra sepsets or the adjacents of i or k, or null if none is found. This version uses conservative
 * reasoning (see the CPC algorithm).</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see SepsetProducer
 * @see SepsetMap
 * @see Cpc
 */
public class SepsetsMinP implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final SepsetMap extraSepsets;
    private final int depth;
    private IndependenceResult lastResult;

    /**
     * <p>Constructor for SepsetsConservative.</p>
     *
     * @param graph            a {@link Graph} object
     * @param independenceTest a {@link IndependenceTest} object
     * @param extraSepsets     a {@link SepsetMap} object
     * @param depth            a int
     */
    public SepsetsMinP(Graph graph, IndependenceTest independenceTest, SepsetMap extraSepsets, int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.extraSepsets = extraSepsets;
        this.depth = depth;
    }

    /**
     * Returns the set of nodes that form the sepset (separating set) between two given nodes.
     *
     * @param i a {@link Node} object representing the first node.
     * @param k a {@link Node} object representing the second node.
     * @return a {@link Set} of nodes that form the sepset between the two given nodes.
     */
    public Set<Node> getSepset(Node i, Node k) {
        return getSepsetContaining(i, k, null);
    }

    /**
     * Returns the set of nodes that form the sepset (separating set) between two given nodes containing all the
     * nodes in the given set. If there is no required set of nodes to include, pass null for s.
     *
     * @param i a {@link Node} object representing the first node.
     * @param k a {@link Node} object representing the second node.
     * @param s a {@link Set} of nodes to that must be included in the sepset, or null if there is no such requirement.
     * @return a {@link Set} of nodes that form the sepset between the two given nodes.
     */
    @Override
    public Set<Node> getSepsetContaining(Node i, Node k, Set<Node> s) {
        double _p = 2;
        Set<Node> _v = null;

        if (this.extraSepsets != null) {
            Set<Node> possibleMsep = this.extraSepsets.get(i, k);
            if (possibleMsep != null) {
                IndependenceResult result = this.independenceTest.checkIndependence(i, k, possibleMsep);
                _p = result.getPValue();
                _v = possibleMsep;
            }
        }

        List<Node> adji = new ArrayList<>(this.graph.getAdjacentNodes(i));
        List<Node> adjk = new ArrayList<>(this.graph.getAdjacentNodes(k));
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= FastMath.min((this.depth == -1 ? 1000 : this.depth), FastMath.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Set<Node> v = GraphUtils.asSet(choice, adji);

                    if (s != null && v.containsAll(s)) {
                        continue;
                    }

                    IndependenceResult result = getIndependenceTest().checkIndependence(i, k, v);

                    if (result.isIndependent()) {
                        double pValue = result.getPValue();
                        if (pValue < _p) {
                            _p = pValue;
                            _v = v;
                        }
                    }
                }
            }

            if (d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Set<Node> v = GraphUtils.asSet(choice, adjk);

                    if (s != null && v.containsAll(s)) {
                        continue;
                    }

                    IndependenceResult result = getIndependenceTest().checkIndependence(i, k, v);

                    if (result.isIndependent()) {
                        double pValue = result.getPValue();
                        if (pValue < _p) {
                            _p = pValue;
                            _v = v;
                        }
                    }
                }
            }
        }

        return _v;

    }

    /**
     * {@inheritDoc}
     */
    public boolean isUnshieldedCollider(Node i, Node j, Node k) {
        List<List<Set<Node>>> ret = getSepsetsLists(i, j, k, this.independenceTest, this.depth, false);
        return ret.get(0).isEmpty();
    }

    // The published version.

    /**
     * <p>getSepsetsLists.</p>
     *
     * @param x       a {@link Node} object
     * @param y       a {@link Node} object
     * @param z       a {@link Node} object
     * @param test    a {@link IndependenceTest} object
     * @param depth   a int
     * @param verbose a boolean
     * @return a {@link List} object
     */
    public List<List<Set<Node>>> getSepsetsLists(Node x, Node y, Node z,
                                                 IndependenceTest test, int depth,
                                                 boolean verbose) {
        List<Set<Node>> sepsetsContainingY = new ArrayList<>();
        List<Set<Node>> sepsetsNotContainingY = new ArrayList<>();

        List<Node> _nodes = new ArrayList<>(this.graph.getAdjacentNodes(x));
        _nodes.remove(z);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }

        _depth = FastMath.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                Set<Node> cond = GraphUtils.asSet(choice, _nodes);

                if (test.checkIndependence(x, z, cond).isIndependent()) {
                    if (verbose) {
                        System.out.println("Indep: " + x + " _||_ " + z + " | " + cond);
                    }

                    if (cond.contains(y)) {
                        sepsetsContainingY.add(cond);
                    } else {
                        sepsetsNotContainingY.add(cond);
                    }
                }
            }
        }

        _nodes = new ArrayList<>(this.graph.getAdjacentNodes(z));
        _nodes.remove(x);

        _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = FastMath.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                Set<Node> cond = GraphUtils.asSet(choice, _nodes);

                if (test.checkIndependence(x, z, cond).isIndependent()) {
                    if (cond.contains(y)) {
                        sepsetsContainingY.add(cond);
                    } else {
                        sepsetsNotContainingY.add(cond);
                    }
                }
            }
        }

        List<List<Set<Node>>> ret = new ArrayList<>();
        ret.add(sepsetsContainingY);
        ret.add(sepsetsNotContainingY);

        return ret;
    }


    /**
     * Determines if two nodes are independent given a set of separator nodes.
     *
     * @param a      A {@link Node} object representing the first node.
     * @param b      A {@link Node} object representing the second node.
     * @param sepset A {@link Set} object representing the set of separator nodes.
     * @return True if the nodes are independent, false otherwise.
     */
    @Override
    public boolean isIndependent(Node a, Node b, Set<Node> sepset) {
        IndependenceResult result = this.independenceTest.checkIndependence(a, b, sepset);
        this.lastResult = result;
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
     * {@inheritDoc}
     */
    @Override
    public double getScore() {
        return -(this.lastResult.getPValue() - this.independenceTest.getAlpha());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return this.independenceTest.getVariables();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVerbose(boolean verbose) {
    }

    /**
     * <p>Getter for the field <code>independenceTest</code>.</p>
     *
     * @return a {@link IndependenceTest} object
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }
}

