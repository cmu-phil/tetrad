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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class SepsetsPossibleDsep implements SepsetProducer {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private int maxPathLength = 100;
    private IKnowledge knowledge = new Knowledge2();
    private int depth = -1;
    private boolean verbose = false;
    private final IndependenceTest test;

    public SepsetsPossibleDsep(final Graph graph, final IndependenceTest independenceTest, final IKnowledge knowledge,
                               final int depth, final int maxPathLength) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.maxPathLength = maxPathLength;
        this.knowledge = knowledge;
        this.depth = depth;
        this.test = independenceTest;
    }

    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest p value.
     */
    public List<Node> getSepset(final Node i, final Node k) {
        List<Node> condSet = getCondSet(this.test, i, k, this.maxPathLength);

        if (condSet == null) {
            condSet = getCondSet(this.test, k, i, this.maxPathLength);
        }

        return condSet;
    }

    public boolean isCollider(final Node i, final Node j, final Node k) {
        final List<Node> sepset = getSepset(i, k);
        return sepset != null && !sepset.contains(j);
    }

    public boolean isNoncollider(final Node i, final Node j, final Node k) {
        final List<Node> sepset = getSepset(i, k);
        return sepset != null && sepset.contains(j);
    }

    @Override
    public boolean isIndependent(final Node a, final Node b, final List<Node> c) {
        return this.independenceTest.isIndependent(a, b, c);
    }

    private List<Node> getCondSet(final IndependenceTest test, final Node node1, final Node node2, final int maxPathLength) {
        final List<Node> possibleDsepSet = getPossibleDsep(node1, node2, maxPathLength);
        final List<Node> possibleDsep = new ArrayList<>(possibleDsepSet);
        final boolean noEdgeRequired = this.knowledge.noEdgeRequired(node1.getName(), node2.getName());

        final int _depth = this.depth == -1 ? 1000 : this.depth;

        for (int d = 0; d <= Math.min(_depth, possibleDsep.size()); d++) {
            final ChoiceGenerator cg = new ChoiceGenerator(possibleDsep.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final List<Node> condSet = GraphUtils.asList(choice, possibleDsep);
                // check against bk knowledge added by DMalinsky 07/24/17 **/
                if (!(this.knowledge == null)) {
//                    if (knowledge.isForbidden(node1.getName(), node2.getName())) continue;
                    boolean flagForbid = false;
                    for (final Node j : condSet) {
                        if (this.knowledge.isInWhichTier(j) > Math.max(this.knowledge.isInWhichTier(node1), this.knowledge.isInWhichTier(node2))) { // condSet cannot be in the future of both endpoints
//                        if (knowledge.isForbidden(j.getName(), node1.getName()) && knowledge.isForbidden(j.getName(), node2.getName())) {
                            flagForbid = true;
                            break;
                        }
                    }
                    if (flagForbid) continue;
                }
                final boolean independent = this.independenceTest.isIndependent(node1, node2, condSet);

                if (independent && noEdgeRequired) {
                    return condSet;
                }
            }
        }

        return null;
    }

    private List<Node> getPossibleDsep(final Node x, final Node y, final int maxPathLength) {
        final List<Node> dsep = GraphUtils.possibleDsep(x, y, this.graph, maxPathLength, this.test);

        if (this.verbose) {
            System.out.println("Possible-D-Sep(" + x + ", " + y + ") = " + dsep);
        }

        return dsep;

    }

    /**
     * Removes from the list of nodes any that cannot be parents of x given the background knowledge.
     */
    private List<Node> possibleParents(final Node x, final List<Node> nodes,
                                       final IKnowledge knowledge) {
        final List<Node> possibleParents = new LinkedList<>();
        final String _x = x.getName();

        for (final Node z : nodes) {
            final String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(final String _z, final String _x, final IKnowledge bk) {
        return !(bk.isForbidden(_z, _x) || bk.isRequired(_x, _z));
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

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

}

