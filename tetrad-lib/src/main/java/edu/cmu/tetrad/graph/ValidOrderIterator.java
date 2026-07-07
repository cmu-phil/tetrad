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
 * Lazily enumerates EVERY valid causal order of a DAG or CPDAG, each exactly once.
 * <p>
 * A valid order is a topological order of some consistent DAG extension of the graph.
 * This is the enumerating generalization of Bryan Andrews' {@code getValidOrder}: that
 * method peels off, at each step, the FIRST node (in a supplied order) that can legally be
 * the last node of an extension -- a Dor-Tarsi <em>valid sink</em>: no child, and its
 * undirected neighbors form a clique.  Here we instead branch over ALL valid sinks at each
 * peel.  Each complete sequence of sink choices is one valid order (reversed); each order
 * arises from exactly one sequence; and by the standard collider argument every topological
 * order of every consistent extension is reachable -- so this yields each valid order once,
 * none missed.
 * <p>
 * The enumeration is lazy and uses O(n^2) working memory (it operates on an induced-subgraph
 * membership set rather than copying the graph), so the potentially exponential number of
 * orders is fine to iterate and break out of early.
 * <p>
 * The first order produced equals {@code getValidOrder(initialOrder, forward)} for the same
 * arguments, and subsequent orders relax that greedy first-fit in DFS order -- a useful
 * cross-check.
 * <p>
 * Assumes a DAG or CPDAG (no bidirected edges; for a raw PDAG the undirected-neighbor clique
 * test is not sufficient).  A well-formed CPDAG always has at least one valid sink at every
 * step; a cyclic/malformed input simply yields no orders (empty iterator).
 *
 * @author josephramsey
 */
public final class ValidOrderIterator implements Iterator<List<Node>>, Iterable<List<Node>> {

    private final Graph graph;
    private final List<Node> tryOrder;      // graph nodes ranked by enumeration preference
    private final int n;

    private final Set<Node> present;        // nodes not yet peeled = current induced subgraph
    private final List<Node> partial;       // sink-first order under construction
    private final Deque<Frame> stack;       // DFS over sink choices

    private boolean pendingEmpty;           // n == 0: exactly one valid order, the empty list
    private List<Node> lookahead;           // buffered next() value

    /**
     * A choice point: the valid sinks available at this peel, and a cursor over which have
     * been explored.
     */
    private static final class Frame {
        final List<Node> candidates;
        int cursor;

        Frame(List<Node> candidates) {
            this.candidates = candidates;
        }
    }

    /**
     * Enumerate preferring the graph's own node order (forward).
     *
     * @param graph a DAG or CPDAG.
     */
    public ValidOrderIterator(Graph graph) {
        this(graph, graph.getNodes(), true);
    }

    /**
     * @param graph        a DAG or CPDAG.
     * @param initialOrder orders are produced preferring to keep nodes close to this order;
     *                     the first order returned matches {@code getValidOrder(initialOrder, forward)}.
     * @param forward      whether the preference runs in the forward (true) or reverse (false)
     *                     direction, matching {@code getValidOrder}.
     */
    public ValidOrderIterator(Graph graph, List<Node> initialOrder, boolean forward) {
        this.graph = graph;
        this.n = graph.getNodes().size();

        // Same convention as getValidOrder: forward peels from the tail of initialOrder.
        List<Node> ranked = new ArrayList<>(initialOrder);
        if (forward) Collections.reverse(ranked);

        // Defensively append any graph nodes missing from initialOrder, preserving graph order.
        Set<Node> seen = new HashSet<>(ranked);
        for (Node v : graph.getNodes()) {
            if (!seen.contains(v)) ranked.add(v);
        }
        this.tryOrder = ranked;

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
    public Iterator<List<Node>> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        if (lookahead == null) lookahead = advance();
        return lookahead != null;
    }

    @Override
    public List<Node> next() {
        if (!hasNext()) throw new NoSuchElementException();
        List<Node> result = lookahead;
        lookahead = null;
        return result;
    }

    /**
     * Advances the DFS to the next complete valid order, or returns null when exhausted.
     */
    private List<Node> advance() {
        if (pendingEmpty) {                                  // the n == 0 order
            pendingEmpty = false;
            return new ArrayList<>();
        }

        while (!stack.isEmpty()) {
            Frame f = stack.peek();

            if (f.cursor >= f.candidates.size()) {           // this level exhausted: backtrack
                stack.pop();
                if (!partial.isEmpty()) {
                    present.add(partial.remove(partial.size() - 1));   // un-peel the parent's choice
                }
                continue;
            }

            Node choice = f.candidates.get(f.cursor++);
            present.remove(choice);
            partial.add(choice);

            if (present.isEmpty()) {                          // complete sink-first sequence
                List<Node> order = new ArrayList<>(partial);
                present.add(partial.remove(partial.size() - 1));       // undo, keep DFS alive
                Collections.reverse(order);                  // source-first causal order
                return order;
            }

            stack.push(new Frame(validSinks()));             // descend
        }

        return null;
    }

    /**
     * Valid sinks of the current induced subgraph on {@code present}, listed in {@code tryOrder}.
     */
    private List<Node> validSinks() {
        List<Node> sinks = new ArrayList<>();
        for (Node x : tryOrder) {
            if (present.contains(x) && !invalidSink(x)) sinks.add(x);
        }
        return sinks;
    }

    /**
     * The variable x is a valid sink of the induced subgraph on {@code present} if it has no
     * children there and its neighbors x--z (within {@code present}) form a clique; otherwise
     * it is an invalid sink.  Edges to already-peeled nodes are ignored, so this is exactly
     * {@code getValidOrder}'s {@code invalidSink} evaluated on the remaining subgraph -- with
     * no graph copy.
     *
     * @param x the node to test (assumed to be in {@code present}).
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