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

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;

/**
 * This is an optimization of the CCD (Cyclic Causal Discovery) algorithm by Thomas Richardson.
 *
 * @author Joseph Ramsey
 */
public final class OrientCollidersMaxP {
    private final IndependenceTest independenceTest;
    private int depth = -1;
    private long elapsed = 0;
    private IKnowledge knowledge = new Knowledge2();
    private boolean useHeuristic = false;
    private int maxPathLength = 3;
    private PcAll.ConflictRule conflictRule = PcAll.ConflictRule.OVERWRITE;

    public OrientCollidersMaxP(IndependenceTest test) {
        if (test == null) throw new NullPointerException();
        this.independenceTest = test;
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * Searches for a PAG satisfying the description in Thomas Richardson (1997), dissertation,
     * Carnegie Mellon University. Uses a simplification of that algorithm.
     */
    public synchronized void orient(Graph graph) {
        addColliders(graph);
    }

    /**
     * @return The depth of search for the Fast Adjacency Search.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @param depth The depth of search for the Fast Adjacency Search.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * @return The elapsed time in milliseconds.
     */
    public long getElapsedTime() {
        return elapsed;
    }

    //======================================== PRIVATE METHODS ====================================//

    private void addColliders(Graph graph) {
        final Map<Triple, Double> scores = new ConcurrentHashMap<>();

        List<Node> nodes = graph.getNodes();

//        class Task extends RecursiveTask<Boolean> {
//            int from;
//            int to;
//            int chunk = 20;
//            List<Node> nodes;
//            Graph graph;
//
//            public Task(List<Node> nodes, Graph graph, Map<Triple, Double> scores, int from, int to) {
//                this.nodes = nodes;
//                this.graph = graph;
//                this.from = from;
//                this.to = to;
//            }
//
//            @Override
//            protected Boolean compute() {
//                if (to - from <= chunk) {
//                    for (int i = from; i < to; i++) {
//                        if (Thread.currentThread().isInterrupted()) {
//                            break;
//                        }
//
//                        doNode(graph, scores, nodes.get(i));
//                    }
//
//                    return true;
//                } else {
//                    int mid = (to + from) / 2;
//
//                    Task left = new Task(nodes, graph, scores, from, mid);
//                    Task right = new Task(nodes, graph, scores, mid, to);
//
//                    left.fork();
//                    right.compute();
//                    left.join();
//
//                    return true;
//                }
//            }
//        }

//        Task task = new Task(nodes, graph, scores, 0, nodes.size());
//
//        ForkJoinPoolInstance.getInstance().getPool().invoke(task);
//
        for (int i = 0; i < nodes.size(); i++) {
            doNode(graph, scores, nodes.get(i));
        }

        List<Triple> tripleList = new ArrayList<>(scores.keySet());

        // Most independent ones first.
        tripleList.sort((o1, o2) -> Double.compare(scores.get(o2), scores.get(o1)));

        for (Triple triple : tripleList) {
            System.out.println(triple + " score = " + scores.get(triple));
        }

        for (Triple triple : tripleList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            orientCollider(graph, a, b, c, getConflictRule());
        }
    }

    private void doNode(Graph graph, Map<Triple, Double> scores, Node b) {
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

            if (useHeuristic) {
                if (existsShortPath(a, c, maxPathLength, graph)) {
                    testColliderMaxP(graph, scores, a, b, c);
                } else {
                    testColliderHeuristic(graph, scores, a, b, c);
                }
            } else {
                testColliderMaxP(graph, scores, a, b, c);
            }
        }
    }

    private void testColliderMaxP(Graph graph, Map<Triple, Double> scores, Node a, Node b, Node c) {
        List<Node> adja = graph.getAdjacentNodes(a);
        List<Node> adjc = graph.getAdjacentNodes(c);
        adja.remove(c);
        adjc.remove(a);

        double p = 0;
        List<Node> S = null;

        double pSum1 = 0.0;
        double pSum2 = 0.0;

        int count1 = 0;
        int count2 = 0;

        DepthChoiceGenerator cg1 = new DepthChoiceGenerator(adja.size(), depth);
        int[] comb2;

        while ((comb2 = cg1.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> s = GraphUtils.asList(comb2, adja);

            independenceTest.isIndependent(a, c, s);
            double _p = independenceTest.getPValue();

            if (_p > p) {
                p = _p;
                S = s;
            }

            if (_p < independenceTest.getAlpha()) continue;

            if (s.contains(b)) {
                pSum1 += p;
                count1++;
            } else {
                pSum2 += p;
                count2++;
            }
        }

        DepthChoiceGenerator cg2 = new DepthChoiceGenerator(adjc.size(), depth);
        int[] comb3;

        while ((comb3 = cg2.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> s = GraphUtils.asList(comb3, adjc);

            independenceTest.isIndependent(a, c, s);
            double _p = independenceTest.getPValue();

            if (_p > p) {
                p = _p;
                S = s;
            }

            if (_p < independenceTest.getAlpha()) continue;

            if (s.contains(b)) {
                pSum1 += p;
                count1++;
            } else {
                pSum2 += p;
                count2++;
            }
        }

        double avg1 = pSum1 / count1;
        double avg2 = pSum2 / count2;

//        if (avg2 > avg1) {
//            scores.put(new Triple(a, b, c), avg2);
//        }

        if (S != null && !S.contains(b)) {
            scores.put(new Triple(a, b, c), p);
        }
    }

    private void testColliderHeuristic(Graph graph, Map<Triple, Double> colliders, Node a, Node b, Node c) {
        if (knowledge.isForbidden(a.getName(), b.getName())) {
            return;
        }

        if (knowledge.isForbidden(c.getName(), b.getName())) {
            return;
        }

        independenceTest.isIndependent(a, c);
        double s1 = independenceTest.getScore();
        independenceTest.isIndependent(a, c, b);
        double s2 = independenceTest.getScore();

        boolean mycollider2 = s2 > s1;

        // Skip triples that are shielded.
        if (graph.isAdjacentTo(a, c)) {
            return;
        }

        if (graph.getEdges(a, b).size() > 1 || graph.getEdges(b, c).size() > 1) {
            return;
        }

        if (mycollider2) {
            colliders.put(new Triple(a, b, c), Math.abs(s2));
        }
    }

    private void orientCollider(Graph graph, Node a, Node b, Node c, PcAll.ConflictRule conflictRule) {
        if (knowledge.isForbidden(a.getName(), b.getName())) return;
        if (knowledge.isForbidden(c.getName(), b.getName())) return;
        orientCollider(a, b, c, conflictRule, graph);
    }

    private boolean wouldCreateBadCollider(Graph graph, Node x, Node y) {
        for (Node z : graph.getAdjacentNodes(y)) {
            if (x == z) continue;

            if (!graph.isAdjacentTo(x, z) &&
                    graph.getEndpoint(z, y) == Endpoint.ARROW &&
                    sepset(graph, x, z, set(), set(y)) == null) {
                return true;
            }
        }

        return false;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    private boolean isForbidden(Node i, Node k, List<Node> v) {
        for (Node w : v) {
            if (knowledge.isForbidden(w.getName(), i.getName())) {
                return true;
            }

            if (knowledge.isForbidden(w.getName(), k.getName())) {
                return true;
            }
        }

        return false;
    }

    // Returns a sepset containing the nodes in 'containing' but not the nodes in 'notContaining', or
    // null if there is no such sepset.
    private List<Node> sepset(Graph graph, Node a, Node c, Set<Node> containing, Set<Node> notContaining) {
        List<Node> adj = graph.getAdjacentNodes(a);
        adj.addAll(graph.getAdjacentNodes(c));
        adj.remove(c);
        adj.remove(a);

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), adj.size()); d++) {
            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Set<Node> v2 = GraphUtils.asSet(choice, adj);
                v2.addAll(containing);
                v2.removeAll(notContaining);
                v2.remove(a);
                v2.remove(c);

                getIndependenceTest().isIndependent(a, c, new ArrayList<>(v2));
                double p2 = getIndependenceTest().getScore();

                if (p2 < 0) {
                    return new ArrayList<>(v2);
                }
            }
        }

        return null;
    }

    private Set<Node> set(Node... n) {
        Set<Node> S = new HashSet<>();
        Collections.addAll(S, n);
        return S;
    }

    private IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    // Returns true if there is an undirected path from x to either y or z within the given number of steps.
    private boolean existsShortPath(Node x, Node z, int bound, Graph graph) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();
        Q.offer(x);
        V.add(x);
        Node e = null;
        int distance = 0;

        while (!Q.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node t = Q.remove();

            if (e == t) {
                e = null;
                distance++;
                if (distance > (bound == -1 ? 1000 : bound)) return false;
            }

            for (Node u : graph.getAdjacentNodes(t)) {
                Edge edge = graph.getEdge(t, u);
                Node c = Edges.traverse(t, edge);
                if (c == null) continue;
                if (c == z && distance > 2) return true;

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        return false;
    }

    public boolean isUseHeuristic() {
        return useHeuristic;
    }

    public void setUseHeuristic(boolean useHeuristic) {
        this.useHeuristic = useHeuristic;
    }

    public int getMaxPathLength() {
        return maxPathLength;
    }

    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    public static void orientCollider(Node x, Node y, Node z, PcAll.ConflictRule conflictRule, Graph graph) {
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

    public PcAll.ConflictRule getConflictRule() {
        return conflictRule;
    }

    public void setConflictRule(PcAll.ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }
}





