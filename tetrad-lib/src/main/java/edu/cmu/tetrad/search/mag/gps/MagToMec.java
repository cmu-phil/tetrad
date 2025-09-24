// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/MagToMec.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;

import java.util.*;

final class MagToMec {
    MecTriples fromMag(Graph g) {
        Graph skeleton = new EdgeListGraph();
        for (Node v : g.getNodes()) skeleton.addNode(v);
        for (Edge e : g.getEdges()) {
            if (!skeleton.isAdjacentTo(e.getNode1(), e.getNode2())) {
                skeleton.addEdge(Edges.undirectedEdge(e.getNode1(), e.getNode2()));
            }
        }

        List<Triple> C = new ArrayList<>();
        List<Triple> D = new ArrayList<>();
        Map<Triple, Integer> order = new LinkedHashMap<>();

        // order-0 (unshielded)
        for (Triple t : findUnshieldedTriples(g)) {
            if (isCollider(g, t)) {
                C.add(t);
                order.put(t, 0);
            } else {
                D.add(t);
                order.put(t, 0);
            }
        }

        // higher orders via queue with exact k
        Deque<Triple> L = new ArrayDeque<>();
        Map<Triple, Integer> pending = new LinkedHashMap<>();
        for (Triple s : seedOrder1Candidates(g, skeleton)) {
            pending.put(s, 1);
            L.addLast(s);
        }
        Set<Triple> seen = new HashSet<>(L);

        while (!L.isEmpty()) {
            Triple t = L.removeFirst();
            int k = pending.getOrDefault(t, 1);
            if (order.containsKey(t)) continue; // already decided at lower order

            if (decideHigherOrderTriple(g, t)) {
                C.add(t);
                order.put(t, k);
            } else {
                D.add(t);
                order.put(t, k);
            }

            for (Triple u : expandWithComplementaries(g, skeleton, t)) {
                if (seen.add(u)) {
                    pending.put(u, k + 1);
                    L.addLast(u);
                }
            }
        }

        return new MecTriples(skeleton, C, D, order);
    }

    private Set<Triple> findUnshieldedTriples(Graph g) {
        Set<Triple> out = new LinkedHashSet<>();
        for (Node z : g.getNodes()) {
            List<Node> nbrs = g.getAdjacentNodes(z);
            int m = nbrs.size();
            for (int i = 0; i < m; i++)
                for (int j = i + 1; j < m; j++) {
                    Node x = nbrs.get(i), y = nbrs.get(j);
                    if (!g.isAdjacentTo(x, y)) out.add(orderedTriple(x, z, y));
                }
        }
        return out;
    }

    private Triple orderedTriple(Node x, Node z, Node y) {
        if (x.getName().compareTo(y.getName()) <= 0) return new Triple(x, z, y);
        return new Triple(y, z, x);
    }

    private boolean isCollider(Graph g, Triple t) {
        Edge xz = g.getEdge(t.getX(), t.getY());
        Edge zy = g.getEdge(t.getY(), t.getZ());
        if (xz == null || zy == null) return false;
        boolean headAtZ1 = xz.getEndpoint(t.getY()) == Endpoint.ARROW;
        boolean headAtZ2 = zy.getEndpoint(t.getY()) == Endpoint.ARROW;
        return headAtZ1 && headAtZ2;
    }

    private Collection<Triple> seedOrder1Candidates(Graph g, Graph skel) {
        Set<Triple> out = new LinkedHashSet<>();
        for (Node z : g.getNodes()) {
            List<Node> adj = skel.getAdjacentNodes(z);
            for (int i = 0; i < adj.size(); i++)
                for (int j = i + 1; j < adj.size(); j++) {
                    Node x = adj.get(i), y = adj.get(j);
                    if (g.isAdjacentTo(x, y)) continue;
                    for (Node w : skel.getAdjacentNodes(y)) {
                        if (w.equals(z) || w.equals(x)) continue;
                        out.add(orderedTriple(x, z, y));
                    }
                }
        }
        return out;
    }

    private boolean decideHigherOrderTriple(Graph g, Triple t) {
        return isCollider(g, t);
    }

    private Collection<Triple> expandWithComplementaries(Graph g, Graph skel, Triple decided) {
        Set<Triple> out = new LinkedHashSet<>();
        Node x = decided.getX(), z = decided.getY(), y = decided.getZ();
        for (Node z2 : skel.getAdjacentNodes(x))
            if (!z2.equals(z) && !g.isAdjacentTo(z2, y)) out.add(orderedTriple(x, z2, y));
        for (Node z3 : skel.getAdjacentNodes(y))
            if (!z3.equals(z) && !g.isAdjacentTo(x, z3)) out.add(orderedTriple(x, z3, y));
        return out;
    }
}