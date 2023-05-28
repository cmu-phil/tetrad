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
import edu.cmu.tetrad.search.test.IndTestDSep;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.ChoiceGenerator;
import org.apache.commons.math3.util.FastMath;

import java.util.List;
import java.util.Set;

/**
 * <p>Provides a SepsetProcuder that selects the first sepset it comes to from
 * among the extra sepsets or the adjacents of i or k, or null if none is found.</p>
 *
 * @author josephramsey
 * @see SepsetProducer
 * @see SepsetMap
 */
public class SepsetsGreedy implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final SepsetMap extraSepsets;
    private int depth;
    private boolean verbose;
    private IndependenceResult result;

    public SepsetsGreedy(Graph graph, IndependenceTest independenceTest, SepsetMap extraSepsets, int depth) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.extraSepsets = extraSepsets;
        this.depth = depth;
    }

    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest score value.
     */
    public Set<Node> getSepset(Node i, Node k) {
        return getSepsetGreedy(i, k);
    }

    public boolean isUnshieldedCollider(Node i, Node j, Node k) {
        Set<Node> set = getSepsetGreedy(i, k);
        return set != null && !set.contains(j);
    }

    @Override
    public boolean isIndependent(Node a, Node b, Set<Node> c) {
        IndependenceResult result = this.independenceTest.checkIndependence(a, b, c);
        this.result = result;
        return result.isIndependent();
    }

    @Override
    public double getScore() {
        return -(result.getPValue() - this.independenceTest.getAlpha());
    }

    @Override
    public List<Node> getVariables() {
        return this.independenceTest.getVariables();
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public Graph getDag() {
        if (this.independenceTest instanceof IndTestDSep) {
            return ((IndTestDSep) this.independenceTest).getGraph();
        } else {
            return null;
        }
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    private Set<Node> getSepsetGreedy(Node i, Node k) {
        if (this.extraSepsets != null) {
            Set<Node> v = this.extraSepsets.get(i, k);

            if (v != null) {
                return v;
            }
        }

        List<Node> adji = this.graph.getAdjacentNodes(i);
        List<Node> adjk = this.graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= FastMath.min((this.depth == -1 ? 1000 : this.depth), FastMath.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Set<Node> v = GraphUtils.asSet(choice, adji);

                    if (this.independenceTest.checkIndependence(i, k, v).isIndependent()) {
                        return v;
                    }
                }
            }

            if (d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Set<Node> v = GraphUtils.asSet(choice, adjk);

                    if (this.independenceTest.checkIndependence(i, k, v).isIndependent()) {
                        return v;
                    }
                }
            }
        }

        return null;
    }

}

