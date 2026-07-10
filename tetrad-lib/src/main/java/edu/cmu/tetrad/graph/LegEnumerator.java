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

package edu.cmu.tetrad.graph;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lazily enumerates the Loyal Equivalent Graphs (LEGs) of a directed Markov equivalence class by
 * walking the class transformationally, via Zhang &amp; Spirtes' legitimate directed-edge
 * reversals rather than by re-orienting the circle component from scratch.
 * <p>
 * A LEG of a DMAG is a Markov-equivalent MAG whose bidirected edges are all invariant (equivalently,
 * the class members with the fewest bidirected edges); see Zhang &amp; Spirtes (2005), "A
 * Transformational Characterization of Markov Equivalence for Directed Acyclic Graphs with Latent
 * Variables," UAI 2005 (arXiv:1207.1419), Prop. 2. The enumeration starts from one LEG -- e.g.
 * {@code GraphTransforms.zhangMagFromPag(pag)}, whose only bidirected edges are the PAG's invariant
 * {@code <->} edges -- and does a breadth-first search over the class using:
 * <p>
 * <b>Legitimate edge reversal (Lemma 2).</b> Reversing {@code A -> B} preserves Markov equivalence
 * iff {@code Pa(B) = Pa(A) union {A}} and {@code Sp(B) = Sp(A)} (parents, resp. spouses). This is the
 * MAG generalization of Chickering's covered-edge reversal (drop spouses and it is exactly the
 * covered edge). Two facts make the walk clean:
 * <ul>
 *   <li>A reversal flips one directed edge and touches no bidirected edge, so the invariant
 *       bidirected set is preserved -- every graph visited is again a LEG.</li>
 *   <li>An invariant directed edge cannot satisfy Lemma 2 (if it did, it would be reversible), so
 *       the walk never disturbs an invariant mark.</li>
 * </ul>
 * Hence no per-step legality or equivalence re-check is required: each move yields a legal MAG in the
 * same class by construction. Completeness -- that BFS from one LEG reaches ALL of them -- is Theorem
 * 2: the LEG set is connected under legitimate reversals, with every intermediate graph a LEG.
 * <p>
 * Compared with enumerating the circle component's acyclic moral orientations, this branches on the
 * currently reversible edges rather than on sink orders, so it sidesteps the linear-extension /
 * simplicial blowup; the first LEG emitted is the seed. Cost: a visited set of size O(#LEGs) is kept
 * to emit each once (#LEGs = the number of AMOs of the circle component), so memory scales with the
 * class, unlike the O(n) circle-orientation iterator. For the small-model regime this is the intended
 * tradeoff.
 * <p>
 * <b>Scope.</b> The transformational characterization is for DIRECTED MAGs; with selection bias
 * (undirected edges) a single mark change breaks ancestrality (Zhang &amp; Spirtes, Fig. 6), so
 * Lemma-2 reversal is not complete. This class therefore rejects a start graph containing an
 * undirected edge; drop back to the circle-resolution enumeration in that case.
 *
 * @author josephramsey
 */
public final class LegEnumerator implements Iterator<Graph>, Iterable<Graph> {

    // ---------------------------------------------------------------------------------------------
    // INSTRUMENTATION. Set LegEnumerator.VERBOSE = true (e.g. from FcitSl, tied to its own verbose
    // flag, immediately before the enumeration loop) to trace the walk. All extra output is prefixed
    // with "[LEG]" so it is easy to grep. Setting VERBOSE = false restores silent behavior; nothing
    // about the iteration order or the emitted graphs changes either way.
    // ---------------------------------------------------------------------------------------------
    public static volatile boolean VERBOSE = false;

    /** Distinguishes concurrent/successive enumerations in the log. */
    private static final AtomicInteger ENUM_SEQ = new AtomicInteger(0);

    private final int enumId = ENUM_SEQ.incrementAndGet();
    private int emittedCount = 0;
    private int discoveredCount = 1;   // the seed is already discovered

    private final Deque<Graph> queue = new ArrayDeque<>();   // discovered-but-not-yet-emitted (FIFO)
    private final Set<String> visited = new HashSet<>();     // canonical keys, one per LEG
    private Graph lookahead;

    /**
     * @param startLeg a LEG of the class (e.g. {@code GraphTransforms.zhangMagFromPag(pag)}).
     *                 Its bidirected edges must all be invariant; if it is a genuine LEG the whole
     *                 LEG set is reached (Theorem 2). Must contain no undirected edge.
     */
    public LegEnumerator(Graph startLeg) {
        for (Edge e : startLeg.getEdges()) {
            if (Edges.isUndirectedEdge(e)) {
                throw new IllegalArgumentException("LegEnumerator: the start graph has an undirected "
                        + "edge (" + e + "); the transformational characterization is for directed "
                        + "MAGs only. Use circle-resolution enumeration under selection bias.");
            }
        }
        Graph seed = new EdgeListGraph(startLeg);           // copy: never mutate the caller's graph
        visited.add(key(seed));
        queue.add(seed);

        if (VERBOSE) {
            int nBi = 0;
            for (Edge e : seed.getEdges()) if (Edges.isBidirectedEdge(e)) nBi++;
            System.out.println("[LEG] === enumeration #" + enumId + " begins ===");
            System.out.println("[LEG] seed key   : " + key(seed));
            System.out.println("[LEG] seed edges : " + edgeString(seed));
            System.out.println("[LEG] seed has " + nBi + " bidirected edge(s); every LEG in this "
                    + "walk will share exactly that bidirected set (Lemma-2 reversals never change it).");
        }
    }

    /**
     * Seeds the enumeration from the canonical Zhang MAG of a PAG, which is a LEG.
     *
     * @param pag the PAG whose (directed) equivalence class is to be enumerated.
     * @return an enumerator over the class's LEGs.
     */
    public static LegEnumerator fromPag(Graph pag) {
        return new LegEnumerator(GraphTransforms.zhangMagFromPag(pag));
    }

    @Override
    public Iterator<Graph> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        if (lookahead == null) lookahead = advance();
        return lookahead != null;
    }

    @Override
    public Graph next() {
        if (!hasNext()) throw new NoSuchElementException();
        Graph result = lookahead;
        lookahead = null;
        return result;
    }

    /**
     * Pops the next LEG, discovers its legitimate-reversal neighbors, and enqueues the unvisited ones.
     */
    private Graph advance() {
        if (queue.isEmpty()) {
            if (VERBOSE) {
                System.out.println("[LEG] === enumeration #" + enumId + " exhausted: "
                        + emittedCount + " LEG(s) emitted, " + discoveredCount + " discovered ===");
            }
            return null;
        }

        Graph g = queue.poll();
        emittedCount++;

        if (VERBOSE) {
            System.out.println("[LEG] #" + enumId + " emit LEG " + emittedCount
                    + " (queue after poll = " + queue.size() + "): " + key(g));
        }

        int reversibleHere = 0;

        for (Edge e : g.getEdges()) {
            if (!Edges.isDirectedEdge(e)) continue;
            Node tail = Edges.getDirectedEdgeTail(e);
            Node head = Edges.getDirectedEdgeHead(e);

            boolean legit = legitimateReversal(g, tail, head);

            if (VERBOSE) {
                System.out.println("[LEG]     edge " + tail.getName() + "->" + head.getName()
                        + " : legitimateReversal = " + legit
                        + "  [Pa(" + head.getName() + ")\\{" + tail.getName() + "} vs Pa("
                        + tail.getName() + ") = " + names(parentsMinus(g, head, tail)) + " vs "
                        + names(parents(g, tail)) + "; Sp(" + tail.getName() + ") vs Sp("
                        + head.getName() + ") = " + names(spouses(g, tail)) + " vs "
                        + names(spouses(g, head)) + "]");
            }

            if (!legit) continue;
            reversibleHere++;

            Graph h = new EdgeListGraph(g);
            h.removeEdges(tail, head);
            h.addDirectedEdge(head, tail);

            String k = key(h);
            if (visited.add(k)) {                            // add() returns false if already present
                queue.add(h);
                discoveredCount++;
                if (VERBOSE) {
                    System.out.println("[LEG]       -> new LEG discovered via reversal "
                            + tail.getName() + "->" + head.getName() + " : " + k);
                }
            } else if (VERBOSE) {
                System.out.println("[LEG]       -> reversal " + tail.getName() + "->" + head.getName()
                        + " revisits a known LEG (skipped)");
            }
        }

        if (VERBOSE) {
            System.out.println("[LEG]   LEG " + emittedCount + " had " + reversibleHere
                    + " legitimately reversible edge(s).");
        }

        return g;
    }

    /**
     * Lemma 2: reversal of {@code a -> b} is legitimate iff {@code Pa(b) \ {a} == Pa(a)} and
     * {@code Sp(b) == Sp(a)}.
     */
    private static boolean legitimateReversal(Graph m, Node a, Node b) {
        Set<Node> paB = parents(m, b);
        paB.remove(a);                                      // a -> b, so a in Pa(b) trivially
        if (!paB.equals(parents(m, a))) return false;
        return spouses(m, a).equals(spouses(m, b));
    }

    /** Parents of x: nodes z with a directed edge z -> x. */
    private static Set<Node> parents(Graph m, Node x) {
        Set<Node> s = new HashSet<>();
        for (Edge e : m.getEdges(x)) {
            if (e.getProximalEndpoint(x) == Endpoint.ARROW && e.getDistalEndpoint(x) == Endpoint.TAIL) {
                s.add(e.getDistalNode(x));
            }
        }
        return s;
    }

    /** Instrumentation helper: Pa(head) \ {tail}, non-mutating, for logging the Lemma-2 test. */
    private static Set<Node> parentsMinus(Graph m, Node head, Node tail) {
        Set<Node> s = parents(m, head);
        s.remove(tail);
        return s;
    }

    /** Spouses of x: nodes z with a bidirected edge z {@code <->} x. */
    private static Set<Node> spouses(Graph m, Node x) {
        Set<Node> s = new HashSet<>();
        for (Edge e : m.getEdges(x)) {
            if (e.getProximalEndpoint(x) == Endpoint.ARROW && e.getDistalEndpoint(x) == Endpoint.ARROW) {
                s.add(e.getDistalNode(x));
            }
        }
        return s;
    }

    /** Instrumentation helper: sorted node names for stable, readable logging. */
    private static String names(Collection<Node> nodes) {
        List<String> ns = new ArrayList<>();
        for (Node n : nodes) ns.add(n.getName());
        Collections.sort(ns);
        return ns.toString();
    }

    /** Instrumentation helper: a stable, readable rendering of a graph's edges. */
    private static String edgeString(Graph m) {
        List<String> toks = new ArrayList<>();
        for (Edge e : m.getEdges()) toks.add(e.toString());
        Collections.sort(toks);
        return toks.toString();
    }

    /**
     * Canonical key: the sorted multiset of oriented edge tokens. Directed edges are directional,
     * bidirected (and any undirected) tokens are endpoint-order-independent, so equal keys mean equal
     * graphs over the same named vertices.
     */
    private static String key(Graph m) {
        List<String> toks = new ArrayList<>();
        for (Edge e : m.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            Endpoint ea = e.getProximalEndpoint(a), eb = e.getDistalEndpoint(a);
            String x = a.getName(), y = b.getName();

            if (ea == Endpoint.TAIL && eb == Endpoint.ARROW) {
                toks.add(x + ">" + y);                      // a -> b
            } else if (ea == Endpoint.ARROW && eb == Endpoint.TAIL) {
                toks.add(y + ">" + x);                      // b -> a
            } else if (ea == Endpoint.ARROW && eb == Endpoint.ARROW) {
                toks.add(x.compareTo(y) <= 0 ? x + "<>" + y : y + "<>" + x);   // a <-> b
            } else {
                toks.add(x.compareTo(y) <= 0 ? x + "--" + y : y + "--" + x);   // undirected / other
            }
        }
        Collections.sort(toks);
        return String.join("|", toks);
    }
}
