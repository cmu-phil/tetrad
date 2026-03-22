package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Java port of FindCausalClusters (Algorithm 3), NoCollider (Algorithm 4),
 * and RefineCausalClusters (Algorithm 5) from:
 *
 *   Dong et al., "A Versatile Causal Discovery Framework to Allow
 *   Causally-Related Hidden Variables", ICLR 2024.
 *
 * <h3>Cover representation</h3>
 * A <em>cover</em> is a {@code List<Integer>} of working-node indices, kept in
 * sorted order.  The <em>active set</em> S is a {@code List<Cover>}.
 * Effective cardinality {@code ||S||} = |∪_{C∈S} C| (distinct indices in union).
 *
 * <h3>Thread safety</h3>
 * Not thread-safe; each call to {@link #runLocalSearch} operates on its own
 * mutable state.
 */
final class LatentGroupsSearch {

    private LatentGroupsSearch() {}

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Runs Phases 2 and 3 for a single clique group.
     *
     * @param dataSet      full dataset (observed variables only)
     * @param group        observed variables in the clique group (XQ)
     * @param neighbourSet neighbours of the group in the CI skeleton (NQ)
     * @param localAdj     adjacency sub-matrix over {@code group} variables
     * @param params       RLCD parameters
     * @return adjacency over [groupVars | new latents]
     */
    static LatentGroupsResult runLocalSearch(DataSet dataSet,
                                             List<Node> group,
                                             Set<Node> neighbourSet,
                                             int[][] localAdj,
                                             RLCDParams params) {
        if (params.getRankTestFactory() == null) {
            return new LatentGroupsResult(localAdj);
        }

        // Working node list: group first, then neighbours.
        // New latent nodes are appended as they are discovered.
        List<Node> workingNodes = new ArrayList<>(group);
        for (Node nb : neighbourSet) {
            if (!workingNodes.contains(nb)) workingNodes.add(nb);
        }
        int nObs      = workingNodes.size();
        int groupSize = group.size();

        // Node → DataSet column index (only observed nodes will be found here).
        Map<Node, Integer> nodeToDataCol = new HashMap<>();
        List<Node> dataVars = dataSet.getVariables();
        for (int i = 0; i < dataVars.size(); i++) nodeToDataCol.put(dataVars.get(i), i);

        // Observed working-node indices (grows only if latents are re-observed, which
        // shouldn't happen, but the set makes the latent check O(1)).
        Set<Integer> observedIdx = new HashSet<>();
        for (int i = 0; i < nObs; i++) observedIdx.add(i);

        // Adjacency matrix – preallocate with headroom for discovered latents.
        int cap = nObs + 30;
        int[][] adj = new int[cap][cap];
        for (int i = 0; i < Math.min(groupSize, localAdj.length); i++)
            for (int j = 0; j < Math.min(groupSize, localAdj[i].length); j++)
                adj[i][j] = localAdj[i][j];

        // Pure-children registry: cover (sorted List<Integer>) → list of child covers.
        // Populated as atomic covers are discovered.
        Map<List<Integer>, List<List<Integer>>> pureChildrenOf = new HashMap<>();

        // Active set S – initially one singleton per group variable (Algorithm 3, line 2).
        List<List<Integer>> S = new ArrayList<>();
        for (int i = 0; i < groupSize; i++) S.add(singleton(i));

        RankTest rankTest = params.getRankTestFactory().create(dataSet);
        double[] alphaByK = params.getAlphaByK();
        int      maxK     = params.getMaxK();

        // ---- Phase 2: FindCausalClusters (Algorithm 3) ----
        findCausalClusters(S, adj, workingNodes, observedIdx, pureChildrenOf,
                nodeToDataCol, rankTest, alphaByK, maxK, groupSize, params);

        // ---- Phase 3: RefineCausalClusters (Algorithm 5) ----
        if (params.getStages() >= 3) {
            refineCausalClusters(S, adj, workingNodes, observedIdx, pureChildrenOf,
                    nodeToDataCol, rankTest, alphaByK, maxK, groupSize, params);
        }

        // Assemble result adjacency over [groupVars | new latents].
        int numLatent = workingNodes.size() - nObs;
        int sz = groupSize + numLatent;
        int[][] result = new int[sz][sz];
        for (int i = 0; i < groupSize; i++)
            System.arraycopy(adj[i], 0, result[i], 0, groupSize);
        for (int li = 0; li < numLatent; li++) {
            int liW = nObs + li;
            int liR = groupSize + li;
            for (int j = 0; j < groupSize; j++) {
                result[liR][j] = adj[liW][j];
                result[j][liR] = adj[j][liW];
            }
            for (int lj = 0; lj < numLatent; lj++)
                result[liR][groupSize + lj] = adj[liW][nObs + lj];
        }
        return new LatentGroupsResult(result);
    }

    // -----------------------------------------------------------------------
    // Algorithm 3 – FindCausalClusters
    // -----------------------------------------------------------------------

    private static void findCausalClusters(List<List<Integer>> S,
                                           int[][] adj,
                                           List<Node> workingNodes,
                                           Set<Integer> observedIdx,
                                           Map<List<Integer>, List<List<Integer>>> pureChildrenOf,
                                           Map<Node, Integer> nodeToDataCol,
                                           RankTest rankTest,
                                           double[] alphaByK,
                                           int maxK,
                                           int groupSize,
                                           RLCDParams params) {
        int k = 1;
        while (k <= maxK) {
            boolean found = search(S, adj, workingNodes, observedIdx, pureChildrenOf,
                    nodeToDataCol, rankTest, alphaByK, k, groupSize, params);
            k = found ? 1 : k + 1;
        }
    }

    /**
     * Inner Search function (Algorithm 3, lines 8-27).
     * Returns true iff a k-cluster was found and the graph was updated.
     */
    private static boolean search(List<List<Integer>> S,
                                  int[][] adj,
                                  List<Node> workingNodes,
                                  Set<Integer> observedIdx,
                                  Map<List<Integer>, List<List<Integer>>> pureChildrenOf,
                                  Map<Node, Integer> nodeToDataCol,
                                  RankTest rankTest,
                                  double[] alphaByK,
                                  int k,
                                  int groupSize,
                                  RLCDParams params) {

        double alpha = alphaForK(alphaByK, k);
        int sSize = S.size();

        // Guard: 2^sSize combinations is the unfolding cost; keep tractable.
        int unfoldLimit = params.isUnfoldCovers() ? Math.min(sSize, 16) : 0;

        // Outer loop: enumerate subsets T of S for unfolding (lines 10-11).
        for (int tMask = (1 << unfoldLimit) - 1; tMask >= 0; tMask--) {
            List<List<Integer>> sPrime = unfold(S, tMask, pureChildrenOf, params);
            if (sPrime.isEmpty()) continue;

            // Inner loop: t = number of observed singleton covers going into X.
            for (int t = k; t >= 0; t--) {
                // Positions of observed singleton covers in S'.
                List<Integer> obsSingletonPos = new ArrayList<>();
                for (int i = 0; i < sPrime.size(); i++) {
                    if (isObsSingleton(sPrime.get(i), observedIdx)) obsSingletonPos.add(i);
                }
                if (obsSingletonPos.size() < t) continue;

                // Enumerate t-element subsets of observed singletons → X.
                for (int[] xChoice : subsets(obsSingletonPos.size(), t)) {
                    Set<Integer> xPos = new HashSet<>();
                    List<List<Integer>> X = new ArrayList<>();
                    for (int xi : xChoice) {
                        int pos = obsSingletonPos.get(xi);
                        xPos.add(pos);
                        X.add(sPrime.get(pos));
                    }

                    // Remaining covers = S' \ X.
                    List<List<Integer>> rem = new ArrayList<>();
                    for (int i = 0; i < sPrime.size(); i++) {
                        if (!xPos.contains(i)) rem.add(sPrime.get(i));
                    }

                    // Target effective cardinality for C: k - t + 1  (Algorithm 3, line 16).
                    int targetEffCard = k - t + 1;
                    if (targetEffCard < 1) continue;

                    // Enumerate C ⊆ rem with ||C|| = targetEffCard.
                    for (List<Integer> cIdxs : subsetsWithEffCard(rem, targetEffCard)) {
                        List<List<Integer>> C = new ArrayList<>();
                        Set<Integer> cPosSet = new HashSet<>(cIdxs);
                        for (int ci : cIdxs) C.add(rem.get(ci));

                        // N = rem \ C  (Algorithm 3, line 16).
                        List<List<Integer>> N = new ArrayList<>();
                        for (int i = 0; i < rem.size(); i++) {
                            if (!cPosSet.contains(i)) N.add(rem.get(i));
                        }

                        // Rank-deficiency test: rank(Σ_{C∪X, N∪X}) = k  (line 17).
                        int[] lCols = toCols(union(C, X), workingNodes, nodeToDataCol, pureChildrenOf);
                        int[] rCols = toCols(union(N, X), workingNodes, nodeToDataCol, pureChildrenOf);

                        if (lCols.length == 0 || rCols.length == 0) continue;
                        // Deficiency requires min(|left|, |right|) > k.
                        if (lCols.length <= k || rCols.length <= k) continue;

                        // rank ≤ k  AND  rank > k-1  (i.e., rank exactly k).
                        boolean rankOk = rankTest.test(lCols, rCols, k, alpha)
                                && (k == 0 || !rankTest.test(lCols, rCols, k - 1, alpha));

                        if (!rankOk) continue;

                        // NoCollider check (Algorithm 4) – line 17.
                        if (params.isCheckVStructures()
                                && !noCollider(C, X, N, workingNodes, nodeToDataCol,
                                pureChildrenOf, rankTest, alpha)) {
                            continue;
                        }

                        // Found a k-cluster – update graph and active set (lines 19-25).
                        applyCluster(C, X, S, adj, workingNodes, observedIdx,
                                pureChildrenOf, k, groupSize);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Algorithm 4 – NoCollider
    // -----------------------------------------------------------------------

    /**
     * Returns {@code false} if any element of C is a collider of C\{O} and N,
     * i.e. if rank(Σ_{C'∪X, N∪X}) &lt; ||C' ∪ X|| for some C' ⊂ C.
     */
    private static boolean noCollider(List<List<Integer>> C,
                                      List<List<Integer>> X,
                                      List<List<Integer>> N,
                                      List<Node> workingNodes,
                                      Map<Node, Integer> nodeToDataCol,
                                      Map<List<Integer>, List<List<Integer>>> pureChildrenOf,
                                      RankTest rankTest,
                                      double alpha) {
        // Pre-compute right-hand side columns (N ∪ X) – same for all C'.
        int[] rCols = toCols(union(N, X), workingNodes, nodeToDataCol, pureChildrenOf);

        for (int c = 1; c < C.size(); c++) {
            for (int[] cPrimeIdx : subsets(C.size(), c)) {
                List<List<Integer>> cPrime = new ArrayList<>();
                for (int ci : cPrimeIdx) cPrime.add(C.get(ci));

                List<List<Integer>> cPrimeUnionX = union(cPrime, X);
                int[] lCols   = toCols(cPrimeUnionX, workingNodes, nodeToDataCol, pureChildrenOf);
                int   effCard = effectiveCardinality(cPrimeUnionX);

                if (lCols.length == 0 || effCard == 0) continue;

                // rank < effCard  ⟺  rank ≤ effCard - 1  (Algorithm 4, line 5).
                if (rankTest.test(lCols, rCols, effCard - 1, alpha)) return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Algorithm 5 – RefineCausalClusters
    // -----------------------------------------------------------------------

    /**
     * For each atomic cover V that contains a latent node, delete V and its latent
     * neighbours from G', then re-run FindCausalClusters on the reduced graph.
     */
    private static void refineCausalClusters(List<List<Integer>> S,
                                             int[][] adj,
                                             List<Node> workingNodes,
                                             Set<Integer> observedIdx,
                                             Map<List<Integer>, List<List<Integer>>> pureChildrenOf,
                                             Map<Node, Integer> nodeToDataCol,
                                             RankTest rankTest,
                                             double[] alphaByK,
                                             int maxK,
                                             int groupSize,
                                             RLCDParams params) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (List<Integer> V : new ArrayList<>(S)) {
                // Only process covers that contain at least one latent node.
                boolean hasLatent = V.stream().anyMatch(i -> !observedIdx.contains(i));
                if (!hasLatent) continue;

                // Nodes to exclude: V itself + latent neighbours of V in adj.
                Set<Integer> toExclude = new HashSet<>(V);
                for (int vi : V) {
                    for (int i = 0; i < workingNodes.size(); i++) {
                        if (!observedIdx.contains(i) && adj[vi][i] != 0) toExclude.add(i);
                    }
                }

                // Reduced S: drop any cover that overlaps with toExclude.
                List<List<Integer>> reducedS = new ArrayList<>();
                for (List<Integer> cov : S) {
                    if (cov.stream().noneMatch(toExclude::contains)) reducedS.add(cov);
                }

                int prevNodeCount = workingNodes.size();
                findCausalClusters(reducedS, adj, workingNodes, observedIdx,
                        pureChildrenOf, nodeToDataCol, rankTest, alphaByK, maxK,
                        groupSize, params);

                if (workingNodes.size() != prevNodeCount) {
                    // New latents were discovered: rebuild S from the reduced set
                    // (which has been updated in-place by findCausalClusters) and restart.
                    S.clear();
                    S.addAll(reducedS);
                    changed = true;
                    break;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Graph / active-set update when a k-cluster is found
    // -----------------------------------------------------------------------

    /**
     * Called after a rank-deficiency hit.  Adds any required latent nodes,
     * wires up parent → child edges in {@code adj}, registers pure children,
     * and updates the active set S.
     */
    private static void applyCluster(List<List<Integer>> C,
                                     List<List<Integer>> X,
                                     List<List<Integer>> S,
                                     int[][] adj,
                                     List<Node> workingNodes,
                                     Set<Integer> observedIdx,
                                     Map<List<Integer>, List<List<Integer>>> pureChildrenOf,
                                     int k,
                                     int groupSize) {
        // Parent cover P = existing observed parents (X) + fresh latent nodes.
        List<Integer> parentCover = new ArrayList<>();
        for (List<Integer> xCov : X) parentCover.addAll(xCov);

        int numNewLatent = k - parentCover.size();
        for (int i = 0; i < numNewLatent; i++) {
            int newIdx = workingNodes.size();
            Node latent = new GraphNode("L_" + (newIdx + 1));
            latent.setNodeType(NodeType.LATENT);
            workingNodes.add(latent);
            parentCover.add(newIdx);
        }
        Collections.sort(parentCover);

        // Children: every variable that appears in a cover in C.
        List<Integer> childNodes = new ArrayList<>();
        for (List<Integer> cov : C) childNodes.addAll(cov);

        // Wire adj: parent → child.
        for (int p : parentCover) {
            for (int c : childNodes) {
                if (p < adj.length && c < adj.length) adj[p][c] = 1;
            }
        }

        // Register pure children (used by unfold and toCols).
        pureChildrenOf.put(new ArrayList<>(parentCover), new ArrayList<>(C));

        // Update S: remove child covers, add parent cover (Algorithm 3, line 24).
        S.removeAll(C);
        if (!S.contains(parentCover)) S.add(new ArrayList<>(parentCover));
    }

    // -----------------------------------------------------------------------
    // Helpers – covers
    // -----------------------------------------------------------------------

    /**
     * Unfolds S according to the bitmask tMask:
     * S' = (S \ T) ∪ (∪_{T∈T} PChG'(T))   (Algorithm 3, lines 10-11).
     */
    private static List<List<Integer>> unfold(List<List<Integer>> S,
                                              int tMask,
                                              Map<List<Integer>, List<List<Integer>>> pureChildrenOf,
                                              RLCDParams params) {
        List<List<Integer>> sPrime = new ArrayList<>();
        for (int i = 0; i < S.size(); i++) {
            if (params.isUnfoldCovers() && (tMask & (1 << i)) != 0) {
                List<List<Integer>> ch = pureChildrenOf.getOrDefault(S.get(i), Collections.emptyList());
                sPrime.addAll(ch);
            } else {
                sPrime.add(S.get(i));
            }
        }
        return sPrime;
    }

    /**
     * Converts a collection of covers to a deduplicated array of DataSet column indices.
     * Latent nodes are replaced by the observed data columns of their pure-child covers
     * (Theorem 6 from the paper).
     */
    private static int[] toCols(List<List<Integer>> covers,
                                List<Node> workingNodes,
                                Map<Node, Integer> nodeToDataCol,
                                Map<List<Integer>, List<List<Integer>>> pureChildrenOf) {
        Set<Integer> cols = new LinkedHashSet<>();
        for (List<Integer> cov : covers)
            for (int nodeIdx : cov)
                resolveToDataCols(nodeIdx, workingNodes, nodeToDataCol, pureChildrenOf, cols, 0);
        return cols.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Recursively resolves a single working-node index to observed DataSet columns.
     * For observed nodes this is a direct lookup; for latent nodes we recurse into
     * their pure children (Theorem 6).
     */
    private static void resolveToDataCols(int nodeIdx,
                                          List<Node> workingNodes,
                                          Map<Node, Integer> nodeToDataCol,
                                          Map<List<Integer>, List<List<Integer>>> pureChildrenOf,
                                          Set<Integer> out,
                                          int depth) {
        if (depth > 30 || nodeIdx < 0 || nodeIdx >= workingNodes.size()) return;
        Integer col = nodeToDataCol.get(workingNodes.get(nodeIdx));
        if (col != null) {
            out.add(col);   // observed node: direct lookup
            return;
        }
        // Latent node: find its pure children from the registry and recurse.
        for (Map.Entry<List<Integer>, List<List<Integer>>> e : pureChildrenOf.entrySet()) {
            if (e.getKey().contains(nodeIdx)) {
                for (List<Integer> childCov : e.getValue())
                    for (int ch : childCov)
                        resolveToDataCols(ch, workingNodes, nodeToDataCol, pureChildrenOf, out, depth + 1);
            }
        }
    }

    /** Effective cardinality: |∪_{C ∈ covers} C|. */
    private static int effectiveCardinality(List<List<Integer>> covers) {
        Set<Integer> all = new HashSet<>();
        for (List<Integer> cov : covers) all.addAll(cov);
        return all.size();
    }

    /** Union of two cover lists without duplicating identical cover objects. */
    private static List<List<Integer>> union(List<List<Integer>> a, List<List<Integer>> b) {
        List<List<Integer>> result = new ArrayList<>(a);
        for (List<Integer> cov : b) if (!result.contains(cov)) result.add(cov);
        return result;
    }

    private static boolean isObsSingleton(List<Integer> cov, Set<Integer> observedIdx) {
        return cov.size() == 1 && observedIdx.contains(cov.get(0));
    }

    private static List<Integer> singleton(int idx) {
        return Collections.singletonList(idx);
    }

    // -----------------------------------------------------------------------
    // Helpers – combinatorics
    // -----------------------------------------------------------------------

    /** All r-element subsets of {0, …, n-1}, as int[] arrays of positions. */
    private static List<int[]> subsets(int n, int r) {
        List<int[]> out = new ArrayList<>();
        if (r == 0) { out.add(new int[0]); return out; }
        if (r > n)  return out;
        subsetsRec(n, r, 0, new int[r], 0, out);
        return out;
    }

    private static void subsetsRec(int n, int r, int start, int[] buf, int depth, List<int[]> out) {
        if (depth == r) { out.add(buf.clone()); return; }
        for (int i = start; i <= n - r + depth; i++) {
            buf[depth] = i;
            subsetsRec(n, r, i + 1, buf, depth + 1, out);
        }
    }

    /**
     * All subsets of {@code covers} (returned as lists of position indices) whose
     * effective cardinality equals {@code targetEffCard}.
     * Uses power-set enumeration; practical because cover lists are small.
     */
    private static List<List<Integer>> subsetsWithEffCard(List<List<Integer>> covers,
                                                          int targetEffCard) {
        List<List<Integer>> out = new ArrayList<>();
        int n = covers.size();
        // Limit to avoid exponential blow-up in degenerate cases.
        if (n > 20) return out;
        for (int mask = 1; mask < (1 << n); mask++) {
            List<Integer> positions = new ArrayList<>();
            List<List<Integer>> selected = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    positions.add(i);
                    selected.add(covers.get(i));
                }
            }
            if (effectiveCardinality(selected) == targetEffCard) out.add(positions);
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // Helpers – misc
    // -----------------------------------------------------------------------

    /** Look up alpha for rank k, clamping to the last entry if k is out of range. */
    private static double alphaForK(double[] alphaByK, int k) {
        if (alphaByK == null || alphaByK.length == 0) return 0.01;
        int idx = Math.max(0, Math.min(k, alphaByK.length - 1));
        return alphaByK[idx];
    }
}