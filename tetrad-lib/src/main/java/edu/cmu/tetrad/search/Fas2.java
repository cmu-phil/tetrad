///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014 by Peter Spirtes, Richard Scheines, Joseph   //
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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;

/**
 * Implements the "fast adjacency search" used in several causal algorithm in this package. In the fast adjacency
 * search, at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S, where S is a subset
 * of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency search performs this
 * procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1, where d1 is either
 * the maximum depth or else the first such depth at which no edges can be removed. The interpretation of this adjacency
 * search is different for different algorithm, depending on the assumptions of the algorithm. A mapping from {x, y} to
 * S({x, y}) is returned for edges x *-* y that have been removed.
 *
 * @author Joseph Ramsey.
 */
public class Fas2 implements IFas {

    /**
     * The search nodes.
     */
    private final List<Node> nodes;

    /**
     * The independence test. This should be appropriate to the types
     */
    private final IndependenceTest test;

    /**
     * Specification of which edges are forbidden or required.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The maximum number of variables conditioned on in any conditional independence test. If the depth is -1, it will
     * be taken to be the maximum value, which is 1000. Otherwise, it should be set to a non-negative integer.
     */
    private int depth = 1000;

    /**
     * The number of independence tests.
     */
    private int numIndependenceTests;


    /**
     * The logger, by default the empty logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

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
    private PcCommon.PcHeuristicType pcHeuristicType;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public Fas2(Graph initialGraph, IndependenceTest test) {
        if (initialGraph != null) {
            this.initialGraph = new EdgeListGraph(initialGraph);
        }
        this.test = test;
        this.nodes = test.getVariables();
    }

    public Fas2(IndependenceTest test) {
        this.test = test;
        this.nodes = test.getVariables();
    }

    //==========================PUBLIC METHODS===========================//

    public Graph search(List<Node> nodes) {
//        return search(nodes, new Knowledge());
        throw new UnsupportedOperationException();
    }

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
//        sepset.setReturnEmptyIfNotSet(sepsetsReturnEmptyIfNotFixed);

        int _depth = depth;

        if (_depth == -1) {
            _depth = 1000;
        }

        Map<Node, Set<Node>> adjacencies = new HashMap<>();

        for (Node node : nodes) {
            adjacencies.put(node, new TreeSet<>());
        }

        for (int d = 0; d <= _depth; d++) {
            boolean more;

            if (d == 0) {
                more = searchAtDepth0(nodes, test, adjacencies);
            } else {
                more = searchAtDepth(nodes, test, adjacencies, d);
            }

            if (!more) {
                break;
            }
        }

        Graph graph = new EdgeListGraph(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (adjacencies.get(x).contains(y)) {
                    graph.addUndirectedEdge(x, y);
                }
            }
        }

        this.logger.log("info", "Finishing Fast Adjacency Search.");

        return graph;
    }

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

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }

    //==============================PRIVATE METHODS======================/

    private boolean searchAtDepth0(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        Set<Node> empty = Collections.emptySet();
        for (int i = 0; i < nodes.size(); i++) {
            if (verbose) {
                if ((i + 1) % 100 == 0) out.println("Node # " + (i + 1));
            }

            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node x = nodes.get(i);

            for (int j = i + 1; j < nodes.size(); j++) {

                Node y = nodes.get(j);

                if (initialGraph != null) {
                    Node x2 = initialGraph.getNode(x.getName());
                    Node y2 = initialGraph.getNode(y.getName());

                    if (!initialGraph.isAdjacentTo(x2, y2)) {
                        continue;
                    }
                }

                boolean independent;

                try {
                    numIndependenceTests++;
                    independent = test.checkIndependence(x, y, empty).isIndependent();
                } catch (Exception e) {
                    e.printStackTrace();
                    independent = false;
                }

                boolean noEdgeRequired =
                        knowledge.noEdgeRequired(x.getName(), y.getName());

                if (independent && noEdgeRequired) {
                    getSepsets().set(x, y, empty);
                } else if (!forbiddenEdge(x, y)) {
                    adjacencies.get(x).add(y);
                    adjacencies.get(y).add(x);
                }
            }
        }

        return freeDegree(nodes, adjacencies) > 0;
    }

    private int freeDegree(List<Node> nodes, Map<Node, Set<Node>> adjacencies) {
        int max = 0;

        for (Node x : nodes) {
            Set<Node> opposites = adjacencies.get(x);

            for (Node y : opposites) {
                Set<Node> adjx = new HashSet<>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

    private boolean forbiddenEdge(Node x, Node y) {
        String name1 = x.getName();
        String name2 = y.getName();

        if (knowledge.isForbidden(name1, name2) &&
                knowledge.isForbidden(name2, name1)) {
            System.out.println(Edges.undirectedEdge(x, y) + " because it was " +
                    "forbidden by background knowledge.");

            return true;
        }

        return false;
    }

    private boolean searchAtDepth(List<Node> nodes, final IndependenceTest test, Map<Node, Set<Node>> adjacencies, int depth) {
        int count = 0;

        for (Node x : nodes) {
            if (verbose) {
                if (++count % 100 == 0) out.println("count " + count + " of " + nodes.size());
            }

            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjx = new ArrayList<>(adjacencies.get(x));

            EDGE:
            for (Node y : adjx) {
                List<Node> _adjx = new ArrayList<>(adjacencies.get(x));
                _adjx.remove(y);
                List<Node> ppx = possibleParents(x, _adjx, knowledge);

                if (ppx.size() >= depth) {
                    ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        Set<Node> condSet = GraphUtils.asSet(choice, ppx);

                        boolean independent;

                        try {
                            numIndependenceTests++;
                            independent = test.checkIndependence(x, y, condSet).isIndependent();
                        } catch (Exception e) {
                            independent = false;
                        }

                        boolean noEdgeRequired =
                                knowledge.noEdgeRequired(x.getName(), y.getName());

                        if (independent && noEdgeRequired) {
                            adjacencies.get(x).remove(y);
                            adjacencies.get(y).remove(x);

                            getSepsets().set(x, y, condSet);

                            continue EDGE;
                        }
                    }
                }
            }
        }

        return freeDegree(nodes, adjacencies) > depth;
    }

    private List<Node> possibleParents(Node x, List<Node> adjx, Knowledge knowledge) {
        List<Node> possibleParents = new LinkedList<>();
        String _x = x.getName();

        for (Node z : adjx) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    public int getNumIndependenceTests() {
        return numIndependenceTests;
    }

    public SepsetMap getSepsets() {
        return sepset;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
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

    public void setPcHeuristicType(PcCommon.PcHeuristicType pcHeuristicType) {
        this.pcHeuristicType = pcHeuristicType;
    }

    public void setStable(boolean b) {
        boolean stable = b;
    }
}
