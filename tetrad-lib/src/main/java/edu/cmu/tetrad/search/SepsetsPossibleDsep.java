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
import java.util.Set;

public class SepsetsPossibleDsep implements SepsetProducer {
    private Graph graph;
    private IndependenceTest independenceTest;
    private int maxPathLength = 5;
    private IKnowledge knowledge = new Knowledge2();
    private int depth = -1;
    private boolean verbose = false;

    public SepsetsPossibleDsep(Graph graph, IndependenceTest independenceTest, IKnowledge knowledge,
                               int depth, int maxPathLength) {
        this.graph = graph;
        this.independenceTest = independenceTest;
        this.maxPathLength = maxPathLength;
        this.knowledge = knowledge;
        this.depth = depth;
    }

    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest p value.
     */
    public List<Node> getSepset(Node i, Node k) {
        List<Node> condSet = getCondSet(i, k, maxPathLength);

        if (condSet == null) {
            condSet = getCondSet(k, i, maxPathLength);
        }

        return condSet;
    }

    public boolean isCollider(Node i, Node j, Node k) {
        List<Node> sepset = getSepset(i, k);
        return sepset != null && !sepset.contains(j);
    }

    public boolean isNoncollider(Node i, Node j, Node k) {
        List<Node> sepset = getSepset(i, k);
        return sepset != null && sepset.contains(j);
    }

    @Override
    public boolean isIndependent(Node a, Node b, List<Node> c) {
        return independenceTest.isIndependent(a, b, c);
    }

    private List<Node> getCondSet(Node node1, Node node2, int maxPathLength) {
        final Set<Node> possibleDsepSet = getPossibleDsep(node1, node2, maxPathLength);
        List<Node> possibleDsep = new ArrayList<Node>(possibleDsepSet);
        boolean noEdgeRequired = knowledge.noEdgeRequired(node1.getName(), node2.getName());

        List<Node> possParents = possibleParents(node1, possibleDsep, knowledge);

        int _depth = depth == -1 ? 1000 : depth;

        for (int d = 0; d <= Math.min(_depth, possParents.size()); d++) {
            ChoiceGenerator cg = new ChoiceGenerator(possParents.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = GraphUtils.asList(choice, possParents);
                boolean independent = independenceTest.isIndependent(node1, node2, condSet);

                if (independent && noEdgeRequired) {
                    return condSet;
                }
            }
        }

        return null;
    }

    private Set<Node> getPossibleDsep(Node x, Node y, int maxPathLength) {
        Set<Node> dsep = GraphUtils.possibleDsep(x, y, graph, maxPathLength);
//        TetradLogger.getInstance().log("details", "Possible-D-Sep(" + x + ", " + y + ") = " + dsep);

        if (verbose) {
            System.out.println("Possible-D-Sep(" + x + ", " + y + ") = " + dsep);
        }

        return dsep;

    }

    /**
     * Removes from the list of nodes any that cannot be parents of x given the background knowledge.
     */
    private List<Node> possibleParents(Node x, List<Node> nodes,
                                       IKnowledge knowledge) {
        List<Node> possibleParents = new LinkedList<Node>();
        String _x = x.getName();

        for (Node z : nodes) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(String _z, String _x, IKnowledge bk) {
        return !(bk.isForbidden(_z, _x) || bk.isRequired(_x, _z));
    }

    @Override
    public double getPValue() {
        return independenceTest.getPValue();
    }

    @Override
    public List<Node> getVariables() {
        return independenceTest.getVariables();
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}

