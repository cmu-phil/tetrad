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

import java.util.List;
import java.util.Set;

/**
 * One is often faced with the following problem. We start by estimating the adjacencies using
 * FGES followed by FAS. But if X is not adjacent to Z in the resulting graph, and you ask for
 * a sepset, one must be given. So we return a conditioning set that minimizes a score (is as
 * close to independence as possible, or independent).
 *
 * @author jdramsey
 */
public class SepsetsMinScore implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private int depth = 3;
    private double p = Double.NaN;
    private boolean verbose;
    private boolean returnNullWhenIndep;

    public SepsetsMinScore(final Graph graph, final IndependenceTest independenceTest, final int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.depth = depth;
    }

    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest p value.
     */
    public List<Node> getSepset(final Node i, final Node k) {
        return getMinSepset(i, k);
    }

    /**
     * Assumes i--j--k is an unshielded triple.
     */
    public boolean isCollider(final Node i, final Node j, final Node k) {
        return !getMinSepset(i, k).contains(j);
    }

    /**
     * Assumes i--j--k is an unshielded triple.
     */
    public boolean isNoncollider(final Node i, final Node j, final Node k) {
        return getMinSepset(i, k).contains(j);
    }

    private List<Node> getMinSepset(final Node i, final Node k) {
        double _p = Double.POSITIVE_INFINITY;
        List<Node> _v = null;

        final List<Node> adji = this.graph.getAdjacentNodes(i);
        final List<Node> adjk = this.graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((this.depth == -1 ? 1000 : this.depth), Math.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                final ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    final List<Node> v2 = GraphUtils.asList(choice, adji);

                    getIndependenceTest().isIndependent(i, k, v2);
                    final double p2 = getIndependenceTest().getScore();

                    if (this.returnNullWhenIndep) {
                        if (p2 < _p && p2 < 0) {
                            _p = p2;
                            _v = v2;
                        }
                    } else {
                        if (p2 < _p) {
                            _p = p2;
                            _v = v2;
                        }
                    }
                }
            }

            if (d <= adjk.size()) {
                final ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    final List<Node> v2 = GraphUtils.asList(choice, adjk);

                    getIndependenceTest().isIndependent(i, k, v2);
                    final double p2 = getIndependenceTest().getScore();

                    if (this.returnNullWhenIndep) {
                        if (p2 < _p && p2 < 0) {
                            _p = p2;
                            _v = v2;
                        }
                    } else {
                        if (p2 < _p) {
                            _p = p2;
                            _v = v2;
                        }
                    }
                }
            }
        }

        this.p = _p;
        return _v;
    }


    @Override
    public boolean isIndependent(final Node a, final Node b, final List<Node> c) {
        return this.independenceTest.isIndependent(a, b, c);
    }

    @Override
    public double getPValue() {
        return this.p;
    }

    @Override
    public double getScore() {
        return -(this.p - this.independenceTest.getAlpha());
    }

    @Override
    public List<Node> getVariables() {
        return this.independenceTest.getVariables();
    }

    private IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    public void setReturnNullWhenIndep(final boolean returnNullWhenIndep) {
        this.returnNullWhenIndep = returnNullWhenIndep;
    }

    public boolean isReturnNullWhenIndep() {
        return this.returnNullWhenIndep;
    }

    public List<Node> getSepset(final Node a, final Node y, final Set<Node> inSet) {
        return getMinSepset(a, y, inSet);
    }

    private List<Node> getMinSepset(final Node i, final Node k, final Set<Node> insSet) {
        double _p = Double.POSITIVE_INFINITY;
        List<Node> _v = null;

        final List<Node> adji = this.graph.getAdjacentNodes(i);
        final List<Node> adjk = this.graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((this.depth == -1 ? 1000 : this.depth), Math.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                final ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    final List<Node> v2 = GraphUtils.asList(choice, adji);

                    if (!insSet.containsAll(v2)) continue;

                    getIndependenceTest().isIndependent(i, k, v2);
                    final double p2 = getIndependenceTest().getScore();

                    if (this.returnNullWhenIndep) {
                        if (p2 < _p && p2 < 0) {
                            _p = p2;
                            _v = v2;
                        }
                    } else {
                        if (p2 < _p) {
                            _p = p2;
                            _v = v2;
                        }
                    }
                }
            }

            if (d <= adjk.size()) {
                final ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    final List<Node> v2 = GraphUtils.asList(choice, adjk);

                    if (!insSet.containsAll(v2)) continue;

                    getIndependenceTest().isIndependent(i, k, v2);
                    final double p2 = getIndependenceTest().getScore();

                    if (this.returnNullWhenIndep) {
                        if (p2 < _p && p2 < 0) {
                            _p = p2;
                            _v = v2;
                        }
                    } else {
                        if (p2 < _p) {
                            _p = p2;
                            _v = v2;
                        }
                    }
                }
            }
        }

        this.p = _p;
        return _v;
    }
}

