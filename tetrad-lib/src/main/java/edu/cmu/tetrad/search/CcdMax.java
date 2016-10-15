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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;

/**
 * This class provides the data structures and methods for carrying out the Cyclic Causal Discovery algorithm (CCD)
 * described by Thomas Richardson and Peter Spirtes in Chapter 7 of Computation, Causation, & Discovery by Glymour and
 * Cooper eds.  The comments that appear below are keyed to the algorithm specification on pp. 269-271. </p> The search
 * method returns an instance of a Graph but it also constructs two lists of node triples which represent the underlines
 * and dotted underlines that the algorithm discovers.
 *
 * @author Frank C. Wimberly
 * @author Joseph Ramsey
 */
public final class CcdMax implements GraphSearch {
    private IndependenceTest independenceTest;
    private int depth = -1;
    private boolean applyOrientAwayFromCollider = false;
    private boolean orientTowardDConnection = true;

    public CcdMax(IndependenceTest test) {
        if (test == null) throw new NullPointerException();
        this.independenceTest = test;
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * The search method assumes that the IndependenceTest provided to the constructor is a conditional independence
     * oracle for the SEM (or Bayes network) which describes the causal structure of the population. The method returns
     * a PAG instantiated as a Tetrad GaSearchGraph which represents the equivalence class of digraphs which are
     * d-separation equivalent to the digraph of the underlying model (SEM or BN). </p> Although they are not returned
     * by the search method it also computes two lists of triples which, respectively store the underlines and dotted
     * underlines of the PAG.
     */
    public Graph search() {

        SepsetMap map = new SepsetMap();

        Graph graph = fastAdjacencySearch();
        orientCollidersMaxP(graph, map);
        orientTwoShieldConstructs(graph);

        if (orientTowardDConnection) {
            orientTowardDConnection(graph, map);
        }

        return graph;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public long getElapsedTime() {
        return 0;
    }

    public void setApplyOrientAwayFromCollider(boolean applyOrientAwayFromCollider) {
        this.applyOrientAwayFromCollider = applyOrientAwayFromCollider;
    }

    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public void setIndependenceTest(IndependenceTest independenceTest) {
        this.independenceTest = independenceTest;
    }

    //======================================== PRIVATE METHODS ====================================//

    private Graph fastAdjacencySearch() {
        FasStableConcurrent fas = new FasStableConcurrent(null, independenceTest);
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        fas.setRecordSepsets(false);
        Graph graph = fas.search();
        graph.reorientAllWith(Endpoint.CIRCLE);
        return graph;
    }

    private void orientCollidersMaxP(Graph graph, SepsetMap map) {
        SepsetsMinScore sepsets = new SepsetsMinScore(graph, independenceTest, -1);
        sepsets.setReturnNullWhenIndep(false);

        final Map<Triple, Double> colliders = new ConcurrentHashMap<>();
        final Map<Triple, Double> noncolliders = new ConcurrentHashMap<>();

        List<Node> nodes = graph.getNodes();

        class Task extends RecursiveTask<Boolean> {
            private final SepsetProducer sepsets;
            private final SepsetMap map;
            private final Map<Triple, Double> colliders;
            private final Map<Triple, Double> noncolliders;
            private final int from;
            private final int to;
            private final int chunk = 100;
            private final List<Node> nodes;
            private final Graph graph;

            private Task(SepsetProducer sepsets, SepsetMap map, List<Node> nodes, Graph graph,
                         Map<Triple, Double> colliders,
                         Map<Triple, Double> noncolliders, int from, int to) {
                this.sepsets = sepsets;
                this.map = map;
                this.nodes = nodes;
                this.graph = graph;
                this.from = from;
                this.to = to;
                this.colliders = colliders;
                this.noncolliders = noncolliders;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        doNodeCollider(graph, colliders, noncolliders, nodes.get(i), map);
                    }

                    return true;
                } else {
                    int mid = (to + from) / 2;

                    Task left = new Task(sepsets, map, nodes, graph, colliders, noncolliders, from, mid);
                    Task right = new Task(sepsets, map, nodes, graph, colliders, noncolliders, mid, to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        Task task = new Task(sepsets, map, nodes, graph, colliders, noncolliders, 0, nodes.size());

        ForkJoinPoolInstance.getInstance().getPool().invoke(task);

        List<Triple> collidersList = new ArrayList<>(colliders.keySet());
        List<Triple> noncollidersList = new ArrayList<>(noncolliders.keySet());

        // Most independent ones first.
        Collections.sort(collidersList, new Comparator<Triple>() {

            @Override
            public int compare(Triple o1, Triple o2) {
                return Double.compare(colliders.get(o2), colliders.get(o1));
            }
        });

        for (Triple triple : collidersList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            if (!(graph.getEndpoint(b, a) == Endpoint.ARROW || graph.getEndpoint(b, c) == Endpoint.ARROW)) {
                orientCollider(graph, a, b, c);
            }
        }

        for (Triple triple : noncollidersList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.addUnderlineTriple(a, b, c);
        }

        orientAwayFromArrow(graph);
    }

    private void doNodeCollider(Graph graph, Map<Triple, Double> colliders,
                                Map<Triple, Double> noncolliders, Node b, SepsetMap map) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(b);

        if (adjacentNodes.size() < 2) {
            return;
        }

        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            Node a = adjacentNodes.get(combination[0]);
            Node c = adjacentNodes.get(combination[1]);

            // Skip triples that are shielded.
            if (graph.isAdjacentTo(a, c)) {
                continue;
            }

            Pair P = getSepset(a, c, graph);
            List<Node> S = P.getCond();
            double score = P.getScore();

            if (S == null) {
                continue;
            }

            map.set(a, c, S);

            if (S.contains(b)) {
                noncolliders.put(new Triple(a, b, c), score);
            } else {
                colliders.put(new Triple(a, b, c), score);
            }
        }
    }

    private void orientTowardDConnection(Graph graph, SepsetMap map) {

        EDGE:
        for (Edge edge : graph.getEdges()) {
            if (!Edges.isNondirectedEdge(edge)) continue;

            Set<Node> surround = new HashSet<>();
            Node x = edge.getNode1();
            Node y = edge.getNode2();
            surround.add(x);

            for (int i = 1; i < 2; i++) {
                for (Node z : new HashSet<>(surround)) {
                    surround.addAll(graph.getAdjacentNodes(z));
                }
            }

            surround.remove(x);
            surround.remove(y);
            surround.removeAll(graph.getAdjacentNodes(x));
            surround.removeAll(graph.getAdjacentNodes(y));
            boolean orient = false;
            boolean agree = true;

            for (Node a : surround) {
                List<Node> sepsetax = map.get(a, x);
                List<Node> sepsetay = map.get(a, y);

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
                addDirectedEdge(graph, y, x);
            }

            for (Node a : surround) {
                if (x == a) continue;
                if (y == a) continue;
                if (graph.getAdjacentNodes(x).contains(a)) continue;
                if (graph.getAdjacentNodes(y).contains(a)) continue;

                List<Node> sepsetax = map.get(a, x);
                List<Node> sepsetay = map.get(a, y);

                if (sepsetax == null) continue;
                if (sepsetay == null) continue;
                if (sepsetay.contains(x)) continue;

                if (!sepsetay.containsAll(sepsetax)) {
                    if (!independenceTest.isIndependent(a, x, sepsetay)) {
                        addDirectedEdge(graph, y, x);
                        orientAwayFromArrow(y, x, graph);
                        continue EDGE;
                    }
                }
            }
        }
    }

    // Orient feedback loops and a few extra directed edges.
    private void orientTwoShieldConstructs(Graph graph) {
        TetradLogger.getInstance().log("info", "\nStep E");

        for (Node x : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(x);

            for (int i = 0; i < adj.size(); i++) {
                Node a = adj.get(i);

                for (int j = i + 1; j < adj.size(); j++) {
                    Node b = adj.get(j);
                    if (a == b) continue;
                    if (graph.isAdjacentTo(a, b)) continue;

                    for (Node y : adj) {
                        if (y == a || y == b) continue;

                        if (graph.isAdjacentTo(y, a) && graph.isAdjacentTo(y, b)) {
                            if (sepsetNotContaining(graph, a, b, x, y) != null) {
                                Edge edge = graph.getEdge(x, y);
                                orientCollider(graph, a, x, b);
                                orientCollider(graph, a, y, b);

                                if ((Edges.isUndirectedEdge(edge) || Edges.isDirectedEdge(edge))) {
                                    continue;
                                }

                                if (sepsetContaining(graph, a, b, x, y) != null) {
                                    addUndirectedEdge(graph, x, y);
                                    graph.addDottedUnderlineTriple(a, x, b);
                                    graph.addDottedUnderlineTriple(a, y, b);
                                } else if (sepsetNotContainingButNot(graph, a, b, x, y) != null) {
                                    addDirectedEdge(graph, x, y);
                                    graph.addDottedUnderlineTriple(a, x, b);
                                } else if (sepsetNotContainingButNot(graph, b, a, y, x) != null) {
                                    addDirectedEdge(graph, y, x);
                                    graph.addDottedUnderlineTriple(a, y, b);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void addDirectedEdge(Graph graph, Node x, Node y) {
        if (wouldCreateBadCollider(x, y, graph)) return;
        graph.removeEdge(x, y);
        graph.addDirectedEdge(x, y);
        orientAwayFromArrow(x, y, graph);
    }

    private void addUndirectedEdge(Graph graph, Node x, Node y) {
        graph.removeEdge(x, y);
        graph.addUndirectedEdge(x, y);
    }

    private void orientCollider(Graph graph, Node a, Node b, Node c) {
        if (wouldCreateBadCollider(a, b, graph)) return;
        if (wouldCreateBadCollider(c, b, graph)) return;
        graph.removeEdge(a, b);
        graph.removeEdge(c, b);
        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(c, b);
    }

    private void orientAwayFromArrow(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();

            edge = graph.getEdge(n1, n2);

            if (edge.pointsTowards(n1)) {
                orientAwayFromArrow(n2, n1, graph);
            } else if (edge.pointsTowards(n2)) {
                orientAwayFromArrow(n1, n2, graph);
            }
        }
    }

    private void orientAwayFromArrow(Node a, Node b, Graph graph) {
        if (!isApplyOrientAwayFromCollider()) return;

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            orientAwayFromArrowVisit(a, b, c, graph);
        }
    }

    private boolean orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {
        if (!Edges.isNondirectedEdge(graph.getEdge(b, c))) {
            return false;
        }

        if (!(graph.isUnderlineTriple(a, b, c))) {
            return false;
        }

        if (graph.getEdge(b, c).pointsTowards(b)) {
            return false;
        }

        addDirectedEdge(graph, b, c);
        graph.removeUnderlineTriple(a, b, c);

        for (Node d : graph.getAdjacentNodes(c)) {
            if (d == b) return true;

            Edge bc = graph.getEdge(b, c);

            if (!orientAwayFromArrowVisit(b, c, d, graph)) {
                graph.removeEdge(b, c);
                graph.addEdge(bc);
            }
        }

        return true;
    }

    private boolean wouldCreateBadCollider(Node x, Node y, Graph graph) {
        for (Node z : graph.getAdjacentNodes(y)) {
            if (x == z) continue;
            if (graph.getEndpoint(x, y) != Endpoint.ARROW && graph.getEndpoint(z, y) == Endpoint.ARROW) return true;
        }

        return false;
    }

    private boolean isApplyOrientAwayFromCollider() {
        return applyOrientAwayFromCollider;
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

    private Pair getSepset(Node i, Node k, Graph graph) {
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

                    getIndependenceTest().isIndependent(i, k, v2);
                    double p2 = getIndependenceTest().getScore();

                    if (p2 < _p) {
                        _p = p2;
                        _v = v2;
                    }
                }
            }

            if (d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v2 = GraphUtils.asList(choice, adjk);

                    getIndependenceTest().isIndependent(i, k, v2);
                    double p2 = getIndependenceTest().getScore();

                    if (p2 < _p) {
                        _p = p2;
                        _v = v2;
                    }
                }
            }
        }

        return new Pair(_v, _p);
    }

    private List<Node> sepsetContaining(Graph graph, Node a, Node c, Node... x) {
        double _p = Double.POSITIVE_INFINITY;
        List<Node> _v = null;

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
                    Collections.addAll(v2, x);
                    v2.remove(a);
                    v2.remove(c);

                    getIndependenceTest().isIndependent(a, c, new ArrayList<>(v2));
                    double p2 = getIndependenceTest().getScore();

                    if (p2 < _p && p2 < 0) {
                        _p = p2;
                        _v = new ArrayList<>(v2);
                    }
                }
            }
        }

        return _v;
    }

    private List<Node> sepsetNotContaining(Graph graph, Node a, Node c, Node... x) {
        double _p = Double.POSITIVE_INFINITY;

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
                    for (Node _x : x) v2.remove(_x);
                    v2.remove(a);
                    v2.remove(c);

                    getIndependenceTest().isIndependent(a, c, new ArrayList<>(v2));
                    double p2 = getIndependenceTest().getScore();

                    if (p2 < _p && p2 < 0) {
                        return new ArrayList<>(v2);
                    }
                }
            }
        }

        return null;
    }

    private List<Node> sepsetNotContainingButNot(Graph graph, Node a, Node c, Node x, Node y) {
        double _p = Double.POSITIVE_INFINITY;

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
                    v2.add(x);
                    v2.remove(y);
                    v2.remove(a);
                    v2.remove(c);

                    getIndependenceTest().isIndependent(a, c, new ArrayList<>(v2));
                    double p2 = getIndependenceTest().getScore();

                    if (p2 < _p && p2 < 0) {
                        return new ArrayList<>(v2);
                    }
                }
            }
        }

        return null;
    }
}






