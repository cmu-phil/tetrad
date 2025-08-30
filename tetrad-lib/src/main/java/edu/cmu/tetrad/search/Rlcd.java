package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Rlcd {

    private final DataSet data;
    // Debug flag and helpers
    private static boolean DEBUG = false;
    public static void setDebug(boolean on) { DEBUG = on; }

    public Rlcd(DataSet data) {
        this.data = data;
    }

    /**
     * End-to-end convenience: run minimal RLCD Phase-2 on the full observed scope and
     * return a Graph with one latent per discovered cluster (latent → observed edges).
     * NOTE: This is a minimal estimator that skips Phase-1 scoping and Phase-3 refinement.
     */
    public Graph search(double alpha) {
        if (data == null) throw new IllegalStateException("Rlcd constructed without DataSet");

        java.util.List<Node> scope = data.getVariables();

        List<Node> myScope = new ArrayList<>();
        myScope.addAll(scope);

        java.util.List<int[]> clusters = runPhase2FromDataSet(data, myScope, alpha);

        EdgeListGraph g = new EdgeListGraph(scope);

        int li = 1;
        for (int[] P : clusters) {
            String lname = "L" + (li++);
            GraphNode L = new GraphNode(lname);
            try { L.setNodeType(NodeType.LATENT); } catch (Throwable ignore) { /* older Tetrad */ }
            g.addNode(L);
            for (int idx : P) {
                Node child = myScope.get(idx);
                if (g.getNode(child.getName()) == null) g.addNode(child);
                g.addDirectedEdge(L, child);
            }
        }
        return g;
    }

    /**
     * Return a sorted-unique copy of indices.
     */
    static int[] sortedUnique(int[] idx) {
        int[] c = java.util.Arrays.copyOf(idx, idx.length);
        java.util.Arrays.sort(c);
        int w = 0;
        for (int i = 0; i < c.length; i++) if (i == 0 || c[i] != c[i - 1]) c[w++] = c[i];
        return java.util.Arrays.copyOf(c, w);
    }

    // Debug string helpers
    static String arrToString(int[] a) {
        if (a == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        return sb.append(']').toString();
    }
    static String coversToString(java.util.List<int[]> covers) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < covers.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(arrToString(covers.get(i)));
        }
        return sb.append(']').toString();
    }

    /**
     * Canonical string key for a cover (sorted-unique indices joined by ',').
     */
    static String coverKey(int[] cover) {
        int[] c = java.util.Arrays.copyOf(cover, cover.length);
        java.util.Arrays.sort(c);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.length; i++) { if (i > 0) sb.append(','); sb.append(c[i]); }
        return sb.toString();
    }

    // Holder for Phase-1 outputs (skeleton + sepsets over observed)
    static class Phase1Result {
        final Graph skeleton;
        final java.util.Map<String, int[]> sepsets; // key "i:j" over observed indices (i<j), value = separating set indices
        final java.util.List<Node> vars;            // observed variable order used
        Phase1Result(Graph skeleton, java.util.Map<String, int[]> sepsets, java.util.List<Node> vars) {
            this.skeleton = skeleton; this.sepsets = sepsets; this.vars = vars;
        }
    }

    /**
     * Return true iff a ⊆ b (indices treated as sets).
     */
    static boolean isSubset(int[] a, int[] b) {
        int[] aa = sortedUnique(a);
        int[] bb = sortedUnique(b);
        int i = 0, j = 0;
        while (i < aa.length && j < bb.length) {
            if (aa[i] == bb[j]) { i++; j++; }
            else if (aa[i] > bb[j]) j++;
            else return false; // aa[i] not found in bb
        }
        return i == aa.length;
    }

    /**
     * Run the minimal Phase-2 loop on a given scope, discovering clusters as index arrays. This version treats all
     * C-covers selected at acceptance as the set of pure children D, forms P = (⋃C) ∪ X, removes D from S, and inserts
     * P back into S. It returns every accepted P (one per acceptance) in discovery order.
     */
    public static java.util.List<int[]> runPhase2OverScope(SimpleMatrix S,
                                                           int[] scopeObserved,
                                                           int n,
                                                           double alpha) {
        // Active set S starts as singleton covers of the scope
        final java.util.List<int[]> S_active = buildSingletonCovers(scopeObserved);

        // For observed-cover filtering
        final int[] XG_flat = sortedUnique(scopeObserved);

        // Collected discovered clusters P (as int[] indices)
        final java.util.ArrayList<int[]> discovered = new java.util.ArrayList<>();

        // Maintain a set of already-accepted P covers (by canonical key)
        final java.util.HashSet<String> seenP = new java.util.HashSet<>();

        // Hook: no unfolding of pure children in this minimal variant
        java.util.function.Function<int[], java.util.List<int[]>> pchHook = cover -> java.util.Collections.emptyList();

        // Update hook: implement Alg. 3 lines 21–24 with duplicate suppression and atomicity
        java.util.function.BiConsumer<java.util.List<int[]>, java.util.List<int[]>> update = (Xcovers, Ccovers) -> {
            // D := Ccovers (minimal assumption)
            java.util.List<int[]> D = Ccovers;

            // P := (⋃C) ∪ X
            int[] P = RankTests.union(unionAll(Ccovers), flatten(Xcovers));
            String key = coverKey(P);
            if (DEBUG) System.out.println("ACCEPT P=" + arrToString(P) + ", from X=" + coversToString(Xcovers) + ", C=" + coversToString(Ccovers));

            // 1) Duplicate suppression: if we’ve already accepted this exact P, skip.
            if (seenP.contains(key)) return;

            // 2) Atomicity check: ensure no existing cover in S_active is a strict subset of P.
            for (int[] c : S_active) {
                if (c.length < P.length && isSubset(c, P)) {
                    // remove strict-subset covers; they’re subsumed by P
                }
            }
            java.util.List<int[]> newS = new java.util.ArrayList<>();
            for (int[] c : S_active) {
                // Drop any strict subset of P and everything in D
                boolean drop = false;
                if (isSubset(c, P) && c.length < P.length) drop = true;
                for (int[] d : D) if (java.util.Arrays.equals(c, d)) { drop = true; break; }
                if (!drop) newS.add(c);
            }

            // Add P and record discovery
            newS.add(P);
            S_active.clear();
            S_active.addAll(newS);
            discovered.add(P);
            seenP.add(key);
        };

        // Main outer loop over k
        int k = 1;
        int maxIter = 1 + scopeObserved.length; // simple safety cap
        for (int iter = 0; iter < maxIter; iter++) {
            boolean found = phase2Search(S, S_active, XG_flat, k, n, alpha, pchHook, update);
            if (found) {
                k = 1; // reset after acceptance
            } else {
                k++;   // try larger k
                // stop if k exceeds current S_active effective size
                int eff = 0;
                for (int[] c : S_active) eff += c.length; // rough upper bound
                if (k > Math.max(1, eff)) break;
            }
        }

        return discovered;
    }


    static boolean noCollider(SimpleMatrix S,
                              int[] X,
                              List<int[]> Ccovers,
                              int[] N,
                              int n,
                              double alpha) {
        // Implements Alg. 4 (NoCollider) from RLCD:
        // For every non-empty proper subset C' of C, check whether
        //   rank(Σ_{C'∪X, N∪X}) < ||C' ∪ X||
        // If any such subset triggers deficiency, return false; else true.

        final int m = Ccovers == null ? 0 : Ccovers.size();
        if (m <= 1) return true; // no non-empty proper subset to invalidate

        // Precompute N ∪ X for the RHS block once
        final int[] NunionX = RankTests.union(N, X);

        // Enumerate all non-empty proper subsets of Ccovers via bitmasks
        // mask ranges from 1 to (1<<m)-2 to exclude empty set and full set
        final int total = 1 << m;
        for (int mask = 1; mask < total - 1; mask++) {
            // Build C' ∪ X by successive unions (RankTests.union deduplicates)
            int[] left = Arrays.copyOf(X, X.length);
            for (int i = 0; i < m; i++) {
                if ((mask & (1 << i)) != 0) {
                    left = RankTests.union(left, Ccovers.get(i));
                }
            }

            // Effective cardinality ||C' ∪ X|| is just the length after union (unique indices)
            final int kEff = left.length;

            // Finite-sample rank via Wilks (canonical correlations)
            final int rHat = RankTests.estimateWilksRank(S, left, NunionX, n, alpha);

            // If rank is deficient (< ||C' ∪ X||), then a collider could explain it → reject
            if (rHat < kEff) return false;
        }

        return true;
    }

    /**
     * Lemma 10 adapter (Alg. 2 / Phase 1): A ⟂ B | C  ⇔  rank(Σ_{A∪C, B∪C}) = |C|  (finite-sample via Wilks/CCA).
     */
    static boolean dSepByRank(SimpleMatrix S,
                              int[] A,
                              int[] B,
                              int[] C,
                              int n,
                              double alpha) {
        int[] X = RankTests.union(A, C); // A ∪ C
        int[] Y = RankTests.union(B, C); // B ∪ C
        int rHat = RankTests.estimateWilksRank(S, X, Y, n, alpha);
        return rHat == C.length;
    }

    /**
     * Unite a list of covers (each an int[]) into one sorted-unique index array.
     */
    static int[] unionAll(List<int[]> covers) {
        int[] acc = new int[0];
        if (covers == null) return acc;
        for (int[] c : covers) acc = RankTests.union(acc, c);
        return acc;
    }

    /**
     * Algorithm 3 acceptance predicate for a candidate (C, X, N) at target rank k. Accept iff rank(Σ_{C∪X, N∪X}) == k
     * AND NoCollider(C, X, N) (Alg. 4).
     * <p>
     * S: full covariance; X, N are flat index arrays; Ccovers is a list of cover index arrays.
     */
    static boolean acceptCombination(SimpleMatrix S,
                                     int[] X,
                                     List<int[]> Ccovers,
                                     int[] N,
                                     int k,
                                     int n,
                                     double alpha) {
        // Left block: C ∪ X
        int[] Cflat = unionAll(Ccovers);
        int[] left = RankTests.union(Cflat, X);

        // Right block: N ∪ X
        int[] right = RankTests.union(N, X);

        // Rank equals target k?
        int rHat = RankTests.estimateWilksRank(S, left, right, n, alpha);
        if (rHat != k) return false;

        // Collider guard (Alg. 4)
        return noCollider(S, X, Ccovers, N, n, alpha);
    }


    /** Build singleton covers for a scope: each int[] has one index. */
    static java.util.List<int[]> buildSingletonCovers(int[] scope) {
        java.util.ArrayList<int[]> S = new java.util.ArrayList<>(scope.length);
        for (int v : scope) S.add(new int[]{v});
        return S;
    }

// ==== Subset / set utilities ====

    // Return true iff every index in cover is contained in the set XG_flat (sorted-unique).
    static boolean isObservedCover(int[] cover, int[] XG_flat) {
        for (int v : cover) if (java.util.Arrays.binarySearch(XG_flat, v) < 0) return false;
        return true;
    }

    // Flatten covers to a single unique-sorted index array.
    static int[] flatten(java.util.List<int[]> covers) {
        return unionAll(covers);
    }

    // S' = (S \\ T) ∪ (⋃_{t∈T} PCh_G'(t))  — here we keep a hook to expand pure-children covers.
    static java.util.List<int[]> unfoldActiveSet(java.util.List<int[]> S,
                                                 java.util.List<int[]> T,
                                                 java.util.function.Function<int[], java.util.List<int[]>> pureChildrenCoversFn) {
        java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
        outer:
        for (int[] c : S) {
            for (int[] t : T) if (java.util.Arrays.equals(c, t)) continue outer;
            out.add(c);
        }
        for (int[] t : T) {
            java.util.List<int[]> pcs = pureChildrenCoversFn.apply(t);
            if (pcs != null) out.addAll(pcs);
        }
        return out;
    }

    // All k-combinations of indices 0..(n-1).
    static java.util.List<int[]> indexCombinations(int n, int k) {
        java.util.ArrayList<int[]> res = new java.util.ArrayList<>();
        if (k < 0 || k > n) return res;
        if (k == 0) {
            res.add(new int[0]);
            return res;
        }
        int[] comb = new int[k];
        for (int i = 0; i < k; i++) comb[i] = i;
        while (true) {
            int[] copy = new int[k];
            System.arraycopy(comb, 0, copy, 0, k);
            res.add(copy);
            int i;
            for (i = k - 1; i >= 0; i--) {
                if (comb[i] != i + n - k) {
                    comb[i]++;
                    for (int j = i + 1; j < k; j++) comb[j] = comb[j - 1] + 1;
                    break;
                }
            }
            if (i < 0) break;
        }
        return res;
    }

    // Choose all size-k sublists from a list.
    static <T> java.util.List<java.util.List<T>> choose(java.util.List<T> list, int k) {
        java.util.ArrayList<java.util.List<T>> out = new java.util.ArrayList<>();
        for (int[] idxs : indexCombinations(list.size(), k)) {
            java.util.ArrayList<T> pick = new java.util.ArrayList<>(k);
            for (int i : idxs) pick.add(list.get(i));
            out.add(pick);
        }
        return out;
    }

    // Return S minus every element in R, using Arrays.equals for content comparison.
    static java.util.List<int[]> minus(java.util.List<int[]> S, java.util.List<int[]> R) {
        java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
        outer:
        for (int[] s : S) {
            for (int[] r : R) if (java.util.Arrays.equals(s, r)) continue outer;
            out.add(s);
        }
        return out;
    }

// ==== Phase 2: minimal Search driver (Algorithm 3) ====

    /**
     * Minimal Phase-2 search loop over a given scope (X_Q ∪ N_Q), following Algorithm 3.
     *
     * @param S_active             Active set of covers S (start as singletons of scope).
     * @param XG_flat              Sorted-unique array of observed indices in the scope (for filtering X).
     * @param k                    Target rank/cardinality to search.
     * @param n                    Sample size.
     * @param alpha                Significance for Wilks sequential rank test.
     * @param pureChildrenCoversFn Hook: given a cover, return its current pure-children covers in G'.
     * @param updateOnAccept       Hook: called when acceptCombination(...) succeeds to update G' and S.
     * @return true if something was found under current k (caller can then reset k to 1), else false.
     */
    static boolean phase2Search(SimpleMatrix S,
                                java.util.List<int[]> S_active,
                                int[] XG_flat,
                                int k,
                                int n,
                                double alpha,
                                java.util.function.Function<int[], java.util.List<int[]>> pureChildrenCoversFn,
                                java.util.function.BiConsumer<java.util.List<int[]>, java.util.List<int[]>> updateOnAccept) {

        // In full Alg. 3 we iterate T ∈ PowerSet(S). Minimal viable: T = ∅.
        java.util.List<int[]> T = java.util.Collections.emptyList();
        java.util.List<int[]> Sprime = unfoldActiveSet(S_active, T, pureChildrenCoversFn);

        // Observed covers in S' (every index in cover ∈ XG_flat)
        java.util.ArrayList<int[]> observedCovers = new java.util.ArrayList<>();
        for (int[] c : Sprime) if (isObservedCover(c, XG_flat)) observedCovers.add(c);

        for (int t = k; t >= 0; t--) { // Alg. 3 line 12: t from k to 0
            for (java.util.List<int[]> Xcovers : choose(observedCovers, t)) { // line 14
                int[] X = flatten(Xcovers);
                // Candidate pool for C excludes X (by content)
                java.util.List<int[]> poolC = minus(Sprime, Xcovers);

                // ||C|| must equal (k - t + 1) — Alg. 3 line 16
                final int needEff = k - t + 1;

                // Enumerate all subsets of poolC via bitmask and filter by effective cardinality
                int m = poolC.size();
                int total = 1 << m;
                for (int mask = 0; mask < total; mask++) {
                    if (needEff > 0 && mask == 0) continue; // quick prune

                    java.util.ArrayList<int[]> Ccovers = new java.util.ArrayList<>();
                    for (int i = 0; i < m; i++) if ((mask & (1 << i)) != 0) Ccovers.add(poolC.get(i));

                    int effC = unionAll(Ccovers).length;
                    if (effC != needEff) continue;

                    // N = S' \\ (X ∪ C) — Alg. 3 line 16
                    java.util.List<int[]> XplusC = new java.util.ArrayList<>(Xcovers);
                    XplusC.addAll(Ccovers);
                    java.util.List<int[]> Ncovers = minus(Sprime, XplusC);
                    int[] N = flatten(Ncovers);

                    // Core acceptance check (Alg. 3 line 17): rank == k and NoCollider
                    if (acceptCombination(S, X, Ccovers, N, k, n, alpha)) {
                        if (DEBUG) System.out.println("accept k=" + k + ", X=" + arrToString(X) + ", C=" + coversToString(Ccovers) + ", N=" + arrToString(N));
                        // Notify caller to update G' and S (Alg. 3 lines 21–24) and then restart with k ← 1.
                        updateOnAccept.accept(Xcovers, Ccovers);
                        return true;
                    }
                }
            }
        }
        return false; // nothing found under this k
    }

// ==== Hook examples (to implement in your integration) ====

    /**
     * Hook example: given a cover (int[] indices), return its pure-children covers under current G'.
     */
    static java.util.List<int[]> getPureChildrenCovers_example(int[] cover) {
        return java.util.Collections.emptyList();
    }

    /**
     * Hook example: update graph and active set when (C, X, N) is accepted.
     */
    static void updateOnAccept_example(java.util.List<int[]> Xcovers, java.util.List<int[]> Ccovers) {
        // Implement per Alg. 3 lines 21–24:
        //  - If |Pa_G'(Di) ∪ X| = k, set P ← Pa_G'(Di) ∪ X; else create |L| = k - |Pa_G'(Di) ∪ X| latents, P = L ∪ Pa ∪ X
        //  - Attach Di ∈ D as pure children of P
        //  - If P is atomic, update S ← (S \\ Di) ∪ P
    }

    /**
     * Convenience: given a DataSet and a chosen observed scope (nodes), build the scope covariance
     * and run the minimal Phase-2 search over that scope. Indices in the returned clusters refer
     * to positions within {@code scopeNodes} (0..scopeNodes.size()-1).
     */
    public static java.util.List<int[]> runPhase2FromDataSet(DataSet data,
                                                             java.util.List<Node> scopeNodes,
                                                             double alpha) {
        int n = data.getNumRows();
        int p = scopeNodes.size();
        // Map each scope node to its column index in the dataset
        List<Node> allVars = data.getVariables();
        int[] map = new int[p];
        for (int i = 0; i < p; i++) {
            map[i] = allVars.indexOf(scopeNodes.get(i));
            if (map[i] < 0) throw new IllegalArgumentException("scope node not in data: " + scopeNodes.get(i));
        }
        // Build covariance over the scope (nodes ordered as in scopeNodes)
        edu.cmu.tetrad.data.CovarianceMatrix cov = new edu.cmu.tetrad.data.CovarianceMatrix(data);
        org.ejml.simple.SimpleMatrix Sscope = new org.ejml.simple.SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                double v = cov.getValue(map[i], map[j]);
                Sscope.set(i, j, v);
            }
        }
        int[] scopeIdx = new int[p];
        for (int i = 0; i < p; i++) scopeIdx[i] = i;
        return runPhase2OverScope(Sscope, scopeIdx, n, alpha);
    }
    // ==== Phase-1 (minimal) and scope extraction ====

    // Helpers for Phase-1 sepsets and node-index mapping
    static String pairKey(int i, int j) { if (i>j) { int t=i; i=j; j=t; } return i+":"+j; }
    static java.util.Map<Node,Integer> indexOf(java.util.List<Node> vars) {
        java.util.HashMap<Node,Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < vars.size(); i++) map.put(vars.get(i), i);
        return map;
    }

    /** Undirected skeleton via rank-based CI with conditioning sets of size ≤ 1. (Legacy: use phase1RankWithSepsetsOrder1 for sepsets) */
    public static Graph phase1SkeletonRankOrder1_Legacy(DataSet data, double alpha) {
        return phase1RankWithSepsetsOrder1(data, alpha).skeleton;
    }

    /** Phase-1 (order ≤1) that also records sepsets for v-structure orientation. */
    public static Phase1Result phase1RankWithSepsetsOrder1(DataSet data, double alpha) {
        java.util.List<Node> vars = data.getVariables();
        int p = vars.size();
        int n = data.getNumRows();
        edu.cmu.tetrad.data.CovarianceMatrix cov = new edu.cmu.tetrad.data.CovarianceMatrix(data);
        org.ejml.simple.SimpleMatrix S = new org.ejml.simple.SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) for (int j = 0; j < p; j++) S.set(i, j, cov.getValue(i, j));

        EdgeListGraph g = new EdgeListGraph(vars);
        for (int i = 0; i < p; i++) for (int j = i + 1; j < p; j++) g.addUndirectedEdge(vars.get(i), vars.get(j));

        java.util.Map<String,int[]> sepsets = new java.util.HashMap<>();

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                if (!g.isAdjacentTo(vars.get(i), vars.get(j))) continue;
                int[] A = new int[]{i};
                int[] B = new int[]{j};
                // C = ∅
                if (dSepByRank(S, A, B, new int[0], n, alpha)) {
                    g.removeEdge(vars.get(i), vars.get(j));
                    sepsets.put(pairKey(i,j), new int[0]);
                    continue;
                }
                // C = {k}
                boolean removed = false;
                for (int k = 0; k < p; k++) if (k != i && k != j) {
                    if (dSepByRank(S, A, B, new int[]{k}, n, alpha)) {
                        g.removeEdge(vars.get(i), vars.get(j));
                        sepsets.put(pairKey(i,j), new int[]{k});
                        removed = true; break;
                    }
                }
                if (!removed) {
                    // keep edge; leave sepset absent
                }
            }
        }
        return new Phase1Result(g, sepsets, vars);
    }

    /** Orient v-structures A->B<-C for observed triples using Phase-1 sepsets. */
    public static void orientVStructuresObserved(Graph g, Phase1Result ph1) {
        java.util.List<Node> vars = ph1.vars;
        java.util.Map<Node,Integer> idx = indexOf(vars);
        int p = vars.size();
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) if (j != i) {
                for (int k = j+1; k < p; k++) if (k != i) {
                    Node A = vars.get(j), B = vars.get(i), C = vars.get(k);
                    if (!g.isAdjacentTo(A,B) || !g.isAdjacentTo(C,B)) continue;
                    if (g.isAdjacentTo(A,C)) continue; // not unshielded
                    // Only orient if B ∉ sepset(A,C)
                    int[] sep = ph1.sepsets.get(pairKey(idx.get(A), idx.get(C)));
                    boolean bInSep = false;
                    if (sep != null) for (int s : sep) if (s == idx.get(B)) { bInSep = true; break; }
                    if (!bInSep) {
                        // Orient A->B and C->B when those edges are undirected
                        if (g.getDirectedEdge(A,B) == null && g.getDirectedEdge(B,A) == null) { g.removeEdge(A,B); g.addDirectedEdge(A,B); }
                        if (g.getDirectedEdge(C,B) == null && g.getDirectedEdge(B,C) == null) { g.removeEdge(C,B); g.addDirectedEdge(C,B); }
                    }
                }
            }
        }
    }

    /** Apply Meek rules R1 & R2 until convergence. */
    public static void applyMeekR1R2(Graph g) {
        boolean changed;
        do {
            changed = false;
            // R1: If A->B and B-C is undirected and A not adjacent to C, orient B-C as B->C
            java.util.List<Node> nodes = g.getNodes();
            for (Node B : nodes) {
                java.util.List<Node> adjB = g.getAdjacentNodes(B);
                for (Node A : adjB) if (g.getDirectedEdge(A,B) != null) {
                    for (Node C : adjB) if (!C.equals(A)) {
                        if (!g.isAdjacentTo(A,C) && g.isAdjacentTo(B,C)
                            && g.getDirectedEdge(B,C) != null && g.getDirectedEdge(C,B) != null) {
                            g.removeEdge(B,C); g.addDirectedEdge(B,C); changed = true;
                        }
                    }
                }
            }
            // R2: If A-B undirected and there exists a directed path A->...->B, orient A-B as A->B
            for (Node A : g.getNodes()) {
                for (Node B : g.getAdjacentNodes(A)) {
                    if (g.getDirectedEdge(A,B) != null || g.getDirectedEdge(B,A) != null) continue; // skip if already directed
                    if (hasDirectedPath(g, A, B)) { g.removeEdge(A,B); g.addDirectedEdge(A,B); changed = true; }
                }
            }
        } while (changed);
    }

    /** BFS for a directed path A->...->B. */
    private static boolean hasDirectedPath(Graph g, Node start, Node target) {
        java.util.ArrayDeque<Node> dq = new java.util.ArrayDeque<>();
        java.util.HashSet<Node> seen = new java.util.HashSet<>();
        dq.add(start); seen.add(start);
        while (!dq.isEmpty()) {
            Node u = dq.removeFirst();
            if (u.equals(target)) return true;
            for (Node v : g.getAdjacentNodes(u)) if (g.getDirectedEdge(u,v) != null && !seen.contains(v)) { seen.add(v); dq.add(v); }
        }
        return false;
    }

    /** Bron–Kerbosch to find maximal cliques (simple, no pivot). */
    private static void bronKerbosch(Graph g, List<Node> all, List<Node> R, List<Node> P, List<Node> X, List<List<Node>> out) {
        if (P.isEmpty() && X.isEmpty()) { out.add(new ArrayList<>(R)); return; }
        // copy to avoid mutation surprises
        List<Node> Pcopy = new ArrayList<>(P);
        for (Node v : Pcopy) {
            R.add(v);
            List<Node> Nv = g.getAdjacentNodes(v);
            List<Node> Pn = new ArrayList<>(); for (Node u : P) if (Nv.contains(u)) Pn.add(u);
            List<Node> Xn = new ArrayList<>(); for (Node u : X) if (Nv.contains(u)) Xn.add(u);
            bronKerbosch(g, all, R, Pn, Xn, out);
            R.remove(R.size()-1);
            P.remove(v);
            X.add(v);
        }
    }

    /** Group maximal cliques by overlap ≥ 2, then add their neighbors to form scopes. */
    public static List<List<Node>> scopesFromSkeleton(Graph g) {
        List<List<Node>> cliques = new ArrayList<>();
        bronKerbosch(g, g.getNodes(), new ArrayList<>(), new ArrayList<>(g.getNodes()), new ArrayList<>(), cliques);
        // group by overlap ≥ 2
        boolean[] used = new boolean[cliques.size()];
        List<List<Node>> groups = new ArrayList<>();
        for (int i = 0; i < cliques.size(); i++) {
            if (used[i]) continue;
            List<Node> groupUnion = new ArrayList<>(cliques.get(i));
            used[i] = true;
            boolean changed;
            do {
                changed = false;
                for (int j = 0; j < cliques.size(); j++) if (!used[j]) {
                    int overlap = 0;
                    for (Node v : cliques.get(j)) if (groupUnion.contains(v)) overlap++;
                    if (overlap >= 2) {
                        // merge
                        for (Node v : cliques.get(j)) if (!groupUnion.contains(v)) groupUnion.add(v);
                        used[j] = true;
                        changed = true;
                    }
                }
            } while (changed);
            // add neighbors
            List<Node> neighbors = new ArrayList<>();
            for (Node v : groupUnion) {
                for (Node w : g.getAdjacentNodes(v)) if (!groupUnion.contains(w) && !neighbors.contains(w)) neighbors.add(w);
            }
            groupUnion.addAll(neighbors);
            groups.add(groupUnion);
        }
        return groups;
    }

    /**
     * Full (minimal) pipeline: Phase‑1 skeleton (order ≤1) → scopes → Phase‑2 per scope → merge clusters into a graph.
     */
    public Graph searchWithPhase1(double alpha) {
        Phase1Result ph1 = phase1RankWithSepsetsOrder1(this.data, alpha);
        Graph skel = ph1.skeleton;
        java.util.List<java.util.List<Node>> scopes = scopesFromSkeleton(skel);

        EdgeListGraph g = new EdgeListGraph(data.getVariables());
        // add skeleton edges as undirected
        for (Node a : skel.getNodes()) for (Node b : skel.getAdjacentNodes(a)) if (a != b) {
            if (!g.isAdjacentTo(a,b) && skel.isAdjacentTo(a,b)) g.addUndirectedEdge(a,b);
        }
        int li = 1;
        for (java.util.List<Node> scopeNodes : scopes) {
            java.util.List<int[]> clusters = runPhase2FromDataSet(this.data, scopeNodes, alpha);
            for (int[] P : clusters) {
                String lname = "L" + (li++);
                GraphNode L = new GraphNode(lname);
                try { L.setNodeType(NodeType.LATENT); } catch (Throwable ignore) {}
                g.addNode(L);
                for (int idx : P) {
                    Node child = scopeNodes.get(idx);
                    if (g.getNode(child.getName()) == null) g.addNode(child);
                    if (!g.isAdjacentTo(L, child)) g.addDirectedEdge(L, child);
                }
            }
        }
        // Orient observed v-structures using Phase-1 sepsets; then apply Meek rules
        orientVStructuresObserved(g, ph1);
        applyMeekR1R2(g);
        return g;
    }

    /** Observed-only PC approximation using rank CI (order ≤1): skeleton → v-structures → Meek. */
    public Graph pcOnly(double alpha) {
        Phase1Result ph1 = phase1RankWithSepsetsOrder1(this.data, alpha);
        Graph g = new EdgeListGraph(ph1.vars);
        // add skeleton edges as undirected
        for (Node a : ph1.skeleton.getNodes()) for (Node b : ph1.skeleton.getAdjacentNodes(a)) if (a != b) {
            if (!g.isAdjacentTo(a,b) && ph1.skeleton.isAdjacentTo(a,b)) g.addUndirectedEdge(a,b);
        }
        orientVStructuresObserved(g, ph1);
        applyMeekR1R2(g);
        return g;
    }
}