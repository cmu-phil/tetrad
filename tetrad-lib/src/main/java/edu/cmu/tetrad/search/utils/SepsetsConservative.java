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
 * @see SepsetProducer
 * @see SepsetMap
 * @see Cpc
 * @version $Id: $Id
 */
public class SepsetsConservative implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final SepsetMap extraSepsets;
    private final int depth;
    private IndependenceResult lastResult;

    /**
     * <p>Constructor for SepsetsConservative.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param independenceTest a {@link edu.cmu.tetrad.search.IndependenceTest} object
     * @param extraSepsets a {@link edu.cmu.tetrad.search.utils.SepsetMap} object
     * @param depth a int
     */
    public SepsetsConservative(Graph graph, IndependenceTest independenceTest, SepsetMap extraSepsets, int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.extraSepsets = extraSepsets;
        this.depth = depth;
    }

    /**
     * {@inheritDoc}
     *
     * Pick out the sepset from among adj(i) or adj(k) with the highest p value.
     */
    public Set<Node> getSepset(Node i, Node k) {
        double _p = 0.0;
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

                    IndependenceResult result = getIndependenceTest().checkIndependence(i, k, v);

                    if (result.isIndependent()) {
                        double pValue = result.getPValue();
                        if (pValue > _p) {
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
                    IndependenceResult result = getIndependenceTest().checkIndependence(i, k, v);

                    if (result.isIndependent()) {
                        double pValue = result.getPValue();
                        if (pValue > _p) {
                            _p = pValue;
                            _v = v;
                        }
                    }
                }
            }
        }

        return _v;
    }

    /** {@inheritDoc} */
    public boolean isUnshieldedCollider(Node i, Node j, Node k) {
        List<List<Set<Node>>> ret = getSepsetsLists(i, j, k, this.independenceTest, this.depth, true);
        return ret.get(0).isEmpty();
    }

    // The published version.
    /**
     * <p>getSepsetsLists.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     * @param test a {@link edu.cmu.tetrad.search.IndependenceTest} object
     * @param depth a int
     * @param verbose a boolean
     * @return a {@link java.util.List} object
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


    /** {@inheritDoc} */
    @Override
    public boolean isIndependent(Node a, Node b, Set<Node> c) {
        IndependenceResult result = this.independenceTest.checkIndependence(a, b, c);
        this.lastResult = result;
        return result.isIndependent();
    }

    /** {@inheritDoc} */
    @Override
    public double getScore() {
        return -(this.lastResult.getPValue() - this.independenceTest.getAlpha());
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getVariables() {
        return this.independenceTest.getVariables();
    }

    /** {@inheritDoc} */
    @Override
    public void setVerbose(boolean verbose) {
    }

    /**
     * <p>Getter for the field <code>independenceTest</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }
}

