package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.CamAdditiveBic;

import java.util.*;

/**
 * CAM (Causal Additive Models) style search:
 * 1) Greedy adjacent-swap search over permutations maximizing sum of nodewise additive BIC
 *    with predecessors-only parents (CAM order score).
 * 2) Given the order, do forward selection + backward pruning for each node among predecessors.
 *
 * Output: a DAG (EdgeListGraph) with arrows from earlier to later in the learned order.
 *
 * Notes:
 * - This mirrors Peters/Bühlmann/Meinshausen CAM: order-based score with additive models.
 * - The regression class is your basis-block BIC internals (no interactions => CAM).
 */
public final class Cam2 implements IGraphSearch {

    private final DataSet data;
    private final CamAdditiveBic scorer;

    // knobs
    private int maxForwardParents = 20;   // per node cap during forward step
    private int maxOrderIters = 2000;     // cap swaps
    private boolean verbose = false;

    public Cam2(DataSet data, int degree) {
        this.data = Objects.requireNonNull(data);
        this.scorer = new CamAdditiveBic(data, degree);
    }

    public Cam2 setMaxForwardParents(int k){ this.maxForwardParents = Math.max(1,k); return this; }
    public Cam2 setMaxOrderIters(int iters){ this.maxOrderIters = Math.max(1,iters); return this; }
    public Cam2 setVerbose(boolean v){ this.verbose = v; return this; }

    // pass-throughs if you want:
    public Cam2 setRidge(double r){ this.scorer.setRidge(r); return this; }
    public Cam2 setPenaltyDiscount(double c){ this.scorer.setPenaltyDiscount(c); return this; }

    @Override
    public Graph search() throws InterruptedException {
        // --- Step 1: order search via adjacent transpositions ---
        List<Node> order = new ArrayList<>(data.getVariables());
        // simple init: random shuffle (could use variance ordering or reg strength)
        Collections.shuffle(order, new Random(17));

        double best = scorer.permutationScore(order);
        if (verbose) System.out.printf("CAM init score: %.3f%n", best);

        boolean improved = true;
        int iters = 0;

        while (improved && iters++ < maxOrderIters) {
            improved = false;
            for (int i = 0; i+1 < order.size(); i++) {
                swap(order, i, i+1);
                double candidate = scorer.permutationScore(order);
                if (candidate < best) {
                    best = candidate;
                    improved = true;
                    if (verbose) System.out.printf(" swap (%d,%d) -> %.3f%n", i, i+1, best);
                } else {
                    swap(order, i, i+1); // revert
                }
                if (Thread.interrupted()) throw new InterruptedException();
            }
        }
        if (verbose) System.out.printf("CAM order done: score=%.3f, iters=%d%n", best, iters);

        // --- Step 2: forward–backward selection w.r.t. this order ---
        Graph g = new EdgeListGraph(order);
        Map<Node,Integer> pos = new HashMap<>();
        for (int i = 0; i < order.size(); i++) pos.put(order.get(i), i);

        for (int i = 0; i < order.size(); i++) {
            Node y = order.get(i);
            // candidates = predecessors in the learned order
            List<Node> cand = new ArrayList<>(order.subList(0, i));
            Set<Node> pa = forwardSelect(y, cand, maxForwardParents);
            backwardPrune(y, pa);

            for (Node p : pa) g.addDirectedEdge(p, y);
            if (Thread.interrupted()) throw new InterruptedException();
        }

        return g;
    }

    private static void swap(List<Node> a, int i, int j) {
        Node t = a.get(i); a.set(i, a.get(j)); a.set(j, t);
    }

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