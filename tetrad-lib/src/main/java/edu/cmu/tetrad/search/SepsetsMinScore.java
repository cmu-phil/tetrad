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
 * Selects the maximum score value sepset
 * <score>
 * Created by josephramsey on 3/24/15.
 */
public class SepsetsMinScore implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final SepsetMap extraSepsets;
    private int depth = 3;
    private double score = Double.NaN;
    private boolean verbose = false;
    private double p = Double.NaN;

    public SepsetsMinScore(Graph graph, IndependenceTest independenceTest, SepsetMap extraSepsets, int depth) {
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

        if (extraSepsets != null) {
            final List<Node> v = extraSepsets.get(i, k);
            if (v != null) {
                independenceTest.isIndependent(i, k, v);
                double _score = independenceTest.getScore();

                if (/*_score < 0 && */_score < score) {
                    score = _score;
                    this.p = independenceTest.getScore();
                    _v = v;
                }
            }
        }

        List<Node> adji = graph.getAdjacentNodes(i);
        List<Node> adjk = graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), adji.size()); d++) {
            ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> v = GraphUtils.asList(choice, adji);

                getIndependenceTest().isIndependent(i, k, v);
                double _score = getIndependenceTest().getScore();

                if (/*_score < 0 && */_score < score) {
                    score = _score;
                    this.p = independenceTest.getScore();
                    _v = v;
                }
            }
        }

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), adjk.size()); d++) {
            ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> v = GraphUtils.asList(choice, adjk);

                getIndependenceTest().isIndependent(i, k, v);
                double _score = getIndependenceTest().getScore();

                if (/*_score < 0 &&*/ _score < score) {
                    score = _score;
                    this.p = independenceTest.getPValue();
                    _v = v;
                }
            }
        }

        this.score = score;
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

