/*
 * Copyright (C) 2026
 *
 * Exact inference for discrete Bayes nets using a Junction Tree (Clique Tree).
 *
 * This implementation follows standard junction-tree theory:
 *  1) Moralize the DAG
 *  2) Triangulate (chordalize) via elimination order with fill-in edges
 *  3) Form cliques induced by elimination (each elim var + its current neighbors)
 *  4) Build a clique tree using a maximum-weight spanning tree on clique intersections
 *  5) Assign each CPT (family = parents ∪ node) to a clique that contains it
 *  6) Initialize clique potentials from assigned CPTs
 *  7) Apply evidence as unary factors (hard + soft)
 *  8) Calibrate via collect + distribute message passing
 *  9) Answer marginals/joints by marginalizing a calibrated clique
 *
 * Notes:
 *  - This is exact for discrete Bayes nets when clique sizes are feasible.
 *  - This class *does* build and calibrate a junction tree.
 *  - Evidence is “hard” in the potentials (0/1 masks). You can extend to soft likelihoods easily.
 *
 * 2026-02-21 jdramsey + (JT-based implementation provided here)
 */
package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * Exact junction-tree inference over a {@link BayesIm} supporting:
 * <ul>
 *   <li>Soft evidence via allowed categories mask ({@link Proposition})</li>
 *   <li>Hard evidence via node=category</li>
 *   <li>Marginals, conditionals, and joint marginals (all conditional on evidence)</li>
 * </ul>
 *
 * This class is designed specifically to satisfy the methods required by
 * {@link JunctionTreeUpdater}.
 */
public final class JunctionTreeInference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BayesIm bayesIm;
    private final int n;              // number of variables
    private final int[] card;         // cardinalities per variable

    // Evidence
    private boolean[][] allowedMask;  // soft evidence: allowed categories; null => all allowed
    private final int[] hardEvidence; // -1 => none else fixed

    // Junction tree structures (built once; calibrated on demand)
    private final List<Clique> cliques;
    private final List<int[]> cliqueNeighbors; // adjacency lists of clique indices
    private final Map<Long, Separator> separators; // key=(minClique,maxClique)

    // Factor assignment
    private final List<List<Factor>> cliqueAssignedFactors;

    // Calibration cache
    private boolean calibratedDirty = true;
    private double cachedEvidenceProb = Double.NaN;

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public JunctionTreeInference(BayesIm bayesIm) {
        if (bayesIm == null) throw new NullPointerException("bayesIm");
        this.bayesIm = bayesIm;
        this.n = bayesIm.getNumNodes();
        this.card = new int[n];
        for (int i = 0; i < n; i++) card[i] = bayesIm.getNumColumns(i);

        this.hardEvidence = new int[n];
        Arrays.fill(this.hardEvidence, -1);
        this.allowedMask = null;

        // Build the JT once from structure
        JunctionTree jt = buildJunctionTree();

        this.cliques = jt.cliques;
        this.cliqueNeighbors = jt.neighbors;
        this.separators = jt.separators;

        // Assign CPT-family factors to cliques
        this.cliqueAssignedFactors = new ArrayList<>(cliques.size());
        for (int i = 0; i < cliques.size(); i++) cliqueAssignedFactors.add(new ArrayList<>());
        assignCptFactorsToCliques();

        this.calibratedDirty = true;
    }

    public void setAllowedCategories(Proposition allowedCategories) {
        if (allowedCategories == null) throw new NullPointerException("allowedCategories");

        Proposition p = new Proposition(this.bayesIm, allowedCategories);

        boolean[][] mask = new boolean[n][];
        for (int v = 0; v < n; v++) {
            int k = card[v];
            mask[v] = new boolean[k];
            for (int c = 0; c < k; c++) mask[v][c] = p.isAllowed(v, c);
        }

        this.allowedMask = mask;
        invalidateCalibration();
    }

//    public void setEvidence(int node, int category) {
//        if (node < 0 || node >= n) throw new IllegalArgumentException("node out of range: " + node);
//        if (category < 0) {
//            hardEvidence[node] = -1;
//            invalidateCalibration();
//            return;
//        }
//        if (category >= card[node]) throw new IllegalArgumentException("category out of range for node " + node + ": " + category);
//        hardEvidence[node] = category;
//        invalidateCalibration();
//    }

    public double getMarginal(int node, int category) {
        if (node < 0 || node >= n) throw new IllegalArgumentException("node out of range: " + node);
        if (category < 0 || category >= card[node]) throw new IllegalArgumentException("category out of range: " + category);

        calibrateIfNeeded();

        if (!(cachedEvidenceProb > 0.0) || Double.isNaN(cachedEvidenceProb)) return Double.NaN;

        // Pick any clique containing node
        int ci = findCliqueContaining(node);
        if (ci < 0) return Double.NaN;

        Factor pot = cliques.get(ci).potential; // calibrated
        Factor marg = pot.marginalizeTo(new int[]{node});
        double denom = marg.totalSum();
        if (!(denom > 0.0) || Double.isNaN(denom)) return Double.NaN;

        return marg.getByVarAssignment(new int[]{node}, new int[]{category}) / denom;
    }

    public double[] getConditional(int node, int[] parents, int[] parentValues) {
        if (parents == null || parentValues == null) throw new NullPointerException();
        if (parents.length != parentValues.length) throw new IllegalArgumentException("parents and parentValues length mismatch.");
        if (node < 0 || node >= n) throw new IllegalArgumentException("node out of range: " + node);

        for (int i = 0; i < parents.length; i++) {
            int p = parents[i];
            int pv = parentValues[i];
            if (p < 0 || p >= n) throw new IllegalArgumentException("parent out of range: " + p);
            if (pv < 0 || pv >= card[p]) throw new IllegalArgumentException("parent value out of range for parent " + p + ": " + pv);
        }

        calibrateIfNeeded();

        int k = card[node];
        double[] out = new double[k];

        // Build var set = parents ∪ {node}
        int m = parents.length;
        int[] vars = new int[m + 1];
        int[] vals = new int[m + 1];
        System.arraycopy(parents, 0, vars, 0, m);
        System.arraycopy(parentValues, 0, vals, 0, m);
        vars[m] = node;

        double sum = 0.0;
        for (int c = 0; c < k; c++) {
            vals[m] = c;
            double p = getJointProbability(vars, vals);
            out[c] = p;
            if (!Double.isNaN(p)) sum += p;
        }
        if (!(sum > 0.0) || Double.isNaN(sum)) {
            Arrays.fill(out, Double.NaN);
            return out;
        }
        for (int c = 0; c < k; c++) out[c] /= sum;
        return out;
    }

    public double getJointProbability(int[] vars, int[] values) {
        if (vars == null || values == null) throw new NullPointerException();
        if (vars.length != values.length) throw new IllegalArgumentException("vars and values length mismatch.");
        if (vars.length == 0) {
            calibrateIfNeeded();
            return 1.0;
        }

        // quick contradiction check vs evidence
        if (contradictsEvidence(vars, values)) return 0.0;

        calibrateIfNeeded();
        if (!(cachedEvidenceProb > 0.0) || Double.isNaN(cachedEvidenceProb)) return Double.NaN;

        // Find clique containing all queried vars (if none, use a chain query via marginalization from any clique containing them
        // Since the clique tree is calibrated, any clique that contains the vars is sufficient.)
        int ci = findCliqueContainingAll(vars);
        if (ci < 0) {
            // fallback: pick clique containing first var, then marginalize from its calibrated potential.
            ci = findCliqueContaining(vars[0]);
            if (ci < 0) return Double.NaN;
        }

        Factor pot = cliques.get(ci).potential;
        Factor marg = pot.marginalizeTo(unique(vars));
        double denom = marg.totalSum();
        if (!(denom > 0.0) || Double.isNaN(denom)) return Double.NaN;

        double num = marg.getByVarAssignment(vars, values);
        return num / denom;
    }

    // ---------------------------------------------------------------------
    // Calibration
    // ---------------------------------------------------------------------

    private void invalidateCalibration() {
        this.calibratedDirty = true;
        this.cachedEvidenceProb = Double.NaN;
    }

    private void calibrateIfNeeded() {
        if (!calibratedDirty) return;

        // Reset clique potentials to product of assigned CPT factors
        for (int i = 0; i < cliques.size(); i++) {
            Clique c = cliques.get(i);
            Factor pot = Factor.identity(c.vars, cardsOf(c.vars));
            for (Factor f : cliqueAssignedFactors.get(i)) {
                pot = pot.multiply(f);
            }
            c.potential = pot;
        }

        // Apply evidence as unary factors into any clique containing the variable
        boolean[][] effMask = effectiveAllowedMask();
        for (int v = 0; v < n; v++) {
            Factor ev = unaryEvidenceFactor(v, effMask[v]);
            int ci = findCliqueContaining(v);
            if (ci < 0) {
                // Should not happen: every variable must appear in some clique.
                calibratedDirty = false;
                cachedEvidenceProb = Double.NaN;
                return;
            }
            cliques.get(ci).potential = cliques.get(ci).potential.multiply(ev);
        }

        // Initialize separator messages to all-ones over separator vars
        for (Separator sep : separators.values()) {
            sep.messageAToB = Factor.identity(sep.sepVars, cardsOf(sep.sepVars));
            sep.messageBToA = Factor.identity(sep.sepVars, cardsOf(sep.sepVars));
        }

        // Calibrate with two-pass (collect then distribute) from an arbitrary root
        int root = 0;
        boolean[] visited = new boolean[cliques.size()];
        collect(root, -1, visited);
        Arrays.fill(visited, false);
        distribute(root, -1, visited);

        // Compute evidence probability from any clique by summing its calibrated potential
        double z = cliques.get(root).potential.totalSum();
        cachedEvidenceProb = z;
        calibratedDirty = false;
    }

    private void collect(int cur, int parent, boolean[] visited) {
        visited[cur] = true;
        for (int nb : cliqueNeighbors.get(cur)) {
            if (nb == parent) continue;
            if (!visited[nb]) collect(nb, cur, visited);
            // After child is calibrated, send message child -> cur
            sendMessage(nb, cur);
        }
    }

    private void distribute(int cur, int parent, boolean[] visited) {
        visited[cur] = true;
        for (int nb : cliqueNeighbors.get(cur)) {
            if (nb == parent) continue;
            // send message cur -> child
            sendMessage(cur, nb);
            if (!visited[nb]) distribute(nb, cur, visited);
        }
    }

    /**
     * HUGIN-style ratio update message passing.
     *
     * IMPORTANT:
     *  - Do NOT multiply incoming messages into src when using ratio updates.
     *    (src.potential already reflects absorbed messages via previous ratio updates.)
     *  - Do NOT normalize messages here; normalization changes the ratio and breaks calibration.
     */
    private void sendMessage(int src, int dst) {
        Separator sep = getSeparator(src, dst);
        int[] sepVars = sep.sepVars;

        // In ratio-update (HUGIN) form, message is just the marginal of src clique potential
        // onto the separator.
        Factor newMsg = cliques.get(src).potential.marginalizeTo(sepVars);

        // Old message: treat null as identity (first pass safety).
        Factor oldMsg;
        if (sep.a == src && sep.b == dst) {
            oldMsg = sep.messageAToB;
            if (oldMsg == null) oldMsg = Factor.identity(sepVars, cardsOf(sepVars));
        } else {
            oldMsg = sep.messageBToA;
            if (oldMsg == null) oldMsg = Factor.identity(sepVars, cardsOf(sepVars));
        }

        // Ratio update on separator scope.
        // NOTE: If your divideSafe convention is causing issues, see note below.
        Factor ratio = newMsg.divideSafe(oldMsg);

        // Update destination clique potential.
        cliques.get(dst).potential = cliques.get(dst).potential.multiply(ratio);

        // Store message.
        if (sep.a == src && sep.b == dst) sep.messageAToB = newMsg;
        else sep.messageBToA = newMsg;
    }

    // ---------------------------------------------------------------------
    // Evidence helpers
    // ---------------------------------------------------------------------

    private boolean contradictsEvidence(int[] vars, int[] vals) {
        for (int i = 0; i < vars.length; i++) {
            int v = vars[i];
            int x = vals[i];
            if (v < 0 || v >= n) throw new IllegalArgumentException("var out of range: " + v);
            if (x < 0 || x >= card[v]) throw new IllegalArgumentException("value out of range for var " + v + ": " + x);

            int he = hardEvidence[v];
            if (he >= 0 && he != x) return true;
            if (allowedMask != null && !allowedMask[v][x]) return true;
        }
        return false;
    }

    private boolean[][] effectiveAllowedMask() {
        boolean[][] mask = new boolean[n][];
        for (int v = 0; v < n; v++) {
            int k = card[v];
            mask[v] = new boolean[k];

            if (allowedMask == null) Arrays.fill(mask[v], true);
            else System.arraycopy(allowedMask[v], 0, mask[v], 0, k);

            int he = hardEvidence[v];
            if (he >= 0) {
                for (int c = 0; c < k; c++) mask[v][c] = (c == he);
            }
        }
        return mask;
    }

    private Factor unaryEvidenceFactor(int v, boolean[] allowed) {
        int[] vars = new int[]{v};
        int[] cards = new int[]{card[v]};
        Factor f = new Factor(vars, cards);
        for (int c = 0; c < card[v]; c++) f.set(new int[]{c}, allowed[c] ? 1.0 : 0.0);
        return f;
    }

    // ---------------------------------------------------------------------
    // Clique/Separator lookup
    // ---------------------------------------------------------------------

    private int findCliqueContaining(int var) {
        for (int i = 0; i < cliques.size(); i++) {
            if (cliques.get(i).contains(var)) return i;
        }
        return -1;
    }

    private int findCliqueContainingAll(int[] vars) {
        int[] u = unique(vars);
        outer:
        for (int i = 0; i < cliques.size(); i++) {
            Clique c = cliques.get(i);
            for (int v : u) if (!c.contains(v)) continue outer;
            return i;
        }
        return -1;
    }

    private Separator getSeparator(int a, int b) {
        int x = Math.min(a, b), y = Math.max(a, b);
        long key = (((long) x) << 32) | (y & 0xffffffffL);
        Separator sep = separators.get(key);
        if (sep == null) throw new IllegalStateException("Missing separator for edge " + a + " - " + b);
        return sep;
    }

    // ---------------------------------------------------------------------
    // CPT factor assignment
    // ---------------------------------------------------------------------

    private void assignCptFactorsToCliques() {
        for (int node = 0; node < n; node++) {
            int[] parents = bayesIm.getParents(node);
            int[] fam = new int[parents.length + 1];
            System.arraycopy(parents, 0, fam, 0, parents.length);
            fam[fam.length - 1] = node;

            int ci = findCliqueContainingAll(fam);
            if (ci < 0) {
                throw new IllegalStateException("No clique contains family of node " + node + ": " + Arrays.toString(fam));
            }
            cliqueAssignedFactors.get(ci).add(buildCptFactor(node));
        }
    }

    private Factor buildCptFactor(int node) {
        int[] parents = bayesIm.getParents(node);
        int[] vars = new int[parents.length + 1];
        System.arraycopy(parents, 0, vars, 0, parents.length);
        vars[vars.length - 1] = node;

        int[] cards = cardsOf(vars);
        Factor f = new Factor(vars, cards);

        int numRows = bayesIm.getNumRows(node);
        int numCols = bayesIm.getNumColumns(node);

        for (int row = 0; row < numRows; row++) {
            int[] parentVals = bayesIm.getParentValues(node, row);
            for (int col = 0; col < numCols; col++) {
                int[] assign = new int[vars.length];
                System.arraycopy(parentVals, 0, assign, 0, parentVals.length);
                assign[assign.length - 1] = col;
                f.set(assign, bayesIm.getProbability(node, row, col));
            }
        }
        return f;
    }

    // ---------------------------------------------------------------------
    // Junction tree construction (moralize + triangulate + cliques + MWST)
    // ---------------------------------------------------------------------

    private JunctionTree buildJunctionTree() {
        // 1) Moralize DAG structure
        UndirectedGraph moral = moralize();

        // 2) Triangulate (min-fill) and record induced cliques from elimination
        Triangulation tri = triangulateMinFill(moral);

        // 3) Reduce cliques to maximal cliques
        List<int[]> maxCliques = maximalCliques(tri.cliques);

        // 4) Build clique tree via maximum-weight spanning tree on clique intersections
        return buildCliqueTree(maxCliques);
    }

    private UndirectedGraph moralize() {
        UndirectedGraph g = new UndirectedGraph(n);
        // add undirected edges for each directed edge parent->child
        for (int child = 0; child < n; child++) {
            int[] parents = bayesIm.getParents(child);
            for (int p : parents) g.addEdge(p, child);
            // connect all pairs of parents (marry parents)
            for (int i = 0; i < parents.length; i++) {
                for (int j = i + 1; j < parents.length; j++) {
                    g.addEdge(parents[i], parents[j]);
                }
            }
        }
        return g;
    }

    private Triangulation triangulateMinFill(UndirectedGraph g0) {
        UndirectedGraph g = g0.copy();
        boolean[] eliminated = new boolean[n];
        List<int[]> inducedCliques = new ArrayList<>();

        for (int step = 0; step < n; step++) {
            int v = pickMinFillVertex(g, eliminated);
            if (v < 0) break;

            int[] nbrs = g.neighborsOf(v, eliminated);
            int[] clique = new int[nbrs.length + 1];
            System.arraycopy(nbrs, 0, clique, 0, nbrs.length);
            clique[clique.length - 1] = v;
            inducedCliques.add(unique(clique));

            // add fill-in edges among neighbors
            for (int i = 0; i < nbrs.length; i++) {
                for (int j = i + 1; j < nbrs.length; j++) {
                    g.addEdge(nbrs[i], nbrs[j]);
                }
            }

            eliminated[v] = true;
        }

        return new Triangulation(inducedCliques);
    }

    private int pickMinFillVertex(UndirectedGraph g, boolean[] eliminated) {
        int best = -1;
        int bestFill = Integer.MAX_VALUE;
        int bestDegree = Integer.MAX_VALUE;

        for (int v = 0; v < n; v++) {
            if (eliminated[v]) continue;
            int[] nbrs = g.neighborsOf(v, eliminated);
            int fill = countMissingEdgesAmong(g, nbrs);
            int deg = nbrs.length;
            // tie-breaker: smaller degree
            if (fill < bestFill || (fill == bestFill && deg < bestDegree)) {
                bestFill = fill;
                bestDegree = deg;
                best = v;
            }
        }
        return best;
    }

    private int countMissingEdgesAmong(UndirectedGraph g, int[] nodes) {
        int missing = 0;
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                if (!g.hasEdge(nodes[i], nodes[j])) missing++;
            }
        }
        return missing;
    }

    private List<int[]> maximalCliques(List<int[]> cliques) {
        // remove duplicates and non-maximal
        List<int[]> uniq = new ArrayList<>();
        for (int[] c : cliques) uniq.add(unique(c));

        // sort by size descending
        uniq.sort((a, b) -> Integer.compare(b.length, a.length));

        List<int[]> out = new ArrayList<>();
        outer:
        for (int i = 0; i < uniq.size(); i++) {
            int[] c = uniq.get(i);
            for (int[] kept : out) {
                if (isSubset(c, kept)) continue outer;
            }
            out.add(c);
        }
        return out;
    }

    private JunctionTree buildCliqueTree(List<int[]> maxCliques) {
        int m = maxCliques.size();
        if (m == 0) throw new IllegalStateException("No cliques produced.");

        List<Clique> cliqueObjs = new ArrayList<>(m);
        for (int i = 0; i < m; i++) cliqueObjs.add(new Clique(i, maxCliques.get(i)));

        // Complete graph over cliques weighted by intersection size
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                int w = intersectionSize(cliqueObjs.get(i).vars, cliqueObjs.get(j).vars);
                if (w > 0) edges.add(new Edge(i, j, w));
            }
        }
        // Maximum spanning tree
        edges.sort((a, b) -> Integer.compare(b.w, a.w));
        DSU dsu = new DSU(m);

        List<int[]> neighbors = new ArrayList<>(m);
        for (int i = 0; i < m; i++) neighbors.add(new int[0]);

        // Build adjacency lists dynamically
        List<List<Integer>> neigh = new ArrayList<>(m);
        for (int i = 0; i < m; i++) neigh.add(new ArrayList<>());

        Map<Long, Separator> seps = new HashMap<>();

        int used = 0;
        for (Edge e : edges) {
            if (dsu.find(e.a) == dsu.find(e.b)) continue;
            dsu.union(e.a, e.b);
            neigh.get(e.a).add(e.b);
            neigh.get(e.b).add(e.a);

            int[] sepVars = intersection(cliqueObjs.get(e.a).vars, cliqueObjs.get(e.b).vars);
            Separator sep = new Separator(e.a, e.b, sepVars);

            int x = Math.min(e.a, e.b), y = Math.max(e.a, e.b);
            long key = (((long) x) << 32) | (y & 0xffffffffL);
            seps.put(key, sep);

            used++;
            if (used == m - 1) break;
        }

        // If disconnected (can happen if intersection graph is disconnected), add a super-root empty separator edges.
        // This keeps calibration defined as a forest -> tree trick.
        if (used != m - 1) {
            int superIdx = cliqueObjs.size();
            cliqueObjs.add(new Clique(superIdx, new int[0]));

            List<Integer> superNeigh = new ArrayList<>();
            neigh.add(superNeigh);

            // connect super-root to each component representative
            DSU d2 = new DSU(m);
            for (Edge e : edges) d2.union(e.a, e.b);
            Map<Integer, Integer> repToClique = new HashMap<>();
            for (int i = 0; i < m; i++) repToClique.put(d2.find(i), i);

            for (int i : repToClique.values()) {
                neigh.get(superIdx).add(i);
                neigh.get(i).add(superIdx);

                Separator sep = new Separator(superIdx, i, new int[0]);
                int x = Math.min(superIdx, i), y = Math.max(superIdx, i);
                long key = (((long) x) << 32) | (y & 0xffffffffL);
                seps.put(key, sep);
            }
        }

        // freeze adjacency
        List<int[]> neighArr = new ArrayList<>(cliqueObjs.size());
        for (int i = 0; i < cliqueObjs.size(); i++) {
            List<Integer> li = neigh.get(i);
            int[] arr = new int[li.size()];
            for (int k = 0; k < li.size(); k++) arr[k] = li.get(k);
            neighArr.add(arr);
        }

        // Running intersection check (tripwire)
        verifyRunningIntersection(cliqueObjs, neighArr);

        return new JunctionTree(cliqueObjs, neighArr, seps);
    }

    private void verifyRunningIntersection(List<Clique> cliques, List<int[]> neigh) {
        // For each variable v, cliques containing v must form a connected subtree.
        for (int v = 0; v < n; v++) {
            List<Integer> contains = new ArrayList<>();
            for (int i = 0; i < cliques.size(); i++) if (cliques.get(i).contains(v)) contains.add(i);
            if (contains.isEmpty()) continue;

            // BFS restricted to cliques containing v
            Set<Integer> set = new HashSet<>(contains);
            ArrayDeque<Integer> q = new ArrayDeque<>();
            Set<Integer> seen = new HashSet<>();
            q.add(contains.get(0));
            seen.add(contains.get(0));

            while (!q.isEmpty()) {
                int cur = q.removeFirst();
                for (int nb : neigh.get(cur)) {
                    if (!set.contains(nb) || seen.contains(nb)) continue;
                    seen.add(nb);
                    q.add(nb);
                }
            }

            if (seen.size() != set.size()) {
                throw new IllegalStateException("Running intersection failed for var " + v
                        + " (cliques containing v are not connected).");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Small utilities / data structures
    // ---------------------------------------------------------------------

    private int[] cardsOf(int[] vars) {
        int[] c = new int[vars.length];
        for (int i = 0; i < vars.length; i++) c[i] = card[vars[i]];
        return c;
    }

    private static int[] unique(int[] a) {
        if (a.length <= 1) return a.clone();
        int[] b = a.clone();
        Arrays.sort(b);
        int k = 1;
        for (int i = 1; i < b.length; i++) if (b[i] != b[i - 1]) b[k++] = b[i];
        return Arrays.copyOf(b, k);
    }

    private static boolean isSubset(int[] small, int[] big) {
        // both assumed unique+sorted
        int i = 0, j = 0;
        while (i < small.length && j < big.length) {
            if (small[i] == big[j]) { i++; j++; }
            else if (small[i] > big[j]) j++;
            else return false;
        }
        return i == small.length;
    }

    private static int intersectionSize(int[] a, int[] b) {
        int i = 0, j = 0, s = 0;
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { s++; i++; j++; }
            else if (a[i] < b[j]) i++;
            else j++;
        }
        return s;
    }

    private static int[] intersection(int[] a, int[] b) {
        int i = 0, j = 0;
        int[] tmp = new int[Math.min(a.length, b.length)];
        int k = 0;
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { tmp[k++] = a[i]; i++; j++; }
            else if (a[i] < b[j]) i++;
            else j++;
        }
        return Arrays.copyOf(tmp, k);
    }

    // ---------------------------------------------------------------------
    // Internal JT containers
    // ---------------------------------------------------------------------

    private static final class JunctionTree implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 23L;

        final List<Clique> cliques;
        final List<int[]> neighbors;
        final Map<Long, Separator> separators;

        JunctionTree(List<Clique> cliques, List<int[]> neighbors, Map<Long, Separator> separators) {
            this.cliques = cliques;
            this.neighbors = neighbors;
            this.separators = separators;
        }
    }

    private static final class Clique implements TetradSerializable {

        @Serial

        final int id;
        final int[] vars;     // sorted unique
        Factor potential;     // calibrated

        Clique(int id, int[] vars) {
            this.id = id;
            this.vars = unique(vars);
            this.potential = Factor.identity(this.vars, new int[this.vars.length]); // placeholder; set later
        }

        boolean contains(int v) {
            return Arrays.binarySearch(vars, v) >= 0;
        }
    }

    private static final class Separator implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 23L;

        final int a, b;       // clique ids
        final int[] sepVars;  // sorted unique
        Factor messageAToB;
        Factor messageBToA;

        Separator(int a, int b, int[] sepVars) {
            this.a = a;
            this.b = b;
            this.sepVars = unique(sepVars);
            this.messageAToB = null;
            this.messageBToA = null;
        }
    }

    private static final class Edge {
        final int a, b, w;
        Edge(int a, int b, int w) { this.a = a; this.b = b; this.w = w; }
    }

    private static final class DSU {
        final int[] p, r;
        DSU(int n) { p = new int[n]; r = new int[n]; for (int i = 0; i < n; i++) p[i] = i; }
        int find(int x) { while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; } return x; }
        void union(int a, int b) {
            a = find(a); b = find(b);
            if (a == b) return;
            if (r[a] < r[b]) p[a] = b;
            else if (r[a] > r[b]) p[b] = a;
            else { p[b] = a; r[a]++; }
        }
    }

    private static final class Triangulation implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 23L;

        final List<int[]> cliques;
        Triangulation(List<int[]> cliques) { this.cliques = cliques; }
    }

    private static final class UndirectedGraph {
        final int n;
        final boolean[][] adj;

        UndirectedGraph(int n) {
            this.n = n;
            this.adj = new boolean[n][n];
        }

        void addEdge(int a, int b) {
            if (a == b) return;
            adj[a][b] = true;
            adj[b][a] = true;
        }

        boolean hasEdge(int a, int b) {
            return adj[a][b];
        }

        int[] neighborsOf(int v, boolean[] eliminated) {
            int[] tmp = new int[n];
            int k = 0;
            for (int u = 0; u < n; u++) {
                if (u == v) continue;
                if (eliminated != null && eliminated[u]) continue;
                if (adj[v][u]) tmp[k++] = u;
            }
            return Arrays.copyOf(tmp, k);
        }

        UndirectedGraph copy() {
            UndirectedGraph g = new UndirectedGraph(n);
            for (int i = 0; i < n; i++) System.arraycopy(this.adj[i], 0, g.adj[i], 0, n);
            return g;
        }
    }

    // ---------------------------------------------------------------------
    // Factor (dense table) supporting multiply / marginalize / scale / divideSafe
    // ---------------------------------------------------------------------

    private static final class Factor implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 23L;

        private final int[] vars;      // sorted? not required, but we keep as given
        private final int[] cards;
        private final int[] strides;
        private final double[] table;

        Factor(int[] vars, int[] cards) {
            this.vars = vars.clone();
            this.cards = cards.clone();
            this.strides = new int[cards.length];

            int size = 1;
            for (int i = cards.length - 1; i >= 0; i--) {
                strides[i] = size;
                size *= cards[i];
            }
            this.table = new double[size];
        }

        static Factor identity(int[] vars, int[] cards) {
            // identity factor = all ones
            Factor f = new Factor(vars, cards);
            Arrays.fill(f.table, 1.0);
            return f;
        }

        void set(int[] assignment, double value) {
            table[indexOf(assignment)] = value;
        }

        double totalSum() {
            double s = 0.0;
            for (double x : table) if (!Double.isNaN(x)) s += x;
            return s;
        }

        Factor scale(double alpha) {
            Factor out = new Factor(vars, cards);
            for (int i = 0; i < table.length; i++) out.table[i] = table[i] * alpha;
            return out;
        }

        boolean containsVar(int v) {
            for (int x : vars) if (x == v) return true;
            return false;
        }

        Factor multiply(Factor other) {
            int[] uVars = unionVars(this.vars, other.vars);
            int[] uCards = new int[uVars.length];

            for (int i = 0; i < uVars.length; i++) {
                int v = uVars[i];
                int c1 = cardOf(this, v);
                int c2 = cardOf(other, v);
                if (c1 < 0) uCards[i] = c2;
                else if (c2 < 0) uCards[i] = c1;
                else {
                    if (c1 != c2) throw new IllegalStateException("Card mismatch for var " + v);
                    uCards[i] = c1;
                }
            }

            Factor out = new Factor(uVars, uCards);

            int[] mapThis = indexMap(uVars, this.vars);
            int[] mapOther = indexMap(uVars, other.vars);

            int[] assignU = new int[uVars.length];
            for (int lin = 0; lin < out.table.length; lin++) {
                decode(lin, out.cards, out.strides, assignU);
                double a = this.table[this.indexFromUnion(assignU, mapThis)];
                double b = other.table[other.indexFromUnion(assignU, mapOther)];
                out.table[lin] = a * b;
            }

            return out;
        }

        /**
         * Safe pointwise division on the overlap (assumes same scope),
         * treating division by 0 as 0 (so ratio is 0 where old is 0).
         */
        Factor divideSafe(Factor denom) {
            // Both should be over same vars in same order (we enforce this in JT code by construction)
            if (!Arrays.equals(this.vars, denom.vars)) {
                // Make compatible by reordering denom to this.vars if needed.
                denom = denom.reorderTo(this.vars, this.cards);
            }
            Factor out = new Factor(this.vars, this.cards);
            for (int i = 0; i < this.table.length; i++) {
                double a = this.table[i];
                double b = denom.table[i];
                if (b == 0.0) out.table[i] = (a == 0.0) ? 1.0 : 0.0; // ratio update convention
                else out.table[i] = a / b;
            }
            return out;
        }

        Factor reorderTo(int[] targetVars, int[] targetCards) {
            // targetVars and targetCards define desired ordering/scope; scope must match
            Factor out = new Factor(targetVars, targetCards);
            int[] mapThis = indexMap(targetVars, this.vars);
            int[] assignT = new int[targetVars.length];

            for (int lin = 0; lin < out.table.length; lin++) {
                decode(lin, out.cards, out.strides, assignT);
                int idxThis = this.indexFromUnion(assignT, mapThis);
                out.table[lin] = this.table[idxThis];
            }
            return out;
        }

        /**
         * Marginalize (sum out) all vars not in targetVars. targetVars can be in any order.
         */
        Factor marginalizeTo(int[] targetVars) {
            int[] tVars = targetVars.clone();
            Arrays.sort(tVars);

            // build target cards
            int[] tCards = new int[tVars.length];
            for (int i = 0; i < tVars.length; i++) {
                int pos = positionOf(this.vars, tVars[i]);
                if (pos < 0) throw new IllegalStateException("Var not in factor: " + tVars[i]);
                tCards[i] = this.cards[pos];
            }

            Factor out = new Factor(tVars, tCards);

            int[] assignThis = new int[this.vars.length];
            int[] assignOut = new int[out.vars.length];

            for (int lin = 0; lin < this.table.length; lin++) {
                decode(lin, this.cards, this.strides, assignThis);

                // project to target vars (sorted)
                for (int i = 0; i < out.vars.length; i++) {
                    int v = out.vars[i];
                    int pos = positionOf(this.vars, v);
                    assignOut[i] = assignThis[pos];
                }

                int outIdx = out.indexOf(assignOut);
                double x = this.table[lin];
                if (!Double.isNaN(x)) out.table[outIdx] += x;
            }
            return out;
        }

        double getByVarAssignment(int[] queryVars, int[] queryVals) {
            // read off value after marginalizing to queryVars (for convenience)
            int[] q = queryVars.clone();
            int[] v = queryVals.clone();
            // normalize ordering: sort query vars together with vals
            Integer[] idx = new Integer[q.length];
            for (int i = 0; i < q.length; i++) idx[i] = i;
            Arrays.sort(idx, Comparator.comparingInt(i -> q[i]));
            int[] qs = new int[q.length];
            int[] vs = new int[v.length];
            for (int i = 0; i < idx.length; i++) {
                qs[i] = q[idx[i]];
                vs[i] = v[idx[i]];
            }

            Factor m = this.marginalizeTo(qs);
            return m.table[m.indexOf(vs)];
        }

        private int indexOf(int[] assignment) {
            int idx = 0;
            for (int i = 0; i < assignment.length; i++) idx += assignment[i] * strides[i];
            return idx;
        }

        private int indexFromUnion(int[] unionAssign, int[] map) {
            int idx = 0;
            for (int i = 0; i < this.vars.length; i++) idx += unionAssign[map[i]] * this.strides[i];
            return idx;
        }

        private static int cardOf(Factor f, int var) {
            for (int i = 0; i < f.vars.length; i++) if (f.vars[i] == var) return f.cards[i];
            return -1;
        }

        private static int positionOf(int[] vars, int var) {
            for (int i = 0; i < vars.length; i++) if (vars[i] == var) return i;
            return -1;
        }

        private static int[] unionVars(int[] a, int[] b) {
            int[] tmp = new int[a.length + b.length];
            int n = 0;
            for (int v : a) tmp[n++] = v;
            outer:
            for (int v : b) {
                for (int x : a) if (x == v) continue outer;
                tmp[n++] = v;
            }
            return Arrays.copyOf(tmp, n);
        }

        private static int[] indexMap(int[] unionVars, int[] subVars) {
            int[] map = new int[subVars.length];
            for (int i = 0; i < subVars.length; i++) {
                int v = subVars[i];
                int pos = -1;
                for (int j = 0; j < unionVars.length; j++) if (unionVars[j] == v) { pos = j; break; }
                if (pos < 0) throw new IllegalStateException("Var not found in union: " + v);
                map[i] = pos;
            }
            return map;
        }

        private static void decode(int linear, int[] cards, int[] strides, int[] outAssign) {
            for (int i = 0; i < cards.length; i++) {
                int s = strides[i];
                outAssign[i] = (linear / s) % cards[i];
            }
        }
    }
}