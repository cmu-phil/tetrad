package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.CamAdditiveBic;
import edu.cmu.tetrad.search.score.CamAdditivePsplineBic;
import edu.cmu.tetrad.sem.Scorer;

import java.util.*;

/**
 * CAM (Causal Additive Models) search implementation.
 */
public class Cam {

    private DataSet data;
    private int degree = 3;
    private double ridge = 1e-6;
    private double penaltyDiscount = 1.0;
    private int maxForwardParents = 20;
    private int maxOrderIters = 2000;
    private boolean verbose = false;

    // --- initialization & multi-start knobs ---
    private long seed = System.nanoTime();
    private int restarts = 10; // number of random initial orders to try

    // Scorer for local scores (assumed initialized elsewhere)
    private CamAdditivePsplineBic scorer;

    public Cam(DataSet data, int degree) {
        this.data = data;
//        this.degree = degree;
//        this.scorer = new CamAdditiveBic(data, degree);//, ridge, penaltyDiscount);
        this.degree = degree;
        this.scorer = new CamAdditivePsplineBic(data)
                .setNumBasis(10)          // try 8–12
                .setPenaltyOrder(2)
                .setRidge(1e-6)
                .setPenaltyDiscount(this.penaltyDiscount); // keep in sync if you change later

    }

    public Cam setRidge(double ridge) {
        this.ridge = ridge;
        this.scorer.setRidge(ridge);
        return this;
    }

    public Cam setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
        this.scorer.setPenaltyDiscount(penaltyDiscount);
        return this;
    }

    public Cam setMaxForwardParents(int maxForwardParents) {
        this.maxForwardParents = maxForwardParents;
        return this;
    }

    public Cam setMaxOrderIters(int maxOrderIters) {
        this.maxOrderIters = maxOrderIters;
        return this;
    }

    public Cam setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public Cam setSeed(long s){ this.seed = s; return this; }
    public Cam setRestarts(int r){ this.restarts = Math.max(1, r); return this; }

    public Graph search() throws InterruptedException {
        final List<Node> vars = new ArrayList<>(data.getVariables());
        List<Node> bestOrder = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int r = 0; r < restarts; r++) {
            // ---- Step 1: order search with seeded random init ----
            List<Node> order = new ArrayList<>(vars);
            Collections.shuffle(order, new Random(seed + 31L * r));

            // Initial total using full scoring
            double current = permutationScoreInitial(order);
            boolean improved = true;
            int iters = 0;

            while (improved && iters++ < maxOrderIters) {
                improved = false;

                for (int i = 0; i + 1 < order.size(); i++) {
                    // Nodes at positions i and i+1
                    Node A = order.get(i);
                    Node B = order.get(i + 1);

                    // Build predecessor lists quickly
                    List<Node> predsForA_before = (i == 0) ? Collections.emptyList() : new ArrayList<>(order.subList(0, i));
                    List<Node> predsForB_before = new ArrayList<>(predsForA_before);
                    predsForB_before.add(A); // because B sits after A before the swap

                    // Scores before the swap (only A and B are affected)
                    double sA_before = scorer.localScore(A, predsForA_before);
                    double sB_before = scorer.localScore(B, predsForB_before);

                    // After swap, positions are B (at i) and A (at i+1)
                    // Predecessors for B_after are exactly predsForA_before
                    // Predecessors for A_after are predsForA_before U {B}
                    double sB_after = scorer.localScore(B, predsForA_before);
                    List<Node> predsForA_after = new ArrayList<>(predsForA_before);
                    predsForA_after.add(B);
                    double sA_after = scorer.localScore(A, predsForA_after);

                    double delta = (sB_after + sA_after) - (sA_before + sB_before);
                    if (delta < 0.0) {
                        // accept swap
                        Collections.swap(order, i, i + 1);
                        current += delta;
                        improved = true;
                    }

                    if (Thread.interrupted()) throw new InterruptedException();
                }
            }

            if (current < bestScore) {
                bestScore = current;
                bestOrder = order;
            }
        }

        if (verbose) System.out.printf("CAM order best score: %.3f (over %d restarts)\n", bestScore, restarts);

        // ---- Step 2: forward–backward selection using the best order ----
        return buildDagFromOrder(bestOrder);
    }

    /** Full score for an order: sum_y BIC(y | predecessors in order). */
    private double permutationScoreInitial(List<Node> order){
        double sum = 0.0;
        for (int i = 0; i < order.size(); i++) {
            Node y = order.get(i);
            List<Node> preds = (i == 0) ? Collections.emptyList() : new ArrayList<>(order.subList(0, i));
            sum += scorer.localScore(y, preds);
        }
        return sum;
    }

    /** Build DAG by greedy forward + backward among predecessors of each node in the given order. */
    private Graph buildDagFromOrder(List<Node> order) throws InterruptedException {
        Graph g = new EdgeListGraph(order);

        for (int i = 0; i < order.size(); i++) {
            Node y = order.get(i);
            List<Node> cand = (i == 0) ? Collections.emptyList() : new ArrayList<>(order.subList(0, i));

            Set<Node> pa = forwardSelect(y, cand, maxForwardParents);
            backwardPrune(y, pa);
            for (Node p : pa) g.addDirectedEdge(p, y);

            if (Thread.interrupted()) throw new InterruptedException();
        }
        return g;
    }

    // Assume forwardSelect, backwardPrune, and any other helpers remain unchanged here.

    private Set<Node> forwardSelect(Node y, List<Node> cand, int cap) {
        Set<Node> parents = new LinkedHashSet<>();
        double cur = scorer.localScore(y, parents);

        // simple greedy forward: add single best ΔBIC < 0 until no improvement or cap
        boolean improved = true;
        while (improved && parents.size() < cap && !cand.isEmpty()) {
            improved = false;
            Node bestP = null; double bestDelta = 0.0;
            for (Node x : cand) {
                if (parents.contains(x)) continue;
                parents.add(x);
                double s = scorer.localScore(y, parents);
                double d = s - cur;
                parents.remove(x);
                if (d < bestDelta) { bestDelta = d; bestP = x; }
            }
            if (bestP != null) {
                parents.add(bestP);
                cur += bestDelta;
                improved = true;
            }
        }
        return parents;
    }

    private void backwardPrune(Node y, Set<Node> parents) {
        double cur = scorer.localScore(y, parents);
        boolean improved = true;
        while (improved && !parents.isEmpty()) {
            improved = false;
            Node bestDrop = null; double bestDelta = 0.0; // negative better
            for (Node x : new ArrayList<>(parents)) {
                parents.remove(x);
                double s = scorer.localScore(y, parents);
                double d = s - cur;
                parents.add(x);
                if (d < bestDelta) { bestDelta = d; bestDrop = x; }
            }
            if (bestDrop != null) {
                parents.remove(bestDrop);
                cur += bestDelta;
                improved = true;
            }
        }
    }
}