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
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements the PC ("Peter/Clark") algorithm, as specified in Chapter 6 of Spirtes, Glymour, and Scheines, "Causation,
 * Prediction, and Search," 2nd edition, with a modified rule set in step D due to Chris Meek. For the modified rule
 * set, see Chris Meek (1995), "Causal inference and causal explanation with background knowledge."
 *
 * @author Joseph Ramsey (this version).
 */
public class PcSearchRsch implements GraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private IndependenceTest independenceTest;

    private IndependenceTest graphicalTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge;

    /**
     * Sepset information accumulated in the search.
     */
    private SepsetMap sepset;

    /**
     * The maximum number of nodes conditioned on in the search.
     */
    private int depth = Integer.MAX_VALUE;

    /**
     * The graph that's constructed during the search.
     */
    private Graph graph;

    /**
     * The true graph, if specifies, to generate information about performance.
     */
    private Graph trueGraph;

    /**
     * Count of false positive edges involved in false positive collider orientations.
     */
    private int cefp;

    /**
     * Count of false negative edges involved in false positive collider orientations.
     */
    private int cefn;

    /**
     * Count of false positive independencies involved in false positive collider orientations.
     */
    private int cindfp;

    /**
     * Overall count of false positive collider orientations.
     */
    private int collfp;

    /**
     * Elapsed time of the last search.
     */
    private long elapsedTime;
    private int numTests;

    //=============================CONSTRUCTORS==========================//

    public PcSearchRsch(IndependenceTest independenceTest,
                        IKnowledge knowledge) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.knowledge = knowledge;
    }

    //==============================PUBLIC METHODS========================//

    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public SepsetMap getSepset() {
        return sepset;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public Graph getPartialGraph() {
        return new EdgeListGraph(graph);
    }

    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     */
    public Graph search() {
        return search(independenceTest.getVariables());
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Runs PC on just the given variable, all of which must be in the domain of the independence test.
     */
    public Graph search(List<Node> nodes) {
        TetradLogger.getInstance().log("info", "Starting PC algorithm.");
        TetradLogger.getInstance().log("info", "Independence test = " + independenceTest + ".");
        long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        graph = new EdgeListGraph(nodes);
        graph.fullyConnect(Endpoint.TAIL);

        Fas fas = new Fas(graph, getIndependenceTest());
        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        graph = fas.search();
        this.sepset = fas.getSepsets();

        pcOrientbk(knowledge, graph, nodes);
        orientCollidersUsingSepsets(this.sepset, knowledge, graph);
        MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        rules.orientImplied(graph);

//        orientUsingMeekRules(knowledge, graph);

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + graph);
        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;
        TetradLogger.getInstance().log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing PC algorithm.");

        return graph;
    }

    /**
     * Orients according to background knowledge
     */
    public void pcOrientbk(IKnowledge bk, Graph graph, List<Node> nodes) {
        TetradLogger.getInstance().log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it =
             bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = translate(edge.getFrom(), nodes);
            Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to-->from
            graph.removeEdge(from, to);
            graph.addDirectedEdge(from, to);
            graph.setEndpoint(from, to, Endpoint.TAIL);
            graph.setEndpoint(to, from, Endpoint.ARROW);
            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(to, from)));
        }

        for (Iterator<KnowledgeEdge> it =
             bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = translate(edge.getFrom(), nodes);
            Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient from-->to
            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }


        TetradLogger.getInstance().log("info", "Finishing BK Orientation.");
    }

    /**
     * Performs step C of the algorithm, as indicated on page xxx of CPS
     */
    public void orientCollidersUsingSepsets(SepsetMap set, IKnowledge knowledge,
                                            Graph graph) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                Node trueA = getTrueGraph().getNode(a.getName());
                Node trueB = getTrueGraph().getNode(b.getName());
                Node trueC = getTrueGraph().getNode(c.getName());
                Graph trueGraph = getTrueGraph();

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                List<Node> sepset = set.get(b, c);

                if (sepset == null) {
                    throw new IllegalArgumentException();
                }

                if (!sepset.contains(a) &&
                        isArrowpointAllowed(b, a, knowledge) &&
                        isArrowpointAllowed(c, a, knowledge)) {

                    // Estimated collider.
                    graph.setEndpoint(b, a, Endpoint.ARROW);
                    graph.setEndpoint(c, a, Endpoint.ARROW);
                    TetradLogger.getInstance().log("info", SearchLogUtils.colliderOrientedMsg(b, a, c, sepset));


                    if (!trueGraph.isAdjacentTo(trueB, trueA) ||
                            !trueGraph.isAdjacentTo(trueC, trueA)) {
                        cefp++;
                    }

                    if (trueGraph.isAdjacentTo(trueB, trueC)) {
                        cefn++;
                    }

                    // true collider.
                    if (trueGraph.getEndpoint(trueB, trueA) == Endpoint.ARROW
                            && trueGraph.getEndpoint(trueC, trueA) == Endpoint.ARROW) {
                        collfp++;
                        printSubsetMessage(graph, b, a, c, sepset);
                    }

                    // true noncollider.  DEPENDENT POSITIVE
                    else {
                        printSubsetMessage(graph, b, a, c, sepset);
//                        System.out.println(trueGraph.getEdge(trueB, trueA));
//                        System.out.println(trueGraph.getEdge(trueC, trueA));
                    }

                    List<Node> trueS = new LinkedList<Node>();

                    for (Node s : sepset) {
                        trueS.add(getTrueGraph().getNode(s.getName()));
                    }

                    if (!(trueGraph.isDSeparatedFrom(trueB, trueC, trueS))) {
                        cindfp++;
                    }
                } else {

                    // true collider.  DEPENDENT NEGATIVE
                    if (!(trueGraph.getEndpoint(trueB, trueA) == Endpoint
                            .ARROW && trueGraph.getEndpoint(trueC, trueA) ==
                            Endpoint.ARROW)) {
//                        printSubsetMessage(graph, b, c, sepset, "FN");
                        collfp++;
                    }

                    // true noncollider.
                    else {
//                        printSubsetMessage(graph, b, c, sepset, "TN");
                    }

                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    /**
     * @return true if x-->y<--z is an unshielded collider, tested locally.
     */
    private boolean isCollider(Node x, Node y, Node z) {
        if (graph.isAdjacentTo(x, z)) {
            return false;
        }

        return !existsLocalSepsetWith(x, y, z,
                independenceTest, graph, depth);
    }

    private boolean isGraphicalCollider(Node x, Node y, Node z) {
        if (graph.isAdjacentTo(x, z)) {
            return false;
        }

        return !existsLocalSepsetWithGraphical(x, y, z, depth);
    }

    public boolean existsLocalSepsetWith(Node x, Node y, Node z,
                                         IndependenceTest test,
                                         Graph graph, int depth) {
        numTests = 0;

        Node trueX = getTrueGraph().getNode(x.getName());
        Node trueY = getTrueGraph().getNode(y.getName());
        Node trueZ = getTrueGraph().getNode(z.getName());

        Set<Node> __nodes = new HashSet<Node>(trueGraph.getAdjacentNodes(trueX));
        __nodes.addAll(trueGraph.getAdjacentNodes(trueZ));
        __nodes.remove(trueX);
        __nodes.remove(trueZ);

        List<Node> _nodes = new LinkedList<Node>();

        for (Node node : __nodes) {
            _nodes.add(graph.getNode(node.getName()));
        }

//        List<Node> _nodes = new LinkedList<Node>(__nodes);
        TetradLogger.getInstance().log("details", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 1; d <= _depth; d++) {
            if (_nodes.size() >= d) {
                ChoiceGenerator cg2 = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg2.next()) != null) {
                    List<Node> condSet = asList(choice, _nodes);

                    if (!condSet.contains(y)) {
                        continue;
                    }

//                     System.out.println("Trying " + condSet);

                    boolean independent = test.isIndependent(x, z, condSet);

                    numTests++;

                    if (independent) {
//                        System.out.println("Sepset exists. Numtests = " + numTests);
                        return true;
                    }
                }
            }
        }

//        System.out.println("No sepset. Numtests = " + numTests);
        return false;
    }

    public boolean existsLocalSepsetWithGraphical(Node x, Node y, Node z, int depth) {
        numTests = 0;

        Node trueX = trueGraph.getNode(x.getName());
        Node trueY = trueGraph.getNode(y.getName());
        Node trueZ = trueGraph.getNode(z.getName());

        List<Node> _nodes = new LinkedList<Node>();
        _nodes.addAll(trueGraph.getAdjacentNodes(trueX));
        _nodes.addAll(trueGraph.getAdjacentNodes(trueZ));

//        List<Node> _nodes = new LinkedList<Node>(__nodes);
        TetradLogger.getInstance().log("details", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 1; d <= _depth; d++) {
            if (_nodes.size() >= d) {
                ChoiceGenerator cg2 = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg2.next()) != null) {
                    List<Node> condSet = asList(choice, _nodes);

                    if (!condSet.contains(trueY)) {
                        continue;
                    }

//                    System.out.println("Trying " + condSet);

                    boolean independent = trueGraph.isDSeparatedFrom(trueX, trueZ, condSet);
                    numTests++;

                    if (independent) {
//                        System.out.println("Dcon exists. Numtests = " + numTests);
                        return true;
                    }
                }
            }
        }

//        System.out.println("No sepset. Numtests = " + numTests);
        return false;
    }

    /**
     * Constructs a list of nodes from the given <code>nodes</code> list at the given indices in that list.
     *
     * @param indices The indices of the desired nodes in <code>nodes</code>.
     * @param nodes   The list of nodes from which we select a sublist.
     * @return the The sublist selected.
     */
    public static List<Node> asList(int[] indices, List<Node> nodes) {
        List<Node> list = new LinkedList<Node>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    private void printSubsetMessage(Graph graph, Node b, Node a, Node c,
                                    List<Node> sepset) {
        Node trueA = trueGraph.getNode(a.getName());
        Node trueB = trueGraph.getNode(b.getName());
        Node trueC = trueGraph.getNode(c.getName());

//        List<Node> badj = new LinkedList<Node>();
//        for (Node node : trueGraph.getAdjacentNodes(trueB)) {
//            badj.add(graph.getNode(node.toString()));
//        }

//        List<Node> cadj = new LinkedList<Node>();
//        for (Node node : trueGraph.getAdjacentNodes(trueC)) {
//            cadj.add(graph.getNode(node.toString()));
//        }

//        List<Node> adj = new LinkedList<Node>();
//        adj.addAll(badj);
//        adj.addAll(cadj);

//        List<Node> badj = graph.getAdjacentNodes(b);
//        List<Node> cadj = graph.getAdjacentNodes(c);

        StringBuilder triple = new StringBuilder();

        triple.append(b);

//        if (!trueGraph.isAdjacentTo(trueA, trueB) || !trueGraph.isAdjacentTo(trueA, trueC)) {
//            return;
//        }

        if (trueGraph.isAdjacentTo(trueA, trueB)) {
            if (trueGraph.isDirectedFromTo(trueA, trueB)) {
                triple.append("<--");
            } else {
                triple.append("-->");
            }
        } else {
            triple.append("   ");
        }

        triple.append(a);

        if (trueGraph.isAdjacentTo(trueA, trueC)) {
            if (trueGraph.isDirectedFromTo(trueA, trueC)) {
                triple.append("-->");
            } else {
                triple.append("<--");
            }
        } else {
            triple.append("   ");
        }

        triple.append(c);

        boolean unshielded = !trueGraph.isAdjacentTo(trueB, trueC);
        boolean dsep = trueGraph.isDSeparatedFrom(trueB, trueC, new LinkedList<Node>());
        boolean localCol = isCollider(b, a, c);
        boolean graphicalCol = isGraphicalCollider(b, a, c);

        System.out.println(triple +
                "\t" + sepset +
                "\t" + (unshielded ? "T" : "F") +
                "\t" + (dsep ? "T" : "F") +
                "\t" + (localCol ? "T" : "F") +
                "\t" + (graphicalCol ? "T" : "F")
        );
    }

//    /**
//     * Performs step D of the algorithm, as indicated on page xxx of CPS. This
//     * method should be called again if it returns true.
//     */
//    private void orientUsingMeekRules(Knowledge knowledge, Graph graph) {
//        LogUtils.getInstance().info("Starting Orientation Step D.");
//
//        // Repeat until no more orientations are made.
//        while (true) {
//            if (SearchGraphUtils.meekR1(graph, knowledge)) {
//                continue;
//            }
//
//            if (SearchGraphUtils.meekR2(graph, knowledge)) {
//                continue;
//            }
//
//            if (SearchGraphUtils.meekR3(graph, knowledge)) {
//                continue;
//            }
//
//            if (SearchGraphUtils.meekR4(graph, knowledge)) {
//                continue;
//            }
//
//            break;
//        }
//
//        LogUtils.getInstance().info("Finishing Orientation Step D.");
//    }

    /**
     * @return the string in nodelist which matches string in BK.
     */
    public Node translate(String a, List<Node> nodes) {
        for (Node node : nodes) {
            if ((node.getName()).equals(a)) {
                return node;
            }
        }

        return null;
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */

    public boolean isArrowpointAllowed(Object from, Object to,
                                       IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public int getCefp() {
        return this.cefp;
    }

    public int getCefn() {
        return this.cefn;
    }

    public int getCindfp() {
        return this.cindfp;
    }

    public int getCollfp() {
        return this.collfp;
    }

    public int getBide() {
        int numBidirected = 0;

        for (Edge edge : graph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                numBidirected++;
            }
        }

        return numBidirected;
    }

    public int getAlle() {
        return graph.getNumEdges();
    }

    public void setTrueGraph(Dag trueGraph) {
        this.trueGraph = trueGraph;
        this.graphicalTest = new IndTestDSep(trueGraph);
    }

    public Graph getTrueGraph() {
        return this.trueGraph;
    }
}




