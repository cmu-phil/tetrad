package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.AdditiveLocalScorer;
import edu.cmu.tetrad.search.score.CamAdditivePsplineBic;

import java.util.*;

/**
 * CAM (Causal Additive Models): PNS -> IncEdge (order) -> Prune. Scoring is injected via AdditiveLocalScorer so you can
 * use either P-splines or BasisFunctionBlocksBicScore (through the adapter).
 */
public class Cam {
    /**
     * The primary dataset used by the CAM (Causal Additive Model) algorithm for causal discovery. Represents the input
     * data upon which the model operates, including processing and scoring. This is a final reference and cannot be
     * re-assigned after initialization.
     */
    private final DataSet data;
    /**
     * Small LRU cache for local scores: key = "Y|P1,P2,..."
     */
    private final Map<Node, List<Node>> pnsCandidates = new HashMap<>();
    /**
     * Small LRU cache for local scores: key = "Y|P1,P2,..."
     */
    private final Map<String, Double> localCache = new LinkedHashMap<>(1 << 12, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
            return size() > 20000;
        }
    };
    /**
     * The `scorer` variable represents an instance of the `AdditiveLocalScorer` interface. It is used to compute local
     * scores for nodes based on specified parent sets. This variable plays a critical role in the structure learning
     * process within the CAM (Causal Additive Modeling) algorithm, as it provides the scoring mechanism necessary for
     * evaluating candidate structures.
     * <p>
     * The scorer can be injected or customized by using the `setScorer` method, allowing flexibility in defining the
     * scoring logic, such as BIC or other additive scoring strategies.
     */
    private AdditiveLocalScorer scorer = null;
    /**
     * A small regularization parameter used to prevent overfitting or numerical instability during the model's scoring
     * and optimization process. This value typically helps to stabilize computations, especially in ridge regression or
     * additive scoring methods.
     */
    private double ridge = 1e-6;
    /**
     * Represents a penalty discount factor used to adjust the penalty term in the scoring function. This value modifies
     * how strongly complexity penalties impact the selection of model structures. A higher value applies less penalty,
     * encouraging more complex structures, while a lower value increases the penalty, favoring simpler structures.
     * <p>
     * Default value is set to 1.0.
     */
    private double penaltyDiscount = 1.0;
    /**
     * Specifies the maximum number of forward parent nodes to be considered for a given target in the constrained
     * optimization process for causal structure learning.
     * <p>
     * This parameter limits the search space during the greedy forward selection when identifying dependencies, thus
     * improving computational efficiency while controlling model complexity.
     * <p>
     * Default value: 20.
     */
    private int maxForwardParents = 20;
    /**
     * Indicates whether verbose output is enabled. If set to true, additional logging or detailed information may be
     * printed during execution. This can be useful for debugging or understanding the internal processes of the
     * algorithm.
     */
    private boolean verbose = false;
    /**
     * Seed used to initialize the random number generator for order search restarts. It is assigned with the current
     * system time in nanoseconds by default.
     * <p>
     * Changing the seed value ensures variation in the search process, which may lead to different results in
     * algorithms that rely on randomization.
     */
    private long seed = System.nanoTime();
    /**
     * Specifies the number of restarts during the optimization process performed by the CAM algorithm. Restarts can
     * help avoid local optima by reinitializing the process multiple times.
     */
    private int restarts = 10;
    /**
     * The number of top univariate candidates to retain for each target during the constraint-based Pre-Neighborhood
     * Selection (PNS) step. This parameter determines how many of the highest-ranked variables (based on univariate
     * additive BIC scores) are considered relevant for further causal structure discovery.
     * <p>
     * Default value is 10.
     * <p>
     * Used in: - {@link #computePnsCandidates()} - {@link #setPnsTopK(int)}
     */
    private int pnsTopK = 10;

    /**
     * Constructs a Cam object with the specified data set.
     *
     * @param data the data set to be used for the algorithm; must not be null
     */
    public Cam(DataSet data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * Sets the AdditiveLocalScorer instance to the specified scorer and updates its configurations with the current
     * penalty discount and ridge values. Clears the local cache after setting.
     *
     * @param s the AdditiveLocalScorer instance to be used; must not be null
     */
    private void setScorer(AdditiveLocalScorer s) {
        this.scorer = Objects.requireNonNull(s, "scorer");
        this.scorer.setPenaltyDiscount(this.penaltyDiscount).setRidge(this.ridge);
        this.localCache.clear();
    }

    /**
     * Sets the ridge parameter for the Cam instance. This parameter is commonly used for regularization in algorithms
     * that involve scoring or optimization processes. Setting this value updates the ridge configuration in the Cam
     * object.
     *
     * @param ridge the double value representing the ridge parameter to be used; must not be negative
     * @return the current Cam instance, allowing for method chaining
     */
    public Cam setRidge(double ridge) {
        this.ridge = ridge;
        return this;
    }

    /**
     * Sets the penalty discount parameter for the Cam instance. The penalty discount is a factor used to adjust the
     * scoring function in the underlying optimization or scoring algorithms. This method updates the current
     * configuration of the Cam object with the specified value.
     *
     * @param penaltyDiscount the double value representing the penalty discount to be applied; must not be negative
     * @return the current Cam instance, allowing for method chaining
     */
    public Cam setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
        return this;
    }

    /**
     * Sets the maximum number of forward parents that can be considered in the algorithm. The method ensures that the
     * value for maximum forward parents is at least 1.
     *
     * @param maxForwardParents the maximum number of forward parents to be set; must be a positive integer
     * @return the current Cam instance, allowing for method chaining
     */
    public Cam setMaxForwardParents(int maxForwardParents) {
        this.maxForwardParents = Math.max(1, maxForwardParents);
        return this;
    }

    /**
     * Sets the verbose mode for the Cam instance. When verbose mode is enabled, additional diagnostic or logging
     * information may be generated during execution.
     *
     * @param verbose a boolean value indicating whether verbose mode should be enabled (true) or disabled (false)
     * @return the current Cam instance, allowing for method chaining
     */
    public Cam setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    /**
     * Sets the seed value for the random operations used within the Cam instance. This method allows reproducibility of
     * results by initializing the random number generator with a specific seed value.
     *
     * @param seed the long value representing the seed for random operations
     * @return the current Cam instance, allowing for method chaining
     */
    public Cam setSeed(long seed) {
        this.seed = seed;
        return this;
    }

    /**
     * Sets the number of restarts to be used in the algorithm. The number of restarts determines how many times the
     * search process restarts to potentially explore different solutions. This method ensures that the value is at
     * least 1.
     *
     * @param restarts the integer value representing the number of restarts; must be a positive integer
     * @return the current Cam instance, allowing for method chaining
     */
    public Cam setRestarts(int restarts) {
        this.restarts = Math.max(1, restarts);
        return this;
    }

    /**
     * CAM PNS strength: keep top-k univariate candidates per target (default 10).
     *
     * @param k the integer value representing the number of top candidates to keep per target; must be a positive
     *          integer
     */
    public Cam setPnsTopK(int k) {
        this.pnsTopK = Math.max(1, k);
        return this;
    }

    /**
     * Executes the search process to learn a Directed Acyclic Graph (DAG) from the given data
     * using the CAM algorithm. The search consists of:
     *
     * <ol>
     *   <li><b>Preselection of Neighborhood Selection (PNS)</b>:
     *     <ul>
     *       <li>Identifies candidate parents for each node via univariate/additive screening.</li>
     *       <li>Reduces the search space by keeping only the top candidates per target.</li>
     *     </ul>
     *   </li>
     *   <li><b>Order search with restarts</b>:
     *     <ul>
     *       <li>Finds a good node ordering by iterating over multiple randomized restarts.</li>
     *       <li>Scores partial orders and applies local improvements (e.g., incremental edge/order moves).</li>
     *     </ul>
     *   </li>
     *   <li><b>DAG construction and pruning</b>:
     *     <ul>
     *       <li>Builds the final graph via forward selection among predecessors restricted to PNS candidates.</li>
     *       <li>Applies backward pruning using the same local score to remove superfluous parents.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * During the search, the algorithm leverages scoring, candidate generation, and order optimization
     * techniques to identify the best graph structure under the CAM assumptions.
     *
     * @return the Directed Acyclic Graph (DAG) learned through the search process
     * @throws InterruptedException if the thread executing the method is interrupted during execution
     */
    public Graph search() throws InterruptedException {
        setScorer(new CamAdditivePsplineBic(data));

        // Stage 1: PNS
        computePnsCandidates();

        // Stage 2: IncEdge order with restarts
        List<Node> bestOrder = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int r = 0; r < restarts; r++) {
            Random rnd = new Random(seed + 31L * r);
            List<Node> order = incEdgeOrder(rnd);
            double total = permutationScore(order);
            if (total < bestScore) {
                bestScore = total;
                bestOrder = order;
            }
            if (Thread.interrupted()) throw new InterruptedException();
        }

        if (verbose) {
            System.out.printf("CAM order best score: %.3f (over %d restarts)%n", bestScore, restarts);
        }

        // Stage 3: Prune (forward+backward) restricted to PNS
        return buildDagFromOrder(bestOrder);
    }

    // ---------------- CAM: PNS ----------------

    /**
     * For each target y, rank all x≠y by univariate additive BIC and keep top-k.
     */
    private void computePnsCandidates() {
        List<Node> vars = data.getVariables();
        pnsCandidates.clear();

        for (Node y : vars) {
            int yIdx = vars.indexOf(y);
            List<Node> others = new ArrayList<>(vars);
            others.remove(y);

            // rank by BIC of y ~ s(x) (lower is better)
            others.sort(Comparator.comparingDouble(x -> {
                int xIdx = vars.indexOf(x);
                return scorer.localScore(yIdx, xIdx);
            }));

            if (others.size() > pnsTopK) {
                others = new ArrayList<>(others.subList(0, pnsTopK));
            }
            pnsCandidates.put(y, others);
        }
    }

    // ---------------- CAM: IncEdge order ----------------

    /**
     * Build an order greedily by appending the best next variable (IncEdge). Predecessors considered for candidate y
     * are placed ∩ PNS(y).
     */
    private List<Node> incEdgeOrder(Random rnd) {
        List<Node> all = new ArrayList<>(data.getVariables());
        Collections.shuffle(all, rnd); // randomized scan order; ties resolved deterministically below

        List<Node> order = new ArrayList<>(all.size());
        Set<Node> placed = new LinkedHashSet<>();

        while (order.size() < all.size()) {
            Node bestVar = null;
            double best = Double.POSITIVE_INFINITY;

            for (Node y : all) {
                if (placed.contains(y)) continue;

                // predecessors = placed ∩ PNS(y)
                List<Node> preds = new ArrayList<>(order);
                List<Node> cand = pnsCandidates.getOrDefault(y, all);
                preds.removeIf(p -> !cand.contains(p));

                double s = cachedLocal(y, preds);
                if (s < best - 1e-12) {
                    best = s;
                    bestVar = y;
                } else if (Math.abs(s - best) <= 1e-12) {
                    // deterministic tie-break: lexicographic by name
                    if (bestVar == null || y.getName().compareTo(bestVar.getName()) < 0) {
                        bestVar = y;
                    }
                }
            }

            order.add(bestVar);
            placed.add(bestVar);
        }
        return order;
    }

    // ---------------- CAM: Prune ----------------

    /**
     * Build DAG by greedy forward + backward restricted to predecessors in PNS(y).
     */
    private Graph buildDagFromOrder(List<Node> order) throws InterruptedException {
        Graph g = new EdgeListGraph(order);

        for (int i = 0; i < order.size(); i++) {
            Node y = order.get(i);

            // predecessors by order
            List<Node> preds = (i == 0) ? Collections.emptyList() : new ArrayList<>(order.subList(0, i));
            // restrict to PNS(y)
            List<Node> cand = new ArrayList<>(pnsCandidates.getOrDefault(y, preds));
            cand.retainAll(preds);

            Set<Node> pa = forwardSelect(y, cand, maxForwardParents);
            backwardPrune(y, pa);

            for (Node p : pa) g.addDirectedEdge(p, y);
            if (Thread.interrupted()) throw new InterruptedException();
        }
        return g;
    }

    // ---------------- helpers: scoring & selection ----------------

    /**
     * Sum of local scores consistent with the order (each node given its predecessors).
     */
    private double permutationScore(List<Node> order) {
        double sum = 0.0;
        for (int i = 0; i < order.size(); i++) {
            Node y = order.get(i);
            List<Node> preds = (i == 0) ? Collections.emptyList() : new ArrayList<>(order.subList(0, i));
            sum += cachedLocal(y, preds);
        }
        return sum;
    }

    /**
     * Tiny LRU cache over scorer.localScore(y, parents).
     */
    private double cachedLocal(Node y, Collection<Node> parents) {
        final String key;
        if (parents.isEmpty()) {
            key = y.getName() + "|";
        } else {
            StringBuilder sb = new StringBuilder(64).append(y.getName()).append('|');
            for (Node p : parents) sb.append(p.getName()).append(',');
            key = sb.toString();
        }
        Double v = localCache.get(key);
        if (v != null) return v;
        double s = scorer.localScore(y, parents);
        localCache.put(key, s);
        return s;
    }

    private Set<Node> forwardSelect(Node y, List<Node> cand, int cap) {
        Set<Node> parents = new LinkedHashSet<>();
        double cur = cachedLocal(y, parents);

        boolean improved = true;
        while (improved && parents.size() < cap && !cand.isEmpty()) {
            improved = false;
            Node bestP = null;
            double bestDelta = 0.0;

            for (Node x : cand) {
                if (parents.contains(x)) continue;
                parents.add(x);
                double s = cachedLocal(y, parents);
                double d = s - cur;
                parents.remove(x);
                if (d < bestDelta) {
                    bestDelta = d;
                    bestP = x;
                }
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
        double cur = cachedLocal(y, parents);
        boolean improved = true;

        while (improved && !parents.isEmpty()) {
            improved = false;
            Node bestDrop = null;
            double bestDelta = 0.0;

            for (Node x : new ArrayList<>(parents)) {
                parents.remove(x);
                double s = cachedLocal(y, parents);
                double d = s - cur;
                parents.add(x);
                if (d < bestDelta) {
                    bestDelta = d;
                    bestDrop = x;
                }
            }
            if (bestDrop != null) {
                parents.remove(bestDrop);
                cur += bestDelta;
                improved = true;
            }
        }
    }
}