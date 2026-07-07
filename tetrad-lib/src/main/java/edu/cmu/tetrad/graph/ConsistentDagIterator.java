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

/**
 * Lazily enumerates the consistent DAG extensions of a DAG or CPDAG.
 * <p>
 * This is the graph-valued companion to the valid-order enumeration.  It runs the same
 * Dor-Tarsi sink-peeling DFS (peel a node with no child whose undirected neighbors form a
 * clique, branching over ALL valid sinks), but at each peel it orients every undirected
 * neighbor z--x into the sink as z-&gt;x, so a completed peel sequence is a consistent
 * extension.
 * <p>
 * With {@code distinct} (the default) each extension is emitted exactly once.  A single
 * extension is a topological order of many peel sequences; two peel sequences produce the same
 * DAG iff they differ only by transposing adjacent nodes that are NON-adjacent in the graph
 * (such a swap flips no edge).  The lexicographically-minimal (by rank) sequence of each such
 * class is characterized by having no adjacent commuting inversion, so we forbid one
 * incrementally: a sink {@code x} is peeled after {@code prev} only unless {@code x} shares no
 * edge with {@code prev} and outranks it (in which case {@code x} could have been peeled before
 * {@code prev} for the same DAG -- a non-canonical duplicate, deferred).  This reaches each
 * distinct DAG exactly once WITHOUT generating the duplicate orders, at O(n) working memory.
 * With {@code distinct == false} the prune is off and every valid order yields a DAG.
 * <p>
 * Ranks come from {@code initialOrder}/{@code forward} exactly as in {@code getValidOrder}; the
 * first extension emitted is the one that method's order induces.
 * <p>
 * Cost note: the prune removes the linear-extension blowup for the common case (e.g. an edgeless
 * or near-edgeless component no longer costs n! -- it costs about the number of rank-increasing
 * prefixes).  It is NOT output-polynomial in the worst case, though: a component with many
 * simplicial vertices that are simultaneously valid sinks and pairwise non-adjacent (a large
 * star, say) can still explore up to ~2^k dead-end prefixes to emit k+1 DAGs, because a
 * lower-rank node blocked only transitively (through a shared neighbor) cannot be pruned by the
 * adjacent-pair test.  When that bites, enumerate the acyclic moral orientations of the chordal
 * component directly with a clique-tree method (polynomial delay); ask and I'll wire it up.
 * <p>
 * Assumes a DAG or CPDAG (no bidirected/circle endpoints; the undirected-neighbor clique test is
 * complete only for a completed PDAG).  A malformed cyclic input simply yields nothing.
 *
 * @author josephramsey
 */
public final class ConsistentDagIterator implements Iterator<Graph>, Iterable<Graph> {

    private final Graph graph;
    private final List<Node> tryOrder;      // graph nodes, min-rank (highest peel priority) first
    private final Map<Node, Integer> rank;  // node -> index in tryOrder
    private final boolean distinct;         // emit each extension once (true) or per-order (false)
    private final int n;

    private final Set<Node> present;        // nodes not yet peeled = current induced subgraph
    private final List<Node> partial;       // sink-first peel sequence under construction
    private final Deque<Frame> stack;

    private boolean pendingEmpty;           // n == 0: the single (empty) extension
    private Graph lookahead;                // buffered next() value

    private static final class Frame {
        final List<Node> candidates;
        int cursor;

        Frame(List<Node> candidates) {
            this.candidates = candidates;
        }
    }

    /**
     * Enumerate distinct extensions, preferring the graph's own node order (forward).
     *
     * @param graph a DAG or CPDAG.
     */
    public ConsistentDagIterator(Graph graph) {
        this(graph, graph.getNodes(), true, true);
    }

    /**
     * @param graph        a DAG or CPDAG.
     * @param initialOrder rank source: the first extension emitted is the one induced by
     *                     {@code getValidOrder(initialOrder, forward)}.
     * @param forward      forward (true) or reverse (false) rank preference.
     * @param distinct     true to emit each consistent extension exactly once; false to emit one
     *                     DAG per valid order (i.e. with linear-extension multiplicity).
     */
    public ConsistentDagIterator(Graph graph, List<Node> initialOrder, boolean forward, boolean distinct) {
        this.graph = graph;
        this.distinct = distinct;
        this.n = graph.getNodes().size();

        List<Node> ranked = new ArrayList<>(initialOrder);
        if (forward) Collections.reverse(ranked);
        Set<Node> seen = new HashSet<>(ranked);
        for (Node v : graph.getNodes()) {
            if (!seen.contains(v)) ranked.add(v);
        }
        this.tryOrder = ranked;

        this.rank = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) rank.put(ranked.get(i), i);

        this.present = new LinkedHashSet<>(graph.getNodes());
        this.partial = new ArrayList<>(n);
        this.stack = new ArrayDeque<>();

        if (n == 0) {
            this.pendingEmpty = true;
        } else {
            stack.push(new Frame(validSinks()));
        }
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
     * Advances the DFS to the next extension, or returns null when exhausted. With {@code distinct}
     * the canonical prune in {@link #validSinks()} guarantees every completed peel is the lex-min
     * order of its DAG, so every leaf is emitted (each DAG reached exactly once); with
     * {@code distinct == false} every leaf is one valid order.
     */
    private Graph advance() {
        if (pendingEmpty) {
            pendingEmpty = false;
            return new EdgeListGraph(graph.getNodes());     // the empty extension
        }

        while (!stack.isEmpty()) {
            Frame f = stack.peek();

            if (f.cursor >= f.candidates.size()) {           // level exhausted: backtrack
                stack.pop();
                if (!partial.isEmpty()) {
                    present.add(partial.remove(partial.size() - 1));
                }
                continue;
            }

            Node choice = f.candidates.get(f.cursor++);
            present.remove(choice);
            partial.add(choice);

            if (present.isEmpty()) {                          // a completed peel sequence
                Graph dag = buildDag();
                present.add(partial.remove(partial.size() - 1));   // undo, keep DFS alive
                return dag;
            }

            stack.push(new Frame(validSinks()));             // descend
        }

        return null;
    }

    /**
     * Builds the consistent extension for the current full peel sequence {@code partial}
     * (sink-first): directed edges are copied; each undirected edge is oriented from the node
     * peeled later (the more source-like end) to the node peeled earlier (the sink end).
     */
    private Graph buildDag() {
        Map<Node, Integer> peelIdx = new HashMap<>();
        for (int i = 0; i < partial.size(); i++) peelIdx.put(partial.get(i), i);

        Graph dag = new EdgeListGraph(graph.getNodes());
        for (Edge e : graph.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            Endpoint pa = e.getProximalEndpoint(a), da = e.getDistalEndpoint(a);

            if (pa == Endpoint.TAIL && da == Endpoint.ARROW) {
                dag.addDirectedEdge(a, b);                    // a --> b
            } else if (pa == Endpoint.ARROW && da == Endpoint.TAIL) {
                dag.addDirectedEdge(b, a);                    // b --> a
            } else {                                          // a --- b: earlier peel is the child
                if (peelIdx.get(a) < peelIdx.get(b)) dag.addDirectedEdge(b, a);
                else dag.addDirectedEdge(a, b);
            }
        }
        return dag;
    }

    /**
     * Valid sinks of the current induced subgraph on {@code present}, in {@code tryOrder}. With
     * {@code distinct}, a sink {@code x} is dropped when it forms an adjacent commuting inversion
     * with the previously peeled node {@code prev} -- i.e. {@code x} outranks {@code prev} and
     * shares no edge with it, so peeling {@code x} here would be a non-lex-min ordering of a DAG
     * also reached by peeling {@code x} later. Deferring it makes the enumeration duplicate-free
     * without walking the extra orders.
     */
    private List<Node> validSinks() {
        Node prev = partial.isEmpty() ? null : partial.get(partial.size() - 1);
        int prevRank = (prev == null) ? -1 : rank.get(prev);

        List<Node> sinks = new ArrayList<>();
        for (Node x : tryOrder) {
            if (!present.contains(x) || invalidSink(x)) continue;
            if (distinct && prev != null && rank.get(x) < prevRank && !graph.isAdjacentTo(x, prev)) {
                continue;                                     // non-canonical: x could precede prev
            }
            sinks.add(x);
        }
        return sinks;
    }

    /**
     * The variable x is a valid sink of the induced subgraph on {@code present} if it has no
     * child there and its neighbors x--z (within {@code present}) form a clique; otherwise it is
     * invalid. Edges to peeled nodes are ignored, so this is {@code getValidOrder}'s
     * {@code invalidSink} on the remaining subgraph, with no graph copy.
     *
     * @param x the node to test (assumed present).
     * @return true if invalid, false if valid.
     */
    private boolean invalidSink(Node x) {
        List<Node> neighbors = new ArrayList<>();

        for (Edge edge : graph.getEdges(x)) {
            Node other = edge.getDistalNode(x);
            if (!present.contains(other)) continue;                        // ignore peeled nodes
            if (edge.getDistalEndpoint(x) == Endpoint.ARROW) return true;  // x --> other: a child
            if (edge.getProximalEndpoint(x) == Endpoint.TAIL) neighbors.add(other); // x --- other
        }

        for (int i = 0; i < neighbors.size(); i++) {
            for (int j = i + 1; j < neighbors.size(); j++) {
                if (!graph.isAdjacentTo(neighbors.get(i), neighbors.get(j))) return true;
            }
        }

        return false;
    }
}
