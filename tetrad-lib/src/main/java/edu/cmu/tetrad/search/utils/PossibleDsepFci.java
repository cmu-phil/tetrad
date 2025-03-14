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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.ChoiceGenerator;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Implements the Possible-M-Sep search step of Spirtes, et al's (1993) FCI algorithm (pp 144-145). Specifically, the
 * methods in this class perform step D. of the algorithm. The algorithm implemented by this class is a bit broader,
 * however, because it allows for the possibility that some pairs of variables have already been compared by a different
 * algorithm. Specifically, if the <code>prevCheck</code> variable is provided in the constructor, then the algorithm
 * pairwise checks every variable in the graph with every variable in v \
 * <code>prevCheck</code> (that is, the unchecked variables). This feature is used by the CIVI algorithm of Danks's
 * "Efficient Inclusion of Novel Variables."
 *
 * @author David Danks
 * @version $Id: $Id
 */
public class PossibleDsepFci {
    private final Graph graph;
    private final IndependenceTest test;
    private final SepsetMap sepset;
    private int depth = -1;
    private Knowledge knowledge = new Knowledge();
    private int maxReachablePathLength = -1;

    /**
     * Creates a new SepSet and assumes that none of the variables have yet been checked.
     *
     * @param graph The GaSearchGraph on which to work
     * @param test  The IndependenceChecker to use as an oracle
     */
    public PossibleDsepFci(Graph graph, IndependenceTest test) {
        if (graph == null) {
            throw new NullPointerException("null GaSearchGraph passed in " +
                                           "PossibleDSepSearch constructor!");
        }
        if (test == null) {
            throw new NullPointerException("null IndependenceChecker passed " +
                                           "in PossibleDSepSearch " + "constructor!");
        }

        this.graph = graph;
        this.test = test;
        this.sepset = new SepsetMap();

        setMaxReachablePathLength(this.maxReachablePathLength);
    }

    /**
     * Performs pairwise comparisons of each variable in the graph with the variables that have not already been
     * checked. We get the Possible-M-Sep sets for the pair of variables, and we check to see if they are independent
     * conditional on some subset of the union of Possible-M-Sep sets. This method returns the SepSet passed in the
     * constructor (if any), possibly augmented by some edge removals in this step. The GaSearchGraph passed in the
     * constructor is directly changed.
     *
     * @return a {@link edu.cmu.tetrad.search.utils.SepsetMap} object
     * @throws java.lang.InterruptedException if any.
     */
    public SepsetMap search() throws InterruptedException {

        for (Edge edge : new ArrayList<>(this.graph.getEdges())) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Set<Node> condSet = getSepset(this.test, x, y);

            if (condSet != null) {
                for (Node n : condSet) {
                    if (!(this.graph.getAdjacentNodes(n).contains(x) || this.graph.getAdjacentNodes(n).contains(y))) {
                        System.out.println("Not adjacent");
                    }
                }

                this.graph.removeEdge(x, y);
                this.sepset.set(x, y, condSet);
                System.out.println("Removed " + x + "--- " + y + " sepset = " + condSet);
            }

        }

        return this.sepset;
    }

    /**
     * <p>Getter for the field <code>sepset</code>.</p>
     *
     * @param test  a {@link edu.cmu.tetrad.search.IndependenceTest} object
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.Set} object
     * @throws java.lang.InterruptedException if any.
     */
    public Set<Node> getSepset(IndependenceTest test, Node node1, Node node2) throws InterruptedException {
        Set<Node> condSet = getCondSet(test, node1, node2, this.maxReachablePathLength);

        if (this.sepset == null) {
            condSet = getCondSet(test, node2, node1, this.maxReachablePathLength);
        }

        return condSet;
    }

    /**
     * <p>Getter for the field <code>depth</code>.</p>
     *
     * @return a int
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * <p>Setter for the field <code>depth</code>.</p>
     *
     * @param depth a int
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the maximum reachable path length for the search algorithm.
     *
     * @param maxReachablePathLength The maximum reachable path length. Must be -1 (unlimited) or >= 0.
     * @throws IllegalArgumentException if maxReachablePathLength is less than -1.
     */
    public void setMaxReachablePathLength(int maxReachablePathLength) {
        if (maxReachablePathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxReachablePathLength);
        }

        this.maxReachablePathLength = maxReachablePathLength == -1 ? Integer.MAX_VALUE : maxReachablePathLength;
    }

    private Set<Node> getCondSet(IndependenceTest test, Node node1, Node node2, int maxPossibleDsepPathLength) throws InterruptedException {
        List<Node> possibleDsepSet = getPossibleDsep(node1, node2, maxPossibleDsepPathLength);
        List<Node> possibleDsep = new ArrayList<>(possibleDsepSet);
        boolean noEdgeRequired = getKnowledge().noEdgeRequired(node1.getName(), node2.getName());

        List<Node> possParents = possibleParents(node1, possibleDsep, getKnowledge());

        int _depth = getDepth() == -1 ? 1000 : getDepth();

        for (int d = 0; d <= FastMath.min(_depth, possParents.size()); d++) {
            ChoiceGenerator cg = new ChoiceGenerator(possParents.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                Set<Node> condSet = GraphUtils.asSet(choice, possParents);
                boolean independent = test.checkIndependence(node1, node2, condSet).isIndependent();

                if (independent && noEdgeRequired) {
                    return condSet;
                }
            }
        }

        return null;
    }

    /**
     * Removes from the list of nodes any that cannot be parents of x given the background knowledge.
     */
    private List<Node> possibleParents(Node x, List<Node> nodes,
                                       Knowledge knowledge) {
        List<Node> possibleParents = new LinkedList<>();
        String _x = x.getName();

        for (Node z : nodes) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(String _z, String _x, Knowledge bk) {
        return !(bk.isForbidden(_z, _x) || bk.isRequired(_x, _z));
    }

    /**
     * A variable v is in Possible-M-Sep(A,B) iff
     * <pre>
     * 	(i) v != A & v != B
     * 	(ii) there is an undirected path U between A and v such that for every
     * 		 subpath <X,Y,Z> of U either:
     * 		(a) Y is a collider on the subpath, or
     * 		(b) X is adjacent to Z.
     * </pre>
     */
    private List<Node> getPossibleDsep(Node node1, Node node2, int maxPathLength) {
        List<Node> msep = this.graph.paths().possibleDsep(node1, node2, maxPathLength);

        msep.remove(node1);
        msep.remove(node2);

//        TetradLogger.getInstance().log("details", "Possible-M-Sep(" + node1 + ", " + node2 + ") = " + msep);

        return msep;
    }

}




