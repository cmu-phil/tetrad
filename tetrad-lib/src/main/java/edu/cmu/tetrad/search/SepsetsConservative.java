///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by josephramsey on 3/24/15.
 */
public class SepsetsConservative implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final SepsetMap extraSepsets;
    private int depth = 3;
    private boolean verbose = false;

    public SepsetsConservative(Graph graph, IndependenceTest independenceTest, SepsetMap extraSepsets, int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.extraSepsets = extraSepsets;
        this.depth = depth;
    }

    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest p value.
     */
    public List<Node> getSepset(Node i, Node k) {
        double _p = 0.0;
        List<Node> _v = null;

        if (extraSepsets != null) {
            final List<Node> possibleDsep = extraSepsets.get(i, k);
            if (possibleDsep != null) {
                independenceTest.isIndependent(i, k, possibleDsep);
                _p = independenceTest.getPValue();
                _v = possibleDsep;
            }
        }

        List<Node> adji = graph.getAdjacentNodes(i);
        List<Node> adjk = graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), Math.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);

                    if (getIndependenceTest().isIndependent(i, k, v)) {
                        double pValue = getIndependenceTest().getPValue();
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
                    List<Node> v = GraphUtils.asList(choice, adjk);
                    if (getIndependenceTest().isIndependent(i, k, v)) {
                        double pValue = getIndependenceTest().getPValue();
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

    public boolean isCollider(Node i, Node j, Node k) {
        List<List<List<Node>>> ret = getSepsetsLists(i, j, k, independenceTest, depth, true);
        return ret.get(0).isEmpty();
    }

    public boolean isNoncollider(Node i, Node j, Node k) {
        List<List<List<Node>>> ret = getSepsetsLists(i, j, k, independenceTest, depth, true);
        return ret.get(1).isEmpty();
    }

    // The published version.
    public List<List<List<Node>>> getSepsetsLists(Node x, Node y, Node z,
                                                  IndependenceTest test, int depth,
                                                  boolean verbose) {
        List<List<Node>> sepsetsContainingY = new ArrayList<List<Node>>();
        List<List<Node>> sepsetsNotContainingY = new ArrayList<List<Node>>();

        List<Node> _nodes = graph.getAdjacentNodes(x);
        _nodes.remove(z);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }

        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.isIndependent(x, z, cond)) {
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

        _nodes = graph.getAdjacentNodes(z);
        _nodes.remove(x);

        _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.isIndependent(x, z, cond)) {
                    if (cond.contains(y)) {
                        sepsetsContainingY.add(cond);
                    } else {
                        sepsetsNotContainingY.add(cond);
                    }
                }
            }
        }

        List<List<List<Node>>> ret = new ArrayList<List<List<Node>>>();
        ret.add(sepsetsContainingY);
        ret.add(sepsetsNotContainingY);

        return ret;
    }


    @Override
    public boolean isIndependent(Node a, Node b, List<Node> c) {
        return independenceTest.isIndependent(a, b, c);
    }

    @Override
    public double getPValue() {
        return independenceTest.getPValue();
    }

    @Override
    public List<Node> getVariables() {
        return independenceTest.getVariables();
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }
}

