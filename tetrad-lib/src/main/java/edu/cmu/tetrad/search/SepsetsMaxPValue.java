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
 * Returns the sepset from among the adjacents of i or the adjacents of k or the 'extra' sepsets with
 * the highest p value as judged by the given independence test.
 */
public class SepsetsMaxPValue implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final SepsetMap extraSepsets;
    private int depth = 3;
    private double p = Double.NaN;
    private boolean verbose;

    public SepsetsMaxPValue(Graph graph, IndependenceTest independenceTest, SepsetMap extraSepsets, int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.extraSepsets = extraSepsets;
        this.depth = depth;
    }

    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest p value.
     */
    public List<Node> getSepset(Node i, Node k) {
        return getMaxSepset(i, k);
    }

    public boolean isCollider(Node i, Node j, Node k) {
        List<Node> _v = getMaxSepset(i, k);
        return _v != null && !_v.contains(j);
    }

    public boolean isNoncollider(Node i, Node j, Node k) {
        List<Node> _v = getMaxSepset(i, k);
        return _v != null && _v.contains(j);
    }

    private List<Node> getMaxSepset(Node i, Node k) {
        double _p = 0.0;
        List<Node> _v = null;

        if (this.extraSepsets != null) {
            List<Node> sepset = this.extraSepsets.get(i, k);

            if (sepset != null) {
                this.independenceTest.isIndependent(i, k, sepset);
                double p = this.independenceTest.getPValue();

                if (p > _p) {
                    _p = p;
                    _v = sepset;
                }
            }
        }

        List<Node> adji = this.graph.getAdjacentNodes(i);
        List<Node> adjk = this.graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((this.depth == -1 ? 1000 : this.depth), Math.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);

                    getIndependenceTest().isIndependent(i, k, v);
                    double p = getIndependenceTest().getPValue();

                    if (p > _p) {
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


    @Override
    public boolean isIndependent(Node a, Node b, List<Node> c) {
        return this.independenceTest.isIndependent(a, b, c);
    }

    @Override
    public double getPValue() {
        return this.independenceTest.getPValue();
    }

    @Override
    public double getScore() {
        return -(this.independenceTest.getPValue() - this.independenceTest.getAlpha());
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
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }


}

