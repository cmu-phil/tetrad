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

import java.util.*;

/**
 * This is an optimization of the CCD (Cyclic Causal Discovery) algorithm by Thomas Richardson.
 *
 * @author Joseph Ramsey
 */
public final class CcdMax implements GraphSearch {
    private IndependenceTest independenceTest;
    private int depth = -1;
    private boolean applyOrientAwayFromCollider = false;
    private long elapsed = 0;
    private IKnowledge knowledge = new Knowledge2();
    private boolean useHeuristic = true;
    private int maxPathLength = 3;
    private boolean useOrientTowardDConnections = true;
    private boolean orientConcurrentFeedbackLoops = true;
    private boolean doColliderOrientations = true;
    private boolean collapseTiers = false;
    private SepsetMap sepsetMap = null;

    public CcdMax(IndependenceTest test) {
        if (test == null) throw new NullPointerException();
        this.independenceTest = test;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search() {

        System.out.println("FAS");
        Graph graph = fastAdjacencySearch();

        System.out.println("Orienting from background knowledge");

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (knowledge.isForbidden(y.getName(), x.getName()) || knowledge.isRequired(x.getName(), y.getName())) {
                graph.removeEdge(x, y);
                graph.addDirectedEdge(x, y);
            } else if (knowledge.isForbidden(x.getName(), y.getName()) || knowledge.isRequired(y.getName(), x.getName())) {
                graph.removeEdge(y, x);
                graph.addDirectedEdge(y, x);
            }
        }

        System.out.println("Bishop's hat");

        if (orientConcurrentFeedbackLoops) {
            orientTwoShieldConstructs(graph);
        }

        System.out.println("Max P collider orientation");

        if (doColliderOrientations) {
            final OrientCollidersMaxP orientCollidersMaxP = new OrientCollidersMaxP(independenceTest);
            orientCollidersMaxP.setUseHeuristic(useHeuristic);
            orientCollidersMaxP.setMaxPathLength(maxPathLength);
            orientCollidersMaxP.setKnowledge(knowledge);
            orientCollidersMaxP.orient(graph);
        }

        System.out.println("Orient away from collider");

        if (applyOrientAwayFromCollider) {
            orientAwayFromArrow(graph);
        }

        System.out.println("Toward D-connection");

        if (useOrientTowardDConnections) {
            orientTowardDConnection(graph);
        }

        System.out.println("Done");

        if (collapseTiers) {
            return collapseGraph(graph);
        } else {
            return graph;
        }
    }

    private Graph collapseGraph(Graph graph) {
        List<Node> nodes = new ArrayList<>();

        for (String n : independenceTest.getVariableNames()) {
            String[] s = n.split(":");

            if (s.length == 1) {
                Node x = independenceTest.getVariable(s[0]);
                nodes.add(x);
            }
        }

        Graph _graph = new EdgeListGraph(nodes);

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            String[] sx = x.getName().split(":");
            String[] sy = y.getName().split(":");

            int lagx = sx.length == 1 ? 0 : new Integer(sx[1]);
            int lagy = sy.length == 1 ? 0 : new Integer(sy[1]);

            int maxInto = knowledge.getNumTiers() - 1;

            if (!((!edge.pointsTowards(x) && lagy < maxInto)
                    || (!edge.pointsTowards(y) && lagx < maxInto))) continue;

            String xName = sx[0];
            String yName = sy[0];

            Node xx = independenceTest.getVariable(xName);
            Node yy = independenceTest.getVariable(yName);

            if (xx == yy) continue;

            Edge _edge = new Edge(xx, yy, edge.getEndpoint1(), edge.getEndpoint2());

            if (!_graph.containsEdge(_edge)) {
                _graph.addEdge(_edge);
            }

            Edge undir = Edges.undirectedEdge(xx, yy);

            if (_graph.getEdges(xx, yy).size() > 1 && _graph.containsEdge(undir)) {
                _graph.removeEdge(undir);
            }
        }

        return _graph;
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

    /**
     * @param applyOrientAwayFromCollider True if the orient away from collider rule should be
     *                                    applied.
     */
    public void setApplyOrientAwayFromCollider(boolean applyOrientAwayFromCollider) {
        this.applyOrientAwayFromCollider = applyOrientAwayFromCollider;
    }

    //======================================== PRIVATE METHODS ====================================//

    private Graph fastAdjacencySearch() {
        long start = System.currentTimeMillis();

        FasConcurrent fas = new FasConcurrent(null, independenceTest);
        fas.setDepth(getDepth());
        fas.setKnowledge(knowledge);
        fas.setVerbose(false);

        Graph graph = fas.search();

        if (useOrientTowardDConnections) {
            this.sepsetMap = fas.getSepsets();
        }

        long stop = System.currentTimeMillis();
        this.elapsed = stop - start;

        return new EdgeListGraph(graph);
    }

    // Orient feedback loops and a few extra directed edges.
    private void orientTwoShieldConstructs(Graph graph) {
        TetradLogger.getInstance().log("info", "\nStep E");

        for (Node c : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(c);

            for (int i = 0; i < adj.size(); i++) {
                Node a = adj.get(i);

                for (int j = i + 1; j < adj.size(); j++) {
                    Node b = adj.get(j);
                    if (a == b) continue;
                    if (graph.isAdjacentTo(a, b)) continue;

                    for (Node d : adj) {
                        if (d == a || d == b) continue;

                        if (graph.isAdjacentTo(d, a) && graph.isAdjacentTo(d, b)) {
                            if (sepset(graph, a, b, set(), set(c, d)) != null) {
                                if ((graph.getEdges().size() == 2 || Edges.isDirectedEdge(graph.getEdge(c, d)))) {
                                    continue;
                                }

                                if (
                                        graph.getEdge(a, c).pointsTowards(a)
                                                || graph.getEdge(a, d).pointsTowards(a)
                                                || graph.getEdge(b, c).pointsTowards(b)
                                                || graph.getEdge(b, d).pointsTowards(b)
                                ) {
                                    continue;
                                }

                                if (sepset(graph, a, b, set(c, d), set()) != null) {
                                    orientCollider(graph, a, c, b);
                                    orientCollider(graph, a, d, b);
                                    addFeedback(graph, c, d);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void orientTowardDConnection(Graph graph) {

        EDGE:
        for (Edge edge : graph.getEdges()) {
            if (!Edges.isUndirectedEdge(edge)) continue;

            Set<Node> surround = new HashSet<>();
            Node b = edge.getNode1();
            Node c = edge.getNode2();
            surround.add(b);

            for (int i = 1; i < 3; i++) {
                for (Node z : new HashSet<>(surround)) {
                    surround.addAll(graph.getAdjacentNodes(z));
                }
            }

            surround.remove(b);
            surround.remove(c);
            surround.removeAll(graph.getAdjacentNodes(b));
            surround.removeAll(graph.getAdjacentNodes(c));
            boolean orient = false;
            boolean agree = true;

            for (Node a : surround) {
//                List<Node> sepsetax = map.get(a, b);
//                List<Node> sepsetay = map.get(a, c);

                List<Node> sepsetax = maxPSepset(a, b, graph).getCond();
                List<Node> sepsetay = maxPSepset(a, c, graph).getCond();

                if (sepsetax == null) continue;
                if (sepsetay == null) continue;

                if (!sepsetax.equals(sepsetay)) {
                    if (sepsetax.containsAll(sepsetay)) {
                        orient = true;
                    } else {
                        agree = false;
                    }
                }
            }

            if (orient && agree) {
                addDirectedEdge(graph, c, b);
            }

            for (Node a : surround) {
                if (b == a) continue;
                if (c == a) continue;
                if (graph.getAdjacentNodes(b).contains(a)) continue;
                if (graph.getAdjacentNodes(c).contains(a)) continue;

                List<Node> sepsetax = sepsetMap.get(a, b);
                List<Node> sepsetay = sepsetMap.get(a, c);

                if (sepsetax == null) continue;
                if (sepsetay == null) continue;
                if (sepsetay.contains(b)) continue;

                if (!sepsetay.containsAll(sepsetax)) {
                    if (!independenceTest.isIndependent(a, b, sepsetay)) {
                        addDirectedEdge(graph, c, b);
                        continue EDGE;
                    }
                }
            }
        }
    }

    private void addDirectedEdge(Graph graph, Node a, Node b) {
        graph.removeEdges(a, b);
        graph.addDirectedEdge(a, b);
        orientAwayFromArrow(graph, a, b);
    }

    private void addFeedback(Graph graph, Node a, Node b) {
        graph.removeEdges(a, b);
        graph.addEdge(Edges.directedEdge(a, b));
        graph.addEdge(Edges.directedEdge(b, a));
    }

    private void orientCollider(Graph graph, Node a, Node b, Node c) {
        if (wouldCreateBadCollider(graph, a, b)) return;
        if (wouldCreateBadCollider(graph, c, b)) return;
        if (graph.getEdges(a, b).size() > 1) return;
        if (graph.getEdges(b, c).size() > 1) return;

        if (knowledge.isForbidden(a.getName(), b.getName())) return;
        if (knowledge.isForbidden(c.getName(), b.getName())) return;

        graph.removeEdge(a, b);
        graph.removeEdge(c, b);
        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(c, b);
    }

    private void orientAwayFromArrow(Graph graph, Node a, Node b) {
        if (!applyOrientAwayFromCollider) return;

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            orientAwayFromArrowVisit(a, b, c, graph);
        }
    }

    private boolean wouldCreateBadCollider(Graph graph, Node x, Node y) {
        for (Node z : graph.getAdjacentNodes(y)) {
            if (x == z) continue;

            if (graph.isDefCollider(x, y, z)) {
                return true;
            }

//            if (!graph.isAdjacentTo(z, y) &&
//                    graph.getEndpoint(z, y) == Endpoint.ARROW
////                    &&
////                    sepset(graph, x, z, set(), set(y)) == null
//                    ) {
//                return true;
//            }
        }

        return false;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
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

    public boolean isUseOrientTowardDConnections() {
        return useOrientTowardDConnections;
    }

    public void setUseOrientTowardDConnections(boolean useOrientTowardDConnections) {
        this.useOrientTowardDConnections = useOrientTowardDConnections;
    }

    public void setOrientConcurrentFeedbackLoops(boolean orientConcurrentFeedbackLoops) {
        this.orientConcurrentFeedbackLoops = orientConcurrentFeedbackLoops;
    }

    public void setDoColliderOrientations(boolean doColliderOrientations) {
        this.doColliderOrientations = doColliderOrientations;
    }

    public void setCollapseTiers(boolean collapseTiers) {
        this.collapseTiers = collapseTiers;
    }

    private class Pair {
        private List<Node> cond;
        private double score;

        Pair(List<Node> cond, double score) {
            this.cond = cond;
            this.score = score;
        }

        public List<Node> getCond() {
            return cond;
        }

        public double getScore() {
            return score;
        }

    }

    private Pair maxPSepset(Node i, Node k, Graph graph) {
        double _p = Double.POSITIVE_INFINITY;
        List<Node> _v = null;

        List<Node> adji = graph.getAdjacentNodes(i);
        List<Node> adjk = graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), Math.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v2 = GraphUtils.asList(choice, adji);

                    if (isForbidden(i, k, v2)) continue;

                    try {
                        getIndependenceTest().isIndependent(i, k, v2);
                        double p2 = getIndependenceTest().getScore();

                        if (p2 < _p) {
                            _p = p2;
                            _v = v2;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return new Pair(null, Double.POSITIVE_INFINITY);
                    }
                }
            }

            if (d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v2 = GraphUtils.asList(choice, adjk);

                    try {
                        getIndependenceTest().isIndependent(i, k, v2);
                        double p2 = getIndependenceTest().getScore();

                        if (p2 < _p) {
                            _p = p2;
                            _v = v2;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return new Pair(null, Double.POSITIVE_INFINITY);
                    }
                }
            }
        }

        return new Pair(_v, _p);
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

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), Math.max(adj.size(), adj.size())); d++) {
            if (d <= adj.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Set<Node> v2 = GraphUtils.asSet(choice, adj);
                    v2.addAll(containing);
                    v2.removeAll(notContaining);
                    v2.remove(a);
                    v2.remove(c);

                    if (!isForbidden(a, c, new ArrayList<>(v2))) {
                        getIndependenceTest().isIndependent(a, c, new ArrayList<>(v2));
                        double p2 = getIndependenceTest().getScore();

                        if (p2 < 0) {
                            return new ArrayList<>(v2);
                        }
                    }
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

    private void orientAwayFromArrow(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();

            edge = graph.getEdge(n1, n2);

            if (edge.pointsTowards(n1)) {
                orientAwayFromArrow(graph, n2, n1);
            } else if (edge.pointsTowards(n2)) {
                orientAwayFromArrow(graph, n1, n2);
            }
        }
    }

    private void orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {

        // This shouldn't happen--a--b--c should be shielded. Checking just in case...
        if (graph.getEdges(b, c).size() > 1) {
            return;
        }

        if (!Edges.isUndirectedEdge(graph.getEdge(b, c))) {
            return;
        }

        if (graph.isAdjacentTo(a, c)) {
            return;
        }

        if (knowledge.isForbidden(b.getName(), c.getName())) {
            return;
        }

        if (wouldCreateBadCollider(graph, b, c)) {
            return;
        }

        addDirectedEdge(graph, b, c);

        List<Edge> undirectedEdges = new ArrayList<>();

        for (Node d : graph.getAdjacentNodes(c)) {
            if (d == b) continue;
            Edge e = graph.getEdge(c, d);
            if (Edges.isUndirectedEdge(e)) undirectedEdges.add(e);
        }

        for (Node d : graph.getAdjacentNodes(c)) {
            if (d == b) continue;
            orientAwayFromArrowVisit(b, c, d, graph);
        }

        boolean allOriented = true;

        for (Edge e : undirectedEdges) {
            Node d = Edges.traverse(c, e);
            Edge f = graph.getEdge(c, d);

            if (!f.pointsTowards(d)) {
                allOriented = false;
                break;
            }
        }

//        if (!allOriented) {
//            for (Edge e : undirectedEdges) {
//                Node d = Edges.traverse(c, e);
//                Edge f = graph.getEdge(c, d);
//
//                graph.removeEdge(f);
//                graph.addEdge(e);
//            }
//        }
    }
}





