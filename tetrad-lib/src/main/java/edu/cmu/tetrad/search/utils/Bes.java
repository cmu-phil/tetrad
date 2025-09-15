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
 * Extracts the backward step of GES for use GES but also in other algorithms. The GES algorithm consists of a forward
 * phase (FES = Forward Equivalence Search) and a backward phase (BES = Backward Equivalence Search). We find the BES
 * step by itself is useful in a number of algorithms, so we extract this step and give as a separate algorithm.
 * <p>
 * The idea of the backward search is to start with a model that is Markov and removed edges from it and do the
 * corresponding reorientations, improving the score each time, until the score can no longer be improved.
 * <p>
 * We use the optimized implementation used in the FGES implementation of GES.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 * @see Fges
 * @see Boss
 */
public class Bes {

    // The variables being searched over.
    private final List<Node> variables;
    // The score.
    private final Score score;
    // The knowledge.
    private Knowledge knowledge = new Knowledge();
    // True if verbose output should be printed.
    private boolean verbose = true;
    // The depth of the search.
    private int depth = 4;

    /**
     * Constructs the search.
     *
     * @param score The score.
     */
    public Bes(@NotNull Score score) {
        this.score = score;
        this.variables = score.getVariables();
    }

    /**
     * Returns the variables being searched over.
     *
     * @return These variables as a list.
     */
    @NotNull
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True iff so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the depth for the search, which is the maximum number of variables conditioned on.
     *
     * @param depth This maximum; for unlimited depth use -1; otherwise, give a nonzero integer.
     */
    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
    }

    /**
     * Runs BES for a graph over the given list of variables
     *
     * @param graph     The graph.
     * @param variables The variables the search should be restricted to.
     * @throws InterruptedException If the search is interrupted.
     */
    public void bes(Graph graph, List<Node> variables) throws InterruptedException {
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

            if (!validDelete(x, y, arrow.getHOrT(), arrow.getNaYX(), graph)) {
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

    private Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge for the search.
     *
     * @param knowledge This knowledge.
     * @see Knowledge
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
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

    private double deleteEval(Node x, Node
            y, Set<Node> complement, Set<Node> parents, Map<Node, Integer> hashIndices) throws InterruptedException {
        Set<Node> set = new HashSet<>(complement);
        set.addAll(parents);
        set.remove(x);

        return -scoreGraphChange(x, y, set, hashIndices);
    }

    private Set<Node> revertToCPDAG(Graph graph) {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getKnowledge());
        rules.setMeekPreventCycles(false);
        rules.setVerbose(verbose);
        return rules.orientImplied(graph);
    }

    private void buildIndexing(List<Node> nodes, Map<Node, Integer> hashIndices) {

        int i = -1;

        for (Node n : nodes) {
            hashIndices.put(n, ++i);
        }
    }

    private boolean validDelete(Node x, Node y, Set<Node> H, Set<Node> naYX, Graph graph) {
        boolean violatesKnowledge = false;

        if (existsKnowledge()) {
            for (Node h : H) {
                if (knowledge.isForbidden(x.getName(), h.getName())) {
                    violatesKnowledge = true;
                }

                if (knowledge.isForbidden(y.getName(), h.getName())) {
                    violatesKnowledge = true;
                }
            }
        }

        Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);
        return isClique(diff, graph) && !violatesKnowledge;
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
            BackwardTask task = new BackwardTask(r, adjacentNodes, getChunkSize(adjacentNodes.size()), 0,
                    adjacentNodes.size(), hashIndices, sortedArrowsBack, arrowsMapBackward);

            try {
                task.invoke();
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                throw e;
            }
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

    /**
     * An arrow in the search.
     */
    public static class Arrow implements Comparable<Arrow> {

        /**
         * The bump.
         */
        private final double bump;

        /**
         * The first node.
         */
        private final Node a;

        /**
         * The second node.
         */
        private final Node b;

        /**
         * The set of nodes that are in H or T.
         */
        private final Set<Node> hOrT;

        /**
         * The set of nodes that are in NaYX.
         */
        private final Set<Node> naYX;

        /**
         * The index of the arrow.
         */
        private final Set<Node> parents;

        /**
         * The index of the arrow.
         */
        private final int index;

        /**
         * The set of nodes that are in TNeighbors.
         */
        private Set<Node> TNeighbors;

        /**
         * Constructs an arrow.
         *
         * @param bump    The bump.
         * @param a       The first node.
         * @param b       The second node.
         * @param hOrT    The set of nodes that are in H or T.
         * @param naYX    The set of nodes that are in NaYX.
         * @param parents The set of nodes that are in TNeighbors.
         * @param index   The index of the arrow.
         */
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

        /**
         * Returns the bump.
         *
         * @return The bump.
         */
        public double getBump() {
            return bump;
        }

        /**
         * Returns the first node.
         *
         * @return The first node.
         */
        public Node getA() {
            return a;
        }

        /**
         * Returns the second node.
         *
         * @return The second node.
         */
        public Node getB() {
            return b;
        }

        /**
         * Returns the set of nodes that are in H or T.
         *
         * @return The set of nodes that are in H or T.
         */
        Set<Node> getHOrT() {
            return hOrT;
        }

        /**
         * Returns the set of nodes that are in NaYX.
         *
         * @return The set of nodes that are in NaYX.
         */
        Set<Node> getNaYX() {
            return naYX;
        }

        /**
         * Sorting by bump, high to low. The problem is the SortedSet contains won't add a new element if it compares to
         * zero with an existing element, so for the cases where the comparison is to zero (i.e. have the same bump), we
         * need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables. The
         * fastest way to do this is using a hash code, though it's still possible for two Arrows to have the same hash
         * code but not be equal. If we're paranoid, in this case we calculate a determinate comparison not equal to
         * zero by keeping a list. This last part is commened out by default.
         */
        public int compareTo(@NotNull Arrow arrow) {

            final int compare = Double.compare(arrow.getBump(), getBump());

            if (compare == 0) {
                return Integer.compare(getIndex(), arrow.getIndex());
            }

            return compare;
        }

        /**
         * Returns the index of the arrow.
         *
         * @return The index of the arrow.
         */
        public String toString() {
            return "Arrow<" + a + "->" + b + " bump = " + bump + " t/h = " + hOrT + " TNeighbors = " + getTNeighbors() + " parents = " + parents + " naYX = " + naYX + ">";
        }

        /**
         * Returns the index of the arrow.
         *
         * @return The index of the arrow.
         */
        public int getIndex() {
            return index;
        }

        /**
         * Returns the set of nodes that are in TNeighbors.
         *
         * @return The set of nodes that are in TNeighbors.
         */
        public Set<Node> getTNeighbors() {
            return TNeighbors;
        }

        /**
         * Sets the set of nodes that are in TNeighbors.
         *
         * @param TNeighbors The set of nodes that are in TNeighbors.
         */
        public void setTNeighbors(Set<Node> TNeighbors) {
            this.TNeighbors = TNeighbors;
        }

        /**
         * Returns the set of nodes that are in TNeighbors.
         *
         * @return The set of nodes that are in TNeighbors.
         */
        public Set<Node> getParents() {
            return parents;
        }
    }
}
