package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.SublistGenerator;

import java.util.*;

/**
 * Harness drop-in: checks whether the graph FCIT starts from (the PAG of the
 * BOSS/GRaSP DAG, i.e. G_0, BEFORE any edge removals) is a Markov PAG for the
 * simulated true model.
 *
 * <p>Why this matters. FCIT's legality-and-revert guard and its inducing-path
 * detector are both sound, yet a maximality violation can still reach the output
 * if it was INHERITED from the seed rather than created by a deletion: the seed
 * is never legality-checked (the check runs only after a removal), and FCIT only
 * deletes edges, so it can never add back a true edge the seed is missing. A
 * non-Markov seed therefore breaks the standing hypothesis of every theorem in
 * the reachability section ({@code prop:maximality} Case 1 depends entirely on
 * {@code adj(G_0) >= adj(G*)}), and the violation is outside what FCIT can fix.
 *
 * <p>Two checks, both against the known true DAG:
 * <ol>
 *   <li><b>Adjacency superset (decisive).</b> Every adjacency of the true PAG
 *       {@code G* = dagToPag(trueDag)} must be present in the seed. A missing one
 *       is a true edge the seed dropped; since {@code G*} is maximal that pair is
 *       m-inseparable in the true model, so the seed claims a false independence
 *       (not an I-map) and bequeaths a permanent inducing path. Each missing edge
 *       is cross-confirmed by searching for a true separating set: finding none
 *       confirms a genuine defect; finding one would instead flag a
 *       {@code dagToPag}/G* artifact rather than a seed problem.</li>
 *   <li><b>Sound unshielded colliders (optional I-map half).</b> Even with the
 *       right skeleton, a seed collider X*-&gt;Z&lt;-*Y that is NOT a true collider
 *       lets the seed entail a false independence. For each unshielded collider in
 *       the seed we confirm the true model can separate X,Y by some observed set
 *       that EXCLUDES Z; if Z is forced into every separator, the collider is
 *       unsound.</li>
 * </ol>
 *
 * <p>Getting the seed: the cleanest hook is one line in {@code Fcit.search()} —
 * right after {@code this.pag = GraphTransforms.dagToPag(dag, ...)} stash a copy
 * ({@code this.seedPag = new EdgeListGraph(pag);}) and expose a getter. Failing
 * that, reproduce it in the harness from the same search DAG you already build.
 * Pass the true DAG with its latents typed {@code NodeType.LATENT}; this checker
 * marginalizes them exactly as FCIT's pipeline does.
 */
public final class SeedMarkovCheck {

    private SeedMarkovCheck() {
    }

    public static final class Result {
        /** True edges of G* absent from the seed AND confirmed m-inseparable in truth. */
        public final List<String> missingTrueEdges = new ArrayList<>();
        /** Absent from the seed but separable in truth — points at dagToPag/G*, not the seed. */
        public final List<String> artifactEdges = new ArrayList<>();
        /** Unshielded colliders in the seed that the true model cannot justify. */
        public final List<String> unsoundColliders = new ArrayList<>();
        /** Names present in G* but missing from the seed graph entirely. */
        public final List<String> missingNodes = new ArrayList<>();

        public boolean seedIsMarkov() {
            return missingTrueEdges.isEmpty() && unsoundColliders.isEmpty() && missingNodes.isEmpty();
        }

        public String report() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Seed Markov / I-map check ===\n");
            if (seedIsMarkov()) {
                sb.append("PASS: seed is a Markov PAG for the true model ")
                        .append("(adjacency superset holds; unshielded colliders sound).\n");
            } else {
                sb.append("FAIL: seed is NOT a Markov PAG — FCIT will inherit this and cannot repair it.\n");
            }
            if (!missingTrueEdges.isEmpty()) {
                sb.append("\nMissing true edges (true edge absent from seed, m-inseparable in truth):\n");
                for (String s : missingTrueEdges) sb.append("  - ").append(s).append('\n');
            }
            if (!unsoundColliders.isEmpty()) {
                sb.append("\nUnsound unshielded colliders in seed (Z forced into every true separator):\n");
                for (String s : unsoundColliders) sb.append("  - ").append(s).append('\n');
            }
            if (!missingNodes.isEmpty()) {
                sb.append("\nObserved nodes in G* missing from seed graph:\n");
                for (String s : missingNodes) sb.append("  - ").append(s).append('\n');
            }
            if (!artifactEdges.isEmpty()) {
                sb.append("\nNOTE (not seed defects): edges in G* absent from the seed but separable in\n")
                        .append("truth — investigate dagToPag/G* construction, not GRaSP:\n");
                for (String s : artifactEdges) sb.append("  - ").append(s).append('\n');
            }
            return sb.toString();
        }
    }

    /**
     * @param seedPag        FCIT's starting PAG (G_0), before any removals.
     * @param trueDag        the simulated true DAG, latents typed NodeType.LATENT.
     * @param knowledge      knowledge used for dagToPag (pass FCIT's, or new Knowledge()).
     * @param recursiveDepth recursiveDepth used for dagToPag (FCIT passes its field; -1 is fine).
     * @param sepsetMaxDepth cap on conditioning-set size in the true-model sepset search
     *                       (-1 = unbounded; keep modest, e.g. 6-8, for 20-node runs).
     * @param checkColliders run the optional sound-collider (I-map) half.
     */
    public static Result check(Graph seedPag, Graph trueDag, Knowledge knowledge,
                               int recursiveDepth, int sepsetMaxDepth, boolean checkColliders) {
        Result r = new Result();

        // True PAG over the observed margin (latents marginalized by dagToPag).
        Graph gStar = GraphTransforms.dagToPag(trueDag, knowledge, false, recursiveDepth);

        IndependenceTest trueTest = new MsepTest(trueDag);

        // Observed pool, resolved to trueDag's own node objects (MsepTest needs those).
        List<Node> observedTrue = new ArrayList<>();
        for (Node n : trueDag.getNodes()) {
            if (n.getNodeType() != NodeType.LATENT) observedTrue.add(n);
        }

        // ---- Check 1: adjacency superset adj(seed) >= adj(G*) ----
        for (Edge e : gStar.getEdges()) {
            String an = e.getNode1().getName();
            String bn = e.getNode2().getName();

            Node sa = seedPag.getNode(an);
            Node sb = seedPag.getNode(bn);
            if (sa == null || sb == null) {
                if (sa == null && !r.missingNodes.contains(an)) r.missingNodes.add(an);
                if (sb == null && !r.missingNodes.contains(bn)) r.missingNodes.add(bn);
                continue;
            }

            if (!seedPag.isAdjacentTo(sa, sb)) {
                Node ta = trueDag.getNode(an);
                Node tb = trueDag.getNode(bn);
                Set<Node> sep = findTrueSepset(trueTest, ta, tb, observedTrue, null, sepsetMaxDepth);
                if (sep == null) {
                    r.missingTrueEdges.add(an + " --- " + bn
                            + "  (no separating set found up to depth "
                            + (sepsetMaxDepth < 0 ? "inf" : sepsetMaxDepth) + ")");
                } else {
                    r.artifactEdges.add(an + " --- " + bn + "  (separable in truth by " + names(sep) + ")");
                }
            }
        }

        // ---- Check 2 (optional): sound unshielded colliders in the seed ----
        if (checkColliders) {
            Set<String> seen = new HashSet<>();
            for (Node z : seedPag.getNodes()) {
                List<Node> adj = seedPag.getAdjacentNodes(z);
                for (int i = 0; i < adj.size(); i++) {
                    for (int j = i + 1; j < adj.size(); j++) {
                        Node x = adj.get(i);
                        Node y = adj.get(j);
                        if (seedPag.isAdjacentTo(x, y)) continue;           // shielded
                        if (!seedPag.isDefCollider(x, z, y)) continue;       // not a definite collider

                        String key = unordered(x.getName(), y.getName()) + "|" + z.getName();
                        if (!seen.add(key)) continue;

                        Node tx = trueDag.getNode(x.getName());
                        Node ty = trueDag.getNode(y.getName());
                        Node tz = trueDag.getNode(z.getName());
                        if (tx == null || ty == null || tz == null) continue;

                        // Sound collider => X,Y separable in truth by some observed set EXCLUDING Z.
                        Set<Node> sepNoZ = findTrueSepset(trueTest, tx, ty, observedTrue, tz, sepsetMaxDepth);
                        if (sepNoZ == null) {
                            r.unsoundColliders.add(x.getName() + " *-> " + z.getName() + " <-* " + y.getName()
                                    + "  (no Z-excluding separator of " + x.getName() + "," + y.getName()
                                    + " found in truth)");
                        }
                    }
                }
            }
        }

        return r;
    }

    /**
     * Searches for a set S (subset of {@code pool}, excluding x, y, and {@code exclude})
     * with x _||_ y | S in the true model. Returns the first such set, or null if none
     * is found within {@code maxDepth}. A null with a generous depth, for a pair that is
     * adjacent in the maximal G*, is consistent with genuine m-inseparability.
     */
    private static Set<Node> findTrueSepset(IndependenceTest trueTest, Node x, Node y,
                                            List<Node> pool, Node exclude, int maxDepth) {
        if (x == null || y == null) return null;

        List<Node> cand = new ArrayList<>();
        for (Node n : pool) {
            if (n == x || n == y || n == exclude) continue;
            cand.add(n);
        }

        int cap = (maxDepth < 0) ? cand.size() : Math.min(maxDepth, cand.size());
        SublistGenerator gen = new SublistGenerator(cand.size(), cap);
        int[] choice;
        while ((choice = gen.next()) != null) {
            Set<Node> s = GraphUtils.asSet(choice, cand);
            try {
                if (trueTest.checkIndependence(x, y, s).isIndependent()) {
                    return s;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static String names(Set<Node> s) {
        List<String> ns = new ArrayList<>();
        for (Node n : s) ns.add(n.getName());
        Collections.sort(ns);
        return ns.toString();
    }

    private static String unordered(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "," + b : b + "," + a;
    }
}
