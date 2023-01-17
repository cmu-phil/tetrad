package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.SublistGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import static java.lang.Math.min;


/**
 * Implements the backward equivalence search of FGES.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class LvBesJoe {
    private final List<Node> variables;
    private final Score score;
    private Knowledge knowledge = new Knowledge();
    private int depth = -1;
    private EdgeListGraph origGraph = null;

    public LvBesJoe(@NotNull Score score) {
        this.score = score;
        this.variables = score.getVariables();
    }

    @NotNull
    public List<Node> getVariables() {
        return this.variables;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
    }

    private void buildIndexing(List<Node> nodes, Map<Node, Integer> hashIndices) {

        int i = -1;

        for (Node n : nodes) {
            hashIndices.put(n, ++i);
        }
    }

    public void bes(Graph graph, List<Node> variables) {
        origGraph = new EdgeListGraph(graph);

        Map<Node, Integer> hashIndices = new HashMap<>();
        SortedSet<Arrow> sortedArrowsBack = new ConcurrentSkipListSet<>();
        Map<Edge, ArrowConfigBackward> arrowsMapBackward = new ConcurrentHashMap<>();
        int[] arrowIndex = new int[1];

        buildIndexing(variables, hashIndices);

        reevaluateBackward(new HashSet<>(variables), graph, hashIndices, arrowIndex, sortedArrowsBack, arrowsMapBackward);

        while (!sortedArrowsBack.isEmpty()) {
            Arrow arrow = sortedArrowsBack.first();

            sortedArrowsBack.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (!graph.isAdjacentTo(x, y)) {
                continue;
            }

            Set<Node> complement = new HashSet<>(arrow.getCommonAdjacents());
            complement.removeAll(arrow.getHOrT());

            double _bump = deleteEval(x, y, complement, arrow.parents, hashIndices);

            delete(x, y, arrow.getHOrT(), _bump, arrow.getCommonAdjacents(), graph);
        }
    }

    private void delete(Node x, Node y, Set<Node> H, double bump, Set<Node> ca, Graph graph) {
        Edge oldxy = graph.getEdge(x, y);

        Set<Node> diff = new HashSet<>(ca);
        diff.removeAll(H);

        graph.removeEdge(oldxy);

        int numEdges = graph.getNumEdges();
        if (numEdges % 1000 == 0 && numEdges > 0) {
            System.out.println("Num edges (backwards) = " + numEdges);
        }

//        if (verbose) {
//            int cond = diff.size() + graph.getParents(y).size();
//
//            String message = (graph.getNumEdges()) + ". DELETE " + x + " --> " + y + " H = " + H + " NaYX = " + ca + " degree = " + GraphUtils.getDegree(graph) + " indegree = " + GraphUtils.getIndegree(graph) + " diff = " + diff + " (" + bump + ") " + " cond = " + cond;
//            TetradLogger.getInstance().forceLogMessage(message);
//        }

        for (Node h : H) {
            if (graph.isAdjacentTo(x, h)) {
                graph.setEndpoint(x, h, Endpoint.ARROW);
            }
            if (graph.isAdjacentTo(y, h)) {
                graph.setEndpoint(y, h, Endpoint.ARROW);
            }
        }
    }

    private double deleteEval(Node x, Node
            y, Set<Node> complement, Set<Node> parents, Map<Node, Integer> hashIndices) {
        Set<Node> set = new HashSet<>(complement);
        set.addAll(parents);
        set.remove(x);

        return -scoreGraphChange(x, y, set, hashIndices);
    }

    private double scoreGraphChange(Node x, Node y, Set<Node> parents, Map<Node, Integer> hashIndices) {
        int xIndex = hashIndices.get(x);
        int yIndex = hashIndices.get(y);

        if (x == y) {
            throw new IllegalArgumentException();
        }

        if (parents.contains(y)) {
            throw new IllegalArgumentException();
        }

        int[] parentIndices = new int[parents.size()];

        int count = 0;
        for (Node parent : parents) {
            parentIndices[count++] = hashIndices.get(parent);
        }

        return score.localScoreDiff(xIndex, yIndex, parentIndices);
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    private Set<Node> getCommonAdjacents(Node x, Node y) {
        List<Node> ca = origGraph.getAdjacentNodes(x);
        ca.retainAll(origGraph.getAdjacentNodes(y));
        return new HashSet<>(ca);
    }

    private void reevaluateBackward(Set<Node> toProcess, Graph graph, Map<Node, Integer> hashIndices,
                                    int[] arrowIndex, SortedSet<Arrow> sortedArrowsBack, Map<Edge, ArrowConfigBackward> arrowsMapBackward) {

        class BackwardTask extends RecursiveTask<Boolean> {
            private final Node r;
            private final List<Node> adj;
            private final Map<Node, Integer> hashIndices;
            private final int chunk;
            private final int from;
            private final int to;
            private final SortedSet<Arrow> sortedArrowsBack;
            final Map<Edge, ArrowConfigBackward> arrowsMapBackward;

            private BackwardTask(Node r, List<Node> adj, int chunk, int from, int to, Map<Node, Integer> hashIndices, SortedSet<Arrow> sortedArrowsBack, Map<Edge, ArrowConfigBackward> arrowsMapBackward) {
                this.adj = adj;
                this.hashIndices = hashIndices;
                this.chunk = chunk;
                this.from = from;
                this.to = to;
                this.r = r;
                this.sortedArrowsBack = sortedArrowsBack;
                this.arrowsMapBackward = arrowsMapBackward;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int _w = from; _w < to; _w++) {
                        final Node w = adj.get(_w);
                        Edge e = graph.getEdge(w, r);

                        if (e != null) {
                            calculateArrowsBackward(w, r, graph, hashIndices, arrowIndex, sortedArrowsBack);
                            calculateArrowsBackward(r, w, graph, hashIndices, arrowIndex, sortedArrowsBack);
                        }
                    }

                } else {
                    int mid = (to - from) / 2;

                    List<BackwardTask> tasks = new ArrayList<>();

                    tasks.add(new BackwardTask(r, adj, chunk, from, from + mid, hashIndices, sortedArrowsBack, arrowsMapBackward));
                    tasks.add(new BackwardTask(r, adj, chunk, from + mid, to, hashIndices, sortedArrowsBack, arrowsMapBackward));

                    invokeAll(tasks);
                }

                return true;
            }
        }

        for (Node r : toProcess) {
            List<Node> adjacentNodes = new ArrayList<>(toProcess);
            ForkJoinPool.commonPool().invoke(new BackwardTask(r, adjacentNodes, getChunkSize(adjacentNodes.size()),
                    0, adjacentNodes.size(), hashIndices, sortedArrowsBack, arrowsMapBackward));
        }
    }

    private int getChunkSize(int n) {
        return 5;
//        int chunk = n / Runtime.getRuntime().availableProcessors();
//        if (chunk < 100) chunk = 100;
//        return chunk;
    }

    private void calculateArrowsBackward(Node a, Node b, Graph graph,
                                         Map<Node, Integer> hashIndices,
                                         int[] arrowIndex, SortedSet<Arrow> sortedArrowsBack) {
        Set<Node> ca = getCommonAdjacents(a, b);

        List<Node> parents = origGraph.getAdjacentNodes(b);
        for (Node n : ca) parents.remove(n);
//        parents.remove(a);

        for (Node n : ca) {
            parents.remove(n);
        }

        List<Node> _ca = new ArrayList<>(ca);

        int _depth = min(depth == -1 ? 100000 : depth, _ca.size());

        final SublistGenerator gen = new SublistGenerator(_ca.size(), _depth);
        int[] choice;
        Set<Node> maxComplement = null;
        double maxBump = Double.NEGATIVE_INFINITY;
        Set<Node> maxParents = new HashSet<>();

        while ((choice = gen.next()) != null) {
            Set<Node> complement = GraphUtils.asSet(choice, _ca);

            SublistGenerator gen2 = new SublistGenerator(parents.size(), -1);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                Set<Node> p = GraphUtils.asSet(choice2, parents);

                double _bump = deleteEval(a, b, complement, p, hashIndices);

                if (_bump > maxBump) {
                    maxBump = _bump;
                    maxComplement = complement;
                    maxParents = p;
                }
            }
        }

        if (maxBump > 0) {
            Set<Node> _H = new HashSet<>(ca);
            _H.removeAll(maxComplement);
            addArrowBackward(a, b, _H, ca, maxParents, maxBump, arrowIndex, sortedArrowsBack);
        }
    }

    private void addArrowBackward(Node a, Node b, Set<Node> hOrT, Set<Node> naYX, Set<Node> parents,
                                  double bump, int[] arrowIndex, SortedSet<Arrow> sortedArrowsBack) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, null, naYX, parents, arrowIndex[0]++);
        sortedArrowsBack.add(arrow);
    }

    private static class ArrowConfigBackward {
        private Set<Node> ca;
        private Set<Node> parents;

        public ArrowConfigBackward(Set<Node> nayx, Set<Node> parents) {
            this.setCa(nayx);
            this.setParents(parents);
        }

        public void setCa(Set<Node> ca) {
            this.ca = ca;
        }

        public Set<Node> getParents() {
            return parents;
        }

        public void setParents(Set<Node> parents) {
            this.parents = parents;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrowConfigBackward that = (ArrowConfigBackward) o;
            return ca.equals(that.ca) && parents.equals(that.parents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ca, parents);
        }
    }


    private static class Arrow implements Comparable<Arrow> {

        private final double bump;
        private final Node a;
        private final Node b;
        private final Set<Node> hOrT;
        private final Set<Node> naYX;
        private final Set<Node> parents;
        private final int index;
        private Set<Node> TNeighbors;

        Arrow(double bump, Node a, Node b, Set<Node> hOrT, Set<Node> capTorH, Set<Node> naYX, Set<Node> parents, int index) {
            this.bump = bump;
            this.a = a;
            this.b = b;
            this.setTNeighbors(capTorH);
            this.hOrT = hOrT;
            this.naYX = naYX;
            this.index = index;
            this.parents = parents;
        }

        public double getBump() {
            return bump;
        }

        public Node getA() {
            return a;
        }

        public Node getB() {
            return b;
        }

        Set<Node> getHOrT() {
            return hOrT;
        }

        Set<Node> getCommonAdjacents() {
            return naYX;
        }

        // Sorting by bump, high to low. The problem is the SortedSet contains won't add a new element if it compares
        // to zero with an existing element, so for the cases where the comparison is to zero (i.e. have the same
        // bump), we need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables.
        // The fastest way to do this is using a hash code, though it's still possible for two Arrows to have the
        // same hash code but not be equal. If we're paranoid, in this case we calculate a determinate comparison
        // not equal to zero by keeping a list. This last part is commened out by default.
        public int compareTo(@NotNull Arrow arrow) {

            final int compare = Double.compare(arrow.getBump(), getBump());

            if (compare == 0) {
                return Integer.compare(getIndex(), arrow.getIndex());
            }

            return compare;
        }

        public String toString() {
            return "Arrow<" + a + "->" + b + " bump = " + bump + " t/h = " + hOrT + " TNeighbors = " + getTNeighbors() + " parents = " + parents + " naYX = " + naYX + ">";
        }

        public int getIndex() {
            return index;
        }

        public Set<Node> getTNeighbors() {
            return TNeighbors;
        }

        public void setTNeighbors(Set<Node> TNeighbors) {
            this.TNeighbors = TNeighbors;
        }

        public Set<Node> getParents() {
            return parents;
        }
    }
}