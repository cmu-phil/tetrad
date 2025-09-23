package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.AdditiveLocalScorer;
import edu.cmu.tetrad.search.score.CamAdditivePsplineBic;

import java.util.*;

/**
 * The Cam class implements the Causal Additive Model (CAM), designed for causal discovery from observational data. This
 * class includes support for preprocessing potential parent nodes using a Prior Neighborhood Search (PNS), searching
 * for variable ordering using incremental edge methods, and refining causal relationships through pruning strategies.
 * <p>
 * It is initialized with a dataset and allows for additional configuration such as injection of a custom local scorer,
 * adjustment of search parameters, and control over numerical stability options.
 */
public class Cam {

    // ----- data -----
    private final DataSet data;

    // ----- PNS candidates: y -> list of top univariate candidates -----
    private final Map<Node, List<Node>> pnsCandidates = new HashMap<>();

    // ----- Tiny LRU cache for local scores: key = "Y|P1,P2,..." (parents sorted) -----
    private final Map<String, Double> localCache = new LinkedHashMap<>(1 << 12, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Double> e) {
            return size() > 20_000;
        }
    };

    // ----- scorer (injectable) -----
    private AdditiveLocalScorer scorer = null;

    // ----- knobs -----
    private double ridge = 1e-6;
    private double penaltyDiscount = 1.0;
    private int maxForwardParents = 20;
    private boolean verbose = false;

    // Order search restarts
    private long seed = System.nanoTime();
    private int restarts = 10;

    // PNS candidates: top-k univariate per target
    private int pnsTopK = 10;

    // --- Numerical-stability knobs (safe defaults) ---
    private double xtxJitter = 1e-8;      // tiny jitter added to B^T B
    private double gcvMinDenom = 5.0;     // floor for N - edf in GCV
    private double edfEps = 1e-6;         // keep edf strictly < N
    private double lambdaMinExp = -4.0;   // λ grid: 10^min .. 10^max
    private double lambdaMaxExp = 6.0;
    private int lambdaNum = 15;        // # grid points

    /**
     * Constructs a Cam instance with the given data set.
     *
     * @param data the data set to be used for the CAM algorithm; must not be null
     */
    public Cam(DataSet data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * Sets the scorer to be used by the current CAM instance. The scorer is an implementation of the
     * {@link AdditiveLocalScorer} interface. This method also applies the current penalty discount and ridge values to
     * the scorer and clears the local cache.
     *
     * @param s the {@link AdditiveLocalScorer} to be set; must not be null
     * @return the current instance of {@code Cam} to allow method chaining
     * @throws NullPointerException if the provided scorer is null
     */
    public Cam setScorer(AdditiveLocalScorer s) {
        this.scorer = Objects.requireNonNull(s, "scorer");
        this.scorer.setPenaltyDiscount(this.penaltyDiscount).setRidge(this.ridge);
        this.localCache.clear();
        return this;
    }

    /**
     * Sets the ridge parameter for the current {@code Cam} instance. The ridge parameter is used for regularization in
     * scoring and optimization processes. If a scorer is already associated with this instance, the scorer's ridge
     * value will also be updated accordingly.
     *
     * @param ridge the value of the ridge parameter to be set; must be a non-negative value
     * @return the current instance of {@code Cam} to allow method chaining
     */
    public Cam setRidge(double ridge) {
        this.ridge = ridge;
        if (this.scorer != null) this.scorer.setRidge(ridge);
        return this;
    }

    /**
     * Sets the penalty discount value for the current {@code Cam} instance. This value is used to adjust the scoring
     * mechanism within the CAM algorithm. If a scorer is already associated with this CAM instance, the penalty
     * discount is also applied to the scorer.
     *
     * @param penaltyDiscount the penalty discount value to be set; must be a valid numerical value representing the
     *                        penalty adjustment
     * @return the current instance of {@code Cam} to allow method chaining
     */
    public Cam setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
        if (this.scorer != null) this.scorer.setPenaltyDiscount(penaltyDiscount);
        return this;
    }

    /**
     * Sets the maximum number of forward parents that can be considered in the algorithm for the current {@code Cam}
     * instance. The value will be set to the maximum of 1 and the value provided to ensure it is always at least 1.
     *
     * @param maxForwardParents the maximum number of forward parents to be set; must be a positive integer
     * @return the current instance of {@code Cam} to allow method chaining
     */
    public Cam setMaxForwardParents(int maxForwardParents) {
        this.maxForwardParents = Math.max(1, maxForwardParents);
        return this;
    }

    /**
     * Sets the verbosity level for the current {@code Cam} instance. When enabled, the verbose mode provides detailed
     * logging information during the execution of CAM processes.
     *
     * @param verbose a boolean indicating whether verbose mode should be enabled (true) or disabled (false)
     * @return the current instance of {@code Cam} to allow method chaining
     */
    public Cam setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    /**
     * Sets the seed value for the random processes in the current {@code Cam} instance. The seed ensures
     * reproducibility in the algorithm's behavior across executions.
     *
     * @param seed the seed value to be set; must be a valid {@code long} value
     * @return the current instance of {@code Cam} to allow method chaining
     */
    public Cam setSeed(long seed) {
        this.seed = seed;
        return this;
    }

    /**
     * Sets the number of restart attempts for the CAM algorithm. The value is constrained to be at least 1. If a number
     * less than 1 is provided, it will be adjusted to 1 automatically.
     *
     * @param restarts the number of restarts to be set; must be a positive integer (values less than 1 will be adjusted
     *                 to 1)
     * @return the current instance of {@code Cam} to allow method chaining
     */
    public Cam setRestarts(int restarts) {
        this.restarts = Math.max(1, restarts);
        return this;
    }

    /**
     * Sets the maximum number of top candidates to be considered during the Parent-Node Selection (PNS) process. The
     * input value will be constrained to a minimum of 1 to ensure valid operation.
     *
     * @param k the maximum number of top candidates to be retained; must be a positive integer (values less than 1 will
     *          be adjusted to 1)
     * @return the current instance of {@code Cam} to allow method chaining
     */
    public Cam setPnsTopK(int k) {
        this.pnsTopK = Math.max(1, k);
        return this;
    }

    /**
     * Executes the CAM (Causal Additive Model) algorithm to search for an optimal directed acyclic graph (DAG)
     * structure that best fits the data. The process consists of three main stages: Stage 1: Parent-Node Selection
     * (PNS) to identify candidate parents. Stage 2: Incremental edge ordering with restarts to determine the best node
     * ordering. Stage 3: Construction and pruning of the DAG based on the obtained order and PNS results.
     *
     * @return the resulting {@code Graph} representing the optimal DAG structure discovered by the CAM algorithm
     * @throws InterruptedException if the thread running this method is interrupted, halting the search process
     *                              prematurely
     */
    public Graph search() throws InterruptedException {
        // Default scorer if none injected by caller.
        if (this.scorer == null) {
            setScorer(new CamAdditivePsplineBic(data));
        }

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
     * Order-invariant cache over scorer.localScore(y, parents).
     */
    private double cachedLocal(Node y, Collection<Node> parents) {
        final String key;
        if (parents.isEmpty()) {
            key = y.getName() + "|";
        } else {
            // canonicalize parent names to avoid cache misses due to order
            List<String> names = new ArrayList<>(parents.size());
            for (Node p : parents) names.add(p.getName());
            Collections.sort(names);
            StringBuilder sb = new StringBuilder(64).append(y.getName()).append('|');
            for (String name : names) sb.append(name).append(',');
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