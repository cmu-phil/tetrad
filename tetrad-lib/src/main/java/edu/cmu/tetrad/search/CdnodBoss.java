package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;

import java.util.*;

/**
 * Implements a variant of the CD-NOD (Causal Discovery from Non-stationary/
 * heterogeneous Data) algorithm that uses BOSS (Best Order Score Search) for
 * the backbone rather than FAS, combining a score-based CPDAG with
 * constraint-based collider orientation for the triples BOSS leaves
 * unresolved.
 *
 * <p><b>Division of labor.</b> BOSS's orientations are treated as
 * authoritative: the constraint-based collider step is applied only to
 * unshielded triples x --- z --- y in which <i>both</i> edges at z are
 * undirected in the BOSS CPDAG (after context forcing). Triples that BOSS has
 * already resolved, in whole or in part, are never revisited or overridden by
 * CI tests. The CI step is thus a supplement that can only add information
 * where the score-based search was agnostic.
 *
 * <p>Context variables are identified as all Tier-0 variables in the supplied
 * {@link Knowledge} object. Any variable in Tier-0 that is also present in the
 * dataset is treated as a context variable and is forced to be a cause of every
 * adjacent non-context variable, reflecting the assumption that changing causal
 * mechanisms are indexed by those context variables.
 *
 * <p>The algorithm proceeds in four stages:
 * <ol>
 *   <li><b>BOSS backbone.</b> BOSS is run on the augmented dataset
 *       (X ∪ contexts) using the supplied {@link Score} (a SEM BIC score over
 *       the covariance matrix by default), with knowledge extended to forbid
 *       all edges into context variables. The resulting CPDAG provides the
 *       adjacencies and the authoritative score-based orientations.</li>
 *   <li><b>Context forcing.</b> For each context variable C and each adjacent
 *       non-context variable X, the edge C --- X is replaced by the directed
 *       edge C → X, subject to any background knowledge constraints.</li>
 *   <li><b>Collider orientation on unresolved triples.</b> Unshielded triples
 *       whose two edges at the collider candidate are both undirected are
 *       oriented as colliders or non-colliders using one of three strategies,
 *       selectable via {@link ColliderOrientationStyle}:
 *       <ul>
 *         <li><b>SEPSETS</b>: orients x → z ← y if z is not in the sepset of
 *             x and y (standard PC rule). Sepsets are derived on demand: a
 *             RecursiveBlocking hint is tried first and validated by an actual
 *             CI test, with enumeration over adjacency subsets as fallback.</li>
 *         <li><b>CONSERVATIVE</b>: orients a collider only if every sepset
 *             excludes z and no sepset includes z (Conservative PC rule).</li>
 *         <li><b>MAX_P</b>: selects the sepset with the highest p-value and
 *             uses it to decide orientation, with an optional tie-guard margin.</li>
 *       </ul>
 *       All sepset computations (RB hints and enumeration pools) are performed
 *       against a frozen copy of the post-forcing BOSS graph, so results do not
 *       depend on the order in which triples are visited or on orientations
 *       added during this step. By default, context variables are admissible
 *       members of conditioning sets, consistent with Huang et al. (2020);
 *       setting {@link Builder#excludeContextsFromS(boolean)} to {@code true}
 *       excludes contexts uniformly from RB hints and enumeration pools
 *       (mirroring the CD-NOD-PAG runner).</li>
 *   <li><b>Meek closure.</b> Meek's orientation rules are applied to propagate
 *       implied orientations and complete the partially directed graph.</li>
 * </ol>
 *
 * <p>Instances are constructed via the nested {@link Builder}:
 * <pre>
 *   CdnodBoss cdnod = new CdnodBoss.Builder()
 *       .test(independenceTest)
 *       .data(dataSet)               // dataset containing all variables (contexts may be anywhere)
 *       .knowledge(knowledge)        // Tier-0 variables treated as contexts
 *       .colliderStyle(ColliderOrientationStyle.MAX_P)
 *       .depth(3)
 *       .verbose(true)
 *       .build();
 *
 *   Graph result = cdnod.search();
 * </pre>
 *
 * <p>For backwards compatibility, a continuous change-index column can be appended
 * to the dataset via {@link Builder#dataAndIndex} rather than being supplied
 * pre-merged. Context variables are determined from Knowledge tier 0 regardless
 * of column position.
 *
 * @see Boss
 * @see PermutationSearch
 * @see MeekRules
 * @see Knowledge
 */
public final class CdnodBoss implements IGraphSearch {

    private final double alpha;              // recorded for reporting/parity; CI decisions are made by the
    // supplied IndependenceTest, which carries its own alpha
    private final ColliderOrientationStyle colliderStyle;
    private final Knowledge knowledge;
    private final boolean verbose;
    private final double maxPMargin;         // tie-guard for MAX_P (0.0 = classic)
    private final int depth;                 // S-size cap for sepset enumeration; -1 = unbounded

    // --- BOSS knobs (passed through to the backbone) ---
    private final boolean useBes;
    private final int numStarts;
    private final int numThreads;
    private final boolean useDataOrder;
    private final long seed;                 // -1 = no fixed seed

    // --- behavior flags ---
    /**
     * If true, context variables are excluded from conditioning sets in the collider-orientation
     * step, uniformly across all collider styles (RB hints containing a context are rejected, and
     * enumeration pools are filtered). If false (the default), contexts are admissible, per Huang
     * et al. (2020).
     */
    private final boolean excludeContextsFromS;

    // --- core config ---
    private IndependenceTest test;
    private DataSet data;                    // dataset containing all variables (contexts may be anywhere)
    private final Score score;               // may be null; a SEM BIC score is constructed by default
    private final double penaltyDiscount;    // used only when the default SEM BIC score is constructed

    // --- runtime ---
    private long timeoutMs = -1;
    private long startTimeMs = 0;
    private final SepsetMap sepsets = new SepsetMap();
    private final Set<Long> noSep = new HashSet<>();
    private Map<Node, Integer> id;
    private Graph sepsetGraph;               // frozen post-forcing graph; all sepset machinery reads this

    private Set<Node> contextNodes = Collections.emptySet();

    private CdnodBoss(IndependenceTest test,
                      DataSet data,
                      Score score,
                      double penaltyDiscount,
                      double alpha,
                      ColliderOrientationStyle colliderStyle,
                      Knowledge knowledge,
                      boolean verbose,
                      double maxPMargin,
                      int depth,
                      boolean excludeContextsFromS,
                      boolean useBes,
                      int numStarts,
                      int numThreads,
                      boolean useDataOrder,
                      long seed) {
        this.test = test;
        this.data = data; // may be null; user can set later
        this.score = score;
        this.penaltyDiscount = penaltyDiscount;
        this.alpha = alpha;
        this.colliderStyle = colliderStyle;
        this.knowledge = knowledge == null ? new Knowledge() : knowledge;
        this.verbose = verbose;
        this.maxPMargin = maxPMargin;
        this.depth = depth;
        this.excludeContextsFromS = excludeContextsFromS;
        this.useBes = useBes;
        this.numStarts = numStarts;
        this.numThreads = numThreads;
        this.useDataOrder = useDataOrder;
        this.seed = seed;
    }

    private static DataSet appendChangeIndexAsLastColumn(DataSet dataX, double[] cIndex, String cName) {
        if (cIndex.length != dataX.getNumRows())
            throw new IllegalArgumentException("Length mismatch: cIndex vs rows.");
        String name = (cName == null || cName.isBlank()) ? "C" : cName;

        int n = dataX.getNumRows();
        int p = dataX.getNumColumns();

        List<Node> vars = new ArrayList<>(dataX.getVariables());
        ContinuousVariable cVar = new ContinuousVariable(name);
        vars.add(cVar);

        DoubleDataBox box = new DoubleDataBox(n, p + 1);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) box.set(i, j, dataX.getDouble(i, j));
            box.set(i, p, cIndex[i]);
        }
        return new BoxDataSet(box, vars);
    }

    private static void ensureVariablesMatch(IndependenceTest test, DataSet data) {
        List<Node> testVars = test.getVariables();
        if (!testVars.equals(data.getVariables())) {
            throw new IllegalStateException("CdnodBoss: IndependenceTest variables must match data variables (same order).");
        }
    }

    // =============== IGraphSearch ===============

    @Override
    public Graph search() throws InterruptedException {
        if (data == null) {
            throw new IllegalStateException("CdnodBoss: data is null. Provide a DataSet via Builder.data(...), " +
                    "or use Builder.dataAndIndex(...) to append a column before search().");
        }
        ensureVariablesMatch(test, data);
        return run(data);
    }

    @Override
    public IndependenceTest getTest() {
        return this.test;
    }

    @Override
    public void setTest(IndependenceTest newTest) {
        if (newTest == null) throw new IllegalArgumentException("test cannot be null");
        if (this.test == null) {
            this.test = newTest;
            return;
        }
        List<Node> oldVars = this.test.getVariables();
        List<Node> newVars = newTest.getVariables();
        if (!oldVars.equals(newVars)) {
            throw new IllegalArgumentException("Proposed test's variables must equal the existing test's variables (same order).");
        }
        this.test = newTest;
    }

    // =============== Public helpers ===============

    /**
     * Sets the dataset to be used in this instance. Contexts are determined from Knowledge tier 0;
     * their column positions are irrelevant.
     *
     * @param data the dataset to be assigned
     */
    public void setData(DataSet data) {
        this.data = data;
    }

    /**
     * Sets the dataset to be used in this instance.
     *
     * @param dataWithC the dataset to be assigned
     * @deprecated Contexts are now determined from Knowledge tier 0, not from column position.
     * Use {@link #setData(DataSet)} instead.
     */
    @Deprecated
    public void setDataWithC(DataSet dataWithC) {
        setData(dataWithC);
    }

    /**
     * Updates the internal dataset by appending a change index as the last column.
     * This method modifies the data to include an additional column (defined by the provided change index and name)
     * and stores the updated dataset for further processing.
     *
     * @param dataX  the original dataset to which the change index will be appended
     * @param cIndex an array representing the change index values to be incorporated into the dataset
     * @param cName  the name of the new column that will represent the change index
     */
    public void setDataAndIndex(DataSet dataX, double[] cIndex, String cName) {
        this.data = appendChangeIndexAsLastColumn(dataX, cIndex, cName);
    }

    /**
     * Sets the timeout value in milliseconds for this instance. This value determines the maximum time allowed for
     * certain operations before they are interrupted or terminated.
     *
     * @param timeoutMs the timeout in milliseconds
     */
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    // =============== Core ===============

    private Graph run(DataSet dataAll) throws InterruptedException {
        this.startTimeMs = System.currentTimeMillis();

        // Resolve contexts from Knowledge tier 0 (CD-NOD semantics).
        this.contextNodes = resolveContextNodesTier0(dataAll);

        // 1) BOSS backbone (CPDAG under knowledge)
        if (verbose) TetradLogger.getInstance().log("CD-NOD(BOSS): BOSS backbone...");
        Graph g = runBossBackbone(dataAll);

        // If no contexts were provided, we skip context forcing.
        if (contextNodes.isEmpty()) {
            if (verbose) {
                TetradLogger.getInstance().log("CD-NOD(BOSS): No Tier-0 contexts in Knowledge; skipping context forcing.");
            }
        } else {
            // 2) Force Context -> X where adjacent (respect knowledge/tiers)
            if (verbose) {
                List<String> cn = contextNodes.stream().map(Node::getName).sorted().toList();
                TetradLogger.getInstance().log("CD-NOD(BOSS): Forcing Context -> X for contexts=" + cn);
            }
            for (Node c : contextNodes) {
                for (Node nbr : new ArrayList<>(g.getAdjacentNodes(c))) {
                    if (contextNodes.contains(nbr)) continue; // do not force among contexts
                    String from = c.getName(), to = nbr.getName();
                    if (knowledgeForbids(from, to) || knowledgeRequires(to, from)) {
                        continue; // skip if forbidden or opposite required
                    }
                    g.removeEdges(c, nbr);
                    g.addDirectedEdge(c, nbr);
                }
            }
        }

        // Freeze the post-forcing graph. All sepset machinery (RB hints, enumeration pools)
        // reads this snapshot, so sepsets are independent of triple visitation order and of
        // orientations added during step 3.
        this.sepsetGraph = new EdgeListGraph(g);

        // 3) UC orientation per style, restricted to triples BOSS left unresolved
        if (verbose) TetradLogger.getInstance().log("CD-NOD(BOSS): UC orientation on unresolved triples (" + colliderStyle + ")...");
        orientUnresolvedTriples(g);

        // 4) Meek closure
        if (verbose) TetradLogger.getInstance().log("CD-NOD(BOSS): Meek closure...");
        MeekRules meek = new MeekRules();
        meek.setKnowledge(knowledge);
        meek.orientImplied(g);

        return g;
    }

    /**
     * Context semantics: all Tier-0 variables in {@link Knowledge} are treated as contexts.
     * Any Tier-0 names not present in the DataSet are silently ignored (matches the PAG runner style).
     */
    private Set<Node> resolveContextNodesTier0(DataSet dataAll) {
        if (knowledge == null || knowledge.getTier(0).isEmpty()) return Collections.emptySet();
        Set<Node> out = new LinkedHashSet<>();
        List<String> tier0 = knowledge.getTier(0);
        for (String name : tier0) {
            Node v = dataAll.getVariable(name);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    // --- BOSS backbone (CPDAG over X ∪ Contexts) -------------------------------

    private Graph runBossBackbone(DataSet dataAug) throws InterruptedException {
        // 0) Prepare knowledge: forbid all edges into contexts.
        Knowledge K = new Knowledge(this.knowledge); // copy
        for (Node context : contextNodes) {
            for (Node v : dataAug.getVariables()) {
                if (v == context) continue;
                K.setForbidden(v.getName(), context.getName());
            }
        }

        // 1) Run BOSS with this knowledge, using the supplied score or the default SEM BIC.
        Score s = this.score;
        if (s == null) {
            SemBicScore bic = new SemBicScore(new CovarianceMatrix(dataAug));
            bic.setPenaltyDiscount(penaltyDiscount);
            s = bic;
        }
        Boss boss = new Boss(s);
        boss.setUseBes(useBes);
        boss.setNumStarts(numStarts);
        boss.setNumThreads(numThreads);
        boss.setUseDataOrder(useDataOrder);
        boss.setVerbose(verbose);

        PermutationSearch search = new PermutationSearch(boss);
        search.setKnowledge(K);
        search.setSeed(seed);

        return search.search();
    }

    // ------------- collider orientation on unresolved triples --------------

    /**
     * Applies the selected collider-orientation style to unshielded triples x --- z --- y in which
     * both edges at z are undirected in the current graph (i.e., triples BOSS left unresolved).
     * BOSS's orientations are authoritative and are never revisited here.
     */
    private void orientUnresolvedTriples(Graph g) throws InterruptedException {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        for (Node z : nodes) {
            List<Node> adj = new ArrayList<>(g.getAdjacentNodes(z));
            adj.sort(Comparator.comparing(Node::getName));

            for (int i = 0; i < adj.size(); i++) {
                Node x = adj.get(i);
                for (int j = i + 1; j < adj.size(); j++) {
                    Node y = adj.get(j);
                    if (g.isAdjacentTo(x, y)) continue;       // only unshielded
                    if (!unresolvedAtZ(g, x, z, y)) continue; // trust BOSS: both edges at z must be undirected

                    checkTimeout();

                    switch (colliderStyle) {
                        case SEPSETS -> {
                            Set<Node> s = getOrComputeSepset(x, y);
                            if (s != null && !s.contains(z) && canOrientCollider(g, x, z, y)) {
                                GraphUtils.orientCollider(g, x, z, y);
                                if (verbose)
                                    TetradLogger.getInstance().log("[SEPSETS] " + x + "->" + z + "<-" + y + " (S=" + labelSet(s) + ")");
                            }
                        }
                        case CONSERVATIVE -> {
                            ColliderOutcome out = judgeConservative(x, z, y);
                            if (out == ColliderOutcome.INDEPENDENT && canOrientCollider(g, x, z, y)) {
                                GraphUtils.orientCollider(g, x, z, y);
                                if (verbose) TetradLogger.getInstance().log("[CPC] " + x + "->" + z + "<-" + y);
                            }
                        }
                        case MAX_P -> {
                            MaxPDecision d = decideMaxP(x, z, y);
                            if (d.outcome == ColliderOutcome.INDEPENDENT && canOrientCollider(g, x, z, y)) {
                                GraphUtils.orientCollider(g, x, z, y);
                                if (verbose)
                                    TetradLogger.getInstance().log("[MAX-P] " + x + "->" + z + "<-" + y + " (p=" + d.bestP + ", S=" + labelSet(d.bestS) + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * True iff both edges x --- z and z --- y are undirected in g, i.e., BOSS (plus context forcing
     * and any colliders committed earlier in this pass) has taken no position at z on this triple.
     */
    private boolean unresolvedAtZ(Graph g, Node x, Node z, Node y) {
        Edge xz = g.getEdge(x, z);
        Edge zy = g.getEdge(z, y);
        return xz != null && zy != null && Edges.isUndirectedEdge(xz) && Edges.isUndirectedEdge(zy);
    }

    // ------------- sepset machinery (reads the frozen sepsetGraph) --------------

    private Set<Node> getOrComputeSepset(Node x, Node y) throws InterruptedException {
        if (id == null) indexNodes(sepsetGraph);
        long key = pairKey(x, y);

        Set<Node> S = sepsets.get(x, y);
        if (S != null || noSep.contains(key)) return S;

        // RB hint (bounded; skip if adjacent). RB is used only as a heuristic proposal here:
        // the proposed blocking set is validated by an actual CI test before acceptance, so the
        // question of RB's exact semantics on a CPDAG does not affect correctness, only hit rate.
        if (!sepsetGraph.isAdjacentTo(x, y)) {
            Set<Node> rb = RecursiveBlocking.blockPathsRecursively(
                    sepsetGraph, x, y, Set.of(), Set.of(), -1, -1, -1, 1, true).blockingSet();

            if (rb != null && (!excludeContextsFromS || Collections.disjoint(rb, contextNodes))) {
                IndependenceResult r = test.checkIndependence(x, y, rb);
                if (r.isIndependent()) {
                    sepsets.set(x, y, rb);
                    return rb;
                }
            }
        }

        // CI fallback (prefer smaller; tie by p)
        Set<Node> best = null;
        int bestSize = Integer.MAX_VALUE;
        double bestP = Double.NEGATIVE_INFINITY;
        for (SepCand c : enumerateSepsetsWithP(x, y)) {
            if (!c.indep) continue;
            int sz = c.S.size();
            if (sz < bestSize || (sz == bestSize && c.p > bestP)) {
                best = c.S;
                bestSize = sz;
                bestP = c.p;
                if (sz == 0) break;
            }
        }
        if (best != null) sepsets.set(x, y, best);
        else noSep.add(key);
        return best;
    }

    private long pairKey(Node a, Node b) {
        int ia = id.get(a), ib = id.get(b);
        int lo = TMath.min(ia, ib), hi = TMath.max(ia, ib);
        return (((long) lo) << 32) | (hi & 0xffffffffL);
    }

    private void indexNodes(Graph g) {
        id = new IdentityHashMap<>();
        int k = 0;
        for (Node v : g.getNodes()) id.put(v, k++);
    }

    // CPC: if any separating set S excludes z AND no separating set includes z -> collider.
    // if both kinds exist -> ambiguous; if only includes-z exist -> noncollider; if none -> no sepset.
    private ColliderOutcome judgeConservative(Node x, Node z, Node y) throws InterruptedException {
        boolean sawAny = false, sawIncl = false, sawExcl = false;

        for (SepCand c : enumerateSepsetsWithP(x, y)) {
            if (!c.indep) continue;
            sawAny = true;
            if (c.S.contains(z)) sawIncl = true;
            else sawExcl = true;
            if (sawIncl && sawExcl) return ColliderOutcome.AMBIGUOUS;
        }
        if (!sawAny) return ColliderOutcome.NO_SEPSET;
        if (sawExcl && !sawIncl) return ColliderOutcome.INDEPENDENT;
        if (sawIncl && !sawExcl) return ColliderOutcome.DEPENDENT;
        return ColliderOutcome.AMBIGUOUS;
    }

    // MAX-P: pick side (includes-z vs excludes-z) with strictly larger best p (by > margin). Else ambiguous.
    private MaxPDecision decideMaxP(Node x, Node z, Node y) throws InterruptedException {
        double bestIncl = Double.NEGATIVE_INFINITY;
        double bestExcl = Double.NEGATIVE_INFINITY;
        Set<Node> bestS_incl = Collections.emptySet();
        Set<Node> bestS_excl = Collections.emptySet();

        for (SepCand c : enumerateSepsetsWithP(x, y)) {
            if (!c.indep) continue;
            if (c.S.contains(z)) {
                if (c.p > bestIncl) {
                    bestIncl = c.p;
                    bestS_incl = c.S;
                }
            } else {
                if (c.p > bestExcl) {
                    bestExcl = c.p;
                    bestS_excl = c.S;
                }
            }
        }
        boolean hasIncl = bestIncl > Double.NEGATIVE_INFINITY;
        boolean hasExcl = bestExcl > Double.NEGATIVE_INFINITY;

        if (hasIncl && hasExcl) {
            if (bestExcl >= bestIncl + maxPMargin) {
                return new MaxPDecision(ColliderOutcome.INDEPENDENT, bestExcl, bestS_excl);
            } else if (bestIncl >= bestExcl + maxPMargin) {
                return new MaxPDecision(ColliderOutcome.DEPENDENT, bestIncl, bestS_incl);
            } else {
                return new MaxPDecision(ColliderOutcome.AMBIGUOUS, TMath.max(bestIncl, bestExcl),
                        (bestIncl >= bestExcl ? bestS_incl : bestS_excl));
            }
        } else if (hasExcl) {
            return new MaxPDecision(ColliderOutcome.INDEPENDENT, bestExcl, bestS_excl);
        } else if (hasIncl) {
            return new MaxPDecision(ColliderOutcome.DEPENDENT, bestIncl, bestS_incl);
        } else {
            return new MaxPDecision(ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());
        }
    }

    // enumerate candidate sepsets (unique by content), across both adjacency sides, up to depth cap.
    // Adjacency pools are taken from the frozen sepsetGraph. Contexts are excluded from the pools
    // iff excludeContextsFromS is set, so that all collider styles share a single conditioning universe.
    private Iterable<SepCand> enumerateSepsetsWithP(Node x, Node y) throws InterruptedException {
        Map<String, SepCand> uniq = new LinkedHashMap<>();

        List<Node> adjx = new ArrayList<>(sepsetGraph.getAdjacentNodes(x));
        List<Node> adjy = new ArrayList<>(sepsetGraph.getAdjacentNodes(y));
        adjx.remove(y);
        adjy.remove(x);

        if (excludeContextsFromS && contextNodes != null && !contextNodes.isEmpty()) {
            adjx.removeAll(contextNodes);
            adjy.removeAll(contextNodes);
        }

        adjx.sort(Comparator.comparing(Node::getName));
        adjy.sort(Comparator.comparing(Node::getName));

        int maxAdj = TMath.max(adjx.size(), adjy.size());
        int cap = (depth < 0 ? maxAdj : TMath.min(depth, maxAdj));

        for (int d = 0; d <= cap; d++) {
            List<List<Node>> both = new ArrayList<>();
            if (d <= adjx.size()) both.add(adjx);
            if (d <= adjy.size()) both.add(adjy);

            for (List<Node> adj : both) {
                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;
                while ((choice = gen.next()) != null) {
                    checkTimeout();
                    Set<Node> S = GraphUtils.asSet(choice, adj);
                    String key = setKey(S);
                    if (uniq.containsKey(key)) continue;

                    IndependenceResult r = test.checkIndependence(x, y, S);
                    uniq.put(key, new SepCand(S, r.isIndependent(), r.getPValue()));
                }
            }
        }
        return uniq.values();
    }

    // ------------- utils -------------

    private boolean canOrientCollider(Graph g, Node x, Node z, Node y) {
        if (!g.isAdjacentTo(x, z) || !g.isAdjacentTo(z, y)) return false;

        // Respect knowledge (forbids/requires + tiers)
        if (knowledge != null && !knowledge.isEmpty()) {
            if (knowledgeForbids(x.getName(), z.getName()) || knowledgeRequires(z.getName(), x.getName())) return false;
            if (knowledgeForbids(y.getName(), z.getName()) || knowledgeRequires(z.getName(), y.getName())) return false;
        }

        // Don’t create z->x or z->y conflicts
        return !g.isParentOf(z, x) && !g.isParentOf(z, y);
    }

    private boolean knowledgeForbids(String from, String to) {
        if (knowledge == null || knowledge.isEmpty()) return false;
        if (knowledge.isForbidden(from, to)) return true;
        return knowledge.isForbiddenByTiers(from, to);
    }

    private boolean knowledgeRequires(String from, String to) {
        if (knowledge == null || knowledge.isEmpty()) return false;
        return knowledge.isRequired(from, to);
    }

    private String labelSet(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        names.sort(NaturalSort.naturalComparator());
        return "{" + String.join(",", names) + "}";
    }

    private String setKey(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        names.sort(NaturalSort.naturalComparator());
        return String.join("\u0001", names);
    }

    private void checkTimeout() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
        if (timeoutMs >= 0) {
            long now = System.currentTimeMillis();
            if (now - startTimeMs > timeoutMs)
                throw new InterruptedException("Timed out after " + (now - startTimeMs) + " ms");
        }
    }

    /**
     * Enumeration representing different strategies for orienting colliders in causal discovery.
     */
    public enum ColliderOrientationStyle {
        /**
         * Orient based on separating sets derived from the data. Each pair of variables is analyzed to determine
         * whether a separating set exists to justify collider orientation.
         */
        SEPSETS,
        /**
         * Apply a conservative approach to collider orientation, favoring ambiguity when evidence for orientation is
         * inconclusive. This approach ensures orientational robustness under stricter constraints.
         */
        CONSERVATIVE,
        /**
         * Decide collider orientation by comparing the maximum p-value margins between two sides (includes given
         * variable vs excludes given variable). The decision is made based on which side has a strictly larger best
         * p-value over a specified threshold margin.
         */
        MAX_P
    }

    /**
     * Represents the possible outcomes when evaluating the presence of a collider in a causal graph under different
     * conditions or testing scenarios.
     * <p>
     * The enumerations are used in methods related to the identification and orientation of colliders during causal
     * structure learning, particularly within constraints-based or score-based search algorithms.
     */
    private enum ColliderOutcome {
        /**
         * Indicates that the nodes involved are conditionally independent and form a collider.
         */
        INDEPENDENT,
        /**
         * Indicates that the nodes involved are conditionally dependent under the given test.
         */
        DEPENDENT,
        /**
         * Indicates that the determination of a collider is uncertain due to conflicting evidence.
         */
        AMBIGUOUS,
        /**
         * Indicates that no valid separating set exists for the evaluated nodes within the given constraints.
         */
        NO_SEPSET
    }

    /**
     * Builder class for creating instances of the CdnodBoss class with customized parameters. The Builder provides a
     * flexible and fluent API for setting optional configurations in the resulting CdnodBoss instance.
     */
    public static final class Builder {
        private IndependenceTest test;
        private DataSet data;
        private DataSet dataX;
        private double[] cIndex;
        private String cName = "C";

        private Score score;                 // optional; SEM BIC by default
        private double penaltyDiscount = 1.0;
        private double alpha = 0.05;
        private ColliderOrientationStyle colliderStyle = ColliderOrientationStyle.SEPSETS;
        private Knowledge knowledge = new Knowledge();
        private boolean verbose = false;
        private double maxPMargin = 0.0;
        private int depth = -1;
        private boolean excludeContextsFromS = false;
        private boolean useBes = false;
        private int numStarts = 1;
        private int numThreads = 1;
        private boolean useDataOrder = true;
        private long seed = -1;

        /**
         * Constructs a new instance of the Builder class. Instantiates an object used for configuring and creating
         * instances of {@link CdnodBoss}.
         */
        public Builder() {
        }

        /**
         * Sets the {@link IndependenceTest} instance to be used by the {@code Builder}.
         *
         * @param t The {@link IndependenceTest} instance to be set. This parameter must not be null.
         * @return The current Builder instance for method chaining.
         * @throws NullPointerException If the provided {@link IndependenceTest} instance is null.
         */
        public Builder test(IndependenceTest t) {
            this.test = Objects.requireNonNull(t);
            return this;
        }

        /**
         * Provide the DataSet on which the search will be run. Contexts are determined from Knowledge
         * tier 0; their column positions are irrelevant.
         *
         * @param data The {@link DataSet} instance to be set. This parameter must not be null.
         * @return The current Builder instance for method chaining.
         */
        public Builder data(DataSet data) {
            this.data = data;
            return this;
        }

        /**
         * Provide X and a continuous change index C to append as the last column.
         *
         * @param dataX  The {@link DataSet} instance to be set. This parameter must not be null.
         * @param cIndex The continuous change index C to be appended. This parameter must not be null.
         * @param cName  The name of the continuous change index C. This parameter must not be null.
         * @return The current Builder instance for method chaining.
         */
        public Builder dataAndIndex(DataSet dataX, double[] cIndex, String cName) {
            this.dataX = dataX;
            this.cIndex = cIndex;
            if (cName != null && !cName.isBlank()) this.cName = cName;
            return this;
        }

        /**
         * Sets the {@link Score} to be used by the BOSS backbone. If not supplied, a
         * {@link SemBicScore} over the covariance matrix of the (augmented) dataset is
         * constructed, with the penalty discount set via {@link #penaltyDiscount(double)}.
         * Supply an appropriate score explicitly for non-Gaussian or mixed data.
         *
         * @param s The {@link Score} instance to be used, or null to use the default.
         * @return The current Builder instance for method chaining.
         */
        public Builder score(Score s) {
            this.score = s;
            return this;
        }

        /**
         * Sets the penalty discount used when the default SEM BIC score is constructed. Ignored
         * if a {@link Score} is supplied via {@link #score(Score)}.
         *
         * @param c The penalty discount; values less than or equal to 0 are clamped to 1.0.
         * @return The current Builder instance for method chaining.
         */
        public Builder penaltyDiscount(double c) {
            this.penaltyDiscount = (c <= 0 ? 1.0 : c);
            return this;
        }

        /**
         * Sets the significance level recorded for this search.
         *
         * <p><b>Note:</b> the CI decisions in collider orientation are made by the supplied
         * {@link IndependenceTest}, which carries its own alpha. Configure the test's alpha
         * directly; this value is retained for reporting/parity only.</p>
         *
         * @param a The significance level (alpha).
         * @return The current Builder instance for method chaining.
         */
        public Builder alpha(double a) {
            this.alpha = a;
            return this;
        }

        /**
         * Formerly configured FAS's stable adjacency search. CdnodBoss uses BOSS rather than FAS,
         * so this setting has no effect.
         *
         * @param s Ignored.
         * @return The current Builder instance for method chaining.
         * @deprecated This parameter is unused in CdnodBoss and will be removed.
         */
        @Deprecated
        public Builder stable(boolean s) {
            return this;
        }

        /**
         * Sets the {@link ColliderOrientationStyle} to be used by the {@code Builder}. The
         * {@link ColliderOrientationStyle} determines the strategy for orienting colliders in causal discovery, such as
         * separating sets, conservative approaches, or using maximum p-value margins.
         *
         * @param c The {@link ColliderOrientationStyle} to be set. This parameter must not be null.
         * @return The current {@code Builder} instance for method chaining.
         * @throws NullPointerException If the provided {@link ColliderOrientationStyle} is null.
         */
        public Builder colliderStyle(ColliderOrientationStyle c) {
            this.colliderStyle = c;
            return this;
        }

        /**
         * Sets the {@link Knowledge} instance to be used by the {@code Builder}. If the provided {@link Knowledge}
         * instance is null, a new instance of {@link Knowledge} is created.
         *
         * @param k The {@link Knowledge} instance to be set. This parameter can be null.
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder knowledge(Knowledge k) {
            this.knowledge = (k == null ? new Knowledge() : new Knowledge(k));
            return this;
        }

        /**
         * Configures whether the builder operates in verbose mode. When enabled, verbose mode may produce more detailed
         * logs, messages, or debug outputs during the building process, depending on the specific implementation of the
         * builder or the constructed object.
         *
         * @param v A boolean indicating whether verbose mode should be enabled (true) or disabled (false).
         * @return The current Builder instance for method chaining.
         */
        public Builder verbose(boolean v) {
            this.verbose = v;
            return this;
        }

        /**
         * Sets the maximum p-value margin to be used by the {@code Builder}. If the provided value is negative, it is
         * set to 0.0.
         *
         * @param m The maximum p-value margin to be set. This parameter must be a non-negative double.
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder maxPMargin(double m) {
            this.maxPMargin = TMath.max(0.0, m);
            return this;
        }

        /**
         * Sets the maximum size of conditioning sets considered during sepset enumeration in the
         * collider-orientation step. A value of -1 (the default) means unbounded: conditioning
         * sets are limited only by the sizes of the relevant adjacency sets.
         *
         * @param d The depth value to be set; -1 for unbounded.
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder depth(int d) {
            this.depth = d;
            return this;
        }

        /**
         * Configures whether BOSS runs a BES step after each permutation pass.
         *
         * @param on true to enable BES; default false.
         * @return The current Builder instance for method chaining.
         */
        public Builder useBes(boolean on) {
            this.useBes = on;
            return this;
        }

        /**
         * Sets the number of random restarts for BOSS. Values less than 1 are clamped to 1.
         *
         * @param n The number of starts; default 1.
         * @return The current Builder instance for method chaining.
         */
        public Builder numStarts(int n) {
            this.numStarts = TMath.max(1, n);
            return this;
        }

        /**
         * Sets the number of threads BOSS may use. Values less than 1 are clamped to 1.
         *
         * @param n The number of threads; default 1.
         * @return The current Builder instance for method chaining.
         */
        public Builder numThreads(int n) {
            this.numThreads = TMath.max(1, n);
            return this;
        }

        /**
         * Configures whether BOSS uses the data order for its initial permutation.
         *
         * @param on true to use the data order; default true.
         * @return The current Builder instance for method chaining.
         */
        public Builder useDataOrder(boolean on) {
            this.useDataOrder = on;
            return this;
        }

        /**
         * Sets the random seed passed to the permutation search; -1 (the default) means no fixed seed.
         *
         * @param seed The seed value.
         * @return The current Builder instance for method chaining.
         */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Configures whether context variables are excluded from conditioning sets during collider
         * orientation. The default is {@code false} (contexts admissible), consistent with Huang et
         * al. (2020), where conditioning on the context removes pseudo-confounding between variables
         * whose mechanisms both change. Set to {@code true} to mirror the CD-NOD-PAG runner; the
         * exclusion is then applied uniformly, including to RecursiveBlocking hints.
         *
         * @param on true to exclude contexts from conditioning sets; false to admit them.
         * @return The current Builder instance for method chaining.
         */
        public Builder excludeContextsFromS(boolean on) {
            this.excludeContextsFromS = on;
            return this;
        }

        /**
         * Backwards-compatible helper: append a single continuous change-index column as the last column.
         *
         * @param dataX  the original dataset to which the change-index column will be appended
         * @param cIndex the array of continuous change-index values to be added
         * @param cName  the name of the new change-index column
         * @return a new dataset with the change-index column appended
         */
        public static DataSet appendChangeIndexAsLastColumn(DataSet dataX, double[] cIndex, String cName) {
            return CdnodBoss.appendChangeIndexAsLastColumn(dataX, cIndex, cName);
        }

        /**
         * Builds and returns an instance of {@link CdnodBoss} using the parameters specified in the {@code Builder}. The
         * method constructs the {@link CdnodBoss} object based on the provided or default configurations, ensuring that all
         * required parameters have been properly initialized.
         *
         * @return A newly constructed {@link CdnodBoss} instance.
         * @throws IllegalStateException If the {@link IndependenceTest} is not set before invoking this method.
         */
        public CdnodBoss build() {
            if (test == null) throw new IllegalStateException("IndependenceTest must be provided.");
            DataSet working = data;
            if (working == null && dataX != null && cIndex != null) {
                working = appendChangeIndexAsLastColumn(dataX, cIndex, cName);
            }
            return new CdnodBoss(test, working, score, penaltyDiscount, alpha, colliderStyle, knowledge, verbose,
                    maxPMargin, depth, excludeContextsFromS, useBes, numStarts, numThreads, useDataOrder, seed);
        }
    }

    private static final class SepCand {
        final Set<Node> S;
        final boolean indep;
        final double p;

        SepCand(Set<Node> s, boolean indep, double p) {
            List<Node> sorted = new ArrayList<>(s);
            sorted.sort(Comparator.comparing(Node::getName));
            this.S = new LinkedHashSet<>(sorted);
            this.indep = indep;
            this.p = p;
        }
    }

    private static final class MaxPDecision {
        final ColliderOutcome outcome;
        final double bestP;
        final Set<Node> bestS;

        MaxPDecision(ColliderOutcome out, double bestP, Set<Node> bestS) {
            this.outcome = out;
            this.bestP = bestP;
            this.bestS = bestS;
        }
    }
}
