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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the CPC-Avg search.
 *
 * @author Joseph Ramsey.
 */
public class CpcMax implements IFas {

    /**
     * The search graph. It is assumed going in that all of the true adjacencies of x are in this graph for every node
     * x. It is hoped (i.e. true in the large sample limit) that true adjacencies are never removed.
     */
    private Graph graph;

    /**
     * The search nodes.
     */
    private List<Node> nodes;

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
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;

    //==========================CONSTRUCTORS=============================//

    public CpcMax(IndependenceTest test) {
        this.test = test;
        this.nodes = test.getVariables();
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

        graph = new EdgeListGraph(nodes);
        graph = GraphUtils.completeGraph(graph);

        int d = 0;
        boolean more;

        do {
            more = adjust(graph, d++, test);
            if (d > depth) break;
        } while (more);

        orientTriples();

        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);

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

        if (depth == -1) this.depth = 1000;
        else this.depth = depth;
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
//        this.trueGraph = trueGraph;
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

    }

    public static enum TripleType {COLLIDER, NONCOLLIDER, AMBIGUOUS, NEITHER}

    //==============================PRIVATE METHODS======================/

    private void orientTriples() {
        addColliders(graph);
    }

    private void addColliders(Graph graph) {
        final Map<Triple, TripleType> scores = new ConcurrentHashMap<>();

        List<Node> nodes = graph.getNodes();

        for (Node node : nodes) {
            doNode(graph, scores, node);
        }

        List<Triple> tripleList = new ArrayList<>(scores.keySet());

        for (Triple triple : tripleList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            if (scores.get(triple) == TripleType.COLLIDER && !graph.isUnderlineTriple(a, b, c)
                    && !graph.isAmbiguousTriple(a, c, b)) {
                orientCollider(graph, a, b, c, PcAll.ConflictRule.PRIORITY);
            } else if (scores.get(triple) == TripleType.NONCOLLIDER) {
                graph.addUnderlineTriple(a, b, c);
            } else if (scores.get(triple) == TripleType.NEITHER) {
//                graph.removeEdge(a, c);
            } else if (scores.get(triple) == TripleType.AMBIGUOUS) {
                graph.addAmbiguousTriple(a, b, c);
            }
        }
    }

    private void doNode(Graph graph, Map<Triple, TripleType> scores, Node b) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(b);

        if (adjacentNodes.size() < 2) {
            return;
        }

        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node a = adjacentNodes.get(combination[0]);
            Node c = adjacentNodes.get(combination[1]);

            // Skip triples that are shielded.
            if (graph.isAdjacentTo(a, c)) {
                continue;
            }

            testColliderMaxP(graph, scores, a, b, c);
        }
    }

    private void orientCollider(Graph graph, Node a, Node b, Node c, PcAll.ConflictRule conflictRule) {
        if (knowledge.isForbidden(a.getName(), b.getName())) return;
        if (knowledge.isForbidden(c.getName(), b.getName())) return;
        orientCollider(a, b, c, conflictRule, graph);
    }

    private static void orientCollider(Node x, Node y, Node z, PcAll.ConflictRule conflictRule, Graph graph) {
        if (conflictRule == PcAll.ConflictRule.PRIORITY) {
            if (!(graph.getEndpoint(y, x) == Endpoint.ARROW || graph.getEndpoint(y, z) == Endpoint.ARROW)) {
                graph.removeEdge(x, y);
                graph.removeEdge(z, y);
                graph.addDirectedEdge(x, y);
                graph.addDirectedEdge(z, y);
            }
        } else if (conflictRule == PcAll.ConflictRule.BIDIRECTED) {
            graph.setEndpoint(x, y, Endpoint.ARROW);
            graph.setEndpoint(z, y, Endpoint.ARROW);
        } else if (conflictRule == PcAll.ConflictRule.OVERWRITE) {
            graph.removeEdge(x, y);
            graph.removeEdge(z, y);
            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, y);
        }

        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
    }

    private void testColliderMaxP(Graph graph, Map<Triple, TripleType> scores, Node a, Node b, Node c) {
        TripleType sepset = maxPSepset(a, b, c, depth, graph, test);
        scores.put(new Triple(a, b, c), sepset);
    }

    private TripleType maxPSepset(Node a, Node b, Node c, int depth, Graph graph, IndependenceTest test) {
        System.out.println("Calculating max avg for " + a + " --- " + b + " --- " + c + " depth = " + depth);

        List<Double> bPvals = new ArrayList<>();
        List<Double> notbPvals = new ArrayList<>();

        List<Node> adja = graph.getAdjacentNodes(a);
        List<Node> adjc = graph.getAdjacentNodes(c);

        adja.remove(c);
        adjc.remove(a);

        List<List<Node>> adj = new ArrayList<>();
        adj.add(adja);
        adj.add(adjc);

        double maxWithout = 0;
        double maxWith = 0;

        List<Node> maxSepsetWith = new ArrayList<>();
        List<Node> maxSepsetWithout = new ArrayList<>();

        boolean bb = false;

        for (List<Node> _adj : adj) {
            DepthChoiceGenerator cg1 = new DepthChoiceGenerator(_adj.size(), depth);
            int[] comb2;

            while ((comb2 = cg1.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                List<Node> s = GraphUtils.asList(comb2, _adj);

                if (bb && s.isEmpty()) continue;

                test.isIndependent(a, c, s);
                double p = test.getPValue();

                if (s.contains(b)) {
                    bPvals.add(p);
                } else {
                    notbPvals.add(p);
                }
            }

            bb = true;
        }

        boolean existsb = existsSepsetFromList(bPvals, test.getAlpha());
        boolean existsnotb = existsSepsetFromList(notbPvals, test.getAlpha());

        if (existsb && !existsnotb) {
            return TripleType.NONCOLLIDER;
        } else if (existsnotb && !existsb) {
            return TripleType.COLLIDER;
        } else if (existsb) {
            return TripleType.AMBIGUOUS;
        } else {
            return TripleType.NEITHER;
        }
    }

    private boolean adjust(Graph graph, int depth, IndependenceTest test) {
        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            boolean existsSepset = existsSepset(x, y, depth, graph, test);

            if (existsSepset) {
                graph.removeEdge(x, y);
            }
        }

        return freeDegree(nodes, graph) > depth;
    }

    private int freeDegree(List<Node> nodes, Graph graph) {
        int max = 0;

        for (Node x : nodes) {
            List<Node> opposites = graph.getAdjacentNodes(x);

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

    private static synchronized boolean existsSepset(Node a, Node c, int depth, Graph graph, IndependenceTest test) {
        System.out.println("Calculating max avg for " + a + " --- " + c + " depth = " + depth);

        List<Node> adja = graph.getAdjacentNodes(a);
        List<Node> adjc = graph.getAdjacentNodes(c);

        adja.remove(c);
        adjc.remove(a);

        depth = depth == -1 ? 1000 : depth;

        List<List<Node>> adj = new ArrayList<>();
        adj.add(adja);
        adj.add(adjc);

        List<Double> pValues = new ArrayList<>();

        boolean bb = false;

        for (List<Node> _adj : adj) {
            DepthChoiceGenerator cg1 = new DepthChoiceGenerator(_adj.size(), depth);
            int[] comb2;

            while ((comb2 = cg1.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                List<Node> s = GraphUtils.asList(comb2, _adj);

                if (bb && s.isEmpty()) continue;

                test.isIndependent(a, c, s);
                pValues.add(test.getPValue());
            }

            bb = true;
        }

        return existsSepsetFromList(pValues, test.getAlpha());
    }

    private static boolean existsSepsetFromList(List<Double> pValues, double alpha) {
        Collections.sort(pValues);
        return  pValues.get(pValues.size() - 1) > alpha;
    }


}

