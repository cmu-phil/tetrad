/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Computes the <em>complete</em> Ordered Local Markov Property (OLMP) for an acyclic
 * directed mixed graph (ADMG), as defined and proved sound and complete by Richardson
 * (2003, Scandinavian Journal of Statistics 30:145-157, Theorem 2).
 *
 * <h2>Definition</h2>
 * Given a consistent ordering {@code ≺} of the vertices (i.e., x ≺ y implies y is not
 * an ancestor of x), a probability measure P satisfies the OLMP if for <em>every</em>
 * vertex x and <em>every</em> ancestral set A with x ∈ A ⊆ pre(x):
 * <pre>
 *   {x} ⊥⊥ A ∖ (mb(x,A) ∪ {x}) | mb(x,A)
 * </pre>
 * where mb(x,A) = pa(dis_A(x)) ∪ (dis_A(x) ∖ {x}) is the Markov blanket of x in the
 * subgraph induced by A, and pre(x) = {v : v ≺ x or v = x}.
 *
 * <h2>Completeness</h2>
 * Richardson's Theorem 2 establishes that the set of distributions satisfying this
 * property equals the set satisfying m-separation (the global Markov property), using
 * only the semi-graphoid axioms (C1)–(C4). No composition axiom (C5) is required.
 *
 * <h2>Key difference from {@link OrderedLocalMarkovPropertySinkElimination}</h2>
 * The Andrews sink-elimination procedure ({@link OrderedLocalMarkovPropertySinkElimination}) generates
 * CI facts only for ancestral sets obtained by removing descendants of district members.
 * This class generates facts for <em>all</em> ancestral subsets of pre(x), including
 * those obtained by removing optional parents of district members. This additional
 * coverage is necessary for completeness in graphs containing discriminating paths.
 *
 * <h2>Efficiency</h2>
 * For each node x, the relevant ancestral sets are parameterised by subsets of
 * R(x) = mb(x, pre(x)) ∖ an(x), the optional Markov-blanket members of x. The
 * method enumerates all 2^|R(x)| such subsets. Since mb(x, pre(x)) is typically
 * small, this is practical. A cap of 2^25 iterations per node is enforced; nodes
 * exceeding this limit receive only the full-pre(x) blanket fact (still sound).
 *
 * <h2>References</h2>
 * Richardson, T.S. (2003). Markov properties for acyclic directed mixed graphs.
 * <em>Scandinavian Journal of Statistics</em>, 30(1), 145–157.
 */
public class OrderedLocalMarkovProperty {

    private OrderedLocalMarkovProperty() {
    }

    /**
     * Computes the complete OLMP model for the given ADMG.
     *
     * @param admg An acyclic directed mixed graph (ADMG). Passed as a MAG in Tetrad
     *             (the code also works for any ADMG).
     * @return The full set of independence facts implied by the OLMP.
     */
    public static Set<IndependenceFact> getModel(Graph admg) {
        // Pre-compute descendants in the ORIGINAL graph once; used when removing nodes.
        Paths paths = new Paths(admg);
        Map<Node, Set<Node>> deMap = paths.getDescendantsMap();

        // Consistent ordering: ancestors before descendants (topological sort).
        List<Node> order = consistentOrdering(admg);

        Set<IndependenceFact> model = new HashSet<>();

        for (int k = 0; k < order.size(); k++) {
            Node x = order.get(k);

            // pre(x) = {order[0], ..., order[k]} — includes x and all its ancestors
            // (plus any other non-descendant nodes that happen to precede x).
            Set<Node> preX = new LinkedHashSet<>(order.subList(0, k + 1));

            generateFactsForNode(model, admg, x, preX, deMap);
        }

        return model;
    }

    /**
     * Returns the subset of OLMP independence facts that have {@code x} as one
     * endpoint (i.e., facts of the form x ⊥ y | Z or y ⊥ x | Z), normalised so
     * that x is always the left-hand ({@code getX()}) node.
     *
     * @param admg The ADMG.
     * @param x    The node of interest.
     * @return Independence facts from the full OLMP model that involve x.
     */
//    public static Set<IndependenceFact> getModelForNode(Graph admg, Node x) {
//        Set<IndependenceFact> all = getModel(admg);
//
//        Map<String, Node> byName = new HashMap<>();
//        for (Node n : admg.getNodes()) byName.put(n.getName(), n);
//
//        String xName = x.getName();
//        Set<IndependenceFact> out = new HashSet<>();
//
//        for (IndependenceFact f : all) {
//            boolean xIsLeft  = f.getX().getName().equals(xName);
//            boolean xIsRight = f.getY().getName().equals(xName);
//            if (!xIsLeft && !xIsRight) continue;
//
//            Node X = byName.get(f.getX().getName());
//            Node Y = byName.get(f.getY().getName());
//
//            Set<Node> Z = new HashSet<>();
//            for (Node z : f.getZ()) {
//                Node zz = byName.get(z.getName());
//                if (zz != null) Z.add(zz);
//            }
//
//            if (X != null && Y != null) {
//                if (xIsLeft) out.add(new IndependenceFact(X, Y, Z));
//                else         out.add(new IndependenceFact(Y, X, Z));
//            }
//        }
//
//        return out;
//    }

    public static Set<IndependenceFact> getModelForNode(Graph admg, Node x) {
        Set<IndependenceFact> all = getModel(admg);
        Map<String, Node> byName = new HashMap<>();
        for (Node n : admg.getNodes()) byName.put(n.getName(), n);
        String xName = x.getName();
        Set<IndependenceFact> out = new HashSet<>();
        for (IndependenceFact f : all) {
            boolean xIsLeft  = f.getX().getName().equals(xName);
            boolean xIsRight = f.getY().getName().equals(xName);
            if (!xIsLeft && !xIsRight) continue;
            Node X = byName.get(f.getX().getName());
            Node Y = byName.get(f.getY().getName());
            Set<Node> Z = new HashSet<>();
            for (Node z : f.getZ()) {
                Node zz = byName.get(z.getName());
                if (zz != null) Z.add(zz);
            }
            if (X != null && Y != null) {
                // Sort by name so role assignment is consistent with getModel / MarkovCheck
                if (X.getName().compareTo(Y.getName()) <= 0) {
                    out.add(new IndependenceFact(X, Y, Z));
                } else {
                    out.add(new IndependenceFact(Y, X, Z));
                }
            }
        }
        return out;
    }

    // ── Core per-node enumeration ────────────────────────────────────────────

    /**
     * Generates all distinct CI facts for node {@code x} over all relevant
     * ancestral subsets of {@code preX}.
     *
     * <p>The key insight (Richardson 2003, §3.1) is that every Markov blanket
     * mb(x,A) for A ⊆ pre(x) is a subset of mb(x, pre(x)). Therefore distinct
     * blankets arise only from removing optional blanket members (those in
     * mb(x,pre(x)) that are not ancestors of x) together with their descendants.
     * Enumerating subsets of these "optional blanket members" R(x) is sufficient
     * to discover every distinct blanket—and hence every distinct CI fact—for x.
     *
     * <p>This covers strictly more ancestral sets than the Andrews procedure, which
     * only removes descendants of district members. R(x) additionally includes
     * non-ancestor parents of district members, and removing those along with their
     * descendants produces ancestral sets not reachable by the Andrews recursion.
     */
    private static void generateFactsForNode(Set<IndependenceFact> model,
                                             Graph admg,
                                             Node x,
                                             Set<Node> preX,
                                             Map<Node, Set<Node>> deMap) {
        // Ancestors of x in the original graph (all are guaranteed to be in preX
        // by the consistent ordering).
        Set<Node> anX = ancestorsOf(x, admg);

        // Full blanket in the induced subgraph on preX.
        Set<Node> fullBlanket = computeMarkovBlanket(x, admg, preX);

        // R(x) = optional blanket members = mb(x, preX) \ an(x).
        // These are the only nodes whose inclusion/exclusion can change the blanket.
        List<Node> optBlanket = new ArrayList<>(fullBlanket);
        optBlanket.removeAll(anX);
        // x itself is never in the blanket, but be safe.
        optBlanket.remove(x);

        int n = optBlanket.size();

        // Cap to prevent runaway computation on pathological graphs.
        if (n > 25) {
            // Fallback: emit only the full-preX blanket fact (sound but not complete).
            emitFacts(model, x, preX, fullBlanket);
            return;
        }

        // For each subset S of optional blanket members to REMOVE (along with their
        // descendants in the original graph), compute the induced ancestral set A and
        // its Markov blanket.  Keep only the maximal A per distinct blanket (§3.1).
        Map<Set<Node>, Set<Node>> blanketToMaxA = new HashMap<>();

        for (int mask = 0; mask < (1 << n); mask++) {
            // Build the set of nodes to remove: ∪_{r ∈ S} de(r, G).
            Set<Node> toRemove = new HashSet<>();
            for (int i = 0; i < n; i++) {
                if ((mask >> i & 1) == 1) {
                    Node r = optBlanket.get(i);
                    toRemove.add(r);
                    Set<Node> desc = deMap.get(r);
                    if (desc != null) toRemove.addAll(desc);
                }
            }

            // A = preX \ toRemove.  Skip if x or any ancestor of x is removed
            // (this would make A non-ancestral w.r.t. x, which shouldn't happen
            // since optBlanket ∩ an(x) = ∅, but guard defensively).
            if (!Collections.disjoint(toRemove, anX) || toRemove.contains(x)) continue;

            Set<Node> A = new HashSet<>(preX);
            A.removeAll(toRemove);

            // Compute mb(x, A) via the induced subgraph.
            Set<Node> blanket = computeMarkovBlanket(x, admg, A);

            // Keep the maximal A for this blanket (larger A → more nodes outside the
            // blanket → more CI facts, but they are all implied by the smaller A fact
            // via decomposition; keeping the maximal A is correct per Richardson §3.1).
            Set<Node> existing = blanketToMaxA.get(blanket);
            if (existing == null || existing.size() < A.size()) {
                blanketToMaxA.put(new HashSet<>(blanket), new HashSet<>(A));
            }
        }

        // Emit one CI fact per distinct blanket.
        for (Map.Entry<Set<Node>, Set<Node>> entry : blanketToMaxA.entrySet()) {
            emitFacts(model, x, entry.getValue(), entry.getKey());
        }
    }

    /** Adds x ⊥ w | blanket for each w ∈ A that is outside {x} ∪ blanket. */
    private static void emitFacts(Set<IndependenceFact> model,
                                  Node x,
                                  Set<Node> A,
                                  Set<Node> blanket) {
        for (Node w : A) {
            if (w.equals(x)) continue;
            if (blanket.contains(w)) continue;
            
            // Sort by name so role assignment is consistent everywhere
            if (x.getName().compareTo(w.getName()) <= 0) {
                model.add(new IndependenceFact(x, w, new HashSet<>(blanket)));
            } else {
                model.add(new IndependenceFact(w, x, new HashSet<>(blanket)));
            }
        }
    }

    // ── Graph helpers ────────────────────────────────────────────────────────

    /**
     * Computes mb(x, G_A) = pa_{G_A}(dis_{G_A}(x)) ∪ (dis_{G_A}(x) ∖ {x}).
     * Since x has no children in G_A (guaranteed by the consistent ordering: all
     * children of x come after x in topological order and are therefore not in
     * preX ⊇ A), Tetrad's GraphUtils.markovBlanket reduces to this formula.
     */
    private static Set<Node> computeMarkovBlanket(Node x, Graph admg, Set<Node> A) {
        EdgeListGraph sub = inducedSubgraph(admg, A);
        return new HashSet<>(GraphUtils.markovBlanket(x, sub));
    }

    /**
     * Returns the proper ancestors of {@code x} in {@code admg} (not including x).
     * Follows directed edges backwards (parent pointers).
     */
    private static Set<Node> ancestorsOf(Node x, Graph admg) {
        Set<Node> result = new HashSet<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(x);
        while (!stack.isEmpty()) {
            Node v = stack.pop();
            for (Node parent : admg.getParents(v)) {
                if (result.add(parent)) stack.push(parent);
            }
        }
        return result;
    }

    /**
     * Returns the subgraph of {@code admg} induced on vertex set {@code A}: same
     * nodes, only edges whose both endpoints are in A.
     */
    private static EdgeListGraph inducedSubgraph(Graph admg, Set<Node> A) {
        EdgeListGraph sub = new EdgeListGraph(new ArrayList<>(A));
        for (Edge e : admg.getEdges()) {
            if (A.contains(e.getNode1()) && A.contains(e.getNode2())) {
                sub.addEdge(e);
            }
        }
        return sub;
    }

    /**
     * Computes a consistent ordering of the nodes: ancestors before descendants.
     * This is a standard topological sort using directed edges only (bidirected
     * edges impose no ordering constraint). Ties are broken alphabetically for
     * reproducibility.
     *
     * <p>For graphs with only bidirected edges (no directed edges), all nodes have
     * in-degree zero and appear first; the ordering is alphabetical, which is a
     * valid consistent ordering since there are no ancestor relationships.
     */
    private static List<Node> consistentOrdering(Graph admg) {
        // Count directed in-degrees.
        Map<Node, Integer> inDegree = new LinkedHashMap<>();
        for (Node n : admg.getNodes()) inDegree.put(n, 0);

        for (Edge e : admg.getEdges()) {
            if (Edges.isDirectedEdge(e)) {
                Node head = Edges.getDirectedEdgeHead(e);
                inDegree.merge(head, 1, Integer::sum);
            }
        }

        // Kahn's algorithm with a sorted queue for determinism.
        PriorityQueue<Node> ready = new PriorityQueue<>(Comparator.comparing(Node::getName));
        for (Node n : admg.getNodes()) {
            if (inDegree.get(n) == 0) ready.add(n);
        }

        List<Node> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            Node n = ready.poll();
            order.add(n);
            for (Node child : admg.getChildren(n)) {
                int remaining = inDegree.merge(child, -1, Integer::sum);
                if (remaining == 0) ready.add(child);
            }
        }

        // Guard: add any node not reached (would indicate a directed cycle, which
        // violates the ADMG assumption, but we handle it gracefully).
        for (Node n : admg.getNodes()) {
            if (!order.contains(n)) order.add(n);
        }

        return order;
    }
}
