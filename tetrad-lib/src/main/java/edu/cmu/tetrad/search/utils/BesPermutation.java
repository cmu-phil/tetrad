package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.RecursiveTask;

import static edu.cmu.tetrad.graph.Edges.directedEdge;
import static org.apache.commons.math3.util.FastMath.min;


/**
 * <p>Implements a version of the BES (Best Equivalent Search) algorithm
 * that takes a permutation as input and yields a permtuation as output, where the related DAG or CPDAG models are
 * implied by the ordering or variables in these permutations. BES is the second step of the GES algorithm (e.g., FGES).
 * The first step in GES starts with an empty graph and adds edges (with corresponding reorientations of edges),
 * yielding a Markov model. The second step, this one, BES, starts with this Markov model and then tries to remove edges
 * from it (with corresponding reorientation) to improve the BES scores.</p>
 * <p>The advantage of doing this is that BES can then be used as
 * a step in certain permutation-based algorithms like BOSS to allow correct models to be inferred under the assumption
 * of faithfulness.</p>
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 * @see Fges
 * @see Bes
 * @see Boss
 */
public class BesPermutation {
    // The variables.
    private final List<Node> variables;
    // The score.
    private final Score score;
    // The knowledge.
    private Knowledge knowledge = new Knowledge();
    // Whether verbose output should be printed.
    private boolean verbose = true;

    /**
     * Constructor.
     *
     * @param score The score that BES (from FGES) will use.
     */
    public BesPermutation(@NotNull Score score) {
        this.score = score;
        this.variables = score.getVariables();
    }

    /**
     * Returns the variables.
     *
     * @return This list.
     */
    @NotNull
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private void buildIndexing(List<Node> nodes, Map<Node, Integer> hashIndices) {

        int i = -1;

        for (Node n : nodes) {
            hashIndices.put(n, ++i);
        }
    }

    /**
     * Runs BES.
     *
     * @param graph    The graph.
     * @param order    The order.
     * @param suborder The suborder.
     * @throws InterruptedException If the search is interrupted.
     */
    public void bes(Graph graph, List<Node> order, List<Node> suborder) throws InterruptedException {
        Map<Node, Integer> hashIndices = new HashMap<>();
        SortedSet<Arrow> sortedArrowsBack = new ConcurrentSkipListSet<>();
        Map<Edge, ArrowConfigBackward> arrowsMapBackward = new ConcurrentHashMap<>();
        int[] arrowIndex = new int[1];

        buildIndexing(order, hashIndices);

        reevaluateBackward(new HashSet<>(order), graph, hashIndices, arrowIndex, sortedArrowsBack, arrowsMapBackward);

        while (!sortedArrowsBack.isEmpty()) {
            Arrow arrow = sortedArrowsBack.first();
            sortedArrowsBack.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (!graph.isAdjacentTo(x, y)) {
                continue;
            }

            Edge edge = graph.getEdge(x, y);

            if (edge.pointsTowards(x)) {
                continue;
            }

            if (!getNaYX(x, y, graph).equals(arrow.getNaYX())) {
                continue;
            }

            if (!new HashSet<>(graph.getParents(y)).equals(new HashSet<>(arrow.getParents()))) {
                continue;
            }

            if (!validDelete(x, y, arrow.getHOrT(), arrow.getNaYX(), graph, suborder)) {
                continue;
            }

            Set<Node> complement = new HashSet<>(arrow.getNaYX());
            complement.removeAll(arrow.getHOrT());

            double _bump = deleteEval(x, y, complement, arrow.parents, hashIndices);

            delete(x, y, arrow.getHOrT(), _bump, arrow.getNaYX(), graph);

            Set<Node> process = revertToCPDAG(graph);
            process.add(x);
            process.add(y);
            process.addAll(graph.getAdjacentNodes(x));
            process.addAll(graph.getAdjacentNodes(y));

            reevaluateBackward(new HashSet<>(process), graph, hashIndices, arrowIndex, sortedArrowsBack, arrowsMapBackward);
        }
    }

    private void delete(Node x, Node y, Set<Node> H, double bump, Set<Node> naYX, Graph graph) {
        Edge oldxy = graph.getEdge(x, y);

        Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);

        graph.removeEdge(oldxy);

        int numEdges = graph.getNumEdges();
        if (numEdges % 1000 == 0 && numEdges > 0) {
            System.out.println("Num edges (backwards) = " + numEdges);
        }

        if (verbose) {
            int cond = diff.size() + graph.getParents(y).size();

            String message = (graph.getNumEdges()) + ". DELETE " + x + " --> " + y + " H = " + H + " NaYX = " + naYX + " degree = " + GraphUtils.getDegree(graph) + " indegree = " + GraphUtils.getIndegree(graph) + " diff = " + diff + " (" + bump + ") " + " cond = " + cond;
            TetradLogger.getInstance().log(message);
        }

        for (Node h : H) {
            if (graph.isParentOf(h, y) || graph.isParentOf(h, x)) {
                continue;
            }

            Edge oldyh = graph.getEdge(y, h);

            graph.removeEdge(oldyh);

            graph.addEdge(directedEdge(y, h));

            if (verbose) {
                TetradLogger.getInstance().log("--- Directing " + oldyh + " to " + graph.getEdge(y, h));
            }

            Edge oldxh = graph.getEdge(x, h);

            if (Edges.isUndirectedEdge(oldxh)) {
                graph.removeEdge(oldxh);

                graph.addEdge(directedEdge(x, h));

                if (verbose) {
                    TetradLogger.getInstance().log("--- Directing " + oldxh + " to " + graph.getEdge(x, h));
                }
            }
        }
    }

    private double deleteEval(Node x, Node
            y, Set<Node> complement, Set<Node> parents, Map<Node, Integer> hashIndices) throws InterruptedException {
        Set<Node> set = new HashSet<>(complement);
        set.addAll(parents);
        set.remove(x);

        return -scoreGraphChange(x, y, set, hashIndices);
    }

    private double scoreGraphChange(Node x, Node y, Set<Node> parents, Map<Node, Integer> hashIndices) throws InterruptedException {
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

    /**
     * Returns the knowledge that BES will use.
     *
     * @return This knowledge.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge that BES will use.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    private Set<Node> revertToCPDAG(Graph graph) {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getKnowledge());
        rules.setMeekPreventCycles(false);
        rules.setVerbose(verbose);
        return rules.orientImplied(graph);
    }

    private boolean validDelete(Node x, Node y, Set<Node> H, Set<Node> naYX, Graph graph, List<Node> suborder) {
        if (existsKnowledge()) {
            for (Node h : H) {
                if (knowledge.isForbidden(x.getName(), h.getName())) return false;
                if (knowledge.isForbidden(y.getName(), h.getName())) return false;
            }
        }

        Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);
        if (!isClique(diff, graph)) return false;

        if (existsKnowledge()) {
            graph = new EdgeListGraph(graph);
            Edge oldxy = graph.getEdge(x, y);
            graph.removeEdge(oldxy);

            for (Node h : H) {
                if (graph.isParentOf(h, y) || graph.isParentOf(h, x)) continue;
                Edge oldyh = graph.getEdge(y, h);
                graph.removeEdge(oldyh);
                graph.addEdge(directedEdge(y, h));

                Edge oldxh = graph.getEdge(x, h);
                if (!Edges.isUndirectedEdge(oldxh)) continue;
                graph.removeEdge(oldxh);
                graph.addEdge(directedEdge(x, h));
            }

            revertToCPDAG(graph);
            List<Node> initialOrder = new ArrayList<>(suborder);
            Collections.reverse(initialOrder);

            while (!initialOrder.isEmpty()) {
                Iterator<Node> itr = initialOrder.iterator();
                Node b;
                do {
                    if (itr.hasNext()) b = itr.next();
                    else return false;
                } while (invalidSink(b, graph));
                graph.removeNode(b);
                itr.remove();
            }

        }

        return true;
    }

    private boolean invalidSink(Node x, Graph graph) {
        LinkedList<Node> neighbors = new LinkedList<>();

        for (Edge edge : graph.getEdges(x)) {
            if (edge.getDistalEndpoint(x) == Endpoint.ARROW) return true;
            if (edge.getProximalEndpoint(x) == Endpoint.TAIL) neighbors.add(edge.getDistalNode(x));
        }

        while (!neighbors.isEmpty()) {
            Node y = neighbors.pop();
            for (Node z : neighbors) if (!graph.isAdjacentTo(y, z)) return true;
        }

        return false;
    }

    private boolean existsKnowledge() {
        return !knowledge.isEmpty();
    }

    private boolean isClique(Set<Node> nodes, Graph graph) {
        List<Node> _nodes = new ArrayList<>(nodes);
        for (int i = 0; i < _nodes.size(); i++) {
            for (int j = i + 1; j < _nodes.size(); j++) {
                if (!graph.isAdjacentTo(_nodes.get(i), _nodes.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

    private Set<Node> getNaYX(Node x, Node y, Graph graph) {
        List<Node> adj = graph.getAdjacentNodes(y);
        Set<Node> nayx = new HashSet<>();

        for (Node z : adj) {
            if (z == x) {
                continue;
            }
            Edge yz = graph.getEdge(y, z);
            if (!Edges.isUndirectedEdge(yz)) {
                continue;
            }
            if (!graph.isAdjacentTo(z, x)) {
                continue;
            }
            nayx.add(z);
        }

        return nayx;
    }

    private void reevaluateBackward(Set<Node> toProcess, Graph graph, Map<Node, Integer> hashIndices,
                                    int[] arrowIndex, SortedSet<Arrow> sortedArrowsBack, Map<Edge, ArrowConfigBackward> arrowsMapBackward) {

        class BackwardTask extends RecursiveTask<Boolean> {
            final Map<Edge, ArrowConfigBackward> arrowsMapBackward;
            private final Node r;
            private final List<Node> adj;
            private final Map<Node, Integer> hashIndices;
            private final int chunk;
            private final int from;
            private final int to;
            private final SortedSet<Arrow> sortedArrowsBack;

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
                            try {
                                if (e.pointsTowards(r)) {
                                    calculateArrowsBackward(w, r, graph, arrowsMapBackward, hashIndices, arrowIndex, sortedArrowsBack);
                                } else if (e.pointsTowards(w)) {
                                    calculateArrowsBackward(r, w, graph, arrowsMapBackward, hashIndices, arrowIndex, sortedArrowsBack);
                                } else {
                                    calculateArrowsBackward(w, r, graph, arrowsMapBackward, hashIndices, arrowIndex, sortedArrowsBack);
                                    calculateArrowsBackward(r, w, graph, arrowsMapBackward, hashIndices, arrowIndex, sortedArrowsBack);
                                }
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
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
//            int parallelism = Runtime.getRuntime().availableProcessors();
//            ForkJoinPool pool = new ForkJoinPool(parallelism);

            // Too many threads are being created, so we will so these all in the current thread.
            // jdramsey 2024-6-67
//            try {
            new BackwardTask(r, adjacentNodes, getChunkSize(adjacentNodes.size()), 0,
                    adjacentNodes.size(), hashIndices, sortedArrowsBack, arrowsMapBackward).compute();
//                pool.invoke(new BackwardTask(r, adjacentNodes, getChunkSize(adjacentNodes.size()), 0,
//                        adjacentNodes.size(), hashIndices, sortedArrowsBack, arrowsMapBackward));
//            } catch (Exception e) {
//                Thread.currentThread().interrupt();
//                throw e;
//            }

//            if (!pool.awaitQuiescence(1, TimeUnit.DAYS)) {
//                Thread.currentThread().interrupt();
//                return;
//            }
        }
    }

    private int getChunkSize(int n) {
        int chunk = n / Runtime.getRuntime().availableProcessors();
        if (chunk < 100) chunk = 100;
        return chunk;
    }

    private void calculateArrowsBackward(Node a, Node b, Graph
                                                 graph, Map<Edge, ArrowConfigBackward> arrowsMapBackward, Map<Node, Integer> hashIndices,
                                         int[] arrowIndex, SortedSet<Arrow> sortedArrowsBack) throws InterruptedException {
        if (existsKnowledge()) {
            if (!getKnowledge().noEdgeRequired(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = getNaYX(a, b, graph);
        Set<Node> parents = new HashSet<>(graph.getParents(b));

        List<Node> _naYX = new ArrayList<>(naYX);

        ArrowConfigBackward config = new ArrowConfigBackward(naYX, parents);
        ArrowConfigBackward storedConfig = arrowsMapBackward.get(directedEdge(a, b));
        if (storedConfig != null && storedConfig.equals(config)) return;
        arrowsMapBackward.put(directedEdge(a, b), new ArrowConfigBackward(naYX, parents));

        // The depth.
        int depth = -1;
        int _depth = min(depth, _naYX.size());

        final SublistGenerator gen = new SublistGenerator(_naYX.size(), _depth);//_naYX.size());
        int[] choice;
        Set<Node> maxComplement = null;
        double maxBump = Double.NEGATIVE_INFINITY;

        while ((choice = gen.next()) != null) {
            Set<Node> complement = GraphUtils.asSet(choice, _naYX);
            double _bump = deleteEval(a, b, complement, parents, hashIndices);

            if (_bump > maxBump) {
                maxBump = _bump;
                maxComplement = complement;
            }
        }

        if (maxBump > 0) {
            Set<Node> _H = new HashSet<>(naYX);
            _H.removeAll(maxComplement);
            addArrowBackward(a, b, _H, naYX, parents, maxBump, arrowIndex, sortedArrowsBack);
        }
    }

    private void addArrowBackward(Node a, Node b, Set<Node> hOrT, Set<Node> naYX, Set<Node> parents,
                                  double bump, int[] arrowIndex, SortedSet<Arrow> sortedArrowsBack) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, null, naYX, parents, arrowIndex[0]++);
        sortedArrowsBack.add(arrow);
    }

    private static class ArrowConfigBackward {
        private Set<Node> nayx;
        private Set<Node> parents;

        public ArrowConfigBackward(Set<Node> nayx, Set<Node> parents) {
            this.setNayx(nayx);
            this.setParents(parents);
        }

        public void setNayx(Set<Node> nayx) {
            this.nayx = nayx;
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
            return nayx.equals(that.nayx) && parents.equals(that.parents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nayx, parents);
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

        Set<Node> getNaYX() {
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
