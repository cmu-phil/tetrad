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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

/**
 * Created by josephramsey on 3/24/15.
 */
public class SepsetsMaxPValuePossDsep implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final SepsetMap extraSepsets;
    private int depth = 3;
    private int maxPathLength = 3;
    private double p = Double.NaN;
    private boolean verbose = false;

    public SepsetsMaxPValuePossDsep(final Graph graph, final IndependenceTest independenceTest, final SepsetMap extraSepsets, final int depth, final int maxPathLength) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.extraSepsets = extraSepsets;
        this.depth = depth;
        this.maxPathLength = maxPathLength;
    }

    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest p value.
     */
    public List<Node> getSepset(final Node i, final Node k) {
        final List<Node> sepset = getMaxSepset(i, k);
        if (getScore() < 0) {// getIndependenceTest().getAlpha()) {
            return sepset;
        } else {
            return null;
        }
    }

    public boolean isCollider(final Node i, final Node j, final Node k) {
        final List<Node> _v = getMaxSepset(i, k);
        return _v != null && !_v.contains(j);
    }

    public boolean isNoncollider(final Node i, final Node j, final Node k) {
        final List<Node> _v = getMaxSepset(i, k);
        return _v != null && _v.contains(j);
    }

    private List<Node> getMaxSepset(final Node i, final Node k) {
        double _p = 0.0;
        List<Node> _v = null;

//        if (extraSepsets != null) {
//            final List<Node> possibleDsep = extraSepsets.get(i, k);
//            if (possibleDsep != null) {
//                independenceTest.isIndependent(i, k, possibleDsep);
//                double p = independenceTest.getScore();
//
//                if (p > _p) {
//                    _p = p;
//                    _v = possibleDsep;
//                }
//            }
//        }

//        List<Node> adji = graph.getAdjacentNodes(i);
//        List<Node> adjk = graph.getAdjacentNodes(k);
        final List<Node> adji = new ArrayList<>(possibleDsep(i, k, this.graph, this.maxPathLength));
        final List<Node> adjk = new ArrayList<>(possibleDsep(k, i, this.graph, this.maxPathLength));
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((this.depth == -1 ? 1000 : this.depth), Math.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                final ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    final List<Node> v = GraphUtils.asList(choice, adji);

                    getIndependenceTest().isIndependent(i, k, v);
                    final double p = getIndependenceTest().getPValue();

                    if (p > _p) {
                        _p = p;
                        _v = v;
                    }
                }
            }

            if (d <= adjk.size()) {
                final ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    final List<Node> v = GraphUtils.asList(choice, adjk);

                    getIndependenceTest().isIndependent(i, k, v);
                    final double p = getIndependenceTest().getPValue();

                    if (p > _p) {
                        _p = p;
                        _v = v;
                    }
                }
            }
        }

        this.p = _p;
        return _v;
    }

    public static Set<Node> possibleDsep(final Node x, final Node y, final Graph graph, final int maxPathLength) {
        final Set<Node> dsep = new HashSet<>();

        final Queue<OrderedPair<Node>> Q = new ArrayDeque<>();
        final Set<OrderedPair<Node>> V = new HashSet<>();

        final Map<Node, List<Node>> previous = new HashMap<>();
        previous.put(x, null);

        OrderedPair e = null;
        int distance = 0;

        for (final Node b : graph.getAdjacentNodes(x)) {
            if (b == y) continue;
            final OrderedPair<Node> edge = new OrderedPair<>(x, b);
            if (e == null) e = edge;
            Q.offer(edge);
            V.add(edge);
            addToList(previous, b, x);
            dsep.add(b);
        }

        while (!Q.isEmpty()) {
            final OrderedPair<Node> t = Q.poll();

            if (e == t) {
                e = null;
                distance++;
                if (distance > 0 && distance > (maxPathLength == -1 ? 1000 : maxPathLength)) break;
            }

            final Node a = t.getFirst();
            final Node b = t.getSecond();

            if (existOnePathWithPossibleParents(previous, b, x, b, graph)) {
                dsep.add(b);
            }

            for (final Node c : graph.getAdjacentNodes(b)) {
                if (c == a) continue;
                if (c == x) continue;
                if (c == y) continue;

                addToList(previous, b, c);

                if (graph.isDefCollider(a, b, c) || graph.isAdjacentTo(a, c)) {
                    final OrderedPair<Node> u = new OrderedPair<>(a, c);
                    if (V.contains(u)) continue;

                    V.add(u);
                    Q.offer(u);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        dsep.remove(x);
        dsep.remove(y);
        return dsep;
    }

    private static boolean existOnePathWithPossibleParents(final Map<Node, List<Node>> previous, final Node w, final Node x, final Node b, final Graph graph) {
        if (w == x) return true;
        final List<Node> p = previous.get(w);
        if (p == null) return false;

        for (final Node r : p) {
            if (r == b || r == x) continue;

            if ((existsSemidirectedPath(r, x, graph)) ||
                    existsSemidirectedPath(r, b, graph)) {
                if (existOnePathWithPossibleParents(previous, r, x, b, graph)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void addToList(final Map<Node, List<Node>> previous, final Node b, final Node c) {
        List<Node> list = previous.get(c);

        if (list == null) {
            list = new ArrayList<>();
        }

        list.add(b);
    }

    public static boolean existsSemidirectedPath(final Node from, final Node to, final Graph G) {
        final Queue<Node> Q = new LinkedList<>();
        final Set<Node> V = new HashSet<>();
        Q.offer(from);
        V.add(from);

        while (!Q.isEmpty()) {
            final Node t = Q.remove();
            if (t == to) return true;

            for (final Node u : G.getAdjacentNodes(t)) {
                final Edge edge = G.getEdge(t, u);
                final Node c = Edges.traverseSemiDirected(t, edge);

                if (c == null) continue;
                if (V.contains(c)) continue;

                V.add(c);
                Q.offer(c);
            }
        }

        return false;
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
        return this.independenceTest.getScore();

//        return -(p - independenceTest.getAlpha());
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

}

