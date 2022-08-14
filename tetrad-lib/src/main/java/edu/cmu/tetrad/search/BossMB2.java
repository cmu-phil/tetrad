package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BOSSTuck;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.Function;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

import static edu.cmu.tetrad.graph.Edges.directedEdge;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.compare;
import static java.lang.Float.NaN;
import static java.lang.Math.PI;
import static java.lang.Math.min;
import static java.util.Collections.shuffle;
import static java.util.Collections.sort;


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
    private long start;
    private boolean useDataOrder = true;
    private boolean verbose = true;
    private int depth = 4;
    private int numStarts = 1;
    private boolean findMb = false;
    private int maxIndegree = -1;

    public BossMB2(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
    }

    public void setMaxIndegree(int maxIndegree) {
        this.maxIndegree = maxIndegree;
    }

    class MyTask2 implements Callable<Graph> {
        final List<Node> order;
        final TeyssierScorer2 scorer0;
        final Node target;

        MyTask2(List<Node> order, TeyssierScorer2 scorer0, Node target) {
            this.order = order;
            this.scorer0 = scorer0;
            this.target = target;
        }

        @Override
        public Graph call() throws InterruptedException {
            return targetVisit(order, scorer0, target);
        }
    }

    /**
     * Prints local graphs for all variables and returns the one of them.
     */
    public Graph search(@NotNull List<Node> order) {
        long start = System.currentTimeMillis();
        order = new ArrayList<>(order);

        TeyssierScorer2 scorer0 = new TeyssierScorer2(this.score);
//        scorer0.setMaxIndegree(this.maxIndegree);

        scorer0.setKnowledge(this.knowledge);
//        scorer0.clearBookmarks();

        scorer0.score(order);

        this.start = System.currentTimeMillis();

        makeValidKnowledgeOrder(order);

        System.out.println("Initial score = " + scorer0.score());

        List<Node> _targets = new ArrayList<>(scorer0.getPi());
        sort(_targets);

        Graph combinedGraph = new EdgeListGraph(_targets);

        List<MyTask2> tasks = new ArrayList<>();

        try {
            for (Node node : _targets) {
                tasks.add(new MyTask2(order, scorer0, node));
            }

            List<Future<Graph>> futures = ForkJoinPool.commonPool().invokeAll(tasks);

            for (Future<Graph> future : futures) {
                Graph g = future.get();
                this.graphs.add(g);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

//        for (Node target : _targets) {
//            try {
//                this.graphs.add(targetVisit(order, scorer0, target));
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }

        for (Graph g : this.graphs) {
            for (Edge e : g.getEdges()) {
                Edge e2 = combinedGraph.getEdge(e.getNode1(), e.getNode2());

                if (e.isDirected() && !GraphUtils.existsSemiDirectedPath(Edges.getDirectedEdgeHead(e), Edges.getDirectedEdgeTail(e), combinedGraph)) {
                    combinedGraph.removeEdge(e2);
                    combinedGraph.addEdge(e);
                }
            }
        }

//                for (Edge e : g.getEdges()) {
//                    Edge e2 = combinedGraph.getEdge(e.getNode1(), e.getNode2());
//
//                    if (e2 == null && Edges.isUndirectedEdge(e)) {
//                        combinedGraph.addEdge(e);
//                    }
//                }

        long stop = System.currentTimeMillis();

        System.out.println("Elapsed time = " + (stop - start) / 1000.0 + " s");
//        System.out.println();
//        return graphs.get(0);

//        MeekRules rules = new MeekRules();
//        rules.setKnowledge(knowledge);
//        rules.orientImplied(combinedGraph);
//
        return combinedGraph;
    }

    private Graph targetVisit(@NotNull List<Node> order, TeyssierScorer2 scorer0, Node target) throws InterruptedException {
        TeyssierScorer2 scorer = new TeyssierScorer2(scorer0);
//        scorer.clearBookmarks();

        List<Node> pi2 = order;
        List<Node> pi1;

        float s1, s2;

        do {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            pi1 = scorer.getPi();
            s1 = scorer.score();

            betterMutationBossTuck(scorer, Collections.singletonList(target));
            pi2 = besOrder(scorer);
            s2 = scorer.score();
        } while (!pi1.equals(pi2));

        scorer.score(pi2);
        Graph graph = scorer.getGraph(true);

//        Graph graph2 = SearchGraphUtils.cpdagForDag(graph);

        if (findMb) {
            Set<Node> mb = new HashSet<>();

            for (Node n : graph.getNodes()) {
                if (graph.isAdjacentTo(target, n)) {
                    mb.add(n);
                } else {
                    for (Node m : graph.getChildren(target)) {
                        if (graph.isParentOf(n, m)) {
                            mb.add(n);
                        }
                    }
                }
            }

            N:
            for (Node n : graph.getNodes()) {
                if (target == n) continue N;
                if (!mb.contains(n)) graph.removeNode(n);
            }
        } else {
            for (Edge e : graph.getEdges()) {
                Node n1 = e.getNode1();
                Node n2 = e.getNode2();

                if (graph.isAdjacentTo(target, n1) && graph.isAdjacentTo(target, n2)) {
                    graph.removeEdge(e);
                }
            }
        }

        System.out.println("Graph for " + target + " = " + graph);

        System.out.println();

        return graph;
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

    public void betterMutationBossTuck(@NotNull TeyssierScorer2 scorer, List<Node> targets) throws InterruptedException {
        double s;
        double sp = scorer.score();

        List<Node> p1, p2;

        do {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            s = sp;
            p1 = scorer.getPi();

            Graph g = scorer.getGraph(false);
            Set<Node> keep = new HashSet<>(targets);
            for (Node n : targets) {
                keep.addAll(g.getAdjacentNodes(n));
            }

            if (findMb) {
                for (Node k : new HashSet<>(keep)) {
                    keep.addAll(g.getAdjacentNodes(k));
                }
            }

            List<Node> _pi = new ArrayList<>();

            for (Node n : scorer.getPi()) {
                if (keep.contains(n)) _pi.add(n);
            }

            sp = scorer.score(_pi);

            scorer.bookmark();

            if (verbose) {
                System.out.println("After snips: # vars = " + scorer.getPi().size() + " # Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutation)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s") + " order = " + scorer.getPi());
            }


            for (Node x : scorer.getPi()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                int i = scorer.index(x);

                for (int j = i - 1; j >= 0; j--) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (scorer.tuck(x, j)) {
                        if (scorer.score() > sp && !violatesKnowledge(scorer.getPi())) {
                            sp = scorer.score();
                            scorer.bookmark();

                            if (verbose) {
                                System.out.println("# vars = " + scorer.getPi().size() + " # Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutation)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
                            }
                        } else {
                            scorer.goToBookmark();
                        }
                    }
                }
            }

            p2 = scorer.getPi();
        } while (!p1.equals(p2));
//    } while (sp > s);
    }

    public List<Node> besOrder(TeyssierScorer2 scorer) {
        Graph graph = scorer.getGraph(true);
        bes(graph, scorer.getPi());
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

    private final List<Graph> graphs = new ArrayList<>();
//    private final SortedSet<Arrow> sortedArrowsBack = new ConcurrentSkipListSet<>();
//    private Map<Node, Integer> hashIndices;
//    private final Map<Edge, ArrowConfigBackward> arrowsMapBackward = new ConcurrentHashMap<>();
//    private int arrowIndex = 0;


    private void buildIndexing(List<Node> nodes, Map<Node, Integer> hashIndices) {
//        hashIndices = new HashMap<>();

        int i = -1;

        for (Node n : nodes) {
            hashIndices.put(n, ++i);
        }
    }

    private void bes(Graph graph, List<Node> variables) {
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

            String message = (graph.getNumEdges()) + ". DELETE " + x + " --> " + y + " H = " + H + " NaYX = " + naYX + " degree = " + GraphUtils.getDegree(graph) + " indegree = " + GraphUtils.getIndegree(graph) + " diff = " + diff + " (" + bump + ") " + " cond = " + cond;
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
                TetradLogger.getInstance().forceLogMessage("--- Directing " + oldyh + " to " + graph.getEdge(y, h));
            }

            Edge oldxh = graph.getEdge(x, h);

            if (Edges.isUndirectedEdge(oldxh)) {
                graph.removeEdge(oldxh);

                graph.addEdge(directedEdge(x, h));

                if (verbose) {
                    TetradLogger.getInstance().forceLogMessage("--- Directing " + oldxh + " to " + graph.getEdge(x, h));
                }
            }
        }
    }


    private double deleteEval(Node x, Node y, Set<Node> complement, Set<Node> parents, Map<Node, Integer> hashIndices) {
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

    private void reevaluateBackward(Set<Node> toProcess, Graph graph, Map<Node, Integer> hashIndices, int[] arrowIndex, SortedSet<Arrow> sortedArrowsBack,
                                    Map<Edge, ArrowConfigBackward> arrowsMapBackward) {
        class BackwardTask extends RecursiveTask<Boolean> {
            private final Node r;
            private final List<Node> adj;
            private final Map<Node, Integer> hashIndices;
            private final int chunk;
            private final int from;
            private final int to;
            private final SortedSet<Arrow> sortedArrowsBack;
            final Map<Edge, ArrowConfigBackward> arrowsMapBackward;

            private BackwardTask(Node r, List<Node> adj, int chunk, int from, int to, Map<Node, Integer> hashIndices, SortedSet<Arrow> sortedArrowsBack,
                                 Map<Edge, ArrowConfigBackward> arrowsMapBackward) {
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
                            if (e.pointsTowards(r)) {
                                calculateArrowsBackward(w, r, graph,
                                        arrowsMapBackward,
                                       hashIndices,
                                        arrowIndex,
                                        sortedArrowsBack);
                            } else if (e.pointsTowards(w)) {
                                calculateArrowsBackward(r, w, graph,
                                        arrowsMapBackward,
                                        hashIndices,
                                        arrowIndex,
                                        sortedArrowsBack);
                            } else {
                                calculateArrowsBackward(w, r, graph,
                                        arrowsMapBackward,
                                        hashIndices,
                                        arrowIndex,
                                        sortedArrowsBack);
                                calculateArrowsBackward(r, w, graph,
                                        arrowsMapBackward,
                                        hashIndices,
                                        arrowIndex,
                                        sortedArrowsBack);
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
            ForkJoinPool.commonPool().invoke(new BackwardTask(r, adjacentNodes, getChunkSize(adjacentNodes.size()), 0,
                    adjacentNodes.size(), hashIndices, sortedArrowsBack, arrowsMapBackward));
        }
    }

    private int getChunkSize(int n) {
        int chunk = n / Runtime.getRuntime().availableProcessors();
        if (chunk < 100) chunk = 100;
        return chunk;
    }

    private void calculateArrowsBackward(Node a, Node b, Graph graph,
                                         Map<Edge, ArrowConfigBackward> arrowsMapBackward,
                                         Map<Node, Integer> hashIndices,
                                         int[] arrowIndex,
                                         SortedSet<Arrow> sortedArrowsBack) {
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

    public enum AlgType {BOSS, BOSS_TUCK, KING_OF_BRIDGES}
}