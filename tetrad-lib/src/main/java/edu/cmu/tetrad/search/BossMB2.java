package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

import static edu.cmu.tetrad.graph.Edges.directedEdge;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Math.min;


/**
 * Implements the GRASP algorithms, with various execution flags.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class BossMB2 {
    private final List<Node> variables;
    private final Score score;
    private IKnowledge knowledge = new Knowledge2();
    private TeyssierScorer2 scorer;
    private long start;
    private boolean useDataOrder = true;
    private boolean verbose = true;
    private int depth = 4;
    private int numStarts = 1;
    private boolean findMb = false;

    public BossMB2(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
    }

    public List<Graph> search(@NotNull List<Node> order, List<Node> targets) {
        this.start = System.currentTimeMillis();
        order = new ArrayList<>(order);

        this.scorer = new TeyssierScorer2(this.score);

        this.scorer.setKnowledge(this.knowledge);
        this.scorer.clearBookmarks();

        List<Node> bestPerm = null;
        int bestSize = scorer.size();
        double bestScore = NEGATIVE_INFINITY;

        this.scorer.score(order);

        System.out.println("Initial score = " + scorer.score());

        this.start = System.currentTimeMillis();

        makeValidKnowledgeOrder(order);

        for (Node target : targets) {
            List<Node> pi2 = order;
            List<Node> pi1;

            float s1, s2;

            do {
                pi1 = scorer.getPi();

                scorer.score(pi2);
                betterMutationBossTuck(scorer, target);
                pi2 = besOrder(scorer);
            } while (pi2.size() > pi1.size());

            bestPerm = scorer.getPi();

            this.scorer.score(bestPerm);
            Graph graph = scorer.getGraph(false);

            if (findMb) {
                Set<Node> mb = new HashSet<>();

                for (Node n : graph.getNodes()) {
                    for (Node t : targets) {
                        if (graph.isAdjacentTo(t, n)) {
                            mb.add(n);
                        } else {
                            for (Node m : graph.getChildren(t)) {
                                if (graph.isParentOf(n, m)) {
                                    mb.add(n);
                                }
                            }
                        }
                    }
                }

                N:
                for (Node n : graph.getNodes()) {
                    for (Node t : targets) {
                        if (t == n) continue N;
                    }

                    if (!mb.contains(n)) graph.removeNode(n);
                }
            } else {
                for (Edge e : graph.getEdges()) {
                    if (!(targets.contains(e.getNode1()) || targets.contains(e.getNode2()))) {
                        graph.removeEdge(e);
                    }
                }
            }

            graph = SearchGraphUtils.cpdagForDag(graph);

            this.graphs.add(graph);
        }


        long stop = System.currentTimeMillis();

        if (this.verbose) {
            TetradLogger.getInstance().forceLogMessage("Final order = " + this.scorer.getPi());
            TetradLogger.getInstance().forceLogMessage("Elapsed time = " + (stop - start) / 1000.0 + " s");
        }

        return graphs;
    }

    public void setFindMb(boolean findMb) {
        this.findMb = findMb;
    }

    class MyTask implements Callable<Ret> {

        Node k;
        TeyssierScorer2 scorer;
        double _sp;
        int _k;
        int chunk;
        int w;

        MyTask(Node k, TeyssierScorer2 scorer, double _sp, int _k, int chunk, int w) {
            this.scorer = scorer;
            this.k = k;
            this._sp = _sp;
            this._k = _k;
            this.chunk = chunk;
            this.w = w;
        }

        @Override
        public Ret call() {
            return relocateVisit(k, scorer, _sp, _k, chunk, w);
        }
    }

    static class Ret {
        double _sp;
        //        List<Node> pi;
        int _k;
    }

    private Ret relocateVisit(Node k, @NotNull TeyssierScorer2 scorer, double _sp, int _k, int chunk, int w) {
        TeyssierScorer2 scorer2 = new TeyssierScorer2(scorer);
        scorer2.score(scorer.getPi());
        scorer2.bookmark(scorer2);

        for (int j = w; j < min(w + chunk, scorer.size()); j++) {
            scorer2.moveTo(k, j);

            if (scorer2.score() >= _sp) {
                if (!violatesKnowledge(scorer.getPi())) {
                    _sp = scorer2.score();
                    _k = j;
                }
            }
        }

        Ret ret = new Ret();
        ret._sp = _sp;
        ret._k = _k;

        return ret;
    }

    public void betterMutationBossTuck(@NotNull TeyssierScorer2 scorer, Node target) {

        List<Node> p1, p2;

        do {
            p1 = scorer.getPi();

            Graph g = scorer.getGraph(false);
            Set<Node> keep = new HashSet<>();
            keep.add(target);
            keep.addAll(g.getAdjacentNodes(target));

            if (findMb) {
                for (Node k : new HashSet<>(keep)) {
                    keep.addAll(g.getAdjacentNodes(k));
                }
            }

            List<Node> _pi = new ArrayList<>();

            for (Node n : scorer.getPi()) {
                if (keep.contains(n)) _pi.add(n);
            }

            double sp = scorer.score(_pi);

            scorer.bookmark();

            System.out.println("After snips: # vars = " + scorer.getPi().size() + " # Edges = " + scorer.getNumEdges()
                    + " Score = " + scorer.score()
                    + " (betterMutation)"
                    + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s") + " order = " + scorer.getPi());


            for (Node x : scorer.getPi()) {
                int i = scorer.index(x);

                for (int j = i - 1; j >= 0; j--) {
                    if (scorer.tuck(x, j)) {
                        if (scorer.score() > sp && !violatesKnowledge(scorer.getPi())) {
                            sp = scorer.score();
                            scorer.bookmark();

//                            if (verbose) {
                            System.out.println("# vars = " + scorer.getPi().size() + " # Edges = " + scorer.getNumEdges()
                                    + " Score = " + scorer.score()
                                    + " (betterMutation)"
                                    + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
//                            }
                        } else {
                            scorer.goToBookmark();
                        }
                    }
                }
            }

            p2 = scorer.getPi();
        } while (!p1.equals(p2));
    }

    public List<Node> besOrder(TeyssierScorer2 scorer) {
        Graph graph = scorer.getGraph(true);
        bes(graph);
        return causalOrder(scorer.getPi(), graph);
    }

    private List<Node> causalOrder(List<Node> initialOrder, Graph graph) {
        List<Node> found = new ArrayList<>();
        boolean _found = true;

        while (_found) {
            _found = false;

            for (Node node : initialOrder) {
                HashSet<Node> __found = new HashSet<>(found);
                if (!__found.contains(node) && __found.containsAll(graph.getParents(node))) {
                    found.add(node);
                    _found = true;
                }
            }
        }
        return found;
    }


    public int getNumEdges() {
        return this.scorer.getNumEdges();
    }

    private void makeValidKnowledgeOrder(List<Node> order) {
        if (!this.knowledge.isEmpty()) {
            order.sort((o1, o2) -> {
                if (o1.getName().equals(o2.getName())) {
                    return 0;
                } else if (this.knowledge.isRequired(o1.getName(), o2.getName())) {
                    return 1;
                } else if (this.knowledge.isRequired(o2.getName(), o1.getName())) {
                    return -1;
                } else if (this.knowledge.isForbidden(o2.getName(), o1.getName())) {
                    return -1;
                } else if (this.knowledge.isForbidden(o1.getName(), o2.getName())) {
                    return 1;
                } else {
                    return 1;
                }
            });
        }
    }

    @NotNull
    public List<Graph> getGraphs() {
        return graphs;
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
    }

    private boolean violatesKnowledge(List<Node> order) {
        if (!this.knowledge.isEmpty()) {
            for (int i = 0; i < order.size(); i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    if (this.knowledge.isForbidden(order.get(i).getName(), order.get(j).getName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    private List<Graph> graphs;
    private final SortedSet<Arrow> sortedArrowsBack = new ConcurrentSkipListSet<>();
    private Map<Node, Integer> hashIndices;
    private final Map<Edge, ArrowConfigBackward> arrowsMapBackward = new ConcurrentHashMap<>();
    private int arrowIndex = 0;


    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new HashMap<>();

        int i = -1;

        for (Node n : nodes) {
            this.hashIndices.put(n, ++i);
        }
    }

    private void bes(Graph graph) {
        buildIndexing(variables);

        reevaluateBackward(new HashSet<>(variables), graph);

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

            double _bump = deleteEval(x, y, complement,
                    arrow.parents, hashIndices);

            delete(x, y, arrow.getHOrT(), _bump, arrow.getNaYX(), graph);

            Set<Node> process = revertToCPDAG(graph);
            process.add(x);
            process.add(y);
            process.addAll(graph.getAdjacentNodes(x));
            process.addAll(graph.getAdjacentNodes(y));

            reevaluateBackward(new HashSet<>(process), graph);
        }
    }

    private void delete(Node x, Node y, Set<Node> H, double bump, Set<Node> naYX, Graph graph) {
        Edge oldxy = graph.getEdge(x, y);

        Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);

        graph.removeEdge(oldxy);

        int numEdges = graph.getNumEdges();
        if (numEdges % 1000 == 0) {
            System.out.println("Num edges (backwards) = " + numEdges);
        }

        if (verbose) {
            int cond = diff.size() + graph.getParents(y).size();

            String message = (graph.getNumEdges()) + ". DELETE " + x + " --> " + y
                    + " H = " + H + " NaYX = " + naYX
                    + " degree = " + GraphUtils.getDegree(graph)
                    + " indegree = " + GraphUtils.getIndegree(graph)
                    + " diff = " + diff + " (" + bump + ") "
                    + " cond = " + cond;
            TetradLogger.getInstance().forceLogMessage(message);
        }

        for (Node h : H) {
            if (graph.isParentOf(h, y) || graph.isParentOf(h, x)) {
                continue;
            }

            Edge oldyh = graph.getEdge(y, h);

            graph.removeEdge(oldyh);

            graph.addEdge(directedEdge(y, h));

            if (verbose) {
                TetradLogger.getInstance().forceLogMessage("--- Directing " + oldyh + " to "
                        + graph.getEdge(y, h));
            }

            Edge oldxh = graph.getEdge(x, h);

            if (Edges.isUndirectedEdge(oldxh)) {
                graph.removeEdge(oldxh);

                graph.addEdge(directedEdge(x, h));

                if (verbose) {
                    TetradLogger.getInstance().forceLogMessage("--- Directing " + oldxh + " to "
                            + graph.getEdge(x, h));
                }
            }
        }
    }


    private double deleteEval(Node x, Node y, Set<Node> complement, Set<Node> parents,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set = new HashSet<>(complement);
        set.addAll(parents);
        set.remove(x);

        return -scoreGraphChange(x, y, set, hashIndices);
    }

    private double scoreGraphChange(Node x, Node y, Set<Node> parents,
                                    Map<Node, Integer> hashIndices) {
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

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    private Set<Node> revertToCPDAG(Graph graph) {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getKnowledge());
        rules.setAggressivelyPreventCycles(true);
        boolean meekVerbose = false;
        rules.setVerbose(meekVerbose);
        return rules.orientImplied(graph);
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

    private void reevaluateBackward(Set<Node> toProcess, Graph graph) {
        class BackwardTask extends RecursiveTask<Boolean> {
            private final Node r;
            private final List<Node> adj;
            private final Map<Node, Integer> hashIndices;
            private final int chunk;
            private final int from;
            private final int to;

            private BackwardTask(Node r, List<Node> adj, int chunk, int from, int to,
                                 Map<Node, Integer> hashIndices) {
                this.adj = adj;
                this.hashIndices = hashIndices;
                this.chunk = chunk;
                this.from = from;
                this.to = to;
                this.r = r;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int _w = from; _w < to; _w++) {
                        final Node w = adj.get(_w);
                        Edge e = graph.getEdge(w, r);

                        if (e != null) {
                            if (e.pointsTowards(r)) {
                                calculateArrowsBackward(w, r, graph);
                            } else if (e.pointsTowards(w)) {
                                calculateArrowsBackward(r, w, graph);
                            } else {
                                calculateArrowsBackward(w, r, graph);
                                calculateArrowsBackward(r, w, graph);
                            }
                        }
                    }

                } else {
                    int mid = (to - from) / 2;

                    List<BackwardTask> tasks = new ArrayList<>();

                    tasks.add(new BackwardTask(r, adj, chunk, from, from + mid, hashIndices));
                    tasks.add(new BackwardTask(r, adj, chunk, from + mid, to, hashIndices));

                    invokeAll(tasks);
                }

                return true;
            }
        }

        for (Node r : toProcess) {
            List<Node> adjacentNodes = new ArrayList<>(toProcess);
            ForkJoinPool.commonPool().invoke(new BackwardTask(r, adjacentNodes, getChunkSize(adjacentNodes.size()), 0,
                    adjacentNodes.size(), hashIndices));
        }
    }

    private int getChunkSize(int n) {
        int chunk = n / Runtime.getRuntime().availableProcessors();
        if (chunk < 100) chunk = 100;
        return chunk;
    }

    private void calculateArrowsBackward(Node a, Node b, Graph graph) {
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

        final DepthChoiceGenerator gen = new DepthChoiceGenerator(_naYX.size(), _depth);//_naYX.size());
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
            addArrowBackward(a, b, _H, naYX, parents, maxBump);
        }
    }

    private void addArrowBackward(Node a, Node b, Set<Node> hOrT, Set<Node> naYX,
                                  Set<Node> parents, double bump) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, null, naYX, parents, arrowIndex++);
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

        Arrow(double bump, Node a, Node b, Set<Node> hOrT, Set<Node> capTorH, Set<Node> naYX,
              Set<Node> parents, int index) {
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
        // bump, we need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables.
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
            return "Arrow<" + a + "->" + b + " bump = " + bump
                    + " t/h = " + hOrT
                    + " TNeighbors = " + getTNeighbors()
                    + " parents = " + parents
                    + " naYX = " + naYX + ">";
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

    public enum AlgType {BOSS, BOSS_TUCK, KING_OF_BRIDGES}
}