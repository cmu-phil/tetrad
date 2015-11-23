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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

/**
 * Implements the "fast adjacency search" used in several causal algorithms in this package. In the fast adjacency
 * search, at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S, where S is a subset
 * of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency search performs this
 * procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1, where d1 is either
 * the maximum depth or else the first such depth at which no edges can be removed. The interpretation of this adjacency
 * search is different for different algorithms, depending on the assumptions of the algorithm. A mapping from {x, y} to
 * S({x, y}) is returned for edges x *-* y that have been removed.
 *
 * @author Joseph Ramsey.
 */
public class Fas implements IFas {

    /**
     * The independence test. This should be appropriate to the types
     */
    private IndependenceTest test;

    /**
     * Specification of which edges are forbidden or required.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The maximum number of variables conditioned on in any conditional independence test. If the depth is -1, it will
     * be taken to be the maximum value, which is 1000. Otherwise, it should be set to a non-negative integer.
     */
    private int depth = 1000;

    /**
     * The logger, by default the empty logger.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * The sepsets found during the search.
     */
    private SepsetMap sepset = new SepsetMap();

    /**
     * The depth 0 graph, specified initially.
     */
    private Graph initialGraph;

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;

    private PrintStream out = System.out;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public Fas(IndependenceTest test) {
        this.test = test;
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent conditional on some other set of variables in the graph (the "sepset"). These are
     * removed in tiers.  First, edges which are independent conditional on zero other variables are removed, then edges
     * which are independent conditional on one other variable are removed, then two, then three, and so on, until no
     * more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @return a SepSet, which indicates which variables are independent conditional on which other variables
     */
    public Graph search() {
        this.logger.log("info", "Starting Fast Adjacency Search.");
        sepset = new SepsetMap();
        sepset.setReturnEmptyIfNotSet(true);

        int _depth = depth;

        if (_depth == -1) {
            _depth = 1000;
        }

        List<Node> nodes = getNodes();

        Graph graph;

        if (initialGraph != null) {
            graph = new EdgeListGraphSingleConnections(initialGraph);
            graph.reorientAllWith(Endpoint.TAIL);
        }
        else {
            graph = new EdgeListGraphSingleConnections(nodes);
            graph.fullyConnect(Endpoint.TAIL);
        }

        for (int d = 0; d <= _depth; d++) {
            pcDepth(nodes, test, graph, d);

            if (maxAdjacents(nodes, graph) < d) {
                break;
            }
        }

        System.out.println("FAS done");

        this.logger.log("info", "Finishing Fast Adjacency Search.");
        return graph;
    }

    //==============================PRIVATE METHODS======================/

    private int maxAdjacents(List<Node> nodes, Graph graph) {
        int max = 0;

        for (Node x : nodes) {
            List<Node> adjx = graph.getAdjacentNodes(x);

            if (adjx.size() > max) {
                max = adjx.size();
            }
        }

        return max;
    }

    private void pcDepth(List<Node> nodes, final IndependenceTest test, Graph graph, int depth) {
        out.println("Depth " + depth);

        for (int i = 0; i < nodes.size(); i++) {

            EDGE:
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (!graph.isAdjacentTo(x, y)) continue;

                List<Node> adjx = graph.getAdjacentNodes(x);
                List<Node> adjy = graph.getAdjacentNodes(y);

                adjx.remove(y);
                adjy.remove(x);

                adjx = possibleParents(x, adjx, knowledge);
                adjy = possibleParents(y, adjy, knowledge);

                if (adjx.size() >= depth) {
                    ChoiceGenerator cg = new ChoiceGenerator(adjx.size(), depth);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        List<Node> condSet = GraphUtils.asList(choice, adjx);

                        boolean independent;

                        try {
                            independent = test.isIndependent(x, y, condSet);
                        } catch (Exception e) {
                            e.printStackTrace();
                            independent = false;
                        }

                        if (independent) {
                            graph.removeEdge(x, y);
                            if (condSet.isEmpty()) {
                                if (!sepset.isReturnEmptyIfNotSet()) {
                                    getSepsets().set(x, y, condSet);
                                }
                            }
                            else {
                                getSepsets().set(x, y, condSet);
                            }
                            continue EDGE;
                        }
                    }

                }

                if (adjy.size() >= depth) {
                    ChoiceGenerator cg2 = new ChoiceGenerator(adjy.size(), depth);
                    int[] choice2;

                    boolean independent;

                    while ((choice2 = cg2.next()) != null) {
                        List<Node> condSet = GraphUtils.asList(choice2, adjy);

                        try {
                            independent = test.isIndependent(x, y, condSet);
                        } catch (Exception e) {
                            e.printStackTrace();
                            independent = false;
                        }

                        if (independent) {
                            graph.removeEdge(x, y);
                            getSepsets().set(x, y, condSet);
                            continue EDGE;
                        }
                    }
                }
            }
        }
    }

    private List<Node> possibleParents(Node x, List<Node> adjx,
                                       IKnowledge knowledge) {
        List<Node> possibleParents = new LinkedList<Node>();
        String _x = x.getName();

        for (Node z : adjx) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(String z, String x, IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    //============================ACCESSORS===============================//

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }

    public int getNumIndependenceTests() {
        return 0;
    }

    public void setTrueGraph(Graph trueGraph) {
    }

    public int getNumFalseDependenceJudgments() {
        return 0;
    }

    public int getNumDependenceJudgments() {
        return 0;
    }

    public SepsetMap getSepsets() {
        return sepset;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public boolean isAggressivelyPreventCycles() {
        return false;
    }

    @Override
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {

    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return null;
    }

    @Override
    public Graph search(List<Node> nodes) {
        return null;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    @Override
    public List<Node> getNodes() {
        return test.getVariables();
    }

    @Override
    public List<Triple> getAmbiguousTriples(Node node) {
        return null;
    }

    @Override
    public void setOut(PrintStream out) {
        this.out = out;
    }
}


