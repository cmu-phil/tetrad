package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * @author bryanandrews
 */
public class PagIdea2 {
    private final List<Node> variables;
    private final Score score;
    private boolean changeFlag;
    private int depth = 3;

    /**
     * Constructor for a score.
     *
     * @param score The score to use.
     */
    public PagIdea2(Score score) {
        this.variables = new ArrayList<>(score.getVariables());
        this.score = score;
    }

    public Graph search() {
        Boss subAlg = new Boss(this.score);
        subAlg.setUseBes(true);
        subAlg.setNumStarts(1);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setCpdag(false);
        Graph bossGraph = alg.search();
        Graph graph = new EdgeListGraph(this.variables);
        for (Node v : bossGraph.getNodes()) {
            for (Node w : bossGraph.getParents(v)) graph.addEdge(new Edge(v, w, Endpoint.ARROW, Endpoint.CIRCLE));
        }

        for (int i = 0; i < this.variables.size(); i++) {
            Node v = this.variables.get(i);

            Set<Integer> W = new HashSet<>();
            for (Node w : bossGraph.getParents(v)) W.add(this.variables.indexOf(w));
//            for (Node w : bossGraph.getAdjacentNodes(v)) W.add(this.variables.indexOf(w));
//            for (Node w : bossGraph.getChildren(v)) W.remove(this.variables.indexOf(w));

            int d = 0;
            do {
                List<Integer> Q = new ArrayList<>(W);
                Set<Integer> T = new HashSet<>();
                ChoiceGenerator cg = new ChoiceGenerator(Q.size(), d);
                int[] choice;
                while ((choice = cg.next()) != null) {
                    Set<Integer> L = asSet(choice, Q);
                    W.removeAll(L);
                    Set<Integer> R = shrink(W, i);
                    W.addAll(L);
                    if (!R.isEmpty()) {
                        T.addAll(R);
                        for (int j : R) {
                            Node u = this.variables.get(j);
                            if (graph.isAdjacentTo(v, u)) graph.removeEdge(v, u);
                        }
                        for (int j : L) {
                            Node w = this.variables.get(j);
                            if (graph.isAdjacentTo(v, w)) {
                                Edge edge = graph.getEdge(v, w);
                                if (edge.getNode1() == w) edge.setEndpoint1(Endpoint.ARROW);
                                if (edge.getNode2() == w) edge.setEndpoint2(Endpoint.ARROW);
                            }
                            for (Node u : graph.getAdjacentNodes(w)) {
                                if (R.contains(this.variables.indexOf(u))) {
                                    Edge edge = graph.getEdge(u, w);
                                    if (edge.getNode1() == w) edge.setEndpoint1(Endpoint.ARROW);
                                    if (edge.getNode2() == w) edge.setEndpoint2(Endpoint.ARROW);
                                }
                            }
                        }
                    }
                }
                W.removeAll(T);
                d -= T.size();
                d = max(d, 0);
            } while (d++ < min(W.size(), this.depth));
        }

        spirtesOrientation(graph);
        return graph;
    }

    private void grow(Set<Integer> S, Set<Integer> W, int v) {
        double best = this.score.localScore(v);
        int w = -1;

        do {
            if (w != -1) {
                S.remove(w);
                W.add(w);
                w = -1;
            }
            for (int s : S) {
                W.add(s);
                if (this.score.localScore(v, W.stream().mapToInt(Integer::intValue).toArray()) > best) w = s;
                W.remove(s);
            }
        } while (w != -1);
    }

    private Set<Integer> shrink(Set<Integer> S, int v) {
        Set<Integer> W = new HashSet<>(S);
        Set<Integer> R = new HashSet<>();

        double best = this.score.localScore(v, S.stream().mapToInt(Integer::intValue).toArray());
        int r = -1;

        do {
            if (r != -1) {
                S.remove(r);
                W.remove(r);
                R.add(r);
                r = -1;
            }
            for (int s : S) {
                W.remove(s);
                if (this.score.localScore(v, W.stream().mapToInt(Integer::intValue).toArray()) > best) r = s;
                W.add(s);
            }
        } while (r != -1);

        return R;
    }

    private void spirtesOrientation(Graph graph) {
        this.changeFlag = true;

        while (this.changeFlag) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            this.changeFlag = false;
            rulesR1R2cycle(graph);
            ruleR3(graph);
        }
    }

    //Does all 3 of these rules at once instead of going through all
    // triples multiple times per iteration of doFinalOrientation.
    private void rulesR1R2cycle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(B));

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null && !Thread.currentThread().isInterrupted()) {
                Node A = adj.get(combination[0]);
                Node C = adj.get(combination[1]);

                //choice gen doesnt do diff orders, so must switch A & C around.
                ruleR1(A, B, C, graph);
                ruleR1(C, B, A, graph);
                ruleR2(A, B, C, graph);
                ruleR2(C, B, A, graph);
            }
        }
    }

    /// R1, away from collider
    // If a*->bo-*c and a, c not adjacent then a*->b->c
    private void ruleR1(Node a, Node b, Node c, Graph graph) {
        if (graph.isAdjacentTo(a, c)) {
            return;
        }

        if (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
            if (!isArrowheadAllowed(b, c, graph)) {
                return;
            }

            graph.setEndpoint(c, b, Endpoint.TAIL);
            graph.setEndpoint(b, c, Endpoint.ARROW);
            this.changeFlag = true;
        }
    }

    // if a*-oc and either a-->b*->c or a*->b-->c, and a*-oc then a*->c
    // This is Zhang's rule R2.
    private void ruleR2(Node a, Node b, Node c, Graph graph) {
        if ((graph.isAdjacentTo(a, c)) && (graph.getEndpoint(a, c) == Endpoint.CIRCLE)) {
            if ((graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(b, c) == Endpoint.ARROW)
                    && (graph.getEndpoint(b, a) == Endpoint.TAIL || graph.getEndpoint(c, b) == Endpoint.TAIL)) {

                if (!isArrowheadAllowed(a, c, graph)) {
                    return;
                }

                graph.setEndpoint(a, c, Endpoint.ARROW);

                this.changeFlag = true;
            }
        }
    }

    /**
     * Implements the double-triangle orientation rule, which states that if D*-oB, A*-&gt;B&lt;-*C and A*-oDo-*C, and !adj(a,
     * c), D*-oB, then D*->B.
     * <p>
     * This is Zhang's rule R3.
     */
    private void ruleR3(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> intoBArrows = graph.getNodesInTo(b, Endpoint.ARROW);

            if (intoBArrows.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(intoBArrows.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> B = GraphUtils.asList(choice, intoBArrows);

                Node a = B.get(0);
                Node c = B.get(1);

                List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(a));
                adj.retainAll(graph.getAdjacentNodes(c));

                for (Node d : adj) {
                    if (d == a) continue;

                    if (graph.getEndpoint(a, d) == Endpoint.CIRCLE && graph.getEndpoint(c, d) == Endpoint.CIRCLE) {
                        if (!graph.isAdjacentTo(a, c)) {
                            if (graph.getEndpoint(d, b) == Endpoint.CIRCLE) {
                                if (!isArrowheadAllowed(d, b, graph)) {
                                    return;
                                }

                                graph.setEndpoint(d, b, Endpoint.ARROW);

                                this.changeFlag = true;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isArrowheadAllowed(Node x, Node y, Graph graph) {
        if (!graph.isAdjacentTo(x, y)) return false;

        if (graph.getEndpoint(x, y) == Endpoint.ARROW) {
            return true;
        }

        if (graph.getEndpoint(x, y) == Endpoint.TAIL) {
            return false;
        }

        return graph.getEndpoint(x, y) == Endpoint.CIRCLE;
    }

    private Set<Integer> asSet(int[] choice, List<Integer> list) {
        Set<Integer> set = new HashSet<>();

        for (int i : choice) {
            if (i >= 0 && i < list.size()) {
                set.add(list.get(i));
            }
        }

        return set;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}