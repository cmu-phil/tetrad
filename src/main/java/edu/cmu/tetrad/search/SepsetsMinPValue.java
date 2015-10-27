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

/**
 * Created by josephramsey on 3/24/15.
 */
public class SepsetsMinPValue implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final SepsetMap extraSepsets;
    private int depth = 3;
    private double p = Double.NaN;

    public SepsetsMinPValue(Graph graph, IndependenceTest independenceTest, SepsetMap extraSepsets, int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.extraSepsets = extraSepsets;
        this.depth = depth;
    }

    @Override
    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest p value.
     */
    public List<Node> getSepset(Node i, Node k) {
        return getMaxSepset(i, k);
    }

    public boolean isCollider(Node i, Node j, Node k) {
//        if (graph.isAdjacentTo(i, k)) return false;
        List<Node> _v = getMaxSepset(i, k);
        return /*getPValue() > independenceTest.getAlpha() &&*/ _v != null && !_v.contains(j);
    }

    public boolean isNoncollider(Node i, Node j, Node k) {
//        if (graph.isAdjacentTo(i, k)) return false;
        List<Node> _v = getMaxSepset(i, k);
        return /*getPValue() > independenceTest.getAlpha() &&*/ _v != null && _v.contains(j);
    }

    private List<Node> getMaxSepset(Node i, Node k) {
        double _p = 0.0;
        List<Node> _v = null;

        if (extraSepsets != null) {
            final List<Node> possibleDsep = extraSepsets.get(i, k);
            if (possibleDsep != null) {
                independenceTest.isIndependent(i, k, possibleDsep);
                double p = independenceTest.getPValue();

                if (p < _p) {
                    _p = p;
                    _v = possibleDsep;
                }
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

                    getIndependenceTest().isIndependent(i, k, v);
                    double p = getIndependenceTest().getPValue();

                    if (p < _p) {
                        _p = p;
                        _v = v;
                    }
                }
            }

            if (d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adjk);

                    getIndependenceTest().isIndependent(i, k, v);
                    double p = getIndependenceTest().getPValue();

                    if (p < _p) {
                        _p = p;
                        _v = v;
                    }
                }
            }
        }

        this.p = _p;
        return _v;
    }


    @Override
    public boolean isIndependent(Node a, Node b, List<Node> c) {
        return independenceTest.isIndependent(a, b, c);
    }

    @Override
    public double getPValue() {
        return p;
    }

    @Override
    public List<Node> getVariables() {
        return independenceTest.getVariables();
    }

    private IndependenceTest getIndependenceTest() {
        return independenceTest;
    }
}

