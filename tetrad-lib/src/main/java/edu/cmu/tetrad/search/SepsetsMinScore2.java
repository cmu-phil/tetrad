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
 * Selects the maximum score value sepset
 * <score>
 * Created by josephramsey on 3/24/15.
 */
public class SepsetsMinScore2 implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final SepsetMap extraSepsets;
    private int depth = 3;
    private double score = Double.NaN;
    private boolean verbose = false;
    private double p = Double.NaN;

    public SepsetsMinScore2(Graph graph, IndependenceTest independenceTest, SepsetMap extraSepsets, int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.extraSepsets = extraSepsets;
        this.depth = depth;
    }

    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest score value.
     */
    public List<Node> getSepset(Node i, Node k) {
        return getMinScoreSet(i, k);
    }

    public boolean isCollider(Node i, Node j, Node k) {
        List<Node> set = getMinScoreSet(i, k);
        return set != null && !set.contains(j);
    }

    public boolean isNoncollider(Node i, Node j, Node k) {
        List<Node> set = getMinScoreSet(i, k);
        return set != null && set.contains(j);
    }

    private List<Node> getMinScoreSet(Node i, Node k) {
        double score = Double.POSITIVE_INFINITY;
        List<Node> _v = null;

        getIndependenceTest().isIndependent(i, k, new ArrayList<Node>());
        double _score = getIndependenceTest().getScore();

        if (_score < 0 && _score < score) {
            score = _score;
            this.p = independenceTest.getPValue();
            _v = new ArrayList<>();
        }

        List<Node> adji = graph.getAdjacentNodes(i);
        List<Node> adjk = graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), adji.size()); d++) {
            List<Node> v = new ArrayList<>();

            while (v.size() < adji.size()) {
                for (int p = 0; p < adji.size(); p++) {
                    Node A = adji.get(p);
                    if (!v.contains(A)) v.add(A);
                    getIndependenceTest().isIndependent(i, k, v);
                    double s0 = getIndependenceTest().getScore();

                    if (_score < 0 && _score < score) {
                        score = _score;
                        this.p = independenceTest.getPValue();
                        _v = v;
                    }
                }
            }
        }

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), adjk.size()); d++) {
            List<Node> v = new ArrayList<>();

            while (v.size() < adjk.size()) {

                for (int p = 0; p < adjk.size(); p++) {
                    Node A = adjk.get(p);
                    if (!v.contains(A)) v.add(A);
                    getIndependenceTest().isIndependent(i, k, v);
                    double s0 = getIndependenceTest().getScore();

                    if (_score < 0 && _score < score) {
                        score = _score;
                        this.p = independenceTest.getPValue();
                        _v = v;
                    }
                }
            }
        }

        this.score = _score;
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
    public double getScore() {
        return score;
    }

    @Override
    public List<Node> getVariables() {
        return independenceTest.getVariables();
    }

    private IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

}

