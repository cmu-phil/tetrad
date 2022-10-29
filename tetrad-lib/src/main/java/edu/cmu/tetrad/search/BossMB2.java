package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

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
    private Knowledge knowledge = new Knowledge();
    private long start;
    private boolean verbose = true;
    private int depth = 4;
    private boolean findMb = false;

    public BossMB2(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
    }

    class MyTask2 implements Callable<Graph> {
        final TeyssierScorer2 scorer0;
        final Node target;

        MyTask2(TeyssierScorer2 scorer0, Node target) {
            this.scorer0 = scorer0;
            this.target = target;
        }

        @Override
        public Graph call() throws InterruptedException {
            return targetVisit(scorer0, target);
        }
    }

    /**
     * Prints local graphs for all variables and returns the one of them.
     */
    public Graph search(@NotNull List<Node> order) {
        long start = System.currentTimeMillis();
        order = new ArrayList<>(order);

        TeyssierScorer2 scorer0 = new TeyssierScorer2(this.score);
        scorer0.setKnowledge(this.knowledge);
        scorer0.score(order);

        this.start = System.currentTimeMillis();

        makeValidKnowledgeOrder(order);

        System.out.println("Initial score = " + scorer0.score() + " Elapsed = " + (System.currentTimeMillis() - start) / 1000.0 + " s");

        List<Node> _targets = new ArrayList<>(scorer0.getPi());
        sort(_targets);

        Graph combinedGraph = new EdgeListGraph(_targets);

        List<MyTask2> tasks = new ArrayList<>();

        try {
            for (Node node : _targets) {
                tasks.add(new MyTask2(scorer0, node));
            }

            List<Future<Graph>> futures = ForkJoinPool.commonPool().invokeAll(tasks);

            for (Future<Graph> future : futures) {
                Graph g = future.get();
                this.graphs.add(g);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        for (Graph graph : graphs) {
            for (Edge e : graph.getEdges()) {
                combinedGraph.addEdge(e);
            }
        }

        for (Edge e : combinedGraph.getEdges()) {
            if (e.isDirected()) {
                if (combinedGraph.containsEdge(e) && combinedGraph.containsEdge(e.reverse())) {
                    combinedGraph.removeEdge(e);
                    combinedGraph.removeEdge(e.reverse());
                }
            } else if (Edges.isUndirectedEdge(e)) {
                Node n1 = e.getNode1();
                Node n2 = e.getNode2();

                List<Edge> edges = combinedGraph.getEdges(n1, n2);

                for (Edge _e : edges) {
                    if (e != _e) combinedGraph.removeEdge(e);
                }
            }
        }

        long stop = System.currentTimeMillis();

        System.out.println("Elapsed time = " + (stop - start) / 1000.0 + " s");

        return combinedGraph;
    }

    private Graph targetVisit(TeyssierScorer2 scorer0, Node target) throws InterruptedException {
        TeyssierScorer2 scorer = new TeyssierScorer2(scorer0);

        List<Node> pi2 = scorer.getPi();
        List<Node> pi1;

        do {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            pi1 = pi2;
            betterMutationBossTuck(scorer, Collections.singletonList(target));
            pi2 = besOrder(scorer);
        } while (!pi1.equals(pi2));

        scorer.score(pi2);
        Graph graph = scorer.getGraph(true);

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

            for (Node n : graph.getNodes()) {
                if (target == n) continue;
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

    public void betterMutationBossTuck(@NotNull TeyssierScorer2 scorer, List<Node> targets) throws
            InterruptedException {
        double sp;

        List<Node> p1, p2;

        do {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
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
    }

    public List<Node> besOrder(TeyssierScorer2 scorer) {
        Graph graph = scorer.getGraph(true);
        Bes bes = new Bes(score);
        bes.setDepth(depth);
        bes.setVerbose(verbose);
        bes.setKnowledge(knowledge);
        bes.bes(graph, scorer.getPi());
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

    public List<Node> getVariables() {
        return this.variables;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
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

    private final List<Graph> graphs = new ArrayList<>();

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public enum AlgType {BOSS_OLD, BOSS}
}